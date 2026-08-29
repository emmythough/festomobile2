package com.example.ui.memory

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendMode
import com.example.data.FestoAppState
import com.example.data.HermesHistoryEntry
import com.example.data.HermesSession
import com.example.ui.components.NovaAvatar
import com.example.ui.components.markdown.RichMessageRenderer
import com.example.ui.components.hermesSessionSubtitle
import com.example.ui.theme.FestoTheme

/** Full-screen Wendy memory screen -- HERMES mode only, opened from the
 * drawer's memory row. The gateway has no /api/search endpoint, so
 * "browsing memory" is exactly what it does offer: the session list
 * (GET /api/sessions), read-only transcripts (GET /api/sessions/{id}/
 * messages), and a client-side, case-insensitive filter over whatever
 * was fetched. Nothing here invents server features.
 *
 * Two views inside one screen, crossfaded on the browsed session id:
 * the session list, and one session's transcript with its search field.
 * Transcripts render user+assistant only -- empty content and tool rows
 * are skipped (they are Wendy's plumbing, not conversation bubbles).
 * The currently-picked Wendy session (the one the app chats inside)
 * carries a subtle "Wendy session" marker in both views. */
@Composable
fun MemoryScreen(
    appState: FestoAppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors

    // Refresh the session list every time the browser opens -- same
    // re-check-once-per-open pattern as the drawer's outbox badge.
    LaunchedEffect(Unit) {
        if (appState.backendMode == BackendMode.HERMES) {
            appState.loadHermesSessions()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("memory_browser_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Wendy memory",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Browse every conversation, Telegram and here",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = extendedColors.inkTertiary
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).testTag("memory_browser_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close memory browser",
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Crossfade(
                targetState = appState.memoryBrowserSessionId,
                animationSpec = tween(180),
                label = "memory_browse_crossfade",
                modifier = Modifier.weight(1f)
            ) { sessionId ->
                if (sessionId == null) {
                    MemorySessionList(appState = appState)
                } else {
                    MemoryTranscriptView(appState = appState, sessionId = sessionId)
                }
            }
        }
    }
}

// ---- Session list ------------------------------------------------------

@Composable
private fun MemorySessionList(appState: FestoAppState) {
    val extendedColors = FestoTheme.colors
    when {
        appState.hermesSessionsLoading && appState.hermesSessions.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = extendedColors.brandNova,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Loading Wendy's sessions...",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                        color = extendedColors.inkTertiary
                    )
                }
            }
        }
        appState.hermesSessions.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = appState.hermesSessionsError
                            ?: "No sessions on the gateway yet.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = extendedColors.inkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { appState.loadHermesSessions() }) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = extendedColors.brandNova
                        )
                    }
                }
            }
        }
        else -> {
            // loadHermesSessions() already sorts Telegram-first, then by
            // last activity descending -- the same order the Settings
            // picker shows, kept identical on purpose.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("memory_session_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(appState.hermesSessions, key = { _, session -> session.id }) { _, session ->
                    MemorySessionRow(
                        session = session,
                        isPicked = session.id == appState.hermesSessionId,
                        onClick = { appState.openMemoryTranscript(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySessionRow(
    session: HermesSession,
    isPicked: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = FestoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.surfaceSubtle)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (session.isTelegram) {
                Spacer(modifier = Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(extendedColors.accentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Wendy · Telegram",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = extendedColors.accentBlue
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hermesSessionSubtitle(session),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            color = extendedColors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        session.preview?.let { preview ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = extendedColors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPicked) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(extendedColors.brandNovaSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Wendy session",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = extendedColors.brandNova
                )
            }
        }
    }
}

// ---- Transcript view ---------------------------------------------------

@Composable
private fun MemoryTranscriptView(
    appState: FestoAppState,
    sessionId: String
) {
    val extendedColors = FestoTheme.colors
    // Search state resets whenever another session is opened.
    var searchQuery by remember(sessionId) { mutableStateOf("") }
    val query = searchQuery.trim()

    // The transcript renders user+assistant only -- empty content and
    // tool rows are skipped (Wendy's plumbing, not conversation bubbles),
    // then the client-side, case-insensitive search filter applies.
    val displayableMessages = appState.memoryBrowserMessages.filter {
        (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
    }
    val visibleMessages = if (query.isBlank()) {
        displayableMessages
    } else {
        displayableMessages.filter { it.content.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-header: back to the list, the session title, and the
        // "Wendy session" marker when this is the session the app chats
        // inside (the one Telegram shares).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { appState.closeMemoryTranscript() },
                modifier = Modifier.size(30.dp).testTag("memory_transcript_back")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back to sessions",
                    tint = extendedColors.inkTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = appState.memoryBrowserTitle ?: "Session",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (sessionId == appState.hermesSessionId) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(extendedColors.brandNovaSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("memory_picked_session_marker")
                ) {
                    Text(
                        text = "Wendy session",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = extendedColors.brandNova
                    )
                }
            }
        }

        // Search field (filters the already-fetched transcript locally)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("memory_search_field"),
            placeholder = {
                Text(
                    text = "Search this conversation...",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = extendedColors.inkTertiary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = extendedColors.inkTertiary,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = extendedColors.inkTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = extendedColors.brandNova,
                unfocusedBorderColor = extendedColors.borderHairline,
                cursorColor = extendedColors.brandNova
            )
        )

        // "N matches" counter while filtering
        if (query.isNotBlank()) {
            Text(
                text = "${visibleMessages.size} ${if (visibleMessages.size == 1) "match" else "matches"}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = extendedColors.brandNova,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag("memory_match_count")
            )
        }

        when {
            appState.memoryBrowserLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = extendedColors.brandNova,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading Wendy's messages...",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                            color = extendedColors.inkTertiary
                        )
                    }
                }
            }
            appState.memoryBrowserError != null && appState.memoryBrowserMessages.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = appState.memoryBrowserError ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = extendedColors.inkTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                val session = appState.hermesSessions.firstOrNull { it.id == sessionId }
                                    ?: HermesSession(
                                        id = sessionId,
                                        title = appState.memoryBrowserTitle ?: "Session",
                                        messageCount = 0,
                                        lastActivityAtMs = null,
                                        source = null,
                                        preview = null
                                    )
                                appState.openMemoryTranscript(session)
                            }
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = extendedColors.brandNova
                            )
                        }
                    }
                }
            }
            visibleMessages.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) {
                            "No messages in this conversation yet."
                        } else {
                            "No matches for \"$query\""
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = extendedColors.inkTertiary
                    )
                }
            }
            else -> {
                // The endpoint has no pagination params (verified contract),
                // so the full transcript is loaded once and rendered lazily.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("memory_transcript_list"),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    itemsIndexed(visibleMessages) { _, entry ->
                        TranscriptEntry(entry = entry, query = query)
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptEntry(entry: HermesHistoryEntry, query: String) {
    when (entry.role) {
        "user" -> UserTranscriptBubble(entry.content, query)
        else -> AssistantTranscriptBlock(entry.content, query)
    }
}

// Bubble language mirrors ChatMessageItem exactly: user = filled card on
// the right, assistant = open on the canvas beside the avatar on the left.

@Composable
private fun UserTranscriptBubble(content: String, query: String) {
    val extendedColors = FestoTheme.colors
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 4.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_user_bubble")
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(extendedColors.surfaceContainer)
                .border(1.dp, extendedColors.borderMedium, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (query.isBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 21.sp,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                HighlightedTranscriptText(
                    content = content,
                    query = query,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 21.sp,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AssistantTranscriptBlock(content: String, query: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_assistant_bubble")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        NovaAvatar(
            size = 28.dp,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp)
        )
        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (query.isBlank()) {
                RichMessageRenderer(content = content)
            } else {
                // Markdown rendering can't take span styling, so while a
                // search filter is active the transcript falls back to
                // plain text with match highlights.
                HighlightedTranscriptText(
                    content = content,
                    query = query,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 21.sp,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ---- Search highlighting ----------------------------------------------

@Composable
private fun HighlightedTranscriptText(
    content: String,
    query: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    Text(
        text = remember(content, query, extendedColors.brandNovaSoft) {
            buildHighlightedString(content, query, extendedColors.brandNovaSoft)
        },
        style = style,
        color = color,
        modifier = modifier
    )
}

/** Case-insensitive match highlighting: every occurrence of [query] in
 * [content] gets a brandNovaSoft background. The query is regex-escaped
 * so searches like "c++" or "(draft)" stay literal. */
private fun buildHighlightedString(content: String, query: String, highlight: Color): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AnnotatedString(content)
    val builder = AnnotatedString.Builder()
    var cursor = 0
    Regex(Regex.escape(trimmed), RegexOption.IGNORE_CASE).findAll(content).forEach { match ->
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (start > cursor) builder.append(content.substring(cursor, start))
        builder.pushStyle(SpanStyle(background = highlight))
        builder.append(content.substring(start, endExclusive))
        builder.pop()
        cursor = endExclusive
    }
    if (cursor < content.length) builder.append(content.substring(cursor))
    return builder.toAnnotatedString()
}

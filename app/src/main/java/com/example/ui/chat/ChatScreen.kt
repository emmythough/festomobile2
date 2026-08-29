package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.example.data.BackendMode
import com.example.data.FestoAppState
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ModelBadgeChip
import com.example.ui.components.NovaAvatar
import com.example.ui.theme.FestoTheme

@Composable
fun ChatScreen(
    appState: FestoAppState,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Opens the voice overlay, requesting RECORD_AUDIO permission the first
    // time. Once granted, starts the real mic recording.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        appState.onMicPermissionResult(granted)
        if (granted) {
            appState.isVoiceOverlayOpen = true
            appState.startVoiceRecording()
        } else {
            appState.isVoiceOverlayOpen = true
            appState.voiceLiveTranscript = "Microphone permission denied. Enable it in Settings to use voice."
        }
    }
    val openVoice: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        appState.onMicPermissionResult(granted)
        if (granted) {
            appState.isVoiceOverlayOpen = true
            appState.startVoiceRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-scroll to bottom on new messages
    val messages = appState.activeMessages
    LaunchedEffect(messages.size, appState.isStreamingResponse) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top Bar
            ChatTopBar(
                appState = appState,
                onOpenDrawer = { appState.isDrawerOpen = true },
                onOpenModelPicker = { appState.isModelSheetOpen = true },
                onOpenVoice = openVoice,
                onNewChat = { appState.startFreshConversation() }
            )

            // Divider Hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extendedColors.borderHairline)
            )

            // Session reset failure chip -- a failed "Start Fresh" must not
            // look like it succeeded (same dismissible-chip pattern the
            // composer uses for attachmentError). Tap to dismiss.
            appState.sessionResetError?.let { resetError ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(extendedColors.accentAmberSoft)
                        .clickable { appState.sessionResetError = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = resetError,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = extendedColors.accentAmber
                    )
                }
            }

            // Hermes notice chip -- same dismissible-chip pattern as
            // sessionResetError above: covers "no session picked yet",
            // fresh-chat attempts in Hermes mode, and other situations
            // the gateway genuinely can't serve.
            appState.hermesNotice?.let { notice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(extendedColors.accentAmberSoft)
                        .clickable { appState.hermesNotice = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = extendedColors.accentAmber
                    )
                }
            }

            // Message Stream List or Empty Starter State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyChatStarter(
                        onSelectPrompt = { prompt ->
                            appState.sendMessage(prompt)
                        },
                        onOpenVoice = openVoice
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageItem(
                                message = msg,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            )
                        }
                    }
                }
            }

            // Hermes tool activity -- while the gateway runs a tool
            // (tool.started / tool.progress / ... frames), show what it's
            // doing as a live line above the composer. Cleared the moment
            // assistant text starts flowing again or the turn ends.
            appState.streamingTool?.let { tool ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("hermes_tool_activity"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (tool.detail.isBlank()) {
                            "${tool.toolName}..."
                        } else {
                            "${tool.toolName} - ${tool.detail}"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = extendedColors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Composer Input Bar
            ChatComposer(
                inputText = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() || appState.pendingAttachment != null) {
                        val text = inputText
                        inputText = ""
                        appState.sendMessage(text)
                    }
                },
                onOpenVoice = openVoice,
                onPickAttachment = { filename, bytes -> appState.setPendingAttachment(filename, bytes) },
                onAttachmentReadFailed = { filename ->
                    appState.attachmentError = "Couldn't read \"$filename\" -- try picking it again."
                },
                pendingAttachmentFilename = appState.pendingAttachment?.filename,
                onClearAttachment = { appState.clearPendingAttachment() },
                attachmentError = appState.attachmentError,
                onDismissAttachmentError = { appState.attachmentError = null },
                isStreaming = appState.isStreamingResponse
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    appState: FestoAppState,
    onOpenDrawer: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenVoice: () -> Unit,
    onNewChat: () -> Unit
) {
    val extendedColors = FestoTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(36.dp).testTag("drawer_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open Drawer",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (appState.backendMode == BackendMode.HERMES) {
                // Hermes mode has no model switch (the gateway picks) --
                // show which backend is live instead of the model chip.
                HermesBackendBadge()
            } else {
                ModelBadgeChip(
                    model = appState.selectedModel,
                    onClick = onOpenModelPicker
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Voice Shortcut Button
            IconButton(
                onClick = onOpenVoice,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(extendedColors.brandNovaSoft)
                    .testTag("voice_shortcut_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Voice Mode",
                    tint = extendedColors.brandNova,
                    modifier = Modifier.size(18.dp)
                )
            }

            // New Conversation Action -- Gen 1 only: Hermes mode shares
            // ONE session with Telegram, there's nothing fresh to start.
            if (appState.backendMode == BackendMode.GEN1) {
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.size(36.dp).testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** Static backend badge shown in the top bar while on the Hermes
 * gateway -- the model chip would be a lie here (the gateway picks the
 * model), so this just says which brain you're talking to. */
@Composable
private fun HermesBackendBadge() {
    val extendedColors = FestoTheme.colors
    Row(
        modifier = Modifier
            .testTag("backend_badge_chip")
            .clip(RoundedCornerShape(20.dp))
            .background(extendedColors.brandNovaSoft)
            .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = extendedColors.brandNova,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Hermes gateway",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyChatStarter(
    onSelectPrompt: (String) -> Unit,
    onOpenVoice: () -> Unit
) {
    val extendedColors = FestoTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NovaAvatar(size = 56.dp, isPulsing = true)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What would you like to explore?",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Unified text & voice intelligence with cross-session recall",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                color = extendedColors.inkTertiary
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Starter Prompt Suggestion Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StarterCard(
                icon = Icons.Rounded.Speed,
                title = "Model Cost & Latency Benchmark",
                subtitle = "Compare Flash, Sonnet & Haiku",
                onClick = { onSelectPrompt("Compare the pricing, context length, and latency tradeoffs among our available models.") }
            )
            StarterCard(
                icon = Icons.Rounded.Code,
                title = "Mobile Audio Protocol Spec",
                subtitle = "Review 24kHz PCM16 buffer management & barge-in",
                onClick = { onSelectPrompt("Summarize how our 24kHz mono PCM16 audio streaming and instant barge-in work on Android.") }
            )
            StarterCard(
                icon = Icons.Rounded.Psychology,
                title = "Vector Memory Indexing",
                subtitle = "Analyze pgvector 1536-dim HNSW recall",
                onClick = { onSelectPrompt("Explain how pgvector HNSW indexing retrieves durable memories across separate conversations.") }
            )
        }
    }
}

@Composable
private fun StarterCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val extendedColors = FestoTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.surfaceSubtle)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = extendedColors.brandNova,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = extendedColors.inkTertiary
                    )
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenVoice: () -> Unit,
    onPickAttachment: (filename: String, bytes: ByteArray) -> Unit,
    onAttachmentReadFailed: (filename: String) -> Unit,
    pendingAttachmentFilename: String?,
    onClearAttachment: () -> Unit,
    attachmentError: String?,
    onDismissAttachmentError: () -> Unit,
    isStreaming: Boolean
) {
    val extendedColors = FestoTheme.colors
    val context = LocalContext.current
    val composerScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val canSend = (inputText.isNotBlank() || pendingAttachmentFilename != null) && !isStreaming

    // Storage Access Framework picker -- no runtime permission needed
    // (unlike RECORD_AUDIO above), the system handles access to whatever
    // the user picks.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        // Reading the full byte stream must never run on the composition
        // thread -- an unconstrained read up to the 12MB cap is a real ANR
        // risk there. IO dispatcher, hop back automatically for the state
        // write onPickAttachment does.
        composerScope.launch(Dispatchers.IO) {
            val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment ?: "attachment"
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                withContext(Dispatchers.Main) {
                    if (bytes != null) {
                        // Light tick on a successful pick only -- read
                        // failures below stay silent (the amber error chip
                        // is the feedback there).
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPickAttachment(name, bytes)
                    } else {
                        onAttachmentReadFailed(name)
                    }
                }
            } catch (_: java.io.IOException) {
                withContext(Dispatchers.Main) { onAttachmentReadFailed(name) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Pending attachment chip -- shown above the input row once a file
        // is picked, until sent or cleared.
        if (pendingAttachmentFilename != null) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(extendedColors.brandNovaSoft)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = null,
                    tint = extendedColors.brandNova,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = pendingAttachmentFilename,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = extendedColors.brandNova,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = extendedColors.brandNova,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClearAttachment)
                )
            }
        }
        if (attachmentError != null) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(extendedColors.accentAmberSoft)
                    .clickable(onClick = onDismissAttachmentError)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = attachmentError,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = extendedColors.accentAmber
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(extendedColors.surfaceSubtle)
                .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach a file -- real, working: opens the system picker,
            // reads the bytes, and the message carries it as base64 to
            // POST /api/chat's "attachment" field (see AI_STUDIO_BRIEF.md
            // R-series for the rendering side; this is the actual gap the
            // owner flagged as missing).
            IconButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.size(36.dp).testTag("composer_attach_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach a file",
                    tint = extendedColors.brandNova,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Microphone action button
            IconButton(
                onClick = onOpenVoice,
                modifier = Modifier.size(36.dp).testTag("composer_mic_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Speak to Assistant",
                    tint = extendedColors.brandNova,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text input field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Message assistant...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = extendedColors.inkTertiary
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            // Send Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) extendedColors.brandNova else extendedColors.surfaceContainer)
                    .clickable(enabled = canSend) {
                        // One light tick per actual send -- the enabled
                        // gate means disabled taps stay silent.
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSend()
                    }
                    .testTag("chat_send_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = extendedColors.brandNova,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else extendedColors.inkTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

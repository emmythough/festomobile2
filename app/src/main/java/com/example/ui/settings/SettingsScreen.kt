package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendMode
import com.example.data.FestoAppState
import com.example.data.HermesSession
import com.example.data.ThemeMode
import com.example.ui.components.hermesSessionSubtitle
import com.example.ui.theme.FestoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: FestoAppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val extendedColors = FestoTheme.colors
    val hapticFeedback = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = extendedColors.surfaceDialog,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extendedColors.borderMedium)
            )
        },
        modifier = modifier.testTag("settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The Hermes section (URL + key + session picker) makes
                // this sheet taller than the theme-only layout ever was
                // -- scroll instead of clipping on small screens.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header -- same icon + title/subtitle + close pattern as
            // MemorySheet / ModelPickerSheet / UsageSheet.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Changes apply instantly and are remembered",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = extendedColors.inkTertiary
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Appearance section
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                ),
                color = extendedColors.inkTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Theme segmented control -- taps switch the live scheme
            // immediately (MainActivity recomposes from appState.themeMode)
            // and write through to the persisted store.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = appState.themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) extendedColors.brandNova else Color.Transparent)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                appState.setThemeMode(mode)
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            ),
                            color = if (isSelected) Color.White else extendedColors.inkTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "System follows your device's light or dark setting.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = extendedColors.inkTertiary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Backend section -- which brain Wendy answers from. Gen 1 is
            // the original direct connection (model switching, voice,
            // file delivery); Hermes is the gateway Telegram's Wendy
            // runs behind, so the app and Telegram share ONE session.
            Text(
                text = "BACKEND",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                ),
                color = extendedColors.inkTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Backend segmented control -- same shape/tap behavior as the
            // theme control above; switching reloads the transcript from
            // the newly active backend.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.surfaceSubtle)
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BackendMode.entries.forEach { mode ->
                    val isSelected = appState.backendMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) extendedColors.brandNova else Color.Transparent)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                appState.setBackendMode(mode)
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.5.sp
                            ),
                            color = if (isSelected) Color.White else extendedColors.inkTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (appState.backendMode) {
                    BackendMode.GEN1 ->
                        "Gen 1 talks straight to Wendy's own server -- model switching, voice and file delivery."
                    BackendMode.HERMES ->
                        "Hermes goes through the gateway Telegram's Wendy runs on -- one shared conversation, no model switch."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = extendedColors.inkTertiary
                )
            )

            // Hermes gateway configuration -- URL, API key, and the
            // shared Wendy session picker. Only shown in Hermes mode;
            // Gen 1's settings stay exactly as they were.
            if (appState.backendMode == BackendMode.HERMES) {
                HermesGatewaySection(appState = appState, hapticFeedback = hapticFeedback)
            }
        }
    }
}

@Composable
private fun HermesGatewaySection(
    appState: FestoAppState,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val extendedColors = FestoTheme.colors
    var showApiKey by remember { mutableStateOf(false) }

    // Fetch the gateway's session list the first time this section opens
    // (and whenever the user re-enters Hermes mode with nothing loaded);
    // the Refresh/Retry button re-fetches on demand.
    LaunchedEffect(appState.backendMode) {
        if (appState.backendMode == BackendMode.HERMES &&
            appState.hermesSessions.isEmpty() &&
            !appState.hermesSessionsLoading &&
            appState.hermesSessionsError == null
        ) {
            appState.loadHermesSessions()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "GATEWAY URL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            ),
            color = extendedColors.inkTertiary
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = appState.hermesBaseUrl,
            onValueChange = { appState.updateHermesBaseUrl(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hermes_url_field"),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = extendedColors.brandNova,
                unfocusedBorderColor = extendedColors.borderHairline
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "API KEY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            ),
            color = extendedColors.inkTertiary
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = appState.hermesApiKey,
            onValueChange = { appState.updateHermesApiKey(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hermes_api_key_field"),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            placeholder = {
                Text(
                    text = "Paste your Hermes API key",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = extendedColors.inkTertiary
                    )
                )
            },
            visualTransformation = if (showApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showApiKey = !showApiKey },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                        tint = extendedColors.inkTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = extendedColors.brandNova,
                unfocusedBorderColor = extendedColors.borderHairline
            )
        )

        Text(
            text = "Stored on this device only, sent as a Bearer header -- same handling as the Gen 1 token.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = extendedColors.inkTertiary
            ),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Shared-session picker: which ONE conversation the app
        // chats inside. Must be the session Telegram uses, or the two
        // surfaces fork into different brains -- the exact bug this
        // backend exists to avoid.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WENDY SESSION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                ),
                color = extendedColors.inkTertiary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(extendedColors.brandNovaSoft)
                    .clickable { appState.loadHermesSessions() }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Refresh",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    color = extendedColors.brandNova
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (appState.hermesSessionId == null) {
            // Not picked yet -- say why it matters before the list.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(extendedColors.brandNovaSoft)
                    .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Wendy and Telegram share one conversation. Pick the session Telegram is using (it's labeled Telegram) so both stay in sync.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            appState.hermesSessionsLoading && appState.hermesSessions.isEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = extendedColors.brandNova
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading sessions from the gateway...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = extendedColors.inkTertiary
                    )
                }
            }
            appState.hermesSessions.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = appState.hermesSessionsError ?: "No sessions to show.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = extendedColors.inkTertiary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(extendedColors.brandNovaSoft)
                            .clickable { appState.loadHermesSessions() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = extendedColors.brandNova
                        )
                    }
                }
            }
            else -> {
                appState.hermesSessionsError?.let { refreshError ->
                    Text(
                        text = refreshError,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = extendedColors.accentAmber
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .testTag("hermes_session_picker"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appState.hermesSessions, key = { it.id }) { session ->
                        HermesSessionRow(
                            session = session,
                            isSelected = session.id == appState.hermesSessionId,
                            hapticFeedback = hapticFeedback,
                            onSelect = { appState.selectHermesSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HermesSessionRow(
    session: HermesSession,
    isSelected: Boolean,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSelect: () -> Unit
) {
    val extendedColors = FestoTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) extendedColors.surfaceContainer else extendedColors.surfaceSubtle)
            .border(
                1.5.dp,
                if (isSelected) extendedColors.brandNova else extendedColors.borderHairline,
                RoundedCornerShape(12.dp)
            )
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (session.isTelegram) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            // accentBlueSoft exists only as a raw palette
                            // color, not on FestoExtendedColors -- build the
                            // same 15%-alpha tint from the theme's blue.
                            .background(extendedColors.accentBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Telegram",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = extendedColors.accentBlue
                        )
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(extendedColors.brandNova),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = sessionSubtitle(session),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = extendedColors.inkTertiary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            session.preview?.let { preview ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = extendedColors.inkTertiary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun sessionSubtitle(session: HermesSession): String = hermesSessionSubtitle(session)

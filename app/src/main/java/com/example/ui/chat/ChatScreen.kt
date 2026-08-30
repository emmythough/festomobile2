package com.example.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.FestoAppState
import com.example.data.HermesImageAttachment
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.NovaAvatar
import com.example.ui.theme.FestoTheme
import com.example.ui.voice.HermesDictation
import com.example.ui.voice.HermesVoiceConversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    appState: FestoAppState,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // ---- HERMES voice dictation (on-device) ----
    // The mic actions run platform SpeechRecognizer dictation: live
    // partials in a chip above the composer, final transcript dropped INTO
    // the composer (never auto-sent) so the user can edit before sending.
    // Declared before the launchers below, which call into it from their
    // permission callbacks.
    var isDictating by remember { mutableStateOf(false) }
    var dictationPartial by remember { mutableStateOf("") }
    var dictationError by remember { mutableStateOf<String?>(null) }

    val dictation = remember(context) {
        HermesDictation(
            context = context,
            onPartial = { dictationPartial = it },
            onFinal = { finalText ->
                isDictating = false
                dictationPartial = ""
                inputText = if (inputText.isBlank()) {
                    finalText
                } else {
                    inputText.trimEnd() + " " + finalText
                }
            },
            onError = { message ->
                isDictating = false
                dictationPartial = ""
                dictationError = message
            }
        )
    }
    DisposableEffect(dictation) {
        onDispose { dictation.destroy() }
    }

    fun startDictation() {
        dictationError = null
        dictationPartial = ""
        dictation.start()
        // start() reports unavailability synchronously through onError
        // (which already reset these); only arm the chip when listening.
        if (dictation.isAvailable) isDictating = true
    }

    // "Done" on the chip / tapping the mic again: let the recognizer
    // finish the utterance and deliver its final transcript.
    fun stopDictation() {
        dictation.stopListening()
    }

    // X on the chip: discard everything heard so far.
    fun cancelDictation() {
        dictation.destroy()
        isDictating = false
        dictationPartial = ""
        dictationError = null
    }

    // ---- HERMES voice conversation (hands-free loop) ----
    // Speak -> auto-send on ~1.5s of silence -> reply streams -> spoken
    // aloud -> listen again. Runs ONLY while toggled on (headset button in
    // the top bar) -- nothing auto-starts on app open, and the manual
    // dictation mic above keeps working exactly as before. The controller
    // reuses the dictation recognizer pattern and the composer's send path
    // (FestoAppState.sendMessage); no HTTP and no recognition plumbing is
    // duplicated here.
    val voiceConversation = remember(context) {
        HermesVoiceConversation(context = context, appState = appState)
    }
    DisposableEffect(voiceConversation) {
        onDispose { voiceConversation.stop() }
    }

    // Leaving the screen (dispose) or backgrounding (ON_PAUSE) must tear
    // the loop down: recognizer destroyed, TTS flushed and shut down.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, voiceConversation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                voiceConversation.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingMicActionIsConversation by remember { mutableStateOf(false) }

    // Start the hands-free loop (permission already checked by callers).
    // Manual dictation and the loop share the one recognizer slot, so an
    // in-flight dictation is cancelled first.
    fun startVoiceConversation() {
        if (isDictating || dictationError != null) cancelDictation()
        voiceConversation.start()
    }

    // Starts dictation (or the hands-free loop, depending on which action
    // was pending), requesting RECORD_AUDIO permission the first time.
    // Once granted, resumes whichever action was pending.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        appState.onMicPermissionResult(granted)
        val wantsConversation = pendingMicActionIsConversation
        pendingMicActionIsConversation = false
        if (granted) {
            if (wantsConversation) startVoiceConversation() else startDictation()
        } else if (wantsConversation) {
            voiceConversation.voiceNotice =
                "Microphone permission denied -- enable it in Settings for hands-free voice."
        } else {
            dictationError = "Microphone permission denied -- enable it in Settings to dictate."
        }
    }
    val openVoice: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        appState.onMicPermissionResult(granted)
        if (granted) {
            // The hands-free loop holds the recognizer while it runs;
            // taking over with manual dictation stops the loop first
            // (two SpeechRecognizers would fight over the mic).
            if (voiceConversation.isActive) voiceConversation.stop()
            if (isDictating) stopDictation() else startDictation()
        } else {
            pendingMicActionIsConversation = false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Hands-free headset toggle: starts the loop (routing through the
    // RECORD_AUDIO permission flow on first use) or stops it. Distinct
    // from the dictation mic -- this one speaks the replies back.
    val toggleVoiceConversation: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        appState.onMicPermissionResult(granted)
        if (granted) {
            if (voiceConversation.isActive) {
                voiceConversation.stop()
            } else {
                startVoiceConversation()
            }
        } else {
            pendingMicActionIsConversation = true
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
                onOpenVoice = openVoice,
                conversationActive = voiceConversation.isActive,
                onToggleVoiceConversation = toggleVoiceConversation
            )

            // Divider Hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extendedColors.borderHairline)
            )

            // Hermes notice chip -- dismissible amber chip covering "no
            // session picked yet" and other situations the gateway
            // genuinely can't serve. Tap to dismiss.
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
                        }
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

            // Dictation status -- live partial transcript while the
            // on-device recognizer listens, or a dismissible error.
            if (isDictating || dictationError != null) {
                DictationStatusChip(
                    isListening = isDictating,
                    partial = dictationPartial,
                    error = dictationError,
                    onStop = { stopDictation() },
                    onCancel = { cancelDictation() },
                    onDismissError = { dictationError = null }
                )
            }

            // Voice-conversation loop status -- what the loop is doing
            // right now (listening… / thinking… / speaking…), the mute
            // switch for private situations, and a Stop affordance.
            // Notices (TTS unavailable, dropped send) use the same amber
            // chip pattern as the dictation/attachment errors.
            if (voiceConversation.isActive || voiceConversation.voiceNotice != null) {
                VoiceConversationChip(
                    phase = voiceConversation.phase,
                    partial = voiceConversation.livePartial,
                    muted = voiceConversation.muted,
                    notice = voiceConversation.voiceNotice,
                    onToggleMute = { voiceConversation.setMuted(!voiceConversation.muted) },
                    onStop = { voiceConversation.stop() },
                    onDismissNotice = { voiceConversation.voiceNotice = null }
                )
            }

            // Bottom Composer Input Bar
            ChatComposer(
                inputText = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() || appState.pendingHermesImage != null) {
                        val text = inputText
                        inputText = ""
                        appState.sendMessage(text)
                    }
                },
                onOpenVoice = openVoice,
                pendingImage = appState.pendingHermesImage,
                onPickImage = { filename, jpegBytes ->
                    appState.setPendingHermesImage(filename, jpegBytes)
                },
                onClearPendingImage = { appState.clearPendingHermesImage() },
                onAttachmentReadFailed = { filename ->
                    appState.attachmentError = "Couldn't read \"$filename\" -- try picking it again."
                },
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
    onOpenVoice: () -> Unit,
    conversationActive: Boolean,
    onToggleVoiceConversation: () -> Unit
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

            // The model the gateway is actually using -- no picker; the
            // gateway picks, and the badge just reports it.
            HermesModelBadge(modelName = appState.hermesActiveModel)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hands-free voice conversation toggle -- distinct from the
            // dictation mic: pulsing while the loop runs, tap again to
            // stop it.
            val pulse = if (conversationActive) {
                val transition = rememberInfiniteTransition(label = "voiceConversationPulse")
                val pulseAlpha by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.45f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "voiceConversationPulseAlpha"
                )
                pulseAlpha
            } else {
                1f
            }
            IconButton(
                onClick = onToggleVoiceConversation,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (conversationActive) extendedColors.brandNova
                        else extendedColors.brandNovaSoft
                    )
                    .alpha(pulse)
                    .testTag("hermes_voice_conversation_toggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headset,
                    contentDescription = if (conversationActive) {
                        "Stop voice conversation"
                    } else {
                        "Start voice conversation"
                    },
                    tint = if (conversationActive) Color.White else extendedColors.brandNova,
                    modifier = Modifier.size(18.dp)
                )
            }

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
        }
    }
}

/** Top-bar model badge: shows the model the gateway actually used for its
 * most recent reply (from the stream's usage frames), falling back to
 * "Wendy" until the first reply arrives. Tapping explains the badge --
 * there is no picker; the gateway picks the model. */
@Composable
private fun HermesModelBadge(modelName: String?) {
    val extendedColors = FestoTheme.colors
    val context = LocalContext.current
    val display = modelName?.takeIf { it.isNotBlank() } ?: "Wendy"
    Row(
        modifier = Modifier
            .testTag("model_badge_chip")
            .clip(RoundedCornerShape(20.dp))
            .background(extendedColors.brandNovaSoft)
            .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(20.dp))
            .clickable {
                Toast.makeText(
                    context,
                    "Model your gateway is using: $display",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
            text = display,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }
}

@Composable
private fun EmptyChatStarter(
    onSelectPrompt: (String) -> Unit
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
            text = "One continuous conversation with Wendy -- here and on Telegram",
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
                title = "Catch Me Up",
                subtitle = "Summarize what we've discussed and what's open",
                onClick = { onSelectPrompt("Catch me up: summarize what we've discussed recently and what's still open.") }
            )
            StarterCard(
                icon = Icons.Rounded.Code,
                title = "Think It Through",
                subtitle = "Reason through a problem step by step",
                onClick = { onSelectPrompt("Help me think through a problem step by step -- ask me clarifying questions first.") }
            )
            StarterCard(
                icon = Icons.Rounded.Psychology,
                title = "What Do You Remember?",
                subtitle = "Recall from our shared conversation",
                onClick = { onSelectPrompt("What do you remember about me and my projects so far?") }
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
    pendingImage: HermesImageAttachment?,
    onPickImage: (filename: String, jpegBytes: ByteArray) -> Unit,
    onClearPendingImage: () -> Unit,
    onAttachmentReadFailed: (filename: String) -> Unit,
    attachmentError: String?,
    onDismissAttachmentError: () -> Unit,
    isStreaming: Boolean
) {
    val extendedColors = FestoTheme.colors
    val context = LocalContext.current
    val composerScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    // A photo counts as content too -- an image-only send (no caption) is
    // valid, same as Telegram's bare photo upload.
    val canSend = (inputText.isNotBlank() || pendingImage != null) && !isStreaming

    // Photo picker -- the system visual-media picker (no runtime
    // permission; the system grants access to whatever is picked). The
    // pick is downscaled to max 1280px / JPEG ~80 OFF the main thread so
    // the request body lands far under the gateway's ~5MB sensible
    // ceiling. Decode failure reports through the same amber chip as any
    // attachment error.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        composerScope.launch(Dispatchers.IO) {
            val picked = decodePickedImageForHermes(context, uri)
            withContext(Dispatchers.Main) {
                if (picked != null) {
                    // Light tick on a successful pick -- read failures
                    // below stay silent (the amber error chip is the
                    // feedback there).
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPickImage(picked.first, picked.second)
                } else {
                    onAttachmentReadFailed("image")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Pending photo chip -- thumbnail of the downscaled JPEG that
        // will ride the next send, until sent or cleared.
        pendingImage?.let { image ->
            PendingImageChip(image = image, onClear = onClearPendingImage)
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
            // Attach an image: opens the system photo picker; the pick
            // becomes a downscaled data-URL image part on the next send
            // (the gateway's content-array contract).
            IconButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.size(36.dp).testTag("composer_attach_image_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = "Attach a photo",
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
                // maxLines = 4 makes this a real multi-line composer, but
                // ImeAction.Send told the keyboard to treat Enter as "send"
                // instead of "newline" -- the exact reported bug (Enter
                // sometimes sending instead of breaking the line). A real,
                // dedicated Send button already exists below (canSend /
                // onSend) and is the only send affordance now; Enter is a
                // plain newline like any normal multi-line text field.
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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

// ---- HERMES dictation + photo-pick support ------------------------------

/** Live status line for HERMES on-device dictation: the recognizer's
 * partial transcript while listening ("Done" finalizes into the composer,
 * X discards), or the failure reason as a dismissible amber chip -- the
 * same chip language the composer already uses for attachment errors. */
@Composable
private fun DictationStatusChip(
    isListening: Boolean,
    partial: String,
    error: String?,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    val extendedColors = FestoTheme.colors
    if (isListening) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extendedColors.brandNovaSoft)
                .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("dictation_status_chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = extendedColors.brandNova,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (partial.isBlank()) "Listening…" else partial,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = extendedColors.inkTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = extendedColors.brandNova,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onStop)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel dictation",
                tint = extendedColors.inkTertiary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onCancel)
            )
        }
    } else if (error != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extendedColors.accentAmberSoft)
                .clickable(onClick = onDismissError)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = extendedColors.accentAmber
            )
        }
    }
}

/** Live status line for the HERMES voice-conversation loop: what the loop
 * is doing right now (listening… / thinking… / speaking…, with the live
 * partial transcript while listening), a speaker mute switch for private
 * situations, and a Stop affordance (the top-bar headset toggle stops it
 * too). Notices render as the same dismissible amber chip pattern the
 * dictation and attachment errors use -- shown independently of the status
 * row so a mid-loop notice (TTS unavailable, dropped send) is still seen. */
@Composable
private fun VoiceConversationChip(
    phase: HermesVoiceConversation.Phase,
    partial: String,
    muted: Boolean,
    notice: String?,
    onToggleMute: () -> Unit,
    onStop: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val extendedColors = FestoTheme.colors
    if (phase != HermesVoiceConversation.Phase.IDLE) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extendedColors.brandNovaSoft)
                .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("hermes_voice_conversation_status"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Headset,
                contentDescription = null,
                tint = extendedColors.brandNova,
                modifier = Modifier.size(14.dp)
            )
            val statusText = when (phase) {
                HermesVoiceConversation.Phase.LISTENING ->
                    if (partial.isBlank()) "Listening…" else partial
                HermesVoiceConversation.Phase.THINKING -> "Thinking…"
                HermesVoiceConversation.Phase.SPEAKING -> "Speaking…"
                HermesVoiceConversation.Phase.IDLE -> ""
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = extendedColors.inkTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute voice replies" else "Mute voice replies",
                tint = extendedColors.brandNova,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onToggleMute)
                    .testTag("hermes_voice_conversation_mute")
            )
            Text(
                text = "Stop",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = extendedColors.brandNova,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onStop)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
    if (notice != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extendedColors.accentAmberSoft)
                .clickable(onClick = onDismissNotice)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = notice,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = extendedColors.accentAmber
            )
        }
    }
}

/** Pending photo chip: a thumbnail decoded off-thread from the downscaled
 * JPEG that will ride the next send, plus name/size and an X to clear. */
@Composable
private fun PendingImageChip(
    image: HermesImageAttachment,
    onClear: () -> Unit
) {
    val extendedColors = FestoTheme.colors
    var thumbnail by remember(image.jpegBase64) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.jpegBase64) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(image.jpegBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(extendedColors.brandNovaSoft)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val thumb = thumbnail
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = extendedColors.brandNova,
                modifier = Modifier
                    .size(36.dp)
                    .padding(8.dp)
            )
        }
        Column(modifier = Modifier.widthIn(max = 200.dp)) {
            Text(
                text = image.filename,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = extendedColors.brandNova,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Photo · ${image.sizeBytes / 1024} KB · attaches to next message",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = extendedColors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Remove photo",
            tint = extendedColors.brandNova,
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onClear)
        )
    }
}

/** Decodes a photo-picker [Uri] into a compact JPEG for the Hermes
 * gateway: power-of-two subsample followed by an exact scale so the
 * longest edge is <= 1280px, re-encoded as JPEG quality 80. Returns
 * (filename, jpegBytes), or null when the stream can't be read or the
 * bytes don't decode as an image. Runs on Dispatchers.IO only. */
private fun decodePickedImageForHermes(context: Context, uri: Uri): Pair<String, ByteArray>? {
    return try {
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "photo.jpg"
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (raw.isEmpty()) return null

        val maxDim = 1280
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val sampled = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val longest = maxOf(sampled.width, sampled.height)
        val bitmap = if (longest > maxDim) {
            val scale = maxDim.toFloat() / longest
            Bitmap.createBitmap(
                sampled, 0, 0, sampled.width, sampled.height,
                android.graphics.Matrix().apply { postScale(scale, scale) },
                true
            ).also { scaled -> if (scaled !== sampled) sampled.recycle() }
        } else {
            sampled
        }
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        name to out.toByteArray()
    } catch (_: Exception) {
        null
    }
}

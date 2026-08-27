package com.example.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FestoAppState
import com.example.data.VoiceState
import com.example.ui.theme.BrandNova
import com.example.ui.theme.BrandNovaGlow
import com.example.ui.theme.FestoTheme

@Composable
fun VoiceOverlay(
    appState: FestoAppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val voiceState = appState.voiceState

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Barge-in tap anywhere during speaking/thinking
                if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) {
                    appState.bargeInStopPlayback()
                }
            },
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Top Bar: Model chip & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Voice • ${appState.selectedModel.name}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        appState.cancelVoiceTurn()
                        onClose()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(extendedColors.surfaceSubtle)
                        .border(1.dp, extendedColors.borderHairline, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit Voice Mode",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: Breathing Animated Orb & Real-time State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Orb with dynamic pulse
                VoiceGlowingOrb(voiceState = voiceState)

                Spacer(modifier = Modifier.height(36.dp))

                // State Title
                Text(
                    text = voiceState.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // State helper caption
                val captionText = when (voiceState) {
                    VoiceState.IDLE -> "Tap the microphone below to start talking"
                    VoiceState.RECORDING -> "Recording audio (${String.format("%.1fs", appState.voiceRecordingDurationSec / 10f)}) • Tap stop when finished"
                    VoiceState.SENDING -> "Uploading 24kHz audio stream to OpenRouter"
                    VoiceState.THINKING -> "Synthesizing response with ${appState.selectedModel.name}"
                    VoiceState.SPEAKING -> "Tap anywhere to interrupt or stop playback"
                }

                Text(
                    text = captionText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = extendedColors.inkTertiary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Live Audio Waveform frequency bars
                if (voiceState == VoiceState.RECORDING || voiceState == VoiceState.SPEAKING) {
                    Spacer(modifier = Modifier.height(24.dp))
                    AudioWaveformVisualizer(
                        levels = appState.voiceAudioLevels,
                        color = extendedColors.brandNova
                    )
                }

                // Streaming Live Transcript Box
                if (appState.voiceLiveTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(extendedColors.surfaceSubtle)
                            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = appState.voiceLiveTranscript,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Bottom Action Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (voiceState) {
                    VoiceState.IDLE -> {
                        // Start Record Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(extendedColors.brandNova)
                                .clickable { appState.startVoiceRecording() }
                                .testTag("voice_start_record_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Start Speaking",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    VoiceState.RECORDING -> {
                        // Stop & Send Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(extendedColors.brandNova)
                                .clickable { appState.stopVoiceRecordingAndSend() }
                                .testTag("voice_stop_record_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Send Recording",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    VoiceState.THINKING, VoiceState.SENDING -> {
                        // Spinner & Cancel
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(extendedColors.surfaceSubtle)
                                    .border(1.dp, extendedColors.borderHairline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = extendedColors.brandNova,
                                    strokeWidth = 2.5.dp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(extendedColors.surfaceSubtle)
                                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(20.dp))
                                    .clickable { appState.cancelVoiceTurn() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = extendedColors.inkTertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    VoiceState.SPEAKING -> {
                        // Barge-in stop playback button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(extendedColors.brandNovaSoft)
                                .border(1.5.dp, extendedColors.brandNova, RoundedCornerShape(24.dp))
                                .clickable { appState.bargeInStopPlayback() }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop",
                                    tint = extendedColors.brandNova,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Interrupt & Return to Idle",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = extendedColors.brandNova,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VoiceGlowingOrb(voiceState: VoiceState) {
    val transition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by if (voiceState == VoiceState.RECORDING || voiceState == VoiceState.SPEAKING) {
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else if (voiceState == VoiceState.THINKING) {
        transition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thinking_pulse"
        )
    } else {
        transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idle_pulse"
        )
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow rings
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(BrandNovaGlow.copy(alpha = if (voiceState == VoiceState.RECORDING) 0.28f else 0.12f))
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(BrandNova.copy(alpha = if (voiceState == VoiceState.RECORDING) 0.45f else 0.25f))
        )
        // Core Orb
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(FestoTheme.voiceOrbBrush()),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(54.dp)) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val cy = h / 2f

                val path = Path().apply {
                    moveTo(cx, 0f)
                    cubicTo(cx, cy * 0.45f, cx * 0.45f, cy, 0f, cy)
                    cubicTo(cx * 0.45f, cy, cx, cy * 1.55f, cx, h)
                    cubicTo(cx, cy * 1.55f, cx * 1.55f, cy, w, cy)
                    cubicTo(cx * 1.55f, cy, cx, cy * 0.45f, cx, 0f)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }
    }
}

@Composable
private fun AudioWaveformVisualizer(
    levels: List<Float>,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(36.dp)
    ) {
        levels.forEach { level ->
            val barHeight = (10.dp + (26.dp * level.coerceIn(0.1f, 1f)))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Message
import com.example.data.Modality
import com.example.data.Role
import com.example.ui.theme.FestoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageItem(
    message: Message,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val isUser = message.role == Role.USER
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val timeFormatted = rememberTimeFormat(message.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_item" else "assistant_message_item")
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            NovaAvatar(
                size = 28.dp,
                modifier = Modifier.padding(top = 4.dp, end = 10.dp)
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Header for assistant (Model label)
            if (!isUser && message.model != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                ) {
                    Text(
                        text = message.model.substringAfter("/"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = extendedColors.brandNova
                    )
                    if (message.modality == Modality.VOICE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(extendedColors.brandNovaSoft)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "VOICE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = extendedColors.brandNova
                            )
                        }
                    }
                }
            }

            // Message Bubble Card
            Box(
                modifier = Modifier
                    .clip(
                        if (isUser) RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                        else RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(
                        if (isUser) extendedColors.surfaceContainer
                        else extendedColors.surfaceSubtle
                    )
                    .border(
                        1.dp,
                        if (isUser) extendedColors.borderMedium else extendedColors.borderHairline,
                        if (isUser) RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                        else RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Voice player indicator if voice modality
                    if (message.modality == Modality.VOICE) {
                        VoiceMessageHeader(
                            durationSec = message.audioDurationSec ?: 3.0f,
                            isUser = isUser
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Content rendering
                    if (message.content.isNotBlank()) {
                        FormattedMessageContent(
                            content = message.content,
                            isUser = isUser
                        )
                    } else if (message.isStreaming) {
                        StreamingDotsIndicator()
                    }

                    // Streaming blinking cursor if active
                    if (message.isStreaming && message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamingCursor()
                    }
                }
            }

            // Footer metadata: timestamp & usage info
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = extendedColors.inkTertiary
                    )
                )

                if (!isUser && message.outputTokens != null && message.costUsd != null) {
                    Text(
                        text = "•  ${message.outputTokens} tok  •  $${String.format(Locale.US, "%.5f", message.costUsd)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            color = extendedColors.inkTertiary
                        )
                    )
                }

                if (!isUser && message.content.isNotBlank() && !message.isStreaming) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy message",
                        modifier = Modifier
                            .size(13.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        tint = extendedColors.inkTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceMessageHeader(
    durationSec: Float,
    isUser: Boolean
) {
    val extendedColors = FestoTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(extendedColors.brandNovaSoft)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = "Spoken turn",
            tint = extendedColors.brandNova,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Spoken Audio (${String.format(Locale.US, "%.1fs", durationSec)})",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = extendedColors.brandNova
        )
    }
}

@Composable
private fun FormattedMessageContent(
    content: String,
    isUser: Boolean
) {
    val extendedColors = FestoTheme.colors

    // Check for code block segments
    if (content.contains("```")) {
        val parts = content.split("```")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    // Code block
                    val codeContent = part.trim()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(extendedColors.surfaceDialog)
                            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = codeContent,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 21.sp,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 21.sp,
                fontSize = 14.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StreamingDotsIndicator() {
    val extendedColors = FestoTheme.colors
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600), repeatMode = RepeatMode.Reverse),
        label = "d1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse),
        label = "d2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(extendedColors.brandNova.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(extendedColors.brandNova.copy(alpha = alpha2)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(extendedColors.brandNova.copy(alpha = alpha3)))
    }
}

@Composable
private fun StreamingCursor() {
    val extendedColors = FestoTheme.colors
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500), repeatMode = RepeatMode.Reverse),
        label = "cursor_blink"
    )
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 14.dp)
            .background(extendedColors.brandNova.copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun rememberTimeFormat(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

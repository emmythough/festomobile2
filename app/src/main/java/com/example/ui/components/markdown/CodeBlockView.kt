package com.example.ui.components.markdown

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FestoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CodeBlockView(
    code: String,
    language: String = "text",
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val horizontalScrollState = rememberScrollState()

    val displayLang = language.ifBlank { "text" }.lowercase()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(extendedColors.surfaceDialog)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
    ) {
        Column {
            // Header Bar (ChatGPT style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extendedColors.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayLang,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = extendedColors.inkTertiary
                )

                // Copy Action with visual feedback
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            isCopied = true
                            scope.launch {
                                delay(800)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    AnimatedContent(
                        targetState = isCopied,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "copy_state"
                    ) { copied ->
                        if (copied) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Copied",
                                tint = extendedColors.accentGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy code",
                                tint = extendedColors.inkTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isCopied) "Copied!" else "Copy code",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isCopied) extendedColors.accentGreen else extendedColors.inkTertiary
                    )
                }
            }

            // Monospaced Code Text with Horizontal Scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = remember(code, displayLang, extendedColors.isDark) {
                        highlightCode(code, displayLang, extendedColors.isDark)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

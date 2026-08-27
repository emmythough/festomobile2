package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandNova
import com.example.ui.theme.BrandNovaGlow
import com.example.ui.theme.BrandNovaHover

@Composable
fun NovaAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    isPulsing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nova_pulse")
    val pulseScale by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        rememberInfiniteTransition(label = "static").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000)),
            label = "static_scale"
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandNovaGlow,
                        BrandNova,
                        BrandNovaHover
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // 4-point star path
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

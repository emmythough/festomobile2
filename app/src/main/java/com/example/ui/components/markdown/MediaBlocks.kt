package com.example.ui.components.markdown

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FestoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Composables for the media blocks RichMessageRenderer splits out of
 * assistant markdown (MessageBlock.Image / MessageBlock.AudioFile).
 *
 * Why here instead of Markwon: the app's Markwon setup has no images
 * plugin, so `![alt](data:image/png;base64,...)` -- the exact form the
 * Hermes gateway inlines Wendy's images in -- renders as broken text
 * today. Rather than adding the Markwon image stack (a new dependency
 * plus an OkHttp loader for URLs that are mostly inline base64 bytes),
 * the renderer extracts images/audio into blocks and these composables
 * decode them directly with platform BitmapFactory (data URLs) or the
 * already-present OkHttp (http(s) URLs). Shared by the chat transcript
 * AND the Hermes memory browser, which render through the same
 * RichMessageRenderer.
 */

private val messageImageClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

/** Longest-edge cap for decoded reply images -- platform decoding only,
 * just enough to keep a huge PNG from ballooning the bitmap heap. */
private const val MAX_DECODE_DIM = 2048

@Composable
fun MessageImageBlock(
    source: String,
    alt: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }

    // Decoding runs off the main thread -- a multi-megabyte data URL is a
    // real jank source there.
    LaunchedEffect(source) {
        val decoded = withContext(Dispatchers.IO) {
            try {
                decodeMessageImage(source)
            } catch (_: Exception) {
                null
            }
        }
        bitmap = decoded
        failed = decoded == null
    }

    val shown = bitmap
    when {
        shown != null -> Column(modifier = modifier.fillMaxWidth()) {
            Image(
                bitmap = shown,
                contentDescription = alt.ifBlank { "Message image" },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(shown.width.toFloat() / shown.height.toFloat())
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            )
            if (alt.isNotBlank()) {
                Text(
                    text = alt,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = extendedColors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        failed -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(extendedColors.surfaceSubtle)
                .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = extendedColors.inkTertiary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = alt.ifBlank { "image couldn't be shown" },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = extendedColors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(extendedColors.surfaceSubtle),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = extendedColors.brandNova,
                strokeWidth = 2.dp
            )
        }
    }
}

/** data: URLs decode straight from base64; http(s) images are fetched with
 * OkHttp. Any failure returns null and the caller shows the fallback chip
 * (the markdown text around it still renders normally). */
private fun decodeMessageImage(source: String): ImageBitmap? {
    return when {
        source.startsWith("data:", ignoreCase = true) -> {
            val comma = source.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
            decodeBounded(bytes)
        }
        source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true) -> {
            messageImageClient.newCall(Request.Builder().url(source).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                decodeBounded(bytes)
            }
        }
        else -> null
    }
}

/** Bounds-first decode with a power-of-two subsample so the longest edge
 * stays under [MAX_DECODE_DIM] -- same pattern as the composer's photo
 * picker pipeline, minus the re-encode (reply images are shown as-is). */
private fun decodeBounded(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_DECODE_DIM ||
        bounds.outHeight / (sample * 2) >= MAX_DECODE_DIM
    ) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )?.asImageBitmap()
}

/** A bare audio file path Wendy dropped into her reply -- a simple chip,
 * no player this pass (the task's contract: the gateway has no playback
 * surface for the app; the path is the information). */
@Composable
fun MessageAudioChip(
    path: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(extendedColors.brandNovaSoft)
            .border(1.dp, extendedColors.brandNovaLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Headset,
            contentDescription = null,
            tint = extendedColors.brandNova,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "audio: $fileName",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = extendedColors.brandNova,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

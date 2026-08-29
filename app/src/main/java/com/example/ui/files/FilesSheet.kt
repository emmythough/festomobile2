package com.example.ui.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendMode
import com.example.data.FestoAppState
import com.example.data.OutboxDownload
import com.example.data.OutboxFile
import com.example.data.WendyApi
import com.example.ui.theme.FestoTheme
import kotlinx.coroutines.launch
import java.io.File

/**
 * Wendy's outbox -- files she builds (spreadsheets, docs, anything) and
 * delivers to the phone. This is a ONE-TIME PICKUP QUEUE, not a file
 * browser: once any surface (this app or Telegram) downloads a file it
 * is archived server-side and disappears from the list, so a successful
 * download removes the row here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesSheet(
    appState: FestoAppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val extendedColors = FestoTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val files = remember { mutableStateListOf<OutboxFile>() }
    var isLoading by remember { mutableStateOf(true) }
    // The name of the file currently downloading (drives per-row spinner).
    var downloadingName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        // The outbox lives on Wendy's own server (Gen 1). The Hermes
        // gateway has no file queue -- skip the call instead of asking a
        // server this backend mode doesn't use; the honest empty state
        // below explains.
        if (appState.backendMode != BackendMode.HERMES) {
            files.addAll(WendyApi.fetchOutbox())
        }
        isLoading = false
    }

    fun onFileTapped(file: OutboxFile) {
        if (downloadingName != null) return // one download at a time
        downloadingName = file.name
        scope.launch {
            val result = WendyApi.downloadOutboxFile(file.name)
            when (result) {
                is OutboxDownload.Ready -> {
                    val saved = if (result.bytes.isEmpty()) false
                    else saveToDownloads(context, file.name, result.bytes)
                    if (saved) {
                        Toast.makeText(
                            context,
                            "Saved \"${file.name}\" to your Downloads folder",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Downloaded \"${file.name}\" but couldn't save it to storage.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    // Gone from the server either way once downloaded --
                    // remove the row so a dead entry isn't left behind.
                    files.removeAll { it.name == file.name }
                }
                is OutboxDownload.NotFound -> {
                    Toast.makeText(
                        context,
                        "This file was already delivered (perhaps via Telegram).",
                        Toast.LENGTH_LONG
                    ).show()
                    files.removeAll { it.name == file.name }
                }
                is OutboxDownload.Failed -> {
                    Toast.makeText(
                        context,
                        "Couldn't download \"${file.name}\": ${result.message ?: "network error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            downloadingName = null
        }
    }

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
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
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
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = extendedColors.brandNova,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Wendy's Files",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "One-time pickup -- files vanish once delivered",
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

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = extendedColors.brandNova
                        )
                        Text(
                            text = "Checking Wendy's outbox...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = extendedColors.inkTertiary
                            ),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
                files.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = extendedColors.inkTertiary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No files waiting",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = extendedColors.inkTertiary
                        )
                        Text(
                            text = if (appState.backendMode == BackendMode.HERMES) {
                                "The Hermes gateway has no file queue -- Wendy's outbox is a Gen 1 feature."
                            } else {
                                "Files Wendy builds for you will show up here."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = extendedColors.inkTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files, key = { it.name }) { file ->
                            OutboxFileItemCard(
                                file = file,
                                isDownloading = downloadingName == file.name,
                                onTap = { onFileTapped(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboxFileItemCard(
    file: OutboxFile,
    isDownloading: Boolean,
    onTap: () -> Unit
) {
    val extendedColors = FestoTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.surfaceSubtle)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(12.dp))
            .clickable(enabled = !isDownloading) { onTap() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = extendedColors.brandNova,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${humanReadableSize(file.sizeBytes)} · ${relativeTimeFromEpochSec(file.createdAtEpochSec)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = extendedColors.inkTertiary
                    )
                )
            }
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = extendedColors.brandNova
                )
            }
        }
    }
}

/** KB/MB formatting -- a raw byte count is not something a human reads. */
private fun humanReadableSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Coarse relative time from the outbox's seconds-float timestamps --
 * deliberately simple; no date library for one label. Clock skew or a
 * future timestamp degrades to "just now" rather than a negative. */
private fun relativeTimeFromEpochSec(epochSec: Double): String {
    val ageMinutes = (System.currentTimeMillis() - (epochSec * 1000).toLong()) / 60000
    return when {
        ageMinutes < 1 -> "just now"
        ageMinutes < 60 -> "${ageMinutes}m ago"
        ageMinutes < 1440 -> "${ageMinutes / 60}h ago"
        else -> "${ageMinutes / 1440}d ago"
    }
}

/** Guesses a MIME type so Android's Downloads/download managers show a
 * sensible entry; unknown extensions stay generic, which is fine. */
private fun mimeTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "txt" -> "text/plain"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "html" -> "text/html"
    "md" -> "text/markdown"
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "zip" -> "application/zip"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}

/** Saves bytes somewhere the user can actually get to them, the
 * scoped-storage way:
 *  - API 29+: MediaStore's Downloads collection (insert + write +
 *    clear IS_PENDING). Needs NO storage permission on modern Android.
 *  - API <29: MediaStore.Downloads doesn't exist there; falls back to
 *    this app's own external files directory (still permission-free,
 *    user-reachable via Files/My Files under Android/data), rather
 *    than requesting legacy broad storage access.
 * Returns true only on a real, completed write. */
private fun saveToDownloads(context: Context, name: String, bytes: ByteArray): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(name))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return false
            File(dir, name).outputStream().use { it.write(bytes) }
            true
        }
    } catch (_: Exception) {
        false
    }
}

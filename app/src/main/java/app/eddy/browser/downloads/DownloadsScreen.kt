package app.eddy.browser.downloads

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.database.DownloadEntity
import app.eddy.browser.data.database.DownloadStatus
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.theme.Dimens

@Composable
fun DownloadsScreen(vm: BrowserViewModel) {
    val items by vm.downloadItems.collectAsStateWithLifecycle(emptyList())
    val live by vm.downloads.live.collectAsStateWithLifecycle()
    ScreenScaffold(
        str(R.string.downloads), onBack = { vm.screen = Screen.BROWSER },
        actions = {
            EddyIconButton(Icons.Rounded.DeleteSweep, str(R.string.clear_finished_downloads), { vm.downloads.clearFinished() }, enabled = items.any { it.status in FINISHED })
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Icons.Rounded.Download, str(R.string.no_downloads), str(R.string.files_you_download_will_appear_here), Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { d -> DownloadCard(vm, d, live[d.id]) }
            }
        }
    }
}

private val FINISHED = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELED)

@Composable
private fun DownloadCard(vm: BrowserViewModel, d: DownloadEntity, progress: LiveProgress?) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val downloaded = progress?.downloaded ?: d.downloadedBytes
    val total = progress?.total ?: d.totalBytes
    val speed = progress?.bytesPerSecond ?: 0L
    val running = d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.QUEUED

    val status = when (d.status) {
        DownloadStatus.COMPLETED -> str(R.string.completed, Formatter.formatShortFileSize(context, d.totalBytes.coerceAtLeast(d.downloadedBytes)))
        DownloadStatus.FAILED -> str(R.string.failed_reason, d.error.ifBlank { str(R.string.unknown_error) })
        DownloadStatus.CANCELED -> str(R.string.canceled)
        DownloadStatus.PAUSED -> str(R.string.paused, Formatter.formatShortFileSize(context, downloaded)) + if (total > 0) " of ${Formatter.formatShortFileSize(context, total)}" else ""
        DownloadStatus.QUEUED -> "Waiting…"
        DownloadStatus.RUNNING -> buildString {
            append(Formatter.formatShortFileSize(context, downloaded))
            if (total > 0) append(" of ").append(Formatter.formatShortFileSize(context, total))
            if (speed > 0) append(" · ").append(Formatter.formatShortFileSize(context, speed)).append("/s")
        }
    }

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(iconFor(d.mimeType, d.fileName), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(d.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${typeLabel(d.mimeType, d.fileName)} · $status", style = MaterialTheme.typography.bodySmall,
                        color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.PAUSED || d.status == DownloadStatus.QUEUED) {
                if (total > 0) LinearProgressIndicator(progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                else if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                when (d.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                        if (d.resumable) EddyIconButton(Icons.Rounded.Pause, str(R.string.pause, d.fileName), { vm.downloads.pause(d.id) })
                        EddyIconButton(Icons.Rounded.Close, str(R.string.cancel_2, d.fileName), { vm.downloads.cancel(d.id) })
                    }
                    DownloadStatus.PAUSED -> {
                        EddyIconButton(Icons.Rounded.PlayArrow, str(R.string.resume, d.fileName), { vm.downloads.resume(d.id) })
                        EddyIconButton(Icons.Rounded.Close, str(R.string.cancel_2, d.fileName), { vm.downloads.cancel(d.id) })
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                        if (d.url.startsWith("http")) EddyIconButton(Icons.Rounded.Refresh, str(R.string.retry_file, d.fileName), { vm.downloads.retry(d.id) })
                    }
                    DownloadStatus.COMPLETED -> EddyIconButton(Icons.AutoMirrored.Rounded.OpenInNew, str(R.string.open_2, d.fileName), { vm.openFile(d.contentUri, d.mimeType, d.fileName) })
                }
                if (!running) EddyIconButton(Icons.Rounded.Delete, str(R.string.delete, d.fileName), { if (d.status == DownloadStatus.COMPLETED) confirmDelete = true else vm.downloads.delete(d.id, false) })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_2, d.fileName)) },
            text = { Text(str(R.string.remove_the_entry_from_this_list_or_delet)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.downloads.delete(d.id, true) }) { Text(str(R.string.delete_file)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) }
                    TextButton(onClick = { confirmDelete = false; vm.downloads.delete(d.id, false) }) { Text(str(R.string.remove_entry)) }
                }
            },
        )
    }
}

private fun iconFor(mime: String, name: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Rounded.Image
    mime.startsWith("video/") -> Icons.Rounded.Videocam
    mime.startsWith("audio/") -> Icons.Rounded.Audiotrack
    mime == "application/pdf" || mime.startsWith("text/") -> Icons.Rounded.Description
    mime.contains("zip") || mime.contains("compressed") || name.endsWith(".zip") || name.endsWith(".7z") || name.endsWith(".rar") || name.endsWith(".tar.gz") -> Icons.Rounded.FolderZip
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun typeLabel(mime: String, name: String): String {
    val ext = name.substringAfterLast('.', "").uppercase()
    return when {
        ext.isNotEmpty() && ext.length <= 5 -> ext
        mime.isNotBlank() -> mime.substringAfter('/').uppercase().take(8)
        else -> "FILE"
    }
}

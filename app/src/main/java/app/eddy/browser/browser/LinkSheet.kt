package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.Dimens

/** Long-press actions for links and images. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(vm: BrowserViewModel, target: HitTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = target.linkUrl
    val image = target.imageUrl
    val primary = link ?: image.orEmpty()
    fun run(block: () -> Unit): () -> Unit = { onDismiss(); block() }
    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", text))
        vm.snackbar(str(R.string.copied))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(primary, Modifier.padding(horizontal = Dimens.gutterLarge, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider()
            if (link != null) {
                Action(Icons.Rounded.Tab, str(R.string.open_in_new_tab), run { vm.openInNewTab(link, select = false) })
                Action(Icons.Rounded.VisibilityOff, str(R.string.open_in_incognito_tab), run { vm.openInNewTab(link, incognito = true) })
                Action(Icons.Rounded.ContentCopy, str(R.string.copy_link_address), run { copy(link) })
                Action(Icons.Rounded.Share, str(R.string.share_link), run { vm.share(link) })
            }
            if (image != null) {
                Action(Icons.AutoMirrored.Rounded.OpenInNew, str(R.string.open_image_in_new_tab), run { vm.openInNewTab(image, select = false) })
                Action(Icons.Rounded.Download, str(R.string.download_image), run {
                    vm.tabs.selected?.let { vm.startDownload(it, image, it.webView?.settings?.userAgentString.orEmpty(), "", "", -1) }
                })
                if (link == null) Action(Icons.Rounded.ContentCopy, str(R.string.copy_image_address), run { copy(image) })
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Dimens.gutterLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

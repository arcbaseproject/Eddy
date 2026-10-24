package app.eddy.browser.bookmarks

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.database.Bookmark
import app.eddy.browser.data.database.BookmarkFolder
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.SearchField
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.components.TextInputDialog
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils
import app.eddy.browser.util.readTextCapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(vm: BrowserViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by vm.bookmarks.bookmarks.collectAsStateWithLifecycle(emptyList())
    val folders by vm.bookmarks.folders.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<BookmarkFolder?>(null) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var addingFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BookmarkFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<BookmarkFolder?>(null) }
    var overflow by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val html = vm.bookmarks.exportHtml()
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) } } }
                .onSuccess { vm.snackbar(str(R.string.bookmarks_exported)) }.onFailure { vm.snackbar(str(R.string.export_failed)) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readTextCapped() ?: error("too big") } }.getOrNull()
            if (text == null) vm.snackbar(str(R.string.could_not_read_the_file_files_over_16_mb)) else vm.snackbar(str(R.string.imported_bookmarks, vm.bookmarks.importHtml(text)))
        }
    }

    val searching = query.isNotBlank()
    val visible = when {
        searching -> all.filter { it.title.contains(query, true) || it.url.contains(query, true) }
        else -> all.filter { it.folderId == (folder?.id ?: Bookmark.ROOT_FOLDER) }
    }
    val shownFolders = if (searching || folder != null) emptyList() else folders

    // System Back leaves a folder the same way the toolbar arrow does, before leaving Bookmarks.
    BackHandler(enabled = folder != null) { folder = null }

    ScreenScaffold(
        title = folder?.name ?: str(R.string.bookmarks),
        onBack = { if (folder != null) folder = null else vm.screen = Screen.BROWSER },
        actions = {
            EddyIconButton(Icons.Rounded.CreateNewFolder, str(R.string.new_folder), { addingFolder = true })
            Box {
                EddyIconButton(Icons.Rounded.MoreVert, str(R.string.more), { overflow = true })
                DropdownMenu(overflow, { overflow = false }) {
                    DropdownMenuItem(text = { Text(str(R.string.import_from_html_file)) }, onClick = { overflow = false; import.launch(arrayOf("text/html", "*/*")) })
                    DropdownMenuItem(text = { Text(str(R.string.export_to_html_file)) }, onClick = { overflow = false; export.launch("eddy-bookmarks.html") })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, str(R.string.search_bookmarks))
            if (visible.isEmpty() && shownFolders.isEmpty()) {
                EmptyState(Icons.Rounded.Bookmarks, if (searching) str(R.string.no_matches) else str(R.string.no_bookmarks_here), if (searching) str(R.string.try_a_different_search) else str(R.string.tap_bookmark_in_the_page_menu_to_save_a))
            } else {
                LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(shownFolders, key = { "f${it.id}" }) { f ->
                        FolderRow(f, all.count { it.folderId == f.id }, onOpen = { folder = f }, onRename = { renaming = f }, onDelete = { deletingFolder = f })
                    }
                    items(visible, key = { "b${it.id}" }) { b ->
                        BookmarkRow(vm, b, onEdit = { editing = b })
                    }
                }
            }
        }
    }

    if (addingFolder) TextInputDialog(str(R.string.new_folder), "", str(R.string.folder_name), str(R.string.create), onDismiss = { addingFolder = false }) { vm.bookmarks.addFolder(it); addingFolder = false }
    renaming?.let { f -> TextInputDialog(str(R.string.rename_folder), f.name, str(R.string.folder_name), onDismiss = { renaming = null }) { vm.bookmarks.renameFolder(f, it); renaming = null } }
    deletingFolder?.let { f ->
        ConfirmDialog(str(R.string.delete_3, f.name), str(R.string.this_also_deletes_the_bookmarks_inside_i), str(R.string.delete_4), { deletingFolder = null }) {
            vm.bookmarks.deleteFolder(f)
            deletingFolder = null
        }
    }
    editing?.let { b -> BookmarkEditor(b, folders, onDismiss = { editing = null }) { vm.bookmarks.save(it); editing = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(vm: BrowserViewModel, b: Bookmark, onEdit: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .combinedClickable(role = Role.Button, onClickLabel = str(R.string.open), onLongClickLabel = str(R.string.more_actions), onClick = { vm.navigate(b.url) }, onLongClick = { haptics.longPress(); menu = true })
                .padding(start = Dimens.gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteIcon(UrlUtils.displayHost(b.url), size = Dimens.faviconLarge)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            EddyIconButton(Icons.Rounded.MoreVert, str(R.string.actions_for, b.title), { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(str(R.string.edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(str(R.string.open_in_new_tab)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { menu = false; vm.openInNewTab(b.url) })
            DropdownMenuItem(text = { Text(str(R.string.open_in_incognito_tab)) }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) }, onClick = { menu = false; vm.openInNewTab(b.url, incognito = true) })
            DropdownMenuItem(text = { Text(str(R.string.copy_link)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                menu = false
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", b.url))
                vm.snackbar(str(R.string.copied))
            })
            DropdownMenuItem(text = { Text(str(R.string.delete_4)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; vm.bookmarks.delete(b); vm.snackbar(str(R.string.bookmark_removed)) })
        }
    }
}

@Composable
private fun FolderRow(f: BookmarkFolder, count: Int, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickableNoRipple(onOpen).padding(start = Dimens.gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Dimens.faviconLarge).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (count == 1) "1 bookmark" else "$count bookmarks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EddyIconButton(Icons.Rounded.MoreVert, str(R.string.actions_for_folder, f.name), { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(str(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(str(R.string.delete_folder)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}

private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Button, onClick = onClick)

@Composable
private fun BookmarkEditor(b: Bookmark, folders: List<BookmarkFolder>, onDismiss: () -> Unit, onSave: (Bookmark) -> Unit) {
    var title by remember { mutableStateOf(b.title) }
    var url by remember { mutableStateOf(b.url) }
    var folderId by remember { mutableStateOf(b.folderId) }
    var pick by remember { mutableStateOf(false) }
    val valid = title.isNotBlank() && UrlUtils.isUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.edit_bookmark)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.title)) }, singleLine = true)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.address)) }, singleLine = true, isError = !UrlUtils.isUrl(url))
                Box {
                    TextButton(onClick = { pick = true }) { Text("Folder: ${folders.firstOrNull { it.id == folderId }?.name ?: "None"}") }
                    DropdownMenu(pick, { pick = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.none)) }, onClick = { folderId = Bookmark.ROOT_FOLDER; pick = false })
                        folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { folderId = f.id; pick = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(b.copy(title = title.trim(), url = UrlUtils.resolve(url.trim(), app.eddy.browser.data.models.SearchEngine("", "", "%s")), folderId = folderId)) }) { Text(str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

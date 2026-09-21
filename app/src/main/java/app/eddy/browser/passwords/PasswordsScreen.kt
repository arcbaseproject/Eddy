package app.eddy.browser.passwords

import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.database.LoginEntity
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.SearchField
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saved logins. Opening it needs the device screen lock, and the window is hidden from screenshots and recents. */
@Composable
fun PasswordsScreen(vm: BrowserViewModel) {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val needsUnlock = keyguard.isDeviceSecure && !vm.passwordsUnlocked
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.passwordsUnlocked = true else vm.screen = Screen.BROWSER
    }
    fun requestUnlock() {
        unlock.launch(keyguard.createConfirmDeviceCredentialIntent("Unlock passwords", "Confirm your screen lock to view saved passwords."))
    }

    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            vm.passwordsUnlocked = false
        }
    }
    LaunchedEffect(needsUnlock) { if (needsUnlock) requestUnlock() }

    val logins by vm.passwords.logins.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LoginEntity?>(null) }
    var editing by remember { mutableStateOf<LoginEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (csv, count) = vm.passwords.exportCsv()
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) } } }
                .onSuccess { vm.snackbar("Exported $count passwords. The file is not encrypted, so delete it when you are done.") }
                .onFailure { vm.snackbar("Export failed") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() } }.getOrNull()
            if (text == null) return@launch vm.snackbar("Could not read the file")
            val r = vm.passwords.importCsv(text)
            vm.snackbar(
                if (!r.recognised) "This file is not a password CSV"
                else "Imported ${r.added} new, ${r.updated} updated" + if (r.unchanged + r.skipped > 0) ", ${r.unchanged + r.skipped} skipped" else "",
            )
        }
    }

    ScreenScaffold(
        "Passwords", onBack = { vm.screen = Screen.BROWSER },
        actions = {
            if (!needsUnlock) {
                EddyIconButton(Icons.Rounded.Add, "Add password", { adding = true })
                Box {
                    EddyIconButton(Icons.Rounded.MoreVert, "More", { overflow = true })
                    DropdownMenu(overflow, { overflow = false }) {
                        DropdownMenuItem(text = { Text("Import from CSV") }, onClick = { overflow = false; import.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
                        DropdownMenuItem(text = { Text("Export to CSV") }, onClick = { overflow = false; confirmExport = true })
                        DropdownMenuItem(
                            text = { Text("Reset “Never save” sites") },
                            onClick = { overflow = false; vm.passwords.resetNeverSave(); vm.snackbar("Eddy will offer to save passwords on every site again") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (needsUnlock) {
                Column(Modifier.fillMaxSize().padding(Dimens.gutterLarge), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Passwords are locked", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Confirm your screen lock to continue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = ::requestUnlock, Modifier.padding(top = 16.dp)) { Text("Unlock") }
                }
            } else {
                SearchField(query, { query = it }, "Search passwords")
                val shown = logins.filter { query.isBlank() || it.origin.contains(query, true) || it.username.contains(query, true) }
                if (shown.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.Key,
                        if (query.isBlank()) "No saved passwords" else "No matches",
                        if (query.isBlank()) "When you sign in or sign up on a site, Eddy offers to save the login here." else "Try a different search.",
                    )
                } else {
                    LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                        items(shown, key = { it.id }) { login -> LoginRow(login) { selected = login } }
                    }
                }
            }
        }
    }

    selected?.let { login ->
        LoginSheet(
            vm, login,
            onEdit = { editing = login; selected = null },
            onDismiss = { selected = null },
        )
    }
    if (confirmExport) {
        ConfirmDialog(
            "Export passwords?",
            "The file is plain text. Anyone who can open it can read every password in it. Save it somewhere private and delete it after you import it elsewhere.",
            "Export", { confirmExport = false },
        ) { confirmExport = false; export.launch("eddy-passwords.csv") }
    }
    if (adding) LoginEditor(vm, null, onDismiss = { adding = false })
    editing?.let { LoginEditor(vm, it, onDismiss = { editing = null }) }
}

@Composable
private fun LoginRow(login: LoginEntity, onClick: () -> Unit) {
    val host = UrlUtils.displayHost(login.origin)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "Open details", onClick = onClick).padding(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host, size = Dimens.faviconLarge)
        Column(Modifier.padding(start = 14.dp)) {
            Text(host, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(login.username.ifEmpty { "No username" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginSheet(vm: BrowserViewModel, login: LoginEntity, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val password by produceState<String?>(null, login.id, login.password) { value = vm.passwords.decrypt(login) }
    var reveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val host = UrlUtils.displayHost(login.origin)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutterLarge).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(host, size = Dimens.faviconLarge)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(host, style = MaterialTheme.typography.titleLarge)
                    Text(login.origin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            DetailRow("Username", login.username.ifEmpty { "None" }, copyable = login.username.isNotEmpty()) { vm.copySecret("Username", login.username) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            password == null -> "Cannot decrypt"
                            reveal -> password.orEmpty()
                            else -> "••••••••••••"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                EddyIconButton(
                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (reveal) "Hide password" else "Show password", { reveal = !reveal }, enabled = password != null,
                )
                EddyIconButton(Icons.Rounded.ContentCopy, "Copy password", { password?.let { vm.copySecret("Password", it) } }, enabled = password != null)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onDismiss(); vm.navigate(login.origin) }) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Text("Open site", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text("Edit", Modifier.padding(start = 6.dp)) }
                TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Text("Delete", Modifier.padding(start = 6.dp)) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete this password?", "Eddy will remove the saved login for $host. The account itself stays unchanged.", "Delete", { confirmDelete = false }) {
            confirmDelete = false
            vm.passwords.delete(login.id)
            onDismiss()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (copyable) EddyIconButton(Icons.Rounded.ContentCopy, "Copy $label", onCopy)
    }
}

/** Add or edit a login by hand. */
@Composable
private fun LoginEditor(vm: BrowserViewModel, existing: LoginEntity?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var site by remember { mutableStateOf(existing?.origin.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val decrypted by produceState<String?>(null, existing?.id) { value = existing?.let { vm.passwords.decrypt(it) } }
    LaunchedEffect(decrypted) { if (existing != null && password.isEmpty()) decrypted?.let { password = it } }

    val origin = PasswordManager.normalizeOrigin(site)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add password" else "Edit password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(site, { site = it }, Modifier.fillMaxWidth(), label = { Text("Website") }, singleLine = true, isError = site.isNotBlank() && origin == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            EddyIconButton(Icons.Rounded.Refresh, "Generate password", { password = PasswordManager.generatePassword(); show = true })
                            EddyIconButton(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password", { show = !show })
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = origin != null && password.isNotEmpty(), onClick = {
                scope.launch {
                    if (existing == null) vm.passwords.save(origin!!, username.trim(), password)
                    else vm.passwords.replace(existing.id, origin!!, username.trim(), password)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

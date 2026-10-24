package app.eddy.browser.passwords

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import app.eddy.browser.util.readTextCapped
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
        unlock.launch(keyguard.createConfirmDeviceCredentialIntent(str(R.string.unlock_passwords), str(R.string.confirm_screen_lock_passwords)))
    }

    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            // This runs after the exit animation, when an incognito tab may already be back on screen and own the flag.
            if (!vm.incognitoShown) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            vm.passwordsUnlocked = false
        }
    }

    val logins by vm.passwords.logins.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LoginEntity?>(null) }
    var editing by remember { mutableStateOf<LoginEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Relocking closes every sheet and dialog that shows or exports a password, and drops their plaintext.
    LaunchedEffect(needsUnlock) {
        if (needsUnlock) {
            selected = null; editing = null; adding = false; confirmExport = false; overflow = false
            requestUnlock()
        }
    }

    // The file picker stops Eddy, which relocks passwords; a picked file waits here until the user unlocks again.
    var pendingExport by remember { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { pendingExport = it }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    LaunchedEffect(needsUnlock, pendingExport) {
        val uri = pendingExport ?: return@LaunchedEffect
        if (needsUnlock) return@LaunchedEffect
        pendingExport = null
        run {
            val (csv, count) = vm.passwords.exportCsv()
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) } } }
                .onSuccess { vm.snackbar(str(R.string.exported_passwords_the_file_is_not_encry, count)) }
                .onFailure { vm.snackbar(str(R.string.export_failed)) }
        }
    }
    LaunchedEffect(needsUnlock, pendingImport) {
        val uri = pendingImport ?: return@LaunchedEffect
        if (needsUnlock) return@LaunchedEffect
        pendingImport = null
        run {
            val text = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readTextCapped() ?: error("too big") } }.getOrNull()
            if (text == null) return@LaunchedEffect vm.snackbar(str(R.string.could_not_read_the_file_files_over_16_mb))
            val r = vm.passwords.importCsv(text)
            vm.snackbar(
                if (!r.recognised) str(R.string.this_file_is_not_a_password_csv)
                else str(R.string.imported_new_updated, r.added, r.updated) + if (r.unchanged + r.skipped > 0) str(R.string.skipped_suffix, r.unchanged + r.skipped) else "",
            )
        }
    }

    ScreenScaffold(
        str(R.string.passwords), onBack = { vm.screen = Screen.BROWSER },
        actions = {
            if (!needsUnlock) {
                EddyIconButton(Icons.Rounded.Add, str(R.string.add_password), { adding = true })
                Box {
                    EddyIconButton(Icons.Rounded.MoreVert, str(R.string.more), { overflow = true })
                    DropdownMenu(overflow, { overflow = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.import_from_csv)) }, onClick = { overflow = false; import.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
                        DropdownMenuItem(text = { Text(str(R.string.export_to_csv)) }, onClick = { overflow = false; confirmExport = true })
                        DropdownMenuItem(
                            text = { Text(str(R.string.reset_never_save_sites)) },
                            onClick = { overflow = false; vm.passwords.resetNeverSave(); vm.snackbar(str(R.string.eddy_will_offer_to_save_passwords_on_eve)) },
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
                    Text(str(R.string.passwords_are_locked), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text(str(R.string.confirm_your_screen_lock_to_continue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = ::requestUnlock, Modifier.padding(top = 16.dp)) { Text(str(R.string.unlock)) }
                }
            } else {
                SearchField(query, { query = it }, str(R.string.search_passwords))
                val shown = logins.filter { query.isBlank() || it.origin.contains(query, true) || it.username.contains(query, true) }
                if (shown.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.Key,
                        if (query.isBlank()) str(R.string.no_saved_passwords) else str(R.string.no_matches),
                        if (query.isBlank()) str(R.string.when_you_sign_in_or_sign_up_on_a_site_ed) else str(R.string.try_a_different_search),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                        items(shown, key = { it.id }) { login -> LoginRow(login) { selected = login } }
                    }
                }
            }
        }
    }

    if (needsUnlock) return
    selected?.let { login ->
        LoginSheet(
            vm, login,
            onEdit = { editing = login; selected = null },
            onDismiss = { selected = null },
        )
    }
    if (confirmExport) {
        ConfirmDialog(
            str(R.string.export_passwords),
            str(R.string.the_file_is_plain_text_anyone_who_can_op),
            str(R.string.export), { confirmExport = false },
        ) { confirmExport = false; export.launch("eddy-passwords.csv") }
    }
    if (adding) LoginEditor(vm, null, onDismiss = { adding = false })
    editing?.let { LoginEditor(vm, it, onDismiss = { editing = null }) }
}

@Composable
private fun LoginRow(login: LoginEntity, onClick: () -> Unit) {
    val host = UrlUtils.displayHost(login.origin)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = str(R.string.open_details), onClick = onClick).padding(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host, size = Dimens.faviconLarge)
        Column(Modifier.padding(start = 14.dp)) {
            Text(host, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(login.username.ifEmpty { str(R.string.no_username) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            DetailRow(str(R.string.username), login.username.ifEmpty { str(R.string.none) }, copyable = login.username.isNotEmpty()) { vm.copySecret(str(R.string.username), login.username) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.password), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            password == null -> str(R.string.cannot_decrypt)
                            reveal -> password.orEmpty()
                            else -> "••••••••••••"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                EddyIconButton(
                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (reveal) str(R.string.hide_password) else str(R.string.show_password), { reveal = !reveal }, enabled = password != null,
                )
                EddyIconButton(Icons.Rounded.ContentCopy, str(R.string.copy_password), { password?.let { vm.copySecret(str(R.string.password), it) } }, enabled = password != null)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onDismiss(); vm.navigate(login.origin) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Text(str(R.string.open_site), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Text(str(R.string.edit), Modifier.padding(start = 6.dp)) }
                TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Text(str(R.string.delete_4), Modifier.padding(start = 6.dp)) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(str(R.string.delete_this_password), str(R.string.eddy_will_remove_the_saved_login_for_the, host), str(R.string.delete_4), { confirmDelete = false }) {
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
        if (copyable) EddyIconButton(Icons.Rounded.ContentCopy, str(R.string.copy, label), onCopy)
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
        title = { Text(if (existing == null) str(R.string.add_password) else str(R.string.edit_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(site, { site = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.website)) }, singleLine = true, isError = site.isNotBlank() && origin == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.username)) }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.password)) }, singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            EddyIconButton(Icons.Rounded.Refresh, str(R.string.generate_password), { password = PasswordManager.generatePassword(); show = true })
                            EddyIconButton(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) str(R.string.hide_password) else str(R.string.show_password), { show = !show })
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
            }) { Text(str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

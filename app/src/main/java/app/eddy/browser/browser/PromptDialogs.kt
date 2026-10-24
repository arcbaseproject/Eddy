package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.ui.components.CheckRow

/** Shows the oldest pending prompt; later ones appear as earlier ones are answered. */
@Composable
fun PromptHost(vm: BrowserViewModel) {
    when (val p = vm.prompts.firstOrNull()) {
        is Prompt.Permission -> PermissionDialog(p, vm)
        is Prompt.HttpAuth -> AuthDialog(p, vm)
        is Prompt.ExternalApp -> AlertDialog(
            onDismissRequest = { vm.answerExternal(p, false) },
            title = { Text(str(R.string.open_in_another_app_2)) },
            text = { Text(str(R.string.page_wants_to_open_app, p.intent.dataString ?: p.intent.`package` ?: str(R.string.unknown))) },
            confirmButton = { TextButton(onClick = { vm.answerExternal(p, true) }) { Text(str(R.string.open)) } },
            dismissButton = { TextButton(onClick = { vm.answerExternal(p, false) }) { Text(str(R.string.stay_here)) } },
        )
        is Prompt.RiskyDownload -> AlertDialog(
            onDismissRequest = { vm.answerRiskyDownload(p, false) },
            title = { Text(str(R.string.download_this_file)) },
            text = {
                Text(
                    "${p.fileName} from ${p.sourceHost} can install or run code on your device." +
                        (if (p.insecure) str(R.string.it_is_also_being_sent_over_an_unencrypte) else "") +
                        str(R.string.download_it_only_if_you_trust_this_site),
                )
            },
            confirmButton = { TextButton(onClick = { vm.answerRiskyDownload(p, true) }) { Text(str(R.string.download)) } },
            dismissButton = { TextButton(onClick = { vm.answerRiskyDownload(p, false) }) { Text(str(R.string.cancel)) } },
        )
        is Prompt.Paste -> AlertDialog(
            onDismissRequest = { vm.answerPaste(p, false) },
            title = { Text(str(R.string.paste_from_clipboard)) },
            text = { Text("${p.host} wants to read the text you copied.") },
            confirmButton = { TextButton(onClick = { vm.answerPaste(p, true) }) { Text(str(R.string.paste)) } },
            dismissButton = { TextButton(onClick = { vm.answerPaste(p, false) }) { Text(str(R.string.block)) } },
        )
        null -> Unit
    }
}

@Composable
private fun PermissionDialog(p: Prompt.Permission, vm: BrowserViewModel) {
    var rememberChoice by remember(p) { mutableStateOf(true) }
    val what = p.features.joinToString(" and ") {
        when (it) {
            SiteFeature.CAMERA -> "camera"
            SiteFeature.MICROPHONE -> "microphone"
            SiteFeature.LOCATION -> "location"
            else -> it.name.lowercase()
        }
    }
    AlertDialog(
        onDismissRequest = { vm.answerPermission(p, false, false) },
        title = { Text(str(R.string.allow_to_use_your, p.host, what)) },
        text = { if (!p.incognito) CheckRow(str(R.string.remember_my_choice_for_this_site), rememberChoice) { rememberChoice = it } },
        confirmButton = { TextButton(onClick = { vm.answerPermission(p, true, rememberChoice) }) { Text(str(R.string.allow)) } },
        dismissButton = { TextButton(onClick = { vm.answerPermission(p, false, rememberChoice) }) { Text(str(R.string.block)) } },
    )
}

@Composable
private fun AuthDialog(p: Prompt.HttpAuth, vm: BrowserViewModel) {
    var user by remember(p) { mutableStateOf("") }
    var pass by remember(p) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.answerAuth(p, null, null) },
        title = { Text(str(R.string.sign_in_to, p.host)) },
        text = {
            Column {
                if (p.realm.isNotBlank()) Text("Realm: ${p.realm}")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.username)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { vm.answerAuth(p, user, pass) }) { Text(str(R.string.sign_in)) } },
        dismissButton = { TextButton(onClick = { vm.answerAuth(p, null, null) }) { Text(str(R.string.cancel)) } },
    )
}

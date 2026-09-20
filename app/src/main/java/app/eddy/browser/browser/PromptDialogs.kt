package app.eddy.browser.browser

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
            title = { Text("Open in another app?") },
            text = { Text("This page wants to open a link in another app:\n\n${p.intent.dataString ?: p.intent.`package` ?: "unknown"}") },
            confirmButton = { TextButton(onClick = { vm.answerExternal(p, true) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { vm.answerExternal(p, false) }) { Text("Stay here") } },
        )
        null -> Unit
    }
}

@Composable
private fun PermissionDialog(p: Prompt.Permission, vm: BrowserViewModel) {
    var rememberChoice by remember { mutableStateOf(true) }
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
        title = { Text("Allow ${p.host} to use your $what?") },
        text = { if (!p.incognito) CheckRow("Remember my choice for this site", rememberChoice) { rememberChoice = it } },
        confirmButton = { TextButton(onClick = { vm.answerPermission(p, true, rememberChoice) }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { vm.answerPermission(p, false, rememberChoice) }) { Text("Block") } },
    )
}

@Composable
private fun AuthDialog(p: Prompt.HttpAuth, vm: BrowserViewModel) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.answerAuth(p, null, null) },
        title = { Text("Sign in to ${p.host}") },
        text = {
            Column {
                if (p.realm.isNotBlank()) Text("Realm: ${p.realm}")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { vm.answerAuth(p, user, pass) }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = { vm.answerAuth(p, null, null) }) { Text("Cancel") } },
    )
}

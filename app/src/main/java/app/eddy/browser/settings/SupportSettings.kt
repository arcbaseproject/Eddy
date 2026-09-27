package app.eddy.browser.settings

import android.content.Context
import android.os.Build
import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.eddy.browser.BuildConfig
import app.eddy.browser.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Web3Forms access keys are public by design: they only allow sending to the inbox they belong to.
private const val WEB3FORMS_KEY = "470c7b16-ebff-4b92-960a-c40cd5641301"
private const val COOLDOWN_MS = 10 * 60 * 1000L
private const val MAX_MESSAGE = 4000

@Composable
fun SupportSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("support", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val emailOk = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun send() {
        val wait = prefs.getLong("last_sent", 0) + COOLDOWN_MS - System.currentTimeMillis()
        if (wait > 0) {
            status = "Please wait ${wait / 60_000 + 1} min before sending another message."
            return
        }
        sending = true
        status = null
        scope.launch {
            val ok = submit(email.trim(), message.trim())
            sending = false
            if (ok) {
                prefs.edit().putLong("last_sent", System.currentTimeMillis()).apply()
                message = ""
                status = "Sent. Thanks for reaching out."
            } else {
                status = "Could not send. Check your connection and try again."
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutterLarge, vertical = 8.dp)) {
        Text(
            "Report a bug, ask a question or suggest a feature. Add your email if you want a reply.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth().padding(top = 16.dp),
            label = { Text("Email (optional)") }, singleLine = true, isError = !emailOk,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            message, { message = it.take(MAX_MESSAGE) }, Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(top = 12.dp),
            label = { Text("Message") },
        )
        Button(::send, Modifier.padding(top = 16.dp), enabled = !sending && emailOk && message.isNotBlank()) {
            Text(if (sending) "Sending…" else "Send")
        }
        status?.let { Text(it, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    SettingsFootnote(
        "Your message, your email if you add one, and the Eddy and Android versions go to the developer through Web3Forms. " +
            "See the privacy policy for details.",
    )
}

private suspend fun submit(email: String, message: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val body = JSONObject()
            .put("access_key", WEB3FORMS_KEY)
            .put("subject", "Eddy support")
            .put("from_name", "Eddy ${BuildConfig.VERSION_NAME}")
            .put("message", message)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("android_version", Build.VERSION.RELEASE)
        if (email.isNotEmpty()) body.put("email", email)
        val conn = URL("https://api.web3forms.com/submit").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode == 200 && JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optBoolean("success")
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}

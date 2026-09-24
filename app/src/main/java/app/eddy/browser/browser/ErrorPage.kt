package app.eddy.browser.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.entrance
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils

/** Native replacement for WebView's own error pages, with a matching action for each failure. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorPage(
    error: PageError,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onProceedUnsafe: () -> Unit,
    onContinueInsecure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, title, message) = when (error.kind) {
        ErrorKind.OFFLINE -> Triple(Icons.Rounded.WifiOff, "You're offline", "Check your Wi-Fi or mobile data connection. Eddy retries when you reconnect.")
        ErrorKind.DNS -> Triple(Icons.Rounded.CloudOff, "Can't find this site", "Eddy could not find ${UrlUtils.displayHost(error.url)}. Check the spelling or try again later.")
        ErrorKind.SSL -> Triple(Icons.Rounded.GppBad, "Connection isn't private", error.detail.ifBlank { "Eddy could not verify this site's security certificate." })
        ErrorKind.TIMEOUT -> Triple(Icons.Rounded.Timer, "Took too long to respond", "${UrlUtils.displayHost(error.url)} did not answer in time. The site may be down, or your connection may be slow.")
        ErrorKind.UNAVAILABLE -> Triple(Icons.Rounded.ErrorOutline, "This page isn't available", "Eddy could not load ${UrlUtils.displayHost(error.url).ifEmpty { "the page" }}.")
        ErrorKind.INSECURE -> Triple(
            Icons.Rounded.NoEncryption, "This site doesn't support HTTPS",
            "${UrlUtils.displayHost(error.url)} did not answer over a secure connection. HTTPS-only mode blocked the plain http version.",
        )
    }
    val isSsl = error.kind == ErrorKind.SSL
    val isInsecure = error.kind == ErrorKind.INSECURE
    val warn = isSsl || isInsecure
    var confirmProceed by remember { mutableStateOf(false) }
    var confirmInsecure by remember { mutableStateOf(false) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(Dimens.gutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.entrance(0).size(112.dp).background(
                    if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    if (warn) MaterialShapes.SoftBurst.toShape() else MaterialShapes.Cookie9Sided.toShape(),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null, Modifier.size(48.dp),
                    tint = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(title, Modifier.entrance(1), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(message, Modifier.entrance(2), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(error.url, Modifier.entrance(2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, maxLines = 2)
            if (isInsecure) {
                Button(onClick = onRetry, Modifier.entrance(3)) { Text("Try again") }
                OutlinedButton(onClick = onBack, Modifier.entrance(3)) { Text("Go back") }
                TextButton(
                    onClick = { confirmInsecure = true }, Modifier.entrance(3),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Continue without HTTPS") }
            } else if (isSsl) {
                Button(onClick = onBack, Modifier.entrance(3)) { Text("Back to safety") }
                if (error.sslHandler != null) {
                    TextButton(onClick = { confirmProceed = true }, Modifier.entrance(3), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Proceed anyway (unsafe)")
                    }
                }
            } else {
                Button(onClick = onRetry, Modifier.entrance(3)) { Text("Try again") }
                OutlinedButton(onClick = onBack, Modifier.entrance(3)) { Text("Go back") }
            }
        }
    }
    }

    if (confirmInsecure) {
        ConfirmDialog(
            title = "Load this site without HTTPS?",
            message = "Anything you send to ${UrlUtils.displayHost(error.url)} travels unencrypted, and anyone on the network can read or change it. Eddy allows plain http for this site until you close the app.",
            confirmLabel = "Continue",
            onDismiss = { confirmInsecure = false },
            onConfirm = { confirmInsecure = false; onContinueInsecure() },
        )
    }

    if (confirmProceed) {
        ConfirmDialog(
            title = "Continue to an unsafe site?",
            message = "Attackers may be able to read or change what you send to ${UrlUtils.displayHost(error.url)}, including passwords and card numbers. Continue only if you accept that risk.",
            confirmLabel = "Continue anyway",
            onDismiss = { confirmProceed = false },
            onConfirm = { confirmProceed = false; onProceedUnsafe() },
        )
    }
}

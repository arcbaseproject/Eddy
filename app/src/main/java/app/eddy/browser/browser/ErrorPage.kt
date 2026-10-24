package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
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
import androidx.compose.material.icons.rounded.Block
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
        ErrorKind.OFFLINE -> Triple(Icons.Rounded.WifiOff, str(R.string.you_re_offline), str(R.string.check_your_wi_fi_or_mobile_data_connecti))
        ErrorKind.DNS -> Triple(Icons.Rounded.CloudOff, str(R.string.can_t_find_this_site), str(R.string.eddy_could_not_find_check_the_spelling_o, UrlUtils.displayHost(error.url)))
        ErrorKind.SSL -> Triple(Icons.Rounded.GppBad, str(R.string.connection_isn_t_private), error.detail.ifBlank { str(R.string.eddy_could_not_verify_this_site_s_securi) })
        ErrorKind.TIMEOUT -> Triple(Icons.Rounded.Timer, str(R.string.took_too_long_to_respond), "${UrlUtils.displayHost(error.url)} did not answer in time. The site may be down, or your connection may be slow.")
        ErrorKind.UNAVAILABLE -> Triple(Icons.Rounded.ErrorOutline, str(R.string.this_page_isn_t_available), str(R.string.eddy_could_not_load, UrlUtils.displayHost(error.url).ifEmpty { str(R.string.the_page) }))
        ErrorKind.INSECURE -> Triple(
            Icons.Rounded.NoEncryption, str(R.string.this_site_doesn_t_support_https),
            "${UrlUtils.displayHost(error.url)} did not answer over a secure connection. HTTPS-only mode blocked the plain http version.",
        )
        ErrorKind.BLOCKED -> Triple(
            Icons.Rounded.Block, str(R.string.eddy_blocked_this_page),
            "${UrlUtils.displayHost(error.url)} is on your ad or tracker blocklist.",
        )
    }
    val isSsl = error.kind == ErrorKind.SSL
    val isInsecure = error.kind == ErrorKind.INSECURE
    val isBlocked = error.kind == ErrorKind.BLOCKED
    val warn = isSsl || isInsecure || isBlocked
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
                Button(onClick = onRetry, Modifier.entrance(3)) { Text(str(R.string.try_again)) }
                OutlinedButton(onClick = onBack, Modifier.entrance(3)) { Text(str(R.string.go_back)) }
                TextButton(
                    onClick = { confirmInsecure = true }, Modifier.entrance(3),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(str(R.string.continue_without_https)) }
            } else if (isBlocked) {
                Button(onClick = onBack, Modifier.entrance(3)) { Text(str(R.string.go_back)) }
                TextButton(onClick = onRetry, Modifier.entrance(3)) { Text(str(R.string.open_anyway)) }
            } else if (isSsl) {
                Button(onClick = onBack, Modifier.entrance(3)) { Text(str(R.string.back_to_safety)) }
                if (error.sslHandler != null) {
                    TextButton(onClick = { confirmProceed = true }, Modifier.entrance(3), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(str(R.string.proceed_anyway_unsafe))
                    }
                }
            } else {
                Button(onClick = onRetry, Modifier.entrance(3)) { Text(str(R.string.try_again)) }
                OutlinedButton(onClick = onBack, Modifier.entrance(3)) { Text(str(R.string.go_back)) }
            }
        }
    }
    }

    if (confirmInsecure) {
        ConfirmDialog(
            title = str(R.string.load_this_site_without_https),
            message = str(R.string.anything_you_send_to_travels_unencrypted, UrlUtils.displayHost(error.url)),
            confirmLabel = str(R.string.continue_label),
            onDismiss = { confirmInsecure = false },
            onConfirm = { confirmInsecure = false; onContinueInsecure() },
        )
    }

    if (confirmProceed) {
        ConfirmDialog(
            title = str(R.string.continue_to_an_unsafe_site),
            message = str(R.string.attackers_may_be_able_to_read_or_change, UrlUtils.displayHost(error.url)),
            confirmLabel = str(R.string.continue_anyway),
            onDismiss = { confirmProceed = false },
            onConfirm = { confirmProceed = false; onProceedUnsafe() },
        )
    }
}

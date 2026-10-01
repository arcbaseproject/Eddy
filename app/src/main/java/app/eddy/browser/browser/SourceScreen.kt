package app.eddy.browser.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.ScreenScaffold

/** Plain-text view of a page's markup or a downloaded text file. One item per line keeps big files scrolling smoothly. */
@Composable
fun SourceScreen(vm: BrowserViewModel) {
    val open = vm.openSource ?: return
    ScreenScaffold(
        title = open.name.ifBlank { "Source" },
        onBack = { vm.closeSource() },
        actions = { EddyIconButton(Icons.Rounded.ContentCopy, "Copy source", { vm.copySource() }) },
    ) { padding ->
        SelectionContainer(Modifier.fillMaxSize().padding(padding)) {
            if (open.isLog) {
                val entries = remember(open) { LogEntry.parse(open.text) }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(entries) { LogRow(it); HorizontalDivider() }
                }
            } else {
                val lines = remember(open) { open.text.lines() }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(12.dp)) {
                    items(lines) { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (e.level) {
        'E', 'F' -> "Error" to colors.error
        'W' -> "Warning" to colors.tertiary
        'I' -> "Info" to colors.primary
        else -> (if (e.level == 'D') "Debug" else "Verbose") to colors.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text(e.tag, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.time, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Text(e.message, Modifier.padding(top = 4.dp), color = if (e.level in "EF") colors.error else colors.onSurface, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

/** One log message from `logcat -v time` output. */
data class LogEntry(val time: String, val level: Char, val tag: String, val message: String) {
    companion object {
        private val LINE = Regex("""\d\d-\d\d (\d\d:\d\d:\d\d)\.\d+ ([VDIWEF])/(.*?)\(\s*\d+\): ?(.*)""")

        /**
         * Newest first. Logcat writes each line of a multi-line message (a stack trace) under its own header,
         * so consecutive lines with the same second, level and tag join into one entry.
         */
        fun parse(text: String): List<LogEntry> {
            val out = mutableListOf<LogEntry>()
            for (line in text.lineSequence()) {
                val (time, level, rawTag, message) = LINE.matchEntire(line)?.destructured ?: continue
                val e = LogEntry(time, level[0], rawTag.trim(), message)
                val last = out.lastOrNull()
                if (last != null && last.copy(message = "") == e.copy(message = "")) out[out.lastIndex] = last.copy(message = last.message + "\n" + message)
                else out += e
            }
            return out.asReversed()
        }
    }
}

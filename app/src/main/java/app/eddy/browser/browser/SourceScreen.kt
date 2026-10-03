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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.DisableSelection
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
                val html = remember(open) { open.text.trimStart().startsWith("<") }
                val lines = remember(open) { (if (html) HtmlPretty.format(open.text) else open.text).lines() }
                val colors = MaterialTheme.colorScheme
                val dark = colors.background.luminance() < 0.5f
                // Fixed hues (GitHub's), because a tonal theme makes tag, attribute and value all the same colour.
                val palette = remember(dark) {
                    if (dark) HtmlColors(Color(0xFF7EE787), Color(0xFF79C0FF), Color(0xFFFFA657), Color(0xFF8B949E))
                    else HtmlColors(Color(0xFF116329), Color(0xFF0550AE), Color(0xFF953800), Color(0xFF6E7781))
                }
                val gutter = (lines.size.toString().length * 8 + 16).dp
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(vertical = 12.dp)) {
                    itemsIndexed(lines) { i, line ->
                        Row(Modifier.padding(end = 12.dp)) {
                            DisableSelection {
                                Text(
                                    "${i + 1}", Modifier.width(gutter).padding(end = 8.dp), color = colors.outline, textAlign = TextAlign.End,
                                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            // Wrapped lines hang under their own indent instead of falling back to the margin.
                            val indent = line.length - line.trimStart().length
                            Text(
                                if (html) HtmlPretty.highlight(line, palette) else AnnotatedString(line), Modifier.weight(1f),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall.copy(textIndent = TextIndent(restLine = ((indent + 2) * 0.6).em)),
                            )
                        }
                    }
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

class HtmlColors(val tag: Color, val attr: Color, val value: Color, val comment: Color)

/** Readable markup for the source viewer: one tag per line, indented by depth, with tags and attributes coloured. */
object HtmlPretty {
    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
    // Their content is not markup, so it is copied through rather than split on '<'.
    private val RAW = setOf("script", "style", "pre", "textarea")
    private val NAME = Regex("""^</?([A-Za-z][\w:-]*)""")
    private val TAG_NAME = Regex("""^</?[!A-Za-z][\w:-]*""")
    private val ATTR = Regex("""([^\s=<>/"']+)(?:\s*=\s*("[^"]*"|'[^']*'|[^\s>]+))?""")

    // ponytail: a '>' inside an attribute value ends the tag early; only the layout suffers, not the text.
    fun format(html: String): String {
        val out = StringBuilder()
        var depth = 0
        var i = 0
        fun emit(s: String) = s.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach {
            repeat(depth) { out.append("  ") }
            out.append(it).append('\n')
        }
        fun until(token: String, from: Int, past: Boolean) =
            html.indexOf(token, from, ignoreCase = true).let { if (it < 0) html.length else if (past) it + token.length else it }
        while (i < html.length) {
            val end = when {
                html.startsWith("<!--", i) -> until("-->", i, past = true).also { emit(html.substring(i, it)) }
                html[i] == '<' -> until(">", i, past = true).also { end ->
                    val tag = html.substring(i, end)
                    val name = NAME.find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
                    when {
                        tag.startsWith("</") -> { depth = (depth - 1).coerceAtLeast(0); emit(tag) }
                        tag.startsWith("<!") || tag.startsWith("<?") || tag.endsWith("/>") || name in VOID -> emit(tag)
                        name in RAW -> {
                            emit(tag)
                            depth++
                            val close = until("</$name", end, past = false)
                            emit(html.substring(end, close))
                            i = close
                            continue
                        }
                        else -> { emit(tag); depth++ }
                    }
                }
                else -> until("<", i, past = false).also { emit(html.substring(i, it)) }
            }
            i = end
        }
        return out.toString()
    }

    fun highlight(line: String, c: HtmlColors): AnnotatedString = buildAnnotatedString {
        append(line)
        val text = line.trimStart()
        val off = line.length - text.length
        if (text.startsWith("<!--")) return@buildAnnotatedString addStyle(SpanStyle(c.comment), off, line.length)
        val name = TAG_NAME.find(text) ?: return@buildAnnotatedString
        addStyle(SpanStyle(c.tag), off, off + name.range.last + 1)
        if (text.endsWith(">")) addStyle(SpanStyle(c.tag), line.length - if (text.endsWith("/>")) 2 else 1, line.length)
        ATTR.findAll(text.removeSuffix(">"), name.range.last + 1).forEach { m ->
            m.groups[1]?.range?.let { addStyle(SpanStyle(c.attr), off + it.first, off + it.last + 1) }
            m.groups[2]?.range?.let { addStyle(SpanStyle(c.value), off + it.first, off + it.last + 1) }
        }
    }
}

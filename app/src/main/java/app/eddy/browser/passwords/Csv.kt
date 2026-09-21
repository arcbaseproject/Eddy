package app.eddy.browser.passwords

/** Minimal RFC 4180 reader/writer: quoted fields, doubled quotes, and line breaks inside quotes. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = if (text.startsWith("\uFEFF")) 1 else 0
        fun endCell() { row.add(cell.toString()); cell.clear() }
        fun endRow() {
            endCell()
            // Skip blank lines, which parse as a single empty cell.
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == ',' -> endCell()
                c == '\r' -> if (text.getOrNull(i + 1) == '\n') i++.also { endRow() } else endRow()
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    fun row(cells: List<String>): String = cells.joinToString(",") { cell ->
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }
}

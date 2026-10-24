package app.eddy.browser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Import files bigger than this are refused instead of read into memory. */
const val MAX_IMPORT_BYTES = 16 * 1024 * 1024

/** Reads the whole stream as UTF-8 and closes it, or returns null when it is longer than [max] bytes. */
fun InputStream.readTextCapped(max: Int = MAX_IMPORT_BYTES): String? = use { input ->
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (out.size() + n > max) return null
        out.write(buf, 0, n)
    }
    out.toString(Charsets.UTF_8.name())
}

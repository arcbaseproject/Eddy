package app.eddy.browser

import app.eddy.browser.util.Strings
import java.io.File

/** Serves R.string ids from res/values/strings.xml, so JVM tests see the same English text the app shows. */
object TestStrings {
    val byName: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { m ->
            m.groupValues[1] to m.groupValues[2].replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("\\n", "\n")
        }
    }
    val nameOf: Map<Int, String> by lazy { R.string::class.java.fields.associate { it.getInt(null) to it.name } }

    fun install() {
        Strings.lookup = { id, args -> byName.getValue(nameOf.getValue(id)).let { if (args.isEmpty()) it else String.format(it, *args) } }
    }
}

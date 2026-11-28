package app.eddy.browser.settings

import android.content.Context
import android.os.Build
import app.eddy.browser.util.readTextCapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class Release(val version: String, val notes: String, val url: String)

/**
 * The newest release on GitHub. Asks for the release list, not /latest, because /latest skips
 * pre-releases and every Eddy build so far is one. Null when offline or GitHub refuses.
 */
suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL("https://api.github.com/repos/arcbaseproject/Eddy/releases?per_page=1").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val o = JSONArray(conn.inputStream.readTextCapped()!!).getJSONObject(0)
            // The body is GitHub markdown; drop heading marks and code ticks so the dialog shows plain text.
            val notes = o.optString("body").lines().joinToString("\n") { it.trimStart('#', ' ').replace("`", "") }.trim()
            Release(o.getString("tag_name").removePrefix("v"), notes, o.getString("html_url"))
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/**
 * Whether [latest] has a higher x.y.z than [current]; suffixes such as -beta are ignored.
 * shortcut: 0.7.0 does not count as newer than 0.7.0-beta; compare suffixes if a stable follows a beta of the same number.
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(v: String) = Regex("\\d+(\\.\\d+)*").find(v)?.value?.split('.')?.map { it.toInt() }.orEmpty()
    val a = parts(latest)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

/**
 * Play and F-Droid installs update through their store. Play does not allow apps to point at other
 * update sources, and F-Droid signs with its own key, so a GitHub APK would not install over it.
 */
fun updatedByStore(context: Context): Boolean {
    val pm = context.packageManager
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
    }.getOrNull()
    return installer in StoreInstallers
}

private val StoreInstallers = setOf("com.android.vending", "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid")

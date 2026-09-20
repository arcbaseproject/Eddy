package app.eddy.browser.browser

import app.eddy.browser.bookmarks.BookmarkManager
import app.eddy.browser.data.models.Settings
import app.eddy.browser.history.HistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SuggestionKind { CLIPBOARD, BOOKMARK, HISTORY, SEARCH }

class Suggestion(val kind: SuggestionKind, val text: String, val title: String, val subtitle: String = "")

/** Local suggestions (bookmarks, history) are instant; the search engine's suggestions arrive afterwards. */
class SuggestionSource(
    private val history: HistoryManager,
    private val bookmarks: BookmarkManager,
    private val settings: () -> Settings,
) {
    suspend fun local(query: String): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        val marked = bookmarks.suggest(query, 3).map { Suggestion(SuggestionKind.BOOKMARK, it.url, it.title, it.url) }
        val seen = marked.map { it.text }.toSet()
        val visited = history.suggest(query, 5).filter { it.url !in seen }.take(4)
            .map { Suggestion(SuggestionKind.HISTORY, it.url, it.title.ifBlank { it.host }, it.url) }
        return marked + visited
    }

    suspend fun remote(query: String): List<Suggestion> {
        val template = settings().searchEngine.suggestUrl ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(template.replace("%s", URLEncoder.encode(query, "UTF-8"))).openConnection() as HttpURLConnection
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.setRequestProperty("Accept", "application/json")
                try {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = JSONArray(body).optJSONArray(1) ?: return@runCatching emptyList()
                    (0 until minOf(list.length(), 6)).mapNotNull { i ->
                        val item = list.opt(i)
                        val text = if (item is org.json.JSONObject) item.optString("phrase") else item?.toString()
                        text?.takeIf { it.isNotBlank() }?.let { Suggestion(SuggestionKind.SEARCH, it, it) }
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(emptyList())
        }
    }
}

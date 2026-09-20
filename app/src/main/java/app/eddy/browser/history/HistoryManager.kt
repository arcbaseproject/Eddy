package app.eddy.browser.history

import app.eddy.browser.data.database.HistoryDao
import app.eddy.browser.data.database.HistoryEntry
import app.eddy.browser.data.database.SiteVisit
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HistoryManager(private val dao: HistoryDao, private val scope: CoroutineScope) {

    suspend fun record(url: String, title: String): Long {
        if (!UrlUtils.isWebUrl(url)) return 0L
        return dao.insert(HistoryEntry(url = url, title = title, host = UrlUtils.displayHost(url), visitTime = System.currentTimeMillis()))
    }

    fun updateTitle(id: Long, title: String) {
        if (id > 0 && title.isNotBlank()) scope.launch { dao.updateTitle(id, title) }
    }

    fun entries(query: String, limit: Int = 3000): Flow<List<HistoryEntry>> =
        if (query.isBlank()) dao.recent(limit) else dao.search(query.trim(), limit)

    fun frequentSites(limit: Int): Flow<List<SiteVisit>> = dao.frequentSites(limit)
    fun recentSites(limit: Int): Flow<List<SiteVisit>> = dao.recentSites(limit)

    suspend fun suggest(query: String, limit: Int) = dao.suggest(query, limit)

    fun delete(id: Long) { scope.launch { dao.delete(id) } }

    fun deleteSince(millisAgo: Long) { scope.launch { dao.deleteSince(System.currentTimeMillis() - millisAgo) } }

    fun clear() { scope.launch { dao.clear() } }

    /** History older than three months is dropped once per launch to keep queries fast. */
    fun prune() { scope.launch { dao.deleteBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)) } }
}

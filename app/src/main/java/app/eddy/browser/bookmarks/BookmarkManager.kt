package app.eddy.browser.bookmarks

import app.eddy.browser.data.database.Bookmark
import app.eddy.browser.data.database.BookmarkDao
import app.eddy.browser.data.database.BookmarkFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BookmarkManager(private val dao: BookmarkDao, private val scope: CoroutineScope) {
    val bookmarks: Flow<List<Bookmark>> = dao.all()
    val folders: Flow<List<BookmarkFolder>> = dao.folders()

    fun isBookmarked(url: String): Flow<Boolean> = dao.isBookmarked(url)

    suspend fun suggest(query: String, limit: Int) = dao.suggest(query, limit)

    /** Adds the bookmark, or removes it when the URL is already saved. Returns true if now bookmarked. */
    suspend fun toggle(url: String, title: String): Boolean {
        val exists = dao.isBookmarked(url).first()
        if (exists) dao.deleteByUrl(url) else dao.insert(Bookmark(title = title.ifBlank { url }, url = url))
        return !exists
    }

    fun save(bookmark: Bookmark) { scope.launch { if (bookmark.id == 0L) dao.insert(bookmark) else dao.update(bookmark) } }
    fun delete(bookmark: Bookmark) { scope.launch { dao.delete(bookmark) } }
    fun addFolder(name: String) { scope.launch { dao.insertFolder(BookmarkFolder(name = name)) } }
    fun renameFolder(folder: BookmarkFolder, name: String) { scope.launch { dao.updateFolder(folder.copy(name = name)) } }

    fun deleteFolder(folder: BookmarkFolder) {
        scope.launch {
            dao.deleteInFolder(folder.id)
            dao.deleteFolder(folder.id)
        }
    }

    /** Netscape bookmark HTML, the format every major browser can import. */
    suspend fun exportHtml(): String {
        val all = dao.all().first()
        val folders = dao.folders().first()
        return buildString {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
            fun link(b: Bookmark, indent: String) =
                append("$indent<DT><A HREF=\"${b.url.escape()}\" ADD_DATE=\"${b.createdAt / 1000}\">${b.title.escape()}</A>\n")
            all.filter { it.folderId == Bookmark.ROOT_FOLDER }.forEach { link(it, "    ") }
            for (f in folders) {
                append("    <DT><H3>${f.name.escape()}</H3>\n    <DL><p>\n")
                all.filter { it.folderId == f.id }.forEach { link(it, "        ") }
                append("    </DL><p>\n")
            }
            append("</DL><p>\n")
        }
    }

    /** Imports Netscape HTML; nested folders are flattened into one level. Returns the count added. */
    suspend fun importHtml(html: String): Int {
        val token = Regex("<H3[^>]*>(.*?)</H3>|<A\\s[^>]*?HREF=\"([^\"]*)\"[^>]*>(.*?)</A>|</DL>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val existingFolders = dao.folders().first().associateBy { it.name }.toMutableMap()
        var folderId = Bookmark.ROOT_FOLDER
        var count = 0
        for (m in token.findAll(html)) {
            val (folderName, href, title) = m.destructured
            when {
                href.isNotEmpty() -> if (href.startsWith("http", true)) {
                    dao.insert(Bookmark(title = title.unescape().ifBlank { href }, url = href.unescape(), folderId = folderId))
                    count++
                }
                m.value.startsWith("<H3", true) -> {
                    val name = folderName.unescape().trim().ifBlank { "Imported" }
                    folderId = existingFolders.getOrPut(name) {
                        BookmarkFolder(id = dao.insertFolder(BookmarkFolder(name = name)), name = name)
                    }.id
                }
                else -> folderId = Bookmark.ROOT_FOLDER
            }
        }
        return count
    }

    private fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun String.unescape() = replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}

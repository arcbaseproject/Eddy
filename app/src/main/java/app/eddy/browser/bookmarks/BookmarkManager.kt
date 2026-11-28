package app.eddy.browser.bookmarks

import app.eddy.browser.R
import app.eddy.browser.util.str
import app.eddy.browser.data.database.Bookmark
import androidx.room.withTransaction
import app.eddy.browser.data.database.EddyDatabase
import app.eddy.browser.data.database.BookmarkFolder
import app.eddy.browser.data.database.withDescendants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BookmarkManager(private val database: EddyDatabase, private val scope: CoroutineScope) {
    private val dao = database.bookmarks()
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
    fun addFolder(name: String, parentId: Long = Bookmark.ROOT_FOLDER) {
        scope.launch { dao.insertFolder(BookmarkFolder(name = name, parentId = parentId, position = dao.lastPosition() + 1)) }
    }
    fun renameFolder(folder: BookmarkFolder, name: String) { scope.launch { dao.updateFolder(folder.copy(name = name)) } }
    fun moveFolder(folder: BookmarkFolder, parentId: Long) { scope.launch { dao.updateFolder(folder.copy(parentId = parentId)) } }

    /** Deletes the folder with every folder and bookmark inside it. */
    fun deleteFolder(folder: BookmarkFolder) {
        scope.launch {
            database.withTransaction {
                dao.folders().first().withDescendants(folder.id).forEach {
                    dao.deleteInFolder(it)
                    dao.deleteFolder(it)
                }
            }
        }
    }

    /** Netscape bookmark HTML, the format every major browser can import. */
    suspend fun exportHtml(): String = database.withTransaction {
        val all = dao.all().first()
        val folders = dao.folders().first()
        buildString {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
            fun link(b: Bookmark, indent: String) =
                append("$indent<DT><A HREF=\"${b.url.escape()}\" ADD_DATE=\"${b.createdAt / 1000}\">${b.title.escape()}</A>\n")
            val byFolder = all.groupBy { it.folderId }
            val subfolders = folders.groupBy { it.parentId }
            fun folder(id: Long, indent: String, seen: Set<Long>) {
                for (item in (subfolders[id].orEmpty() + byFolder[id].orEmpty()).sortedBy { it.position }) when (item) {
                    is Bookmark -> link(item, indent)
                    // A folder that is somehow its own ancestor would recurse forever; write it once.
                    is BookmarkFolder -> if (item.id !in seen) {
                        append("$indent<DT><H3>${item.name.escape()}</H3>\n$indent<DL><p>\n")
                        folder(item.id, "$indent    ", seen + item.id)
                        append("$indent</DL><p>\n")
                    }
                }
            }
            folder(Bookmark.ROOT_FOLDER, "    ", emptySet())
            append("</DL><p>\n")
        }
    }

    /** Imports Netscape HTML, keeping nested folders and the file's order. Returns the count added. */
    suspend fun importHtml(html: String): Int = database.withTransaction {
        val token = Regex("<H3[^>]*>(.*?)</H3>|<A\\s[^>]*?HREF=\"([^\"]*)\"[^>]*>(.*?)</A>|</DL>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        // Keyed by parent and name, so importing the same file twice merges into the folders it made before.
        val existingFolders = dao.folders().first().associateBy { it.parentId to it.name }.toMutableMap()
        // Each folder's own </DL> returns to the folder around it.
        val open = ArrayDeque<Long>()
        var position = dao.lastPosition()
        var count = 0
        for (m in token.findAll(html)) {
            val (folderName, href, title) = m.destructured
            when {
                href.isNotEmpty() -> if (href.startsWith("http", true)) {
                    dao.insert(Bookmark(title = title.unescape().ifBlank { href }, url = href.unescape(), position = ++position, folderId = open.lastOrNull() ?: Bookmark.ROOT_FOLDER))
                    count++
                }
                m.value.startsWith("<H3", true) -> {
                    val name = folderName.unescape().trim().ifBlank { str(R.string.imported) }
                    val parent = open.lastOrNull() ?: Bookmark.ROOT_FOLDER
                    val folder = existingFolders[parent to name]
                    val ordered = (folder ?: BookmarkFolder(name = name, parentId = parent)).copy(position = ++position)
                    val id = if (folder == null) dao.insertFolder(ordered) else {
                        dao.updateFolder(ordered)
                        folder.id
                    }
                    existingFolders[parent to name] = ordered.copy(id = id)
                    open.addLast(id)
                }
                else -> open.removeLastOrNull()
            }
        }
        count
    }

    private fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun String.unescape() = replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}

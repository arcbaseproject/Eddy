package app.eddy.browser.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index("visitTime"), Index("host")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val visitTime: Long,
)

@Entity(tableName = "bookmarks", indices = [Index("folderId"), Index(value = ["url", "folderId"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderId: Long = ROOT_FOLDER,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROOT_FOLDER = 0L
    }
}

@Entity(tableName = "bookmark_folders")
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    val userAgent: String = "",
    val referer: String = "",
    val etag: String = "",
    val resumable: Boolean = true,
    val contentUri: String = "",
    val error: String = "",
)

/** Per-host overrides. A null column means "use the global default" (or "ask" for prompts). */
@Entity(tableName = "site_settings")
data class SiteSettings(
    @PrimaryKey val host: String,
    val location: Int? = null,
    val camera: Int? = null,
    val microphone: Int? = null,
    val javascript: Int? = null,
    val popups: Int? = null,
    val thirdPartyCookies: Int? = null,
    val desktop: Int? = null,
    val contentBlocking: Int? = null,
) {
    companion object {
        const val ALLOW = 1
        const val BLOCK = 0
    }
}

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

/** A saved login. [password] is AES-GCM ciphertext (see PasswordVault); usernames and origins are stored as typed. */
@Entity(
    tableName = "logins",
    indices = [Index(value = ["origin", "username"], unique = true), Index("origin")],
)
data class LoginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Scheme, host and port, e.g. https://example.com. Autofill matches this exactly. */
    val origin: String,
    val username: String,
    val password: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long = 0,
)

/** Origins where the user chose "Never save". */
@Entity(tableName = "login_blocklist")
data class LoginBlock(@PrimaryKey val origin: String)

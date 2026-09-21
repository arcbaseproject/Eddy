package app.eddy.browser.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SiteVisit(val url: String, val title: String, val host: String, val visits: Int, val lastVisit: Long)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("UPDATE history SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("SELECT * FROM history ORDER BY visitTime DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' " +
            "ORDER BY visitTime DESC LIMIT :limit",
    )
    fun search(q: String, limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' " +
            "GROUP BY url ORDER BY MAX(visitTime) DESC LIMIT :limit",
    )
    suspend fun suggest(q: String, limit: Int): List<HistoryEntry>

    /** One row per host: most recent page of the site, ranked by visit count. */
    @Query(
        "SELECT url, title, host, COUNT(*) AS visits, MAX(visitTime) AS lastVisit FROM history " +
            "GROUP BY host ORDER BY visits DESC, lastVisit DESC LIMIT :limit",
    )
    fun frequentSites(limit: Int): Flow<List<SiteVisit>>

    @Query(
        "SELECT url, title, host, 1 AS visits, MAX(visitTime) AS lastVisit FROM history " +
            "GROUP BY host ORDER BY lastVisit DESC LIMIT :limit",
    )
    fun recentSites(limit: Int): Flow<List<SiteVisit>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE visitTime >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history WHERE visitTime < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' ORDER BY title LIMIT :limit")
    suspend fun suggest(q: String, limit: Int): List<Bookmark>

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE url = :url")
    fun isBookmarked(url: String): Flow<Boolean>

    @Insert
    suspend fun insertFolder(folder: BookmarkFolder): Long

    @Update
    suspend fun updateFolder(folder: BookmarkFolder)

    @Query("SELECT * FROM bookmark_folders ORDER BY name")
    fun folders(): Flow<List<BookmarkFolder>>

    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("DELETE FROM bookmarks WHERE folderId = :folderId")
    suspend fun deleteInFolder(folderId: Long)
}

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(d: DownloadEntity): Long

    @Update
    suspend fun update(d: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM downloads WHERE incognito = 1")
    suspend fun incognitoIds(): List<Long>

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun clearFinished()

    /** Anything left "running" after a process death is really paused. */
    @Query("UPDATE downloads SET status = 'PAUSED' WHERE status IN ('RUNNING','QUEUED')")
    suspend fun pauseInterrupted()
}

@Dao
interface SiteSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SiteSettings)

    @Query("SELECT * FROM site_settings WHERE host = :host")
    suspend fun get(host: String): SiteSettings?

    @Query("SELECT * FROM site_settings ORDER BY host")
    fun all(): Flow<List<SiteSettings>>

    @Query("DELETE FROM site_settings WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM site_settings")
    suspend fun clear()
}

@Dao
interface LoginDao {
    @Query("SELECT * FROM logins ORDER BY origin, username")
    fun all(): Flow<List<LoginEntity>>

    @Query("SELECT * FROM logins ORDER BY origin, username")
    suspend fun snapshot(): List<LoginEntity>

    @Query("SELECT * FROM logins WHERE origin = :origin ORDER BY lastUsedAt DESC, username")
    suspend fun forOrigin(origin: String): List<LoginEntity>

    @Query("SELECT * FROM logins WHERE origin = :origin AND username = :username LIMIT 1")
    suspend fun find(origin: String, username: String): LoginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(login: LoginEntity): Long

    @Query("UPDATE logins SET lastUsedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("SELECT * FROM logins WHERE id = :id")
    suspend fun get(id: Long): LoginEntity?

    @Query("DELETE FROM logins WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM logins")
    suspend fun clear()

    @Query("SELECT COUNT(*) > 0 FROM login_blocklist WHERE origin = :origin")
    suspend fun isBlocked(origin: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(entry: LoginBlock)

    @Query("DELETE FROM login_blocklist")
    suspend fun clearBlocklist()
}

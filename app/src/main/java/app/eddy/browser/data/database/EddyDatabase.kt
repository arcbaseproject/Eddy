package app.eddy.browser.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
    @TypeConverter fun fromStatus(status: DownloadStatus): String = status.name
}

@Database(
    entities = [
        HistoryEntry::class, Bookmark::class, BookmarkFolder::class, DownloadEntity::class, SiteSettings::class,
        LoginEntity::class, LoginBlock::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EddyDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun downloads(): DownloadDao
    abstract fun sites(): SiteSettingsDao
    abstract fun logins(): LoginDao

    companion object {
        fun create(context: Context): EddyDatabase =
            Room.databaseBuilder(context, EddyDatabase::class.java, "eddy.db").build()
    }
}

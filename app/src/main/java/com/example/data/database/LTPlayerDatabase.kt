package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaybackPositionEntity::class,
        PlaybackHistoryEntity::class,
        FavoriteMediaEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        RecentGameEntity::class,
        SafFolderEntity::class,
        ShareTransferHistoryEntity::class,
        BrowserBookmarkEntity::class,
        BrowserHistoryEntity::class,
        EditorProjectEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LTPlayerDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun favoriteMediaDao(): FavoriteMediaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun recentGameDao(): RecentGameDao
    abstract fun safFolderDao(): SafFolderDao
    abstract fun shareHistoryDao(): ShareHistoryDao
    abstract fun browserBookmarkDao(): BrowserBookmarkDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun editorProjectDao(): EditorProjectDao

    companion object {
        @Volatile
        private var INSTANCE: LTPlayerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `share_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `peerDeviceName` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `sha256Hex` TEXT NOT NULL,
                        `verified` INTEGER NOT NULL,
                        `localUriString` TEXT,
                        `transportMode` TEXT NOT NULL,
                        `completedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT 'application/octet-stream'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `mediaCategory` TEXT NOT NULL DEFAULT 'FILE'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `qualityLabel` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `supportsRange` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etag` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `safTreeUri` TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `browser_bookmarks` (
                        `url` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `browser_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `visitedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `editor_projects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sourceUriString` TEXT NOT NULL,
                        `configJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): LTPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LTPlayerDatabase::class.java,
                    "lt_player.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

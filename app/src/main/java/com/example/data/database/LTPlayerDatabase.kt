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
        ShareTransferHistoryEntity::class
    ],
    version = 2,
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

        fun getInstance(context: Context): LTPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LTPlayerDatabase::class.java,
                    "lt_player.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

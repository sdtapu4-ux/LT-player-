package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        SafFolderEntity::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: LTPlayerDatabase? = null

        fun getInstance(context: Context): LTPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LTPlayerDatabase::class.java,
                    "lt_player.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

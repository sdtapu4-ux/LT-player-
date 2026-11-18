package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val isVideo: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val resolution: String,
    val folderPath: String,
    val isVideo: Boolean,
    val playedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteMediaEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val isVideo: Boolean,
    val addedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val sortOrder: Int,
    val addedAt: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemDownloadId: Long,
    val title: String,
    val url: String,
    val destinationUri: String?,
    val localPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: String,
    val errorReason: String?,
    val createdAt: Long
)

@Entity(tableName = "recent_games")
data class RecentGameEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isFavorite: Boolean,
    val lastOpenedAt: Long
)

@Entity(tableName = "saf_folders")
data class SafFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long
)

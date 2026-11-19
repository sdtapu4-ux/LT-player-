package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE uriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions")
    suspend fun clearAll()
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()
}

@Dao
interface FavoriteMediaDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorites WHERE uriString = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uriString = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items")
    fun observeAllItems(): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: Long, newName: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Update
    suspend fun updateItems(items: List<PlaylistItemEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxOrder(playlistId: Long): Int

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchPlaylist(id: Long, updatedAt: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'RUNNING', 'PAUSED')")
    suspend fun getActiveDownloads(): List<DownloadEntity>
}

@Dao
interface RecentGameDao {
    @Query("SELECT * FROM recent_games ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<RecentGameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentGameEntity)
}

@Dao
interface SafFolderDao {
    @Query("SELECT * FROM saf_folders ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SafFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SafFolderEntity)

    @Query("DELETE FROM saf_folders WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)
}

@Dao
interface ShareHistoryDao {
    @Query("SELECT * FROM share_history ORDER BY completedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<ShareTransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShareTransferHistoryEntity): Long

    @Query("DELETE FROM share_history")
    suspend fun clearAll()
}

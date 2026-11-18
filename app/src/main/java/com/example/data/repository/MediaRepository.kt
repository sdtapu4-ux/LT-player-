package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.database.FavoriteMediaEntity
import com.example.data.database.LTPlayerDatabase
import com.example.data.database.PlaybackHistoryEntity
import com.example.data.database.PlaybackPositionEntity
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistItemEntity
import com.example.data.database.RecentGameEntity
import com.example.data.database.SafFolderEntity
import com.example.data.database.SearchHistoryEntity
import com.example.data.model.AudioItem
import com.example.data.model.DuplicateMediaGroup
import com.example.data.model.MediaFolder
import com.example.data.model.PlaylistSummary
import com.example.data.model.VideoItem
import com.example.data.scanner.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MediaRepository(
    private val context: Context,
    private val database: LTPlayerDatabase,
    val scanner: MediaScanner
) {
    private val positionDao = database.playbackPositionDao()
    private val historyDao = database.playbackHistoryDao()
    private val favoriteDao = database.favoriteMediaDao()
    private val playlistDao = database.playlistDao()
    private val searchHistoryDao = database.searchHistoryDao()
    private val recentGameDao = database.recentGameDao()
    private val safFolderDao = database.safFolderDao()

    private val _rawVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _rawAudio = MutableStateFlow<List<AudioItem>>(emptyList())
    private val _openedExternalVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val safFolders: Flow<List<SafFolderEntity>> = safFolderDao.observeAll()
    val favorites: Flow<List<FavoriteMediaEntity>> = favoriteDao.observeAll()
    val playbackHistory: Flow<List<PlaybackHistoryEntity>> = historyDao.observeRecent()
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeRecent()
    val recentGames: Flow<List<RecentGameEntity>> = recentGameDao.observeAll()

    val videos: Flow<List<VideoItem>> = combine(
        _rawVideos,
        _openedExternalVideos,
        positionDao.observeAll(),
        favoriteDao.observeAll(),
        historyDao.observeRecent()
    ) { scanned, external, positions, favs, history ->
        val posMap = positions.associateBy { it.uriString }
        val favSet = favs.map { it.uriString }.toHashSet()
        val histMap = history.associateBy { it.uriString }

        val merged = (external + scanned).distinctBy { it.uriString }
        merged.map { video ->
            val pos = posMap[video.uriString]?.positionMs ?: 0L
            val lastPlayed = histMap[video.uriString]?.playedAt ?: 0L
            video.copy(
                lastPositionMs = pos,
                isFavorite = video.uriString in favSet,
                lastPlayedAt = lastPlayed
            )
        }
    }

    val audioTracks: Flow<List<AudioItem>> = combine(
        _rawAudio,
        favoriteDao.observeAll(),
        historyDao.observeRecent()
    ) { scanned, favs, history ->
        val favSet = favs.map { it.uriString }.toHashSet()
        val histMap = history.associateBy { it.uriString }
        scanned.map { track ->
            track.copy(
                isFavorite = track.uriString in favSet,
                lastPlayedAt = histMap[track.uriString]?.playedAt ?: 0L
            )
        }
    }

    val folders: Flow<List<MediaFolder>> = combine(
        videos,
        audioTracks,
        safFolders
    ) { vids, auds, safs ->
        scanner.buildFolders(vids, auds, safs.map { it.treeUri })
    }

    val playlistSummaries: Flow<List<PlaylistSummary>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeAllItems()
    ) { playlists, items ->
        val byPlaylist = items.groupBy { it.playlistId }
        playlists.map { pl ->
            val plItems = byPlaylist[pl.id].orEmpty()
            PlaylistSummary(
                id = pl.id,
                name = pl.name,
                itemCount = plItems.size,
                totalDurationMs = plItems.sumOf { it.durationMs },
                updatedAt = pl.updatedAt
            )
        }
    }

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.observeItems(playlistId)

    suspend fun refreshLibrary() {
        _isScanning.value = true
        try {
            val safUris = safFolderDao.observeAll().first().map { it.treeUri }
            _rawVideos.value = scanner.scanVideos(safUris)
            _rawAudio.value = scanner.scanAudio()
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun addPickedExternalVideo(uri: Uri): VideoItem? {
        val item = scanner.resolveSingleVideoFromUri(uri) ?: return null
        _openedExternalVideos.value = (listOf(item) + _openedExternalVideos.value).distinctBy { it.uriString }
        return item
    }

    suspend fun addSafFolder(treeUri: Uri) {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrDefault(treeUri.lastPathSegment ?: "Folder")
        val name = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "External Folder" }
        safFolderDao.insert(
            SafFolderEntity(
                treeUri = treeUri.toString(),
                displayName = name,
                addedAt = System.currentTimeMillis()
            )
        )
        refreshLibrary()
    }

    suspend fun removeSafFolder(treeUri: String) {
        safFolderDao.delete(treeUri)
        refreshLibrary()
    }

    suspend fun savePlaybackPosition(
        uriString: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        isVideo: Boolean
    ) {
        positionDao.upsert(
            PlaybackPositionEntity(
                uriString = uriString,
                title = title,
                positionMs = positionMs,
                durationMs = durationMs,
                isVideo = isVideo,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedPosition(uriString: String): Long =
        positionDao.getByUri(uriString)?.positionMs ?: 0L

    suspend fun recordPlaybackHistory(
        uriString: String,
        title: String,
        subtitle: String,
        durationMs: Long,
        sizeBytes: Long,
        resolution: String,
        folderPath: String,
        isVideo: Boolean
    ) {
        historyDao.upsert(
            PlaybackHistoryEntity(
                uriString = uriString,
                title = title,
                subtitle = subtitle,
                durationMs = durationMs,
                sizeBytes = sizeBytes,
                resolution = resolution,
                folderPath = folderPath,
                isVideo = isVideo,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFavorite(uriString: String, title: String, isVideo: Boolean) {
        if (favoriteDao.isFavorite(uriString)) {
            favoriteDao.deleteByUri(uriString)
        } else {
            favoriteDao.insert(
                FavoriteMediaEntity(
                    uriString = uriString,
                    title = title,
                    isVideo = isVideo,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim().ifBlank { "My Playlist" }, createdAt = now, updatedAt = now)
        )
    }

    suspend fun renamePlaylist(id: Long, newName: String) {
        if (newName.isNotBlank()) {
            playlistDao.renamePlaylist(id, newName.trim(), System.currentTimeMillis())
        }
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)

    suspend fun addToPlaylist(
        playlistId: Long,
        uriString: String,
        title: String,
        durationMs: Long,
        isVideo: Boolean
    ) {
        val nextOrder = playlistDao.getMaxOrder(playlistId) + 1
        val now = System.currentTimeMillis()
        playlistDao.insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                uriString = uriString,
                title = title,
                durationMs = durationMs,
                isVideo = isVideo,
                sortOrder = nextOrder,
                addedAt = now
            )
        )
        playlistDao.touchPlaylist(playlistId, now)
    }

    suspend fun removePlaylistItem(itemId: Long) = playlistDao.deleteItem(itemId)

    suspend fun movePlaylistItem(items: List<PlaylistItemEntity>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in items.indices || toIndex !in items.indices) return
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        val reindexed = mutable.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        playlistDao.updateItems(reindexed)
    }

    suspend fun recordSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            searchHistoryDao.upsert(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
        }
    }

    suspend fun clearSearchHistory() = searchHistoryDao.clearAll()

    suspend fun clearAllPlaybackHistory() {
        historyDao.clearAll()
        positionDao.clearAll()
        searchHistoryDao.clearAll()
    }

    suspend fun recordGameOpened(packageName: String, appName: String, isFav: Boolean) {
        recentGameDao.upsert(
            RecentGameEntity(
                packageName = packageName,
                appName = appName,
                isFavorite = isFav,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMediaUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false).also { deleted ->
            if (deleted) refreshLibrary()
        }
    }

    suspend fun renameMediaUri(uri: Uri, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        }.getOrDefault(false).also { renamed ->
            if (renamed) refreshLibrary()
        }
    }

    fun findDuplicateVideos(allVideos: List<VideoItem>): List<DuplicateMediaGroup> {
        return allVideos
            .filter { it.sizeBytes > 0L }
            .groupBy { "${it.sizeBytes}_${it.durationMs / 1000L}" }
            .filter { it.value.size > 1 }
            .map { (key, list) ->
                DuplicateMediaGroup(
                    signatureKey = key,
                    fileSizeBytes = list.first().sizeBytes,
                    durationMs = list.first().durationMs,
                    items = list
                )
            }
    }
}

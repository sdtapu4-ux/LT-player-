package com.example.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.billingclient.api.ProductDetails
import com.example.LTPlayerContainer
import com.example.browser.BrowserSuggestion
import com.example.browser.DetectedWebMedia
import com.example.data.database.BrowserBookmarkEntity
import com.example.data.database.BrowserHistoryEntity
import com.example.data.database.EditorProjectEntity
import com.example.data.database.PlaylistItemEntity
import com.example.data.model.AppSettings
import com.example.data.model.AspectRatioMode
import com.example.data.model.AudioItem
import com.example.data.model.DecoderPreference
import com.example.data.model.DownloadMediaCategory
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.data.model.DuplicateMediaGroup
import com.example.data.model.InstalledGameInfo
import com.example.data.model.MediaFolder
import com.example.data.model.PlaybackEnginePreference
import com.example.data.model.PlaylistSummary
import com.example.data.model.StorageBreakdown
import com.example.data.model.SubtitlePreferences
import com.example.data.model.ThemeMode
import com.example.data.model.VideoItem
import com.example.data.repository.PremiumUiState
import com.example.data.scanner.CodecInspector
import com.example.player.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

sealed interface AiTaskUiState {
    data object Idle : AiTaskUiState
    data class Loading(val label: String) : AiTaskUiState
    data class Streaming(val title: String, val text: String) : AiTaskUiState
    data class Success(val title: String, val text: String, val generatedBitmap: Bitmap? = null) : AiTaskUiState
    data class Error(val message: String) : AiTaskUiState
}

class MainViewModel(
    private val container: LTPlayerContainer
) : ViewModel() {

    private val mediaRepo = container.mediaRepository
    private val downloadRepo = container.downloadRepository
    private val browserRepo = container.browserRepository
    private val editorProjectDao = container.editorProjectDao
    private val settingsStore = container.settingsDataStore
    val playerController = container.playerController
    val ltShareManager = container.ltShareManager
    private val billingRepo = container.billingRepository
    private val aiRepo = container.aiVideoRepository

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playerState: StateFlow<PlayerUiState> = playerController.state
    val shareState = ltShareManager.state

    val videos: StateFlow<List<VideoItem>> = mediaRepo.videos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioTracks: StateFlow<List<AudioItem>> = mediaRepo.audioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<MediaFolder>> = mediaRepo.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistSummaries: StateFlow<List<PlaylistSummary>> = mediaRepo.playlistSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = mediaRepo.isScanning

    val downloads: StateFlow<List<DownloadRecord>> = downloadRepo.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory = mediaRepo.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory = mediaRepo.playbackHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val safFolders = mediaRepo.safFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserBookmarks: StateFlow<List<BrowserBookmarkEntity>> = browserRepo.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserHistory: StateFlow<List<BrowserHistoryEntity>> = browserRepo.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editorProjects: StateFlow<List<EditorProjectEntity>> = editorProjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billingState: StateFlow<PremiumUiState> = billingRepo.state

    private val _installedGames = MutableStateFlow<List<InstalledGameInfo>>(emptyList())
    val installedGames: StateFlow<List<InstalledGameInfo>> = combine(
        _installedGames,
        mediaRepo.recentGames
    ) { installed, recent ->
        val recentMap = recent.associateBy { it.packageName }
        installed.map { game ->
            val rec = recentMap[game.packageName]
            game.copy(
                isFavorite = rec?.isFavorite ?: false,
                lastOpenedAt = rec?.lastOpenedAt ?: 0L
            )
        }.sortedByDescending { it.lastOpenedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _storageBreakdown = MutableStateFlow(
        StorageBreakdown(0L, 0L, 0L, 0L, 0L, 0L, 0L)
    )
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateMediaGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateMediaGroup>> = _duplicateGroups.asStateFlow()

    private val _aiState = MutableStateFlow<AiTaskUiState>(AiTaskUiState.Idle)
    val aiState: StateFlow<AiTaskUiState> = _aiState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var aiJob: Job? = null

    init {
        refreshAll()
        viewModelScope.launch { downloadRepo.recoverInterruptedDownloads() }
        billingRepo.connectAndQuery()
    }

    fun refreshAll() {
        viewModelScope.launch {
            mediaRepo.refreshLibrary()
            scanInstalledGames()
            recomputeStorage()
        }
    }

    fun recomputeStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val vids = videos.value
            val auds = audioTracks.value
            val dlBytes = downloads.value
                .filter { it.status == DownloadStatus.SUCCESSFUL }
                .sumOf { it.totalBytes.coerceAtLeast(it.bytesDownloaded) }
            _storageBreakdown.value = mediaRepo.scanner.computeStorageBreakdown(vids, auds, dlBytes)
            _duplicateGroups.value = mediaRepo.findDuplicateVideos(vids)
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    // Playback helpers
    fun playVideo(video: VideoItem, queue: List<VideoItem> = videos.value) {
        val list = if (queue.any { it.uriString == video.uriString }) queue else listOf(video) + queue
        val idx = list.indexOfFirst { it.uriString == video.uriString }.coerceAtLeast(0)
        val s = settings.value
        playerController.playVideoQueue(
            videos = list,
            startIndex = idx,
            resumeFromSaved = true,
            defaultSpeed = s.defaultSpeed,
            defaultAspectRatio = s.defaultAspectRatio
        )
    }

    fun playAudio(track: AudioItem, queue: List<AudioItem> = audioTracks.value) {
        val list = if (queue.any { it.uriString == track.uriString }) queue else listOf(track) + queue
        val idx = list.indexOfFirst { it.uriString == track.uriString }.coerceAtLeast(0)
        playerController.playAudioQueue(list, idx)
    }

    fun openPickedVideoUri(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            val item = mediaRepo.addPickedExternalVideo(uri)
            if (item != null) {
                playVideo(item, listOf(item) + videos.value)
                onReady()
            } else {
                _statusBanner.value = "Could not open selected media file."
            }
        }
    }

    fun addSafFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                container.appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            mediaRepo.addSafFolder(uri)
            recomputeStorage()
            _statusBanner.value = "Folder linked to LT PLAYER library."
        }
    }

    fun removeSafFolder(treeUri: String) {
        viewModelScope.launch {
            mediaRepo.removeSafFolder(treeUri)
            recomputeStorage()
        }
    }

    fun toggleFavoriteVideo(video: VideoItem) {
        viewModelScope.launch {
            mediaRepo.toggleFavorite(video.uriString, video.title, isVideo = true)
        }
    }

    fun toggleFavoriteAudio(track: AudioItem) {
        viewModelScope.launch {
            mediaRepo.toggleFavorite(track.uriString, track.title, isVideo = false)
        }
    }

    fun deleteVideoFile(video: VideoItem) {
        viewModelScope.launch {
            val ok = mediaRepo.deleteMediaUri(Uri.parse(video.uriString))
            _statusBanner.value = if (ok) {
                recomputeStorage()
                "Deleted ${video.displayName}"
            } else {
                "Android denied delete permission for ${video.displayName}. Use system Files app if stored on protected volume."
            }
        }
    }

    fun renameVideoFile(video: VideoItem, newName: String) {
        viewModelScope.launch {
            val finalName = if (newName.contains('.')) newName else "$newName.${video.containerExtension}"
            val ok = mediaRepo.renameMediaUri(Uri.parse(video.uriString), finalName)
            _statusBanner.value = if (ok) "Renamed to $finalName" else "Rename restricted by Android scoped storage."
        }
    }

    // Playlists
    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        mediaRepo.observePlaylistItems(playlistId)

    fun createPlaylist(name: String) {
        viewModelScope.launch { mediaRepo.createPlaylist(name) }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch { mediaRepo.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { mediaRepo.deletePlaylist(id) }
    }

    fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            mediaRepo.addToPlaylist(playlistId, video.uriString, video.title, video.durationMs, true)
            _statusBanner.value = "Added '${video.title}' to playlist."
        }
    }

    fun addAudioToPlaylist(playlistId: Long, audio: AudioItem) {
        viewModelScope.launch {
            mediaRepo.addToPlaylist(playlistId, audio.uriString, audio.title, audio.durationMs, false)
            _statusBanner.value = "Added '${audio.title}' to playlist."
        }
    }

    fun removePlaylistItem(itemId: Long) {
        viewModelScope.launch { mediaRepo.removePlaylistItem(itemId) }
    }

    fun movePlaylistItem(items: List<PlaylistItemEntity>, from: Int, to: Int) {
        viewModelScope.launch { mediaRepo.movePlaylistItem(items, from, to) }
    }

    // Search
    fun recordSearch(query: String) {
        viewModelScope.launch { mediaRepo.recordSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { mediaRepo.clearSearchHistory() }
    }

    // Downloads
    fun startDownload(
        url: String,
        customTitle: String?,
        usePublicFolder: Boolean,
        safTreeUri: String? = null
    ) {
        viewModelScope.launch {
            val result = downloadRepo.enqueueDownload(
                rawUrl = url,
                customTitle = customTitle,
                usePublicDownloadsFolder = usePublicFolder,
                safTreeUri = safTreeUri
            )
            result.onSuccess {
                _statusBanner.value = "Added to download queue."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Download failed to start."
            }
        }
    }

    fun queueDetectedWebDownload(media: DetectedWebMedia) {
        viewModelScope.launch {
            val result = downloadRepo.enqueueDownload(
                rawUrl = media.url,
                customTitle = media.title,
                mimeType = media.mimeType,
                qualityLabel = media.qualityLabel,
                knownTotalBytes = media.sizeBytes,
                usePublicDownloadsFolder = true
            )
            result.onSuccess {
                _statusBanner.value = "Downloading ${media.title}..."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not start download."
            }
        }
    }

    fun pauseDownload(id: Long) {
        viewModelScope.launch { downloadRepo.pauseDownload(id) }
    }

    fun resumeDownload(id: Long) {
        viewModelScope.launch {
            downloadRepo.resumeDownload(id).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Resume failed."
            }
        }
    }

    fun cancelDownload(record: DownloadRecord) {
        viewModelScope.launch { downloadRepo.cancelDownload(record) }
    }

    fun retryDownload(record: DownloadRecord) {
        viewModelScope.launch {
            val res = downloadRepo.retryDownload(record)
            res.onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteDownloadedFile(record: DownloadRecord) {
        viewModelScope.launch {
            downloadRepo.deleteDownloadedFile(record)
            recomputeStorage()
            _statusBanner.value = "Removed ${record.title}"
        }
    }

    fun clearDownloadHistory() {
        viewModelScope.launch {
            downloadRepo.clearDownloadHistory()
            _statusBanner.value = "Completed & failed download history cleared."
        }
    }

    fun openDownloadedFile(record: DownloadRecord) {
        val intent = downloadRepo.buildOpenIntent(record)
        if (intent != null) {
            runCatching { container.appContext.startActivity(intent) }
                .onFailure { _statusBanner.value = "No app found to open ${record.title}." }
        } else {
            _statusBanner.value = "File URI unavailable."
        }
    }

    fun shareDownloadedFile(record: DownloadRecord) {
        val intent = downloadRepo.buildShareIntent(record)
        if (intent != null) {
            runCatching { container.appContext.startActivity(intent) }
                .onFailure { _statusBanner.value = "Could not share ${record.title}." }
        } else {
            _statusBanner.value = "File URI unavailable for sharing."
        }
    }

    fun playDownloadedMedia(
        record: DownloadRecord,
        onOpenVideoPlayer: () -> Unit,
        onOpenMusicPlayer: () -> Unit
    ) {
        val dest = record.destinationUri ?: return
        if (record.mediaCategory == DownloadMediaCategory.MUSIC) {
            val audio = AudioItem(
                id = record.id,
                uriString = dest,
                title = record.title.substringBeforeLast('.'),
                artist = "Downloaded Music",
                album = "LT Downloads",
                albumId = 0L,
                genre = "",
                durationMs = 0L,
                sizeBytes = record.totalBytes.coerceAtLeast(record.bytesDownloaded),
                mimeType = record.mimeType,
                folderName = "Downloads",
                folderPath = record.localPath ?: "Downloads",
                dateAddedSec = record.createdAt / 1000L
            )
            playAudio(audio, listOf(audio) + audioTracks.value)
            onOpenMusicPlayer()
        } else {
            openPickedVideoUri(Uri.parse(dest), onOpenVideoPlayer)
        }
    }

    // Browser
    fun recordBrowserVisit(url: String, title: String, isIncognito: Boolean) {
        viewModelScope.launch { browserRepo.recordVisit(url, title, isIncognito) }
    }

    fun toggleBrowserBookmark(url: String, title: String, isBookmarked: Boolean) {
        viewModelScope.launch {
            browserRepo.toggleBookmark(url, title, isBookmarked)
            _statusBanner.value = if (isBookmarked) "Bookmark removed." else "Bookmarked $title."
        }
    }

    fun deleteBrowserHistoryItem(id: Long) {
        viewModelScope.launch { browserRepo.deleteHistoryItem(id) }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            browserRepo.clearHistory()
            _statusBanner.value = "Browser history cleared."
        }
    }

    fun buildBrowserSuggestions(query: String): List<BrowserSuggestion> =
        browserRepo.buildSuggestions(query, browserBookmarks.value, browserHistory.value)

    // Video Editor Draft Projects
    fun saveEditorProject(name: String, sourceUriString: String, configJson: String) {
        viewModelScope.launch {
            editorProjectDao.insert(
                EditorProjectEntity(
                    name = name,
                    sourceUriString = sourceUriString,
                    configJson = configJson,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _statusBanner.value = "Editor project '$name' saved."
        }
    }

    fun deleteEditorProject(id: Long) {
        viewModelScope.launch { editorProjectDao.deleteById(id) }
    }

    // Settings
    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.setOnboardingCompleted(true) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.updateThemeMode(mode) }
    }

    fun updateLanguage(code: String) {
        viewModelScope.launch { settingsStore.updateLanguage(code) }
    }

    fun updateProfile(name: String, avatarIndex: Int) {
        viewModelScope.launch { settingsStore.updateProfile(name, avatarIndex) }
    }

    fun updatePlayerDefaults(
        speed: Float? = null,
        aspectRatio: AspectRatioMode? = null,
        gestureBrightness: Boolean? = null,
        gestureVolume: Boolean? = null,
        gestureSeek: Boolean? = null,
        autoPlayNext: Boolean? = null,
        keepScreenAwake: Boolean? = null,
        backgroundAudio: Boolean? = null,
        notificationsEnabled: Boolean? = null,
        privacyHideThumbs: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsStore.updatePlayerDefaults(
                speed, aspectRatio, gestureBrightness, gestureVolume,
                gestureSeek, autoPlayNext, keepScreenAwake, backgroundAudio,
                notificationsEnabled, privacyHideThumbs
            )
        }
    }

    fun updateSubtitlePreferences(subPrefs: SubtitlePreferences) {
        viewModelScope.launch { settingsStore.updateSubtitlePreferences(subPrefs) }
    }

    fun updateCompatibilitySettings(
        enginePreference: PlaybackEnginePreference? = null,
        decoderPreference: DecoderPreference? = null,
        preferredAudioLanguage: String? = null,
        audioDelayMs: Long? = null,
        audioBoostPercent: Int? = null
    ) {
        viewModelScope.launch {
            settingsStore.updateCompatibilitySettings(
                enginePreference = enginePreference,
                decoderPreference = decoderPreference,
                preferredAudioLanguage = preferredAudioLanguage,
                audioDelayMs = audioDelayMs,
                audioBoostPercent = audioBoostPercent
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            mediaRepo.clearAllPlaybackHistory()
            _statusBanner.value = "Playback and search history cleared."
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsStore.resetAllSettings()
            _statusBanner.value = "Settings reset to defaults."
        }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = mediaRepo.scanner.clearAppThumbnailCache()
            recomputeStorage()
            _statusBanner.value = "Cleared ${com.example.utils.FormatUtils.formatFileSize(freed)} thumbnail cache."
        }
    }

    // Games
    fun scanInstalledGames() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = container.appContext.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val games = resolved.mapNotNull { info ->
                val appInfo = info.activityInfo.applicationInfo
                val isGame = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                    @Suppress("DEPRECATION")
                    ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
                if (!isGame) return@mapNotNull null
                val pkg = info.activityInfo.packageName
                val version = runCatching { pm.getPackageInfo(pkg, 0).versionName ?: "1.0" }.getOrDefault("1.0")
                InstalledGameInfo(
                    packageName = pkg,
                    appName = info.loadLabel(pm).toString(),
                    versionName = version
                )
            }.distinctBy { it.packageName }
            _installedGames.value = games
        }
    }

    fun launchGame(game: InstalledGameInfo) {
        viewModelScope.launch {
            mediaRepo.recordGameOpened(game.packageName, game.appName, game.isFavorite)
            val launchIntent = container.appContext.packageManager.getLaunchIntentForPackage(game.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                container.appContext.startActivity(launchIntent)
            } else {
                _statusBanner.value = "Unable to launch ${game.appName}."
            }
        }
    }

    fun toggleFavoriteGame(game: InstalledGameInfo) {
        viewModelScope.launch {
            mediaRepo.recordGameOpened(game.packageName, game.appName, !game.isFavorite)
        }
    }

    // Billing
    fun refreshBilling() = billingRepo.connectAndQuery()
    fun restorePurchases() = billingRepo.restorePurchases()
    fun launchPremiumPurchase(activity: Activity, product: ProductDetails) =
        billingRepo.launchSubscriptionFlow(activity, product)

    // AI Video Workspace
    fun runAiSummary(video: VideoItem, notes: String) {
        launchAiStream("Video Summary: ${video.title}") {
            val frame = mediaRepo.scanner.extractFrameForAi(video.uriString)
            aiRepo.streamVideoSummary(video, frame, notes)
        }
    }

    fun runAiSceneDescription(video: VideoItem) {
        launchAiStream("Scene Analysis: ${video.title}") {
            val frame = mediaRepo.scanner.extractFrameForAi(video.uriString)
            aiRepo.streamSceneDescription(video, frame)
        }
    }

    fun runAiSubtitleGeneration(video: VideoItem, script: String) {
        launchAiStream("Generated SRT: ${video.title}") {
            aiRepo.streamSubtitleGeneration(video, script)
        }
    }

    fun runAiSubtitleTranslation(sourceSubtitles: String, targetLanguage: String) {
        launchAiStream("Translated Subtitles ($targetLanguage)") {
            aiRepo.streamSubtitleTranslation(sourceSubtitles, targetLanguage)
        }
    }

    fun runAiMetadataExplanation(video: VideoItem) {
        launchAiStream("Codec & Metadata Report: ${video.title}") {
            val report = withContext(Dispatchers.IO) {
                CodecInspector.inspectUri(container.appContext, Uri.parse(video.uriString), video.containerExtension)
            }
            aiRepo.streamMetadataExplanation(video, report.message)
        }
    }

    fun runAiEditingAssistance(video: VideoItem, goal: String) {
        launchAiStream("Editing Plan: ${video.title}") {
            aiRepo.streamEditingAssistance(video, goal)
        }
    }

    fun runAiThumbnailGeneration(prompt: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiTaskUiState.Loading("Synthesizing cinema thumbnail with Gemini...")
            val result = aiRepo.generateAiThumbnail(prompt)
            result.onSuccess { bmp ->
                _aiState.value = AiTaskUiState.Success(
                    title = "AI Thumbnail Concept",
                    text = "Prompt: $prompt",
                    generatedBitmap = bmp
                )
            }.onFailure { err ->
                _aiState.value = AiTaskUiState.Error(
                    err.localizedMessage ?: "Thumbnail generation failed."
                )
            }
        }
    }

    fun clearAiState() {
        aiJob?.cancel()
        _aiState.value = AiTaskUiState.Idle
    }

    private fun launchAiStream(title: String, flowProvider: suspend () -> Flow<String>) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiTaskUiState.Loading("Analyzing with Gemini 3.8 Flash...")
            try {
                var lastText = ""
                flowProvider().collect { accumulated ->
                    lastText = accumulated
                    _aiState.value = AiTaskUiState.Streaming(title, accumulated)
                }
                if (lastText.isNotBlank()) {
                    _aiState.value = AiTaskUiState.Success(title, lastText)
                } else {
                    _aiState.value = AiTaskUiState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _aiState.value = AiTaskUiState.Error("AI request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI request failed", e)
                _aiState.value = AiTaskUiState.Error(
                    e.localizedMessage ?: "AI analysis failed. Ensure network connectivity and App Check attestation."
                )
            }
        }
    }

    override fun onCleared() {
        billingRepo.release()
        super.onCleared()
    }

    companion object {
        fun provideFactory(container: LTPlayerContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}

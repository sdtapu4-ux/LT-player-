package com.example.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.AspectRatioMode
import com.example.data.model.AudioItem
import com.example.data.model.AudioTrackOption
import com.example.data.model.RepeatModeOption
import com.example.data.model.SubtitleCue
import com.example.data.model.SubtitleTrackOption
import com.example.data.model.VideoItem
import com.example.data.repository.MediaRepository
import com.example.data.scanner.CodecInspector
import com.example.data.scanner.SubtitleParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(
    val uriString: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val artworkUri: String? = null,
    val sizeBytes: Long = 0L,
    val resolution: String = "",
    val folderPath: String = ""
)

data class EqualizerBandUi(
    val index: Short,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val currentLevelMb: Short
)

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val currentEntry: QueueEntry? = null,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val shuffleEnabled: Boolean = false,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val subtitleTracks: List<SubtitleTrackOption> = emptyList(),
    val selectedSubtitleId: String? = null,
    val externalSubtitleCues: List<SubtitleCue> = emptyList(),
    val playbackError: String? = null,
    val sleepTimerRemainingSec: Int? = null,
    val equalizerSupported: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerPresets: List<String> = emptyList(),
    val selectedEqPreset: Int = -1,
    val equalizerBands: List<EqualizerBandUi> = emptyList()
)

class PlayerController(
    private val context: Context,
    private val mediaRepository: MediaRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var equalizer: Equalizer? = null
    private var pendingAction: (() -> Unit)? = null

    val playerOrNull: Player?
        get() = controller

    init {
        connect()
        observeAudioSessionForEqualizer()
    }

    private fun observeAudioSessionForEqualizer() {
        scope.launch {
            PlaybackService.audioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    initEqualizer(sessionId)
                }
            }
        }
    }

    private fun initEqualizer(audioSessionId: Int) {
        runCatching {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = _state.value.equalizerEnabled
            equalizer = eq
            val bands = (0 until eq.numberOfBands).map { i ->
                val band = i.toShort()
                val range = eq.bandLevelRange
                EqualizerBandUi(
                    index = band,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    minLevelMb = range[0],
                    maxLevelMb = range[1],
                    currentLevelMb = eq.getBandLevel(band)
                )
            }
            val presets = (0 until eq.numberOfPresets).map { idx ->
                eq.getPresetName(idx.toShort())
            }
            _state.update {
                it.copy(
                    equalizerSupported = true,
                    equalizerBands = bands,
                    equalizerPresets = presets
                )
            }
        }.onFailure {
            _state.update { it.copy(equalizerSupported = false) }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mc = future.get()
                    controller = mc
                    mc.addListener(playerListener)
                    _state.update { it.copy(isConnected = true) }
                    pendingAction?.invoke()
                    pendingAction = null
                    startProgressLoop()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                persistCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    playbackState = playbackState,
                    durationMs = controller?.duration?.takeIf { d -> d > 0L } ?: it.durationMs
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = controller?.currentMediaItemIndex ?: 0
            val queue = _state.value.queue
            val entry = queue.getOrNull(idx)
            _state.update {
                it.copy(
                    currentIndex = idx,
                    currentEntry = entry,
                    playbackError = null,
                    durationMs = entry?.durationMs ?: 0L,
                    externalSubtitleCues = emptyList(),
                    selectedSubtitleId = null
                )
            }
            entry?.let { recordHistoryEntry(it) }
        }

        @OptIn(UnstableApi::class)
        override fun onTracksChanged(tracks: Tracks) {
            val audioList = mutableListOf<AudioTrackOption>()
            val textList = mutableListOf<SubtitleTrackOption>()

            tracks.groups.forEachIndexed { groupIdx, group ->
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        for (t in 0 until group.length) {
                            val fmt = group.getTrackFormat(t)
                            val lang = fmt.language ?: "und"
                            val ch = fmt.channelCount.takeIf { it > 0 } ?: 2
                            val label = fmt.label ?: "${lang.uppercase()} • ${ch}ch (${fmt.sampleMimeType ?: "audio"})"
                            audioList.add(
                                AudioTrackOption(
                                    id = "aud_${groupIdx}_$t",
                                    label = label,
                                    language = fmt.language,
                                    channels = ch,
                                    codec = fmt.sampleMimeType,
                                    groupIndex = groupIdx,
                                    trackIndex = t,
                                    isSelected = group.isTrackSelected(t)
                                )
                            )
                        }
                    }
                    C.TRACK_TYPE_TEXT -> {
                        for (t in 0 until group.length) {
                            val fmt = group.getTrackFormat(t)
                            val label = fmt.label ?: fmt.language?.uppercase() ?: "Embedded Track ${t + 1}"
                            textList.add(
                                SubtitleTrackOption(
                                    id = "emb_${groupIdx}_$t",
                                    label = "$label (Embedded)",
                                    language = fmt.language,
                                    isEmbedded = true,
                                    groupIndex = groupIdx,
                                    trackIndex = t
                                )
                            )
                        }
                    }
                }
            }

            val existingExternal = _state.value.subtitleTracks.filter { !it.isEmbedded }
            _state.update {
                it.copy(
                    audioTracks = audioList,
                    subtitleTracks = textList + existingExternal
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val entry = _state.value.currentEntry
            val diagnostic = if (entry != null) {
                val report = CodecInspector.inspectUri(
                    context,
                    Uri.parse(entry.uriString),
                    entry.title.substringAfterLast('.', "")
                )
                "${error.errorCodeName}: ${error.localizedMessage ?: "Playback error"}\n${report.message}"
            } else {
                "${error.errorCodeName}: ${error.localizedMessage ?: "Unsupported format or missing file."}"
            }
            _state.update { it.copy(playbackError = diagnostic, isPlaying = false) }
        }
    }

    fun playVideoQueue(
        videos: List<VideoItem>,
        startIndex: Int,
        resumeFromSaved: Boolean = true,
        defaultSpeed: Float = 1.0f,
        defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    ) {
        if (videos.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, videos.lastIndex)
        val entries = videos.map { v ->
            QueueEntry(
                uriString = v.uriString,
                title = v.title,
                subtitle = "${v.resolutionLabel} • ${v.folderName}",
                durationMs = v.durationMs,
                isVideo = true,
                sizeBytes = v.sizeBytes,
                resolution = v.resolutionLabel,
                folderPath = v.folderPath
            )
        }
        val targetVideo = videos[safeIdx]

        val action = {
            scope.launch {
                val savedPos = if (resumeFromSaved) {
                    val pos = mediaRepository.getSavedPosition(targetVideo.uriString)
                    if (targetVideo.durationMs > 10_000L && pos in 2_000L..(targetVideo.durationMs - 4_000L)) pos else 0L
                } else 0L

                val mediaItems = entries.map { entry ->
                    MediaItem.Builder()
                        .setUri(Uri.parse(entry.uriString))
                        .setMediaId(entry.uriString)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(entry.title)
                                .setSubtitle(entry.subtitle)
                                .build()
                        )
                        .build()
                }
                val discoveredSubs = mediaRepository.scanner.discoverSubtitlesForVideo(targetVideo)
                _state.update {
                    it.copy(
                        queue = entries,
                        currentIndex = safeIdx,
                        currentEntry = entries[safeIdx],
                        playbackSpeed = defaultSpeed,
                        aspectRatioMode = defaultAspectRatio,
                        playbackError = null,
                        subtitleTracks = discoveredSubs,
                        externalSubtitleCues = emptyList(),
                        selectedSubtitleId = null
                    )
                }
                controller?.apply {
                    setMediaItems(mediaItems, safeIdx, savedPos)
                    setPlaybackSpeed(defaultSpeed)
                    prepare()
                    playWhenReady = true
                }
                recordHistoryEntry(entries[safeIdx])

                if (discoveredSubs.isNotEmpty()) {
                    selectSubtitleTrack(discoveredSubs.first(), "UTF-8")
                }
            }
        }
        if (controller != null) action() else {
            pendingAction = { action() }
            connect()
        }
    }

    fun playAudioQueue(
        tracks: List<AudioItem>,
        startIndex: Int
    ) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val entries = tracks.map { a ->
            QueueEntry(
                uriString = a.uriString,
                title = a.title,
                subtitle = "${a.artist} • ${a.album}",
                durationMs = a.durationMs,
                isVideo = false,
                artworkUri = a.albumArtUri,
                sizeBytes = a.sizeBytes,
                resolution = "AUDIO",
                folderPath = a.folderPath
            )
        }
        val action = {
            val mediaItems = entries.map { entry ->
                MediaItem.Builder()
                    .setUri(Uri.parse(entry.uriString))
                    .setMediaId(entry.uriString)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entry.title)
                            .setArtist(entry.subtitle)
                            .setArtworkUri(entry.artworkUri?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
            }
            _state.update {
                it.copy(
                    queue = entries,
                    currentIndex = safeIdx,
                    currentEntry = entries[safeIdx],
                    playbackError = null,
                    externalSubtitleCues = emptyList(),
                    subtitleTracks = emptyList()
                )
            }
            controller?.apply {
                setMediaItems(mediaItems, safeIdx, 0L)
                prepare()
                playWhenReady = true
            }
            recordHistoryEntry(entries[safeIdx])
        }
        if (controller != null) action() else {
            pendingAction = { action() }
            connect()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        val dur = controller?.duration?.takeIf { it > 0L } ?: _state.value.durationMs
        val clamped = positionMs.coerceIn(0L, dur.coerceAtLeast(0L))
        controller?.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun seekForward10s() {
        val pos = controller?.currentPosition ?: _state.value.currentPositionMs
        seekTo(pos + 10_000L)
    }

    fun seekBackward10s() {
        val pos = controller?.currentPosition ?: _state.value.currentPositionMs
        seekTo(pos - 10_000L)
    }

    fun skipNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun skipPrevious() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
    }

    fun playQueueIndex(index: Int) {
        if (index in _state.value.queue.indices) {
            controller?.seekTo(index, 0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        controller?.setPlaybackSpeed(safeSpeed)
        _state.update { it.copy(playbackSpeed = safeSpeed) }
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        val next = modes[(_state.value.aspectRatioMode.ordinal + 1) % modes.size]
        _state.update { it.copy(aspectRatioMode = next) }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.update { it.copy(aspectRatioMode = mode) }
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffleEnabled
        controller?.shuffleModeEnabled = next
        _state.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val next = when (_state.value.repeatMode) {
            RepeatModeOption.OFF -> RepeatModeOption.ALL
            RepeatModeOption.ALL -> RepeatModeOption.ONE
            RepeatModeOption.ONE -> RepeatModeOption.OFF
        }
        controller?.repeatMode = when (next) {
            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
        }
        _state.update { it.copy(repeatMode = next) }
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        val mc = controller ?: return
        val group = mc.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        mc.trackSelectionParameters = mc.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun loadExternalSubtitleUri(uri: Uri, encoding: String) {
        scope.launch(Dispatchers.IO) {
            val cues = SubtitleParser.loadFromUri(context, uri, encoding)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "External Subtitle"
            val option = SubtitleTrackOption(
                id = "ext_${uri.toString().hashCode()}",
                label = name,
                language = null,
                isEmbedded = false,
                uriString = uri.toString()
            )
            _state.update { current ->
                current.copy(
                    subtitleTracks = (current.subtitleTracks.filter { it.id != option.id } + option),
                    selectedSubtitleId = option.id,
                    externalSubtitleCues = cues
                )
            }
        }
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?, encoding: String) {
        val mc = controller
        if (option == null) {
            mc?.let {
                it.trackSelectionParameters = it.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            _state.update { it.copy(selectedSubtitleId = null, externalSubtitleCues = emptyList()) }
            return
        }

        if (option.isEmbedded && mc != null) {
            val group = mc.currentTracks.groups.getOrNull(option.groupIndex)
            if (group != null) {
                mc.trackSelectionParameters = mc.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
                    .build()
            }
            _state.update { it.copy(selectedSubtitleId = option.id, externalSubtitleCues = emptyList()) }
        } else if (option.uriString != null) {
            mc?.let {
                it.trackSelectionParameters = it.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            scope.launch(Dispatchers.IO) {
                val cues = SubtitleParser.loadFromUri(context, Uri.parse(option.uriString), encoding)
                _state.update {
                    it.copy(
                        selectedSubtitleId = option.id,
                        externalSubtitleCues = cues
                    )
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingSec = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _state.update { it.copy(sleepTimerRemainingSec = remaining) }
                delay(1000L)
                remaining--
            }
            if (isActive) {
                controller?.pause()
                _state.update { it.copy(sleepTimerRemainingSec = null) }
            }
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        _state.update { it.copy(equalizerEnabled = enabled) }
    }

    fun selectEqPreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            val updatedBands = (0 until eq.numberOfBands).map { i ->
                val band = i.toShort()
                val range = eq.bandLevelRange
                EqualizerBandUi(
                    index = band,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    minLevelMb = range[0],
                    maxLevelMb = range[1],
                    currentLevelMb = eq.getBandLevel(band)
                )
            }
            _state.update {
                it.copy(selectedEqPreset = presetIndex, equalizerBands = updatedBands)
            }
        }
    }

    fun setEqBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(bandIndex, levelMb)
            _state.update { state ->
                state.copy(
                    selectedEqPreset = -1,
                    equalizerBands = state.equalizerBands.map {
                        if (it.index == bandIndex) it.copy(currentLevelMb = levelMb) else it
                    }
                )
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                val mc = controller
                if (mc != null) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val dur = mc.duration.takeIf { it > 0L } ?: _state.value.durationMs
                    val buf = mc.bufferedPosition.coerceAtLeast(0L)
                    _state.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buf
                        )
                    }
                    if (mc.isPlaying && tickCount % 6 == 0) {
                        persistCurrentPosition()
                    }
                    tickCount++
                }
                delay(500L)
            }
        }
    }

    private fun persistCurrentPosition() {
        val entry = _state.value.currentEntry ?: return
        val pos = controller?.currentPosition ?: _state.value.currentPositionMs
        val dur = controller?.duration?.takeIf { it > 0L } ?: entry.durationMs
        scope.launch(Dispatchers.IO) {
            mediaRepository.savePlaybackPosition(
                uriString = entry.uriString,
                title = entry.title,
                positionMs = pos,
                durationMs = dur,
                isVideo = entry.isVideo
            )
        }
    }

    private fun recordHistoryEntry(entry: QueueEntry) {
        scope.launch(Dispatchers.IO) {
            mediaRepository.recordPlaybackHistory(
                uriString = entry.uriString,
                title = entry.title,
                subtitle = entry.subtitle,
                durationMs = entry.durationMs,
                sizeBytes = entry.sizeBytes,
                resolution = entry.resolution,
                folderPath = entry.folderPath,
                isVideo = entry.isVideo
            )
        }
    }

    fun release() {
        persistCurrentPosition()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        runCatching { equalizer?.release() }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}

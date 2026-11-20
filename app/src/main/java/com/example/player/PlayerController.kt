package com.example.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.example.data.model.ActiveDecoderMode
import com.example.data.model.ActivePlaybackEngine
import com.example.data.model.AppSettings
import com.example.data.model.AspectRatioMode
import com.example.data.model.AudioItem
import com.example.data.model.AudioTrackOption
import com.example.data.model.DecoderPreference
import com.example.data.model.PlaybackDiagnosticReport
import com.example.data.model.PlaybackEnginePreference
import com.example.data.model.RepeatModeOption
import com.example.data.model.SleepTimerEndAction
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
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout

data class QueueEntry(
    val uriString: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val isVideo: Boolean,
    val artworkUri: String? = null,
    val sizeBytes: Long = 0L,
    val resolution: String = "",
    val folderPath: String = "",
    val containerExtension: String = ""
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
    val sleepTimerTotalSec: Int? = null,
    val sleepTimerEndAction: SleepTimerEndAction = SleepTimerEndAction.AUTO_PAUSE,
    val sleepTimerLastEndAction: SleepTimerEndAction = SleepTimerEndAction.AUTO_PAUSE,
    val sleepTimerAutoPausedNotice: Boolean = false,
    val equalizerSupported: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerPresets: List<String> = emptyList(),
    val selectedEqPreset: Int = -1,
    val equalizerBands: List<EqualizerBandUi> = emptyList(),
    val activeEngine: ActivePlaybackEngine = ActivePlaybackEngine.NONE,
    val enginePreference: PlaybackEnginePreference = PlaybackEnginePreference.AUTO,
    val decoderPreference: DecoderPreference = DecoderPreference.AUTO,
    val activeDecoderMode: ActiveDecoderMode = ActiveDecoderMode.UNKNOWN,
    val engineNotice: String? = null,
    val diagnosticReport: PlaybackDiagnosticReport? = null,
    val preferredAudioLanguage: String = "und",
    val audioDelayMs: Long = 0L,
    val audioBoostPercent: Int = 100
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
    private var sleepCountDownTimer: CountDownTimer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var pendingAction: (() -> Unit)? = null

    private var attemptedEnginesForCurrentItem: MutableSet<ActivePlaybackEngine> = mutableSetOf()
    private var swFallbackTriedInCurrentEngine: Boolean = false
    private var activeSubtitleDelayMs: Long = 0L

    private val vlcEngine: VlcPlaybackEngine by lazy {
        VlcPlaybackEngine(
            context = context,
            scope = scope,
            onStateChanged = { isPlaying, isBuffering ->
                if (_state.value.activeEngine != ActivePlaybackEngine.VLC) return@VlcPlaybackEngine
                val pState = when {
                    isBuffering -> Player.STATE_BUFFERING
                    isPlaying -> Player.STATE_READY
                    else -> Player.STATE_READY
                }
                _state.update {
                    it.copy(
                        isPlaying = isPlaying,
                        playbackState = pState
                    )
                }
                if (!isPlaying) {
                    persistCurrentPosition()
                }
            },
            onProgress = { posMs, durMs ->
                if (_state.value.activeEngine != ActivePlaybackEngine.VLC) return@VlcPlaybackEngine
                val resolvedDur = durMs.takeIf { it > 0L } ?: _state.value.durationMs
                _state.update {
                    it.copy(
                        currentPositionMs = posMs,
                        durationMs = resolvedDur,
                        bufferedPositionMs = resolvedDur
                    )
                }
            },
            onTracksUpdated = { audio, subs ->
                if (_state.value.activeEngine != ActivePlaybackEngine.VLC) return@VlcPlaybackEngine
                val external = _state.value.subtitleTracks.filter { !it.isEmbedded }
                _state.update {
                    it.copy(
                        audioTracks = audio,
                        subtitleTracks = subs + external
                    )
                }
            },
            onDecoderResolved = { mode ->
                if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
                    _state.update { it.copy(activeDecoderMode = mode) }
                }
            },
            onEnded = {
                if (_state.value.activeEngine != ActivePlaybackEngine.VLC) return@VlcPlaybackEngine
                handleVlcEnded()
            },
            onError = { errMsg ->
                if (_state.value.activeEngine != ActivePlaybackEngine.VLC) return@VlcPlaybackEngine
                handleEngineFailure(
                    failedEngine = ActivePlaybackEngine.VLC,
                    errorDetail = errMsg,
                    isDecoderInitFailure = "decoder" in errMsg.lowercase() || "codec" in errMsg.lowercase()
                )
            }
        )
    }

    val playerOrNull: Player?
        get() = controller

    init {
        observeAudioSessionForEffects()
        PlaybackService.onCancelSleepTimerFromNotification = {
            scope.launch(Dispatchers.Main.immediate) {
                cancelSleepTimer()
            }
        }
    }

    fun applySettings(settings: AppSettings) {
        activeSubtitleDelayMs = settings.subtitlePrefs.delayMs
        PlaybackService.setDesiredDecoderPreference(settings.decoderPreference)
        _state.update {
            it.copy(
                enginePreference = settings.enginePreference,
                decoderPreference = settings.decoderPreference,
                preferredAudioLanguage = settings.preferredAudioLanguage,
                audioDelayMs = settings.audioDelayMs,
                audioBoostPercent = settings.audioBoostPercent
            )
        }
        applyPreferredAudioLanguageToMedia3(settings.preferredAudioLanguage)
        applyAudioBoost(settings.audioBoostPercent)
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.setAudioDelayMs(settings.audioDelayMs)
            vlcEngine.setSubtitleDelayMs(settings.subtitlePrefs.delayMs)
            vlcEngine.setAudioBoostPercent(settings.audioBoostPercent)
        }
    }

    fun attachVlcLayout(layout: VLCVideoLayout) {
        vlcEngine.attachLayout(layout)
    }

    fun detachVlcLayout(layout: VLCVideoLayout?) {
        vlcEngine.detachLayout(layout)
    }

    private fun observeAudioSessionForEffects() {
        scope.launch {
            PlaybackService.audioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    initAudioEffects(sessionId)
                }
            }
        }
    }

    private fun initAudioEffects(audioSessionId: Int) {
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

        runCatching {
            loudnessEnhancer?.release()
            val enhancer = LoudnessEnhancer(audioSessionId)
            loudnessEnhancer = enhancer
            applyAudioBoost(_state.value.audioBoostPercent)
        }
    }

    private fun applyAudioBoost(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.setAudioBoostPercent(clamped)
        }
        runCatching {
            loudnessEnhancer?.let { enhancer ->
                val gainMb = (clamped - 100) * 10
                enhancer.setTargetGain(gainMb)
                enhancer.enabled = clamped > 100
            }
        }
    }

    private fun applyPreferredAudioLanguageToMedia3(langCode: String) {
        val mc = controller ?: return
        runCatching {
            val builder = mc.trackSelectionParameters.buildUpon()
            if (langCode == "und" || langCode.isBlank()) {
                builder.setPreferredAudioLanguages()
            } else {
                builder.setPreferredAudioLanguage(langCode)
            }
            mc.trackSelectionParameters = builder.build()
        }
    }

    fun connect() {
        if (controller?.isConnected == true || controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(mc: MediaController) {
                    if (controller === mc) {
                        controller = null
                        controllerFuture = null
                        _state.update { it.copy(isConnected = false, isPlaying = false) }
                    }
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mc = future.get()
                    controller = mc
                    controllerFuture = null
                    mc.addListener(playerListener)
                    _state.update { it.copy(isConnected = true) }
                    applyPreferredAudioLanguageToMedia3(_state.value.preferredAudioLanguage)
                    pendingAction?.invoke()
                    pendingAction = null
                    startProgressLoop()
                }.onFailure {
                    controllerFuture = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_state.value.activeEngine != ActivePlaybackEngine.MEDIA3) return
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                persistCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value.activeEngine != ActivePlaybackEngine.MEDIA3) return
            _state.update {
                it.copy(
                    playbackState = playbackState,
                    durationMs = controller?.duration?.takeIf { d -> d > 0L } ?: it.durationMs,
                    activeDecoderMode = if (playbackState == Player.STATE_READY) {
                        if (it.decoderPreference == DecoderPreference.SOFTWARE) {
                            ActiveDecoderMode.SOFTWARE
                        } else {
                            ActiveDecoderMode.HARDWARE
                        }
                    } else it.activeDecoderMode
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_state.value.activeEngine != ActivePlaybackEngine.MEDIA3) return
            val idx = controller?.currentMediaItemIndex ?: 0
            val queue = _state.value.queue
            val entry = queue.getOrNull(idx)
            attemptedEnginesForCurrentItem = mutableSetOf(ActivePlaybackEngine.MEDIA3)
            swFallbackTriedInCurrentEngine = false
            _state.update {
                it.copy(
                    currentIndex = idx,
                    currentEntry = entry,
                    playbackError = null,
                    diagnosticReport = null,
                    durationMs = entry?.durationMs ?: 0L,
                    externalSubtitleCues = emptyList(),
                    selectedSubtitleId = null
                )
            }
            entry?.let { recordHistoryEntry(it) }
        }

        @OptIn(UnstableApi::class)
        override fun onTracksChanged(tracks: Tracks) {
            if (_state.value.activeEngine != ActivePlaybackEngine.MEDIA3) return
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
            if (_state.value.activeEngine != ActivePlaybackEngine.MEDIA3) return
            val isDecoderErr = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                error.errorCodeName.contains("DECODER", ignoreCase = true)
            handleEngineFailure(
                failedEngine = ActivePlaybackEngine.MEDIA3,
                errorDetail = "${error.errorCodeName}: ${error.localizedMessage ?: "Playback failed"}",
                isDecoderInitFailure = isDecoderErr
            )
        }
    }

    private fun handleEngineFailure(
        failedEngine: ActivePlaybackEngine,
        errorDetail: String,
        isDecoderInitFailure: Boolean
    ) {
        val currentState = _state.value
        val entry = currentState.currentEntry
        val resumePos = currentPositionMs().coerceAtLeast(0L)

        val decision = PlaybackEnginePolicy.decideOnFailure(
            preference = currentState.enginePreference,
            failedEngine = failedEngine,
            attemptedEngines = attemptedEnginesForCurrentItem,
            decoderPreference = currentState.decoderPreference,
            softwareFallbackAlreadyTriedInEngine = swFallbackTriedInCurrentEngine,
            isDecoderInitFailure = isDecoderInitFailure
        )

        when (decision) {
            is EngineFallbackDecision.RetryWithSoftwareDecoder -> {
                swFallbackTriedInCurrentEngine = true
                _state.update {
                    it.copy(
                        engineNotice = decision.reason,
                        playbackError = null,
                        diagnosticReport = null,
                        activeDecoderMode = ActiveDecoderMode.SOFTWARE
                    )
                }
                if (entry != null) {
                    launchEntryOnEngine(
                        engine = decision.engine,
                        entry = entry,
                        startPosMs = resumePos,
                        decoderOverride = DecoderPreference.SOFTWARE
                    )
                }
            }

            is EngineFallbackDecision.SwitchEngine -> {
                attemptedEnginesForCurrentItem.add(decision.nextEngine)
                swFallbackTriedInCurrentEngine = false
                if (failedEngine == ActivePlaybackEngine.MEDIA3) {
                    controller?.stop()
                    controller?.clearMediaItems()
                } else if (failedEngine == ActivePlaybackEngine.VLC) {
                    vlcEngine.stopPlaybackOnly()
                }
                _state.update {
                    it.copy(
                        activeEngine = decision.nextEngine,
                        engineNotice = decision.reason,
                        playbackError = null,
                        diagnosticReport = null,
                        activeDecoderMode = ActiveDecoderMode.UNKNOWN
                    )
                }
                if (entry != null) {
                    launchEntryOnEngine(
                        engine = decision.nextEngine,
                        entry = entry,
                        startPosMs = resumePos,
                        decoderOverride = decision.nextDecoder
                    )
                }
            }

            is EngineFallbackDecision.TerminalFailure -> {
                val attemptedSnapshot = attemptedEnginesForCurrentItem.toList()
                    .ifEmpty { listOf(failedEngine) }
                if (entry == null) {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            playbackError = errorDetail
                        )
                    }
                    return
                }
                scope.launch {
                    val report = withContext(Dispatchers.IO) {
                        CodecInspector.diagnoseFailure(
                            context = context,
                            uri = Uri.parse(entry.uriString),
                            containerExtension = entry.containerExtension.ifBlank {
                                entry.title.substringAfterLast('.', "")
                            },
                            errorDetail = errorDetail,
                            attemptedEngines = attemptedSnapshot,
                            decoderPreference = currentState.decoderPreference
                        )
                    }
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            playbackError = report.userMessage,
                            diagnosticReport = report
                        )
                    }
                }
            }
        }
    }

    private fun launchEntryOnEngine(
        engine: ActivePlaybackEngine,
        entry: QueueEntry,
        startPosMs: Long,
        decoderOverride: DecoderPreference = _state.value.decoderPreference
    ) {
        val currentState = _state.value
        when (engine) {
            ActivePlaybackEngine.VLC -> {
                controller?.pause()
                _state.update {
                    it.copy(
                        activeEngine = ActivePlaybackEngine.VLC,
                        playbackError = null,
                        diagnosticReport = null
                    )
                }
                vlcEngine.playUri(
                    uri = Uri.parse(entry.uriString),
                    startPositionMs = startPosMs,
                    decoderPreference = decoderOverride,
                    speed = currentState.playbackSpeed,
                    aspectRatio = currentState.aspectRatioMode,
                    audioDelayMs = currentState.audioDelayMs,
                    subDelayMs = activeSubtitleDelayMs,
                    audioBoostPercent = currentState.audioBoostPercent,
                    preferredAudioLanguage = currentState.preferredAudioLanguage
                )
                startProgressLoop()
            }

            ActivePlaybackEngine.MEDIA3 -> {
                vlcEngine.stopPlaybackOnly()
                PlaybackService.setDesiredDecoderPreference(decoderOverride)
                val loadAction = {
                    val queue = _state.value.queue
                    val mediaItems = queue.map { item ->
                        MediaItem.Builder()
                            .setUri(Uri.parse(item.uriString))
                            .setMediaId(item.uriString)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(item.title)
                                    .setSubtitle(item.subtitle)
                                    .build()
                            )
                            .build()
                    }
                    val idx = _state.value.currentIndex.coerceIn(0, mediaItems.lastIndex.coerceAtLeast(0))
                    _state.update {
                        it.copy(
                            activeEngine = ActivePlaybackEngine.MEDIA3,
                            playbackError = null,
                            diagnosticReport = null
                        )
                    }
                    controller?.apply {
                        if (mediaItems.isNotEmpty()) {
                            setMediaItems(mediaItems, idx, startPosMs)
                        } else {
                            setMediaItem(
                                MediaItem.Builder()
                                    .setUri(Uri.parse(entry.uriString))
                                    .setMediaId(entry.uriString)
                                    .build(),
                                startPosMs
                            )
                        }
                        setPlaybackSpeed(currentState.playbackSpeed)
                        applyPreferredAudioLanguageToMedia3(currentState.preferredAudioLanguage)
                        prepare()
                        playWhenReady = true
                    }
                    startProgressLoop()
                }
                if (controller?.isConnected == true) loadAction() else {
                    pendingAction = { loadAction() }
                    connect()
                }
            }

            ActivePlaybackEngine.NONE -> Unit
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
                folderPath = v.folderPath,
                containerExtension = v.containerExtension
            )
        }
        val targetVideo = videos[safeIdx]
        val chosenEngine = PlaybackEnginePolicy.selectInitialEngine(_state.value.enginePreference)

        scope.launch {
            val savedPos = if (resumeFromSaved) {
                val pos = mediaRepository.getSavedPosition(targetVideo.uriString)
                if (targetVideo.durationMs > 10_000L && pos in 2_000L..(targetVideo.durationMs - 4_000L)) pos else 0L
            } else 0L

            val discoveredSubs = mediaRepository.scanner.discoverSubtitlesForVideo(targetVideo)
            attemptedEnginesForCurrentItem = mutableSetOf(chosenEngine)
            swFallbackTriedInCurrentEngine = false

            _state.update {
                it.copy(
                    queue = entries,
                    currentIndex = safeIdx,
                    currentEntry = entries[safeIdx],
                    playbackSpeed = defaultSpeed,
                    aspectRatioMode = defaultAspectRatio,
                    playbackError = null,
                    diagnosticReport = null,
                    engineNotice = null,
                    activeEngine = chosenEngine,
                    activeDecoderMode = ActiveDecoderMode.UNKNOWN,
                    subtitleTracks = discoveredSubs,
                    externalSubtitleCues = emptyList(),
                    selectedSubtitleId = null
                )
            }

            launchEntryOnEngine(
                engine = chosenEngine,
                entry = entries[safeIdx],
                startPosMs = savedPos
            )
            recordHistoryEntry(entries[safeIdx])

            if (discoveredSubs.isNotEmpty()) {
                selectSubtitleTrack(discoveredSubs.first(), "UTF-8")
            }
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
        vlcEngine.stopPlaybackOnly()
        attemptedEnginesForCurrentItem = mutableSetOf(ActivePlaybackEngine.MEDIA3)
        swFallbackTriedInCurrentEngine = false

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
                    diagnosticReport = null,
                    engineNotice = null,
                    activeEngine = ActivePlaybackEngine.MEDIA3,
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

    fun switchEngineNow(targetPref: PlaybackEnginePreference) {
        val targetEngine = when (targetPref) {
            PlaybackEnginePreference.VLC -> ActivePlaybackEngine.VLC
            PlaybackEnginePreference.MEDIA3 -> ActivePlaybackEngine.MEDIA3
            PlaybackEnginePreference.AUTO -> PlaybackEnginePolicy.selectInitialEngine(targetPref)
        }
        val entry = _state.value.currentEntry
        val resumePos = currentPositionMs().coerceAtLeast(0L)
        _state.update {
            it.copy(
                enginePreference = targetPref,
                engineNotice = "Switched playback engine to ${targetEngine.label}."
            )
        }
        if (entry != null) {
            attemptedEnginesForCurrentItem = mutableSetOf(targetEngine)
            swFallbackTriedInCurrentEngine = false
            launchEntryOnEngine(targetEngine, entry, resumePos)
        }
    }

    fun switchDecoderNow(targetDecoder: DecoderPreference) {
        val entry = _state.value.currentEntry
        val resumePos = currentPositionMs().coerceAtLeast(0L)
        PlaybackService.setDesiredDecoderPreference(targetDecoder)
        _state.update {
            it.copy(
                decoderPreference = targetDecoder,
                engineNotice = "Decoder set to ${targetDecoder.label}."
            )
        }
        if (entry != null) {
            val active = _state.value.activeEngine.takeIf { it != ActivePlaybackEngine.NONE }
                ?: PlaybackEnginePolicy.selectInitialEngine(_state.value.enginePreference)
            attemptedEnginesForCurrentItem = mutableSetOf(active)
            swFallbackTriedInCurrentEngine = targetDecoder == DecoderPreference.SOFTWARE
            launchEntryOnEngine(active, entry, resumePos, decoderOverride = targetDecoder)
        }
    }

    fun retryCurrentEntry(forceSoftware: Boolean = false) {
        val entry = _state.value.currentEntry ?: return
        val resumePos = currentPositionMs().coerceAtLeast(0L)
        val chosenEngine = PlaybackEnginePolicy.selectInitialEngine(_state.value.enginePreference)
        attemptedEnginesForCurrentItem = mutableSetOf(chosenEngine)
        swFallbackTriedInCurrentEngine = forceSoftware
        val decoderPref = if (forceSoftware) DecoderPreference.SOFTWARE else _state.value.decoderPreference
        launchEntryOnEngine(chosenEngine, entry, resumePos, decoderOverride = decoderPref)
    }

    fun clearEngineNotice() {
        _state.update { it.copy(engineNotice = null) }
    }

    private fun currentPositionMs(): Long {
        return when (_state.value.activeEngine) {
            ActivePlaybackEngine.VLC -> vlcEngine.currentPositionMs().takeIf { it > 0L } ?: _state.value.currentPositionMs
            ActivePlaybackEngine.MEDIA3 -> controller?.currentPosition ?: _state.value.currentPositionMs
            ActivePlaybackEngine.NONE -> _state.value.currentPositionMs
        }
    }

    fun togglePlayPause() {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            if (vlcEngine.isPlaying()) vlcEngine.pause() else vlcEngine.play()
            return
        }
        val mc = controller
        if (mc != null && mc.isConnected) {
            if (mc.isPlaying) mc.pause() else mc.play()
        } else if (_state.value.currentEntry != null) {
            val resumeEntry = _state.value.currentEntry!!
            val resumePos = _state.value.currentPositionMs
            pendingAction = {
                launchEntryOnEngine(ActivePlaybackEngine.MEDIA3, resumeEntry, resumePos)
            }
            connect()
        }
    }

    fun pause() {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.pause()
        } else {
            controller?.pause()
        }
    }

    fun stopPlayback() {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.stopPlaybackOnly()
        } else {
            controller?.apply {
                pause()
                stop()
                seekTo(0L)
            }
        }
        _state.update { it.copy(isPlaying = false, playbackState = Player.STATE_IDLE) }
    }

    fun seekTo(positionMs: Long) {
        val dur = _state.value.durationMs.coerceAtLeast(0L)
        val clamped = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.seekTo(clamped)
        } else {
            controller?.seekTo(clamped)
        }
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun seekForward10s() {
        seekTo(currentPositionMs() + 10_000L)
    }

    fun seekBackward10s() {
        seekTo(currentPositionMs() - 10_000L)
    }

    private fun handleVlcEnded() {
        val stateVal = _state.value
        when (stateVal.repeatMode) {
            RepeatModeOption.ONE -> {
                stateVal.currentEntry?.let { launchEntryOnEngine(ActivePlaybackEngine.VLC, it, 0L) }
            }
            RepeatModeOption.ALL -> {
                if (stateVal.queue.isNotEmpty()) {
                    val nextIdx = (stateVal.currentIndex + 1) % stateVal.queue.size
                    playQueueIndex(nextIdx)
                }
            }
            RepeatModeOption.OFF -> {
                if (stateVal.currentIndex + 1 < stateVal.queue.size) {
                    playQueueIndex(stateVal.currentIndex + 1)
                }
            }
        }
    }

    fun skipNext() {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            val next = _state.value.currentIndex + 1
            if (next in _state.value.queue.indices) {
                playQueueIndex(next)
            }
            return
        }
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun skipPrevious() {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            val prev = _state.value.currentIndex - 1
            if (prev in _state.value.queue.indices) {
                playQueueIndex(prev)
            } else {
                seekTo(0L)
            }
            return
        }
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
    }

    fun playQueueIndex(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC ||
            _state.value.enginePreference == PlaybackEnginePreference.VLC
        ) {
            val entry = queue[index]
            val engine = PlaybackEnginePolicy.selectInitialEngine(_state.value.enginePreference)
            attemptedEnginesForCurrentItem = mutableSetOf(engine)
            swFallbackTriedInCurrentEngine = false
            _state.update {
                it.copy(
                    currentIndex = index,
                    currentEntry = entry,
                    playbackError = null,
                    diagnosticReport = null,
                    externalSubtitleCues = emptyList(),
                    selectedSubtitleId = null
                )
            }
            launchEntryOnEngine(engine, entry, 0L)
            recordHistoryEntry(entry)
        } else {
            controller?.seekTo(index, 0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.setPlaybackSpeed(safeSpeed)
        } else {
            controller?.setPlaybackSpeed(safeSpeed)
        }
        _state.update { it.copy(playbackSpeed = safeSpeed) }
    }

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        val next = modes[(_state.value.aspectRatioMode.ordinal + 1) % modes.size]
        setAspectRatio(next)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.setAspectRatio(mode)
        }
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
        if (option.groupIndex == VlcPlaybackEngine.VLC_GROUP_SENTINEL ||
            _state.value.activeEngine == ActivePlaybackEngine.VLC
        ) {
            vlcEngine.selectAudioTrack(option.trackIndex)
            return
        }
        val mc = controller ?: return
        val group = mc.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        mc.trackSelectionParameters = mc.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun setAudioDelayMs(delayMs: Long) {
        val clamped = delayMs.coerceIn(-10_000L, 10_000L)
        _state.update { it.copy(audioDelayMs = clamped) }
        if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
            vlcEngine.setAudioDelayMs(clamped)
        }
    }

    fun setAudioBoostPercent(boostPercent: Int) {
        val clamped = boostPercent.coerceIn(100, 200)
        _state.update { it.copy(audioBoostPercent = clamped) }
        applyAudioBoost(clamped)
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
            if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
                withContext(Dispatchers.Main.immediate) {
                    // Disable VLC native subtitle rendering when Compose overlay handles external cues cleanly (incl. Bengali)
                    vlcEngine.selectEmbeddedSubtitleTrack(null)
                }
            }
        }
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?, encoding: String) {
        val mc = controller
        if (option == null) {
            if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
                vlcEngine.selectEmbeddedSubtitleTrack(null)
            } else {
                mc?.let {
                    it.trackSelectionParameters = it.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            }
            _state.update { it.copy(selectedSubtitleId = null, externalSubtitleCues = emptyList()) }
            return
        }

        if (option.isEmbedded && option.groupIndex == VlcPlaybackEngine.VLC_GROUP_SENTINEL) {
            vlcEngine.selectEmbeddedSubtitleTrack(option.trackIndex)
            _state.update { it.copy(selectedSubtitleId = option.id, externalSubtitleCues = emptyList()) }
        } else if (option.isEmbedded && mc != null) {
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
            if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
                vlcEngine.selectEmbeddedSubtitleTrack(null)
            } else {
                mc?.let {
                    it.trackSelectionParameters = it.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
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

    fun setSleepTimerEndAction(action: SleepTimerEndAction) {
        _state.update { it.copy(sleepTimerEndAction = action) }
        val rem = _state.value.sleepTimerRemainingSec
        if (rem != null && rem > 0) {
            PlaybackService.postSleepTimerNotification(context, rem, action.label)
        }
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        startSleepTimerSeconds(minutes.coerceIn(1, 720) * 60)
    }

    fun startSleepTimer(hours: Int, minutes: Int) {
        val totalMin = (hours.coerceIn(0, 12) * 60) + minutes.coerceIn(0, 59)
        if (totalMin <= 0) {
            cancelSleepTimer()
            return
        }
        startSleepTimerSeconds(totalMin.coerceIn(1, 720) * 60)
    }

    fun startSleepTimerSeconds(totalSeconds: Int) {
        cancelSleepTimerInternal(clearNotice = false)
        if (totalSeconds <= 0) {
            cancelSleepTimer()
            return
        }
        val clampedSec = totalSeconds.coerceIn(1, 720 * 60)
        val durationMs = clampedSec * 1000L
        _state.update {
            it.copy(
                sleepTimerTotalSec = clampedSec,
                sleepTimerRemainingSec = clampedSec,
                sleepTimerAutoPausedNotice = false
            )
        }
        PlaybackService.postSleepTimerNotification(
            context = context,
            remainingSec = clampedSec,
            actionLabel = _state.value.sleepTimerEndAction.label
        )
        val startTimerRunnable = Runnable {
            sleepCountDownTimer?.cancel()
            sleepCountDownTimer = object : CountDownTimer(durationMs, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val secLeft = ((millisUntilFinished + 999L) / 1000L).toInt().coerceAtLeast(1)
                    _state.update { it.copy(sleepTimerRemainingSec = secLeft) }
                    PlaybackService.postSleepTimerNotification(
                        context = context,
                        remainingSec = secLeft,
                        actionLabel = _state.value.sleepTimerEndAction.label
                    )
                }

                override fun onFinish() {
                    sleepCountDownTimer = null
                    val chosenAction = _state.value.sleepTimerEndAction
                    when (chosenAction) {
                        SleepTimerEndAction.AUTO_PAUSE -> {
                            pause()
                            PlaybackService.stopForegroundMediaServiceForSleepTimer(
                                context = context,
                                stopPlaybackToo = false
                            )
                        }
                        SleepTimerEndAction.STOP_PLAYBACK -> {
                            stopPlayback()
                            PlaybackService.stopForegroundMediaServiceForSleepTimer(
                                context = context,
                                stopPlaybackToo = true
                            )
                        }
                    }
                    PlaybackService.postSleepTimerFinishedNotification(context, chosenAction.label)
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            sleepTimerRemainingSec = null,
                            sleepTimerTotalSec = null,
                            sleepTimerLastEndAction = chosenAction,
                            sleepTimerAutoPausedNotice = true
                        )
                    }
                }
            }.start()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            startTimerRunnable.run()
        } else {
            mainHandler.post(startTimerRunnable)
        }
    }

    fun resetSleepTimer() {
        val total = _state.value.sleepTimerTotalSec ?: return
        if (total > 0) {
            startSleepTimerSeconds(total)
        }
    }

    fun cancelSleepTimer() {
        cancelSleepTimerInternal(clearNotice = true)
    }

    private fun cancelSleepTimerInternal(clearNotice: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sleepCountDownTimer?.cancel()
            sleepCountDownTimer = null
        } else {
            mainHandler.post {
                sleepCountDownTimer?.cancel()
                sleepCountDownTimer = null
            }
        }
        if (clearNotice) {
            PlaybackService.cancelSleepTimerNotification(context)
            _state.update {
                it.copy(
                    sleepTimerRemainingSec = null,
                    sleepTimerTotalSec = null,
                    sleepTimerAutoPausedNotice = false
                )
            }
        }
    }

    fun clearSleepTimerNotice() {
        _state.update { it.copy(sleepTimerAutoPausedNotice = false) }
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
                if (_state.value.activeEngine == ActivePlaybackEngine.VLC) {
                    val pos = vlcEngine.currentPositionMs()
                    val dur = vlcEngine.durationMs().takeIf { it > 0L } ?: _state.value.durationMs
                    _state.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = dur
                        )
                    }
                    if (vlcEngine.isPlaying() && tickCount % 6 == 0) {
                        persistCurrentPosition()
                    }
                } else {
                    val mc = controller
                    if (mc != null && _state.value.activeEngine == ActivePlaybackEngine.MEDIA3) {
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
                    }
                }
                tickCount++
                delay(500L)
            }
        }
    }

    private fun persistCurrentPosition() {
        val entry = _state.value.currentEntry ?: return
        val pos = currentPositionMs()
        val dur = when (_state.value.activeEngine) {
            ActivePlaybackEngine.VLC -> vlcEngine.durationMs().takeIf { it > 0L } ?: entry.durationMs
            else -> controller?.duration?.takeIf { it > 0L } ?: entry.durationMs
        }
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
        sleepCountDownTimer?.cancel()
        sleepCountDownTimer = null
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        runCatching { equalizer?.release() }
        equalizer = null
        vlcEngine.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}

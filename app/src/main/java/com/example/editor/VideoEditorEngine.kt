package com.example.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class VideoEditorEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _exportState = MutableStateFlow<VideoExportState>(VideoExportState.Idle)
    val exportState: StateFlow<VideoExportState> = _exportState.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var exportJob: Job? = null

    fun resetExportState() {
        _exportState.value = VideoExportState.Idle
    }

    fun cancelExport() {
        mainHandler.post {
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
        }
        exportJob?.cancel()
        exportJob = null
        _exportState.value = VideoExportState.Idle
    }

    fun startExport(
        config: VideoEditorConfig,
        onSavedToGallery: () -> Unit
    ) {
        exportJob?.cancel()
        exportJob = scope.launch {
            val safeConfig = VideoEditorSpecBuilder.normalizeConfig(config)
            val savedResults = mutableListOf<SavedGalleryVideo>()
            val tempFiles = mutableListOf<File>()

            try {
                if (safeConfig.timelineMode == VideoTimelineMode.SPLIT &&
                    safeConfig.splitExportChoice == VideoSplitExportChoice.BOTH
                ) {
                    val splitParts = listOf(
                        VideoSplitExportChoice.PART_1 to "part1",
                        VideoSplitExportChoice.PART_2 to "part2"
                    )
                    splitParts.forEachIndexed { idx, (choice, suffix) ->
                        val partConfig = safeConfig.copy(splitExportChoice = choice)
                        val tempFile = File(
                            context.cacheDir,
                            "lt_edit_${System.currentTimeMillis()}_$idx.mp4"
                        )
                        tempFiles += tempFile
                        val composition = buildComposition(partConfig)
                        val baseProgress = idx * 50
                        runTransformerAwait(
                            composition = composition,
                            outputPath = tempFile.absolutePath,
                            onProgress = { pct ->
                                val combined = (baseProgress + pct / 2).coerceIn(0, 98)
                                _exportState.value = VideoExportState.Exporting(
                                    progressPercent = combined,
                                    stageLabel = "Exporting split part ${idx + 1} of 2..."
                                )
                            }
                        )
                        _exportState.value = VideoExportState.Exporting(
                            progressPercent = if (idx == 0) 50 else 99,
                            stageLabel = "Saving part ${idx + 1} to Gallery..."
                        )
                        val fileName = GalleryMediaSaver.sanitizeOutputFileName(safeConfig.title, suffix)
                        val saved = withContext(Dispatchers.IO) {
                            GalleryMediaSaver.saveVideoToGallery(context, tempFile, fileName)
                        }
                        savedResults += saved
                    }
                } else {
                    val tempFile = File(context.cacheDir, "lt_edit_${System.currentTimeMillis()}.mp4")
                    tempFiles += tempFile
                    val composition = buildComposition(safeConfig)
                    val stageLabel = when (safeConfig.timelineMode) {
                        VideoTimelineMode.TRIM -> "Trimming & encoding video..."
                        VideoTimelineMode.CUT -> "Cutting unwanted segment..."
                        VideoTimelineMode.SPLIT -> "Exporting split segment..."
                        VideoTimelineMode.MERGE -> "Merging ${safeConfig.mergeClips.size.coerceAtLeast(1)} video clips..."
                    }
                    runTransformerAwait(
                        composition = composition,
                        outputPath = tempFile.absolutePath,
                        onProgress = { pct ->
                            _exportState.value = VideoExportState.Exporting(
                                progressPercent = pct.coerceIn(0, 98),
                                stageLabel = stageLabel
                            )
                        }
                    )
                    _exportState.value = VideoExportState.Exporting(
                        progressPercent = 99,
                        stageLabel = "Saving new copy to Gallery..."
                    )
                    val suffix = when (safeConfig.timelineMode) {
                        VideoTimelineMode.TRIM -> "trim"
                        VideoTimelineMode.CUT -> "cut"
                        VideoTimelineMode.SPLIT ->
                            if (safeConfig.splitExportChoice == VideoSplitExportChoice.PART_1) "part1" else "part2"
                        VideoTimelineMode.MERGE -> "merged"
                    }
                    val fileName = GalleryMediaSaver.sanitizeOutputFileName(safeConfig.title, suffix)
                    val saved = withContext(Dispatchers.IO) {
                        GalleryMediaSaver.saveVideoToGallery(context, tempFile, fileName)
                    }
                    savedResults += saved
                }

                val primary = savedResults.first()
                _exportState.value = VideoExportState.Success(
                    primaryGalleryUri = primary.contentUri.toString(),
                    displayFileName = primary.displayName,
                    outputSizeBytes = savedResults.sumOf { it.sizeBytes },
                    savedFileCount = savedResults.size,
                    additionalGalleryUris = savedResults.drop(1).map { it.contentUri.toString() }
                )
                onSavedToGallery()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) return@launch
                _exportState.value = VideoExportState.Error(
                    t.localizedMessage ?: "Video export failed due to codec or storage constraints."
                )
            } finally {
                tempFiles.forEach { file ->
                    runCatching { if (file.exists()) file.delete() }
                }
            }
        }
    }

    private fun buildComposition(config: VideoEditorConfig): Composition {
        val isMultiClipSequence =
            (config.timelineMode == VideoTimelineMode.MERGE && config.mergeClips.size > 1) ||
                (config.timelineMode == VideoTimelineMode.CUT &&
                    VideoEditorSpecBuilder.buildSingleVideoSegmentsMs(config).size > 1)

        val targetShortSide = VideoEditorSpecBuilder.resolveTargetShortSidePx(
            config = config,
            requiresUniformSequenceResolution = isMultiClipSequence
        )

        val videoEditedItems: List<EditedMediaItem> = if (
            config.timelineMode == VideoTimelineMode.MERGE && config.mergeClips.isNotEmpty()
        ) {
            var runningOffsetMs = 0L
            config.mergeClips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(clip.uriString))
                    .build()
                val effectsList = VideoEditorSpecBuilder.buildVideoEffects(
                    config = config,
                    includeOverlays = true,
                    segmentStartOffsetMs = runningOffsetMs,
                    targetShortSidePx = targetShortSide
                )
                runningOffsetMs += clip.durationMs.coerceAtLeast(0L)
                buildEditedMediaItem(
                    mediaItem = mediaItem,
                    videoEffects = effectsList,
                    speed = config.speedMultiplier,
                    removeAudio = config.muteOriginalAudio
                )
            }
        } else {
            val segments = VideoEditorSpecBuilder.buildSingleVideoSegmentsMs(config)
            segments.map { (startMs, endMs) ->
                val clipped = MediaItem.Builder()
                    .setUri(Uri.parse(config.sourceUriString))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    )
                    .build()
                val effectsList = VideoEditorSpecBuilder.buildVideoEffects(
                    config = config,
                    includeOverlays = true,
                    segmentStartOffsetMs = startMs,
                    targetShortSidePx = targetShortSide
                )
                buildEditedMediaItem(
                    mediaItem = clipped,
                    videoEffects = effectsList,
                    speed = config.speedMultiplier,
                    removeAudio = config.muteOriginalAudio
                )
            }
        }

        val primarySequence = if (config.muteOriginalAudio) {
            EditedMediaItemSequence.withVideoFrom(videoEditedItems)
        } else {
            EditedMediaItemSequence.withAudioAndVideoFrom(videoEditedItems)
        }

        val sequences = mutableListOf(primarySequence)

        val bgMusicUri = config.backgroundMusicUriString
        if (!bgMusicUri.isNullOrBlank()) {
            val vol = config.backgroundMusicVolume.coerceIn(0.05f, 1.0f)
            val audioProcessors = mutableListOf<AudioProcessor>()
            if (vol < 0.99f) {
                audioProcessors += GainProcessor(object : GainProcessor.GainProvider {
                    override fun getGainFactorAtSamplePosition(samplePosition: Long, channelIndex: Int): Float = vol
                    override fun isUnityUntil(samplePosition: Long, channelIndex: Int): Long = -1L
                })
            }
            val bgItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(bgMusicUri)))
                .setRemoveVideo(true)
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()
            val bgSequence = EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO))
                .addItem(bgItem)
                .setIsLooping(true)
                .build()
            sequences += bgSequence
        }

        return Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    private fun buildEditedMediaItem(
        mediaItem: MediaItem,
        videoEffects: List<androidx.media3.common.Effect>,
        speed: Float,
        removeAudio: Boolean
    ): EditedMediaItem {
        val builder = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(removeAudio)
            .setEffects(Effects(emptyList(), videoEffects))
        if (kotlin.math.abs(speed - 1.0f) > 0.01f) {
            builder.setSpeed(
                SpeedParameters(
                    VideoEditorSpecBuilder.ConstantSpeedProvider(speed),
                    true
                )
            )
        }
        return builder.build()
    }

    private suspend fun runTransformerAwait(
        composition: Composition,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): ExportResult {
        return suspendCancellableCoroutine { cont ->
            val progressHolder = ProgressHolder()
            var pollingJob: Job? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                    pollingJob?.cancel()
                    activeTransformer = null
                    if (cont.isActive) cont.resume(exportResult)
                }

                override fun onError(
                    comp: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    pollingJob?.cancel()
                    activeTransformer = null
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setLooper(Looper.getMainLooper())
                .addListener(listener)
                .build()

            activeTransformer = transformer

            mainHandler.post {
                runCatching {
                    transformer.start(composition, outputPath)
                }.onFailure { err ->
                    pollingJob?.cancel()
                    activeTransformer = null
                    if (cont.isActive) cont.resumeWithException(err)
                }
            }

            pollingJob = scope.launch {
                while (isActive && cont.isActive) {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    }
                    delay(200L)
                }
            }

            cont.invokeOnCancellation {
                pollingJob?.cancel()
                mainHandler.post {
                    runCatching { transformer.cancel() }
                    if (activeTransformer === transformer) {
                        activeTransformer = null
                    }
                }
            }
        }
    }

    fun release() {
        cancelExport()
    }
}

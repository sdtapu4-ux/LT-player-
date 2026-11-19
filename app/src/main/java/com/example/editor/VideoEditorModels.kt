package com.example.editor

import com.example.data.model.SubtitleCue
import kotlinx.serialization.Serializable

@Serializable
enum class VideoTimelineMode(val label: String) {
    TRIM("Trim"),
    CUT("Cut"),
    SPLIT("Split"),
    MERGE("Merge")
}

@Serializable
enum class VideoSplitExportChoice(val label: String) {
    PART_1("Part 1"),
    PART_2("Part 2"),
    BOTH("Both Parts")
}

@Serializable
enum class VideoFilterPreset(val label: String) {
    NONE("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    GRAYSCALE("Mono"),
    SEPIA("Sepia"),
    INVERT("Invert")
}

@Serializable
enum class VideoCropPreset(val label: String, val targetAspect: Float?) {
    ORIGINAL("Free", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_9_16("9:16", 9f / 16f)
}

@Serializable
enum class TextOverlayPosition(val label: String, val anchorY: Float) {
    TOP("Top", 0.78f),
    CENTER("Center", 0f),
    BOTTOM("Bottom", -0.68f)
}

@Serializable
enum class VideoExportResolution(val shortSidePx: Int?, val label: String) {
    ORIGINAL(null, "Original"),
    P360(360, "360p"),
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p")
}

@Serializable
data class MergeClipEntry(
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val resolutionLabel: String = ""
)

data class CropBounds(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
) {
    val isFullFrame: Boolean
        get() = left <= -0.995f && right >= 0.995f && bottom <= -0.995f && top >= 0.995f
}

data class SourceAudioTrackInfo(
    val indexInContainer: Int,
    val audioOrderIndex: Int,
    val label: String,
    val language: String?,
    val mimeType: String
)

@Serializable
data class VideoEditorConfig(
    val sourceUriString: String,
    val title: String,
    val durationMs: Long,
    val sourceWidth: Int = 1280,
    val sourceHeight: Int = 720,
    val timelineMode: VideoTimelineMode = VideoTimelineMode.TRIM,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs.coerceAtLeast(1000L),
    val cutStartMs: Long = (durationMs * 0.30f).toLong().coerceAtLeast(0L),
    val cutEndMs: Long = (durationMs * 0.60f).toLong().coerceAtLeast(0L),
    val splitPositionMs: Long = (durationMs / 2L).coerceAtLeast(500L),
    val splitExportChoice: VideoSplitExportChoice = VideoSplitExportChoice.BOTH,
    val mergeClips: List<MergeClipEntry> = emptyList(),
    val cropPreset: VideoCropPreset = VideoCropPreset.ORIGINAL,
    val cropInsetLeft: Float = 0f,
    val cropInsetRight: Float = 0f,
    val cropInsetTop: Float = 0f,
    val cropInsetBottom: Float = 0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val resizeWidth: Int = 0,
    val resizeHeight: Int = 0,
    val customFrameRate: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val filterPreset: VideoFilterPreset = VideoFilterPreset.NONE,
    val overlayText: String = "",
    val overlayTextColorHex: Long = 0xFFFFFFFFL,
    val overlayTextPosition: TextOverlayPosition = TextOverlayPosition.BOTTOM,
    val overlayTextSizeScale: Float = 0.75f,
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val subtitleEnabled: Boolean = true,
    val subtitleFileName: String? = null,
    val subtitleDelayMs: Long = 0L,
    val muteOriginalAudio: Boolean = false,
    val originalAudioVolume: Float = 1.0f,
    val selectedAudioTrackIndex: Int = 0,
    val backgroundMusicUriString: String? = null,
    val backgroundMusicTitle: String? = null,
    val backgroundMusicVolume: Float = 0.65f,
    val exportResolution: VideoExportResolution = VideoExportResolution.P720
)

sealed interface VideoExportState {
    data object Idle : VideoExportState
    data class Exporting(
        val progressPercent: Int,
        val stageLabel: String
    ) : VideoExportState

    data class Success(
        val primaryGalleryUri: String,
        val displayFileName: String,
        val outputSizeBytes: Long,
        val savedFileCount: Int = 1,
        val additionalGalleryUris: List<String> = emptyList(),
        val outputFolderLabel: String = GalleryMediaSaver.EDITED_RELATIVE_PATH
    ) : VideoExportState

    data class Error(val message: String) : VideoExportState
}

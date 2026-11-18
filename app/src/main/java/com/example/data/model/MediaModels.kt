package com.example.data.model

import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) {
    DARK("Deep Black"),
    LIGHT("Light Studio"),
    SYSTEM("System Default")
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    CROP("Crop"),
    ZOOM("Zoom")
}

enum class RepeatModeOption(val label: String) {
    OFF("Repeat Off"),
    ONE("Repeat One"),
    ALL("Repeat All")
}

enum class FolderViewMode(val label: String) {
    LIST("List"),
    GRID("Grid"),
    COMPACT("Compact")
}

enum class MediaSortOption(val label: String) {
    DATE_DESC("Newest First"),
    NAME_ASC("Name A-Z"),
    SIZE_DESC("Largest First"),
    DURATION_DESC("Longest First")
}

enum class DownloadStatus(val label: String) {
    PENDING("Queued"),
    RUNNING("Downloading"),
    PAUSED("Paused"),
    SUCCESSFUL("Completed"),
    FAILED("Failed")
}

data class VideoItem(
    val id: Long,
    val uriString: String,
    val title: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val containerExtension: String,
    val folderName: String,
    val folderPath: String,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val isCompatible: Boolean = true,
    val compatibilityNote: String? = null
) {
    val resolutionLabel: String
        get() = when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "2K"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            width > 0 && height > 0 -> "${width}×${height}"
            else -> containerExtension.uppercase()
        }

    val progressFraction: Float
        get() = if (durationMs > 0L && lastPositionMs > 0L) {
            (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class AudioItem(
    val id: Long,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val folderName: String,
    val folderPath: String,
    val dateAddedSec: Long,
    val albumArtUri: String? = null,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val isCompatible: Boolean = true,
    val compatibilityNote: String? = null
)

data class MediaFolder(
    val name: String,
    val path: String,
    val videoCount: Int,
    val audioCount: Int,
    val totalSizeBytes: Long,
    val isSafTree: Boolean = false
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val isEmbedded: Boolean,
    val uriString: String? = null,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1
)

data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val channels: Int,
    val codec: String?,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

data class SubtitlePreferences(
    val enabled: Boolean = true,
    val fontSizeSp: Float = 18f,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundOpacity: Float = 0.55f,
    val verticalBottomPaddingDp: Float = 56f,
    val delayMs: Long = 0L,
    val encoding: String = "UTF-8"
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageCode: String = "en",
    val displayName: String = "LT Creator",
    val avatarIndex: Int = 0,
    val defaultSpeed: Float = 1.0f,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val backgroundAudioEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val privacyHideRecentThumbs: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val subtitlePrefs: SubtitlePreferences = SubtitlePreferences()
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val totalDurationMs: Long,
    val updatedAt: Long
)

data class DownloadRecord(
    val id: Long,
    val systemDownloadId: Long,
    val title: String,
    val url: String,
    val destinationUri: String?,
    val localPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val status: DownloadStatus,
    val errorReason: String?,
    val createdAt: Long
) {
    val progress: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class InstalledGameInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long = 0L
)

data class StorageBreakdown(
    val totalDeviceBytes: Long,
    val availableDeviceBytes: Long,
    val usedDeviceBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val downloadBytes: Long,
    val appCacheBytes: Long
)

data class DuplicateMediaGroup(
    val signatureKey: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val items: List<VideoItem>
)

@Serializable
data class CodecSupportReport(
    val container: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val isVideoSupported: Boolean,
    val isAudioSupported: Boolean,
    val message: String
)

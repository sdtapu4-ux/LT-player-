package com.example.data.scanner

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.CodecSupportReport

object CodecInspector {

    val supportedVideoContainers = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp",
        "mpeg", "mpg", "ts", "mts", "m2ts", "vob", "ogv", "m4v"
    )

    private val experimentalContainers = setOf("flv", "wmv", "vob", "avi")

    private val cachedDecoderMimes: Set<String> by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun isDecoderAvailable(mimeType: String): Boolean {
        val normalized = mimeType.lowercase()
        if (normalized in cachedDecoderMimes) return true
        return when (normalized) {
            "audio/mpeg", "audio/mp3" -> "audio/mpeg" in cachedDecoderMimes
            "audio/raw", "audio/wav", "audio/x-wav" -> true // PCM handled by ExoPlayer DefaultAudioSink
            "audio/flac" -> true // Media3 FLAC software/platform decoder
            "audio/opus", "audio/vorbis" -> true
            else -> false
        }
    }

    fun quickContainerCheck(extension: String, mimeType: String): Pair<Boolean, String?> {
        val ext = extension.lowercase()
        if (ext.isNotBlank() && ext !in supportedVideoContainers && !mimeType.startsWith("video/") && !mimeType.startsWith("audio/")) {
            return false to "Container .$ext may not be supported by Media3 default extractors."
        }
        if (ext in experimentalContainers) {
            return true to "Legacy .$ext container — playback depends on embedded codec support on this device."
        }
        return true to null
    }

    fun inspectUri(context: Context, uri: Uri, containerExtension: String): CodecSupportReport {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var videoMime: String? = null
            var audioMime: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoMime == null) {
                    videoMime = mime
                } else if (mime.startsWith("audio/") && audioMime == null) {
                    audioMime = mime
                }
            }
            val videoOk = videoMime == null || isDecoderAvailable(videoMime)
            val audioOk = audioMime == null || isDecoderAvailable(audioMime)
            val issues = buildList {
                if (!videoOk && videoMime != null) add("No device decoder for video codec ($videoMime)")
                if (!audioOk && audioMime != null) add("No device decoder for audio codec ($audioMime)")
            }
            val msg = if (issues.isEmpty()) {
                "Compatible (${videoMime ?: "Audio-only"}${if (audioMime != null) " + $audioMime" else ""})"
            } else {
                issues.joinToString("; ")
            }
            CodecSupportReport(
                container = containerExtension.uppercase().ifBlank { "MEDIA" },
                videoCodec = videoMime,
                audioCodec = audioMime,
                isVideoSupported = videoOk,
                isAudioSupported = audioOk,
                message = msg
            )
        } catch (e: Exception) {
            CodecSupportReport(
                container = containerExtension.uppercase().ifBlank { "UNKNOWN" },
                videoCodec = null,
                audioCodec = null,
                isVideoSupported = false,
                isAudioSupported = false,
                message = "Unable to inspect tracks: ${e.localizedMessage ?: "Unsupported container or inaccessible file"}"
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun getDeviceDecoderInventory(): List<Pair<String, Boolean>> {
        val targets = listOf(
            "H.264 / AVC (video/avc)" to "video/avc",
            "H.265 / HEVC (video/hevc)" to "video/hevc",
            "VP8 (video/x-vnd.on2.vp8)" to "video/x-vnd.on2.vp8",
            "VP9 (video/x-vnd.on2.vp9)" to "video/x-vnd.on2.vp9",
            "AV1 (video/av01)" to "video/av01",
            "MPEG-4 (video/mp4v-es)" to "video/mp4v-es",
            "MPEG-2 (video/mpeg2)" to "video/mpeg2",
            "AAC (audio/mp4a-latm)" to "audio/mp4a-latm",
            "MP3 (audio/mpeg)" to "audio/mpeg",
            "FLAC (audio/flac)" to "audio/flac",
            "Opus (audio/opus)" to "audio/opus",
            "Vorbis (audio/vorbis)" to "audio/vorbis",
            "AC3 (audio/ac3)" to "audio/ac3",
            "E-AC3 (audio/eac3)" to "audio/eac3"
        )
        return targets.map { (label, mime) -> label to isDecoderAvailable(mime) }
    }
}

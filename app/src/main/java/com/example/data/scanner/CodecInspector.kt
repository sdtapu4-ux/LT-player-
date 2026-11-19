package com.example.data.scanner

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.example.data.model.ActivePlaybackEngine
import com.example.data.model.CodecSupportReport
import com.example.data.model.DecoderCapabilityItem
import com.example.data.model.DecoderPreference
import com.example.data.model.MediaFailureCause
import com.example.data.model.PlaybackDiagnosticReport
import java.io.FileNotFoundException

object CodecInspector {

    val supportedVideoContainers = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp",
        "mpeg", "mpg", "ts", "mts", "m2ts", "vob", "ogv", "m4v", "asf", "mxf"
    )

    private val vlcPreferredContainers = setOf(
        "avi", "flv", "wmv", "asf", "vob", "mpg", "mpeg", "mxf", "ogv", "mts", "m2ts"
    )

    private val vlcSupportedVideoMimes = setOf(
        "video/avc", "video/hevc", "video/av01", "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9",
        "video/mpeg2", "video/mp4v-es", "video/xvid", "video/divx", "video/theora",
        "video/x-ms-wmv", "video/x-flv"
    )

    private val vlcSupportedAudioMimes = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4a-latm", "audio/aac", "audio/flac",
        "audio/raw", "audio/wav", "audio/x-wav", "audio/opus", "audio/vorbis",
        "audio/ac3", "audio/eac3", "audio/vnd.dts", "audio/vnd.dts.hd",
        "audio/x-ms-wma", "audio/alac"
    )

    private data class DecoderCaps(
        val hasHardware: Boolean,
        val hasSoftware: Boolean
    )

    private val decoderCapsMap: Map<String, DecoderCaps> by lazy {
        val map = mutableMapOf<String, DecoderCaps>()
        runCatching {
            val infos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            for (info in infos) {
                if (info.isEncoder) continue
                val isSw = isSoftwareCodecInfo(info)
                val isHw = isHardwareCodecInfo(info)
                for (type in info.supportedTypes) {
                    val mime = type.lowercase()
                    val prev = map[mime]
                    map[mime] = DecoderCaps(
                        hasHardware = (prev?.hasHardware == true) || isHw,
                        hasSoftware = (prev?.hasSoftware == true) || isSw
                    )
                }
            }
        }
        map
    }

    private fun isSoftwareCodecInfo(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isSoftwareOnly
        }
        val lower = info.name.lowercase()
        return lower.startsWith("omx.google.") ||
            lower.startsWith("c2.android.") ||
            lower.contains(".sw.")
    }

    private fun isHardwareCodecInfo(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        return !isSoftwareCodecInfo(info)
    }

    fun hasHardwareDecoder(mimeType: String): Boolean {
        return decoderCapsMap[mimeType.lowercase()]?.hasHardware == true
    }

    fun hasSoftwareDecoder(mimeType: String): Boolean {
        val normalized = mimeType.lowercase()
        if (decoderCapsMap[normalized]?.hasSoftware == true) return true
        return normalized in setOf(
            "audio/raw", "audio/wav", "audio/x-wav", "audio/flac", "audio/opus", "audio/vorbis"
        )
    }

    fun isDecoderAvailable(mimeType: String): Boolean {
        val normalized = mimeType.lowercase()
        if (normalized in decoderCapsMap) return true
        return when (normalized) {
            "audio/mpeg", "audio/mp3" -> "audio/mpeg" in decoderCapsMap
            "audio/raw", "audio/wav", "audio/x-wav" -> true
            "audio/flac", "audio/opus", "audio/vorbis" -> true
            else -> false
        }
    }

    fun isVlcSupportedCodec(mimeType: String): Boolean {
        val norm = mimeType.lowercase()
        return norm in vlcSupportedVideoMimes || norm in vlcSupportedAudioMimes
    }

    /**
     * Inspects actual file header magic bytes rather than trusting extension alone.
     */
    fun detectContainerFromHeaderBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null

        // EBML header -> Matroska (.mkv) or WebM (.webm)
        if (bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            val ascii = String(bytes, Charsets.ISO_8859_1).lowercase()
            return if ("webm" in ascii) "WEBM" else "MKV"
        }

        // ISO Base Media File Format ("ftyp" at offset 4) -> MP4 / MOV / 3GP / M4V
        if (bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, 4, Charsets.ISO_8859_1)
            return when {
                brand.startsWith("qt") -> "MOV"
                brand.startsWith("3g") -> "3GP"
                brand.startsWith("M4V", ignoreCase = true) -> "M4V"
                else -> "MP4"
            }
        }

        // RIFF .... AVI
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'A'.code.toByte() &&
            bytes[9] == 'V'.code.toByte() &&
            bytes[10] == 'I'.code.toByte()
        ) {
            return "AVI"
        }

        // FLV signature
        if (bytes[0] == 'F'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'V'.code.toByte()
        ) {
            return "FLV"
        }

        // ASF / WMV GUID: 30 26 B2 75 8E 66 CF 11
        if (bytes.size >= 8 &&
            bytes[0] == 0x30.toByte() &&
            bytes[1] == 0x26.toByte() &&
            bytes[2] == 0xB2.toByte() &&
            bytes[3] == 0x75.toByte() &&
            bytes[4] == 0x8E.toByte() &&
            bytes[5] == 0x66.toByte() &&
            bytes[6] == 0xCF.toByte() &&
            bytes[7] == 0x11.toByte()
        ) {
            return "ASF/WMV"
        }

        // Ogg / Theora / OGV
        if (bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) {
            return "OGV"
        }

        // MPEG Program Stream / VOB pack header (00 00 01 BA)
        if (bytes[0] == 0x00.toByte() &&
            bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x01.toByte() &&
            bytes[3] == 0xBA.toByte()
        ) {
            return "MPEG/VOB"
        }

        // SMPTE MXF Partition Pack key (06 0E 2B 34)
        if (bytes[0] == 0x06.toByte() &&
            bytes[1] == 0x0E.toByte() &&
            bytes[2] == 0x2B.toByte() &&
            bytes[3] == 0x34.toByte()
        ) {
            return "MXF"
        }

        // MPEG-TS (188-byte packets starting with 0x47)
        if (bytes[0] == 0x47.toByte() && (bytes.size < 189 || bytes[188] == 0x47.toByte())) {
            return "TS"
        }

        // Blu-ray / AVCHD M2TS (192-byte packets with 4-byte timestamp + 0x47 sync byte)
        if (bytes.size >= 197 && bytes[4] == 0x47.toByte() && bytes[196] == 0x47.toByte()) {
            return "M2TS"
        }

        return null
    }

    fun detectContainerFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(256)
                val read = stream.read(buf)
                if (read > 0) detectContainerFromHeaderBytes(buf.copyOf(read)) else null
            }
        }.getOrNull()
    }

    fun quickContainerCheck(extension: String, mimeType: String): Pair<Boolean, String?> {
        val ext = extension.lowercase()
        if (ext.isNotBlank() &&
            ext !in supportedVideoContainers &&
            !mimeType.startsWith("video/") &&
            !mimeType.startsWith("audio/")
        ) {
            return false to "Container .$ext may require LibVLC compatibility mode."
        }
        if (ext in vlcPreferredContainers) {
            return true to "Legacy .$ext container — auto-fallback to LibVLC active if needed."
        }
        return true to null
    }

    fun inspectUri(context: Context, uri: Uri, containerExtension: String): CodecSupportReport {
        val headerContainer = detectContainerFromUri(context, uri)
        val containerLabel = headerContainer
            ?: containerExtension.uppercase().ifBlank { "MEDIA" }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var videoMime: String? = null
            var audioMime: String? = null
            var isDrm = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (format.containsKey("crypto-mode") ||
                    format.containsKey("crypto-key") ||
                    mime.contains("drm", ignoreCase = true)
                ) {
                    isDrm = true
                }
                if (mime.startsWith("video/") && videoMime == null) {
                    videoMime = mime
                } else if (mime.startsWith("audio/") && audioMime == null) {
                    audioMime = mime
                }
            }

            if (extractor.psshInfo?.isNotEmpty() == true) {
                isDrm = true
            }

            val videoHw = videoMime == null || hasHardwareDecoder(videoMime)
            val videoSw = videoMime == null || hasSoftwareDecoder(videoMime)
            val videoOk = videoMime == null || isDecoderAvailable(videoMime)
            val audioOk = audioMime == null || isDecoderAvailable(audioMime)
            val vlcHandles = (videoMime == null || isVlcSupportedCodec(videoMime)) &&
                (audioMime == null || isVlcSupportedCodec(audioMime))

            val issues = buildList {
                if (isDrm) add("DRM-encrypted stream detected")
                if (!videoOk && videoMime != null) add("No MediaCodec decoder for $videoMime (LibVLC fallback ready)")
                if (!audioOk && audioMime != null) add("No MediaCodec decoder for $audioMime (LibVLC fallback ready)")
            }

            val msg = if (issues.isEmpty()) {
                val hwTag = if (videoMime != null) {
                    if (videoHw) "HW" else "SW"
                } else "Audio"
                "Compatible [$containerLabel • $hwTag] (${videoMime ?: "Audio-only"}${if (audioMime != null) " + $audioMime" else ""})"
            } else {
                issues.joinToString("; ")
            }

            CodecSupportReport(
                container = containerLabel,
                videoCodec = videoMime,
                audioCodec = audioMime,
                isVideoSupported = videoOk,
                isAudioSupported = audioOk,
                message = msg,
                detectedByHeader = headerContainer != null,
                hasHardwareDecoder = videoHw,
                hasSoftwareDecoder = videoSw,
                isDrmProtected = isDrm,
                vlcCanHandle = vlcHandles
            )
        } catch (e: Exception) {
            val normalizedContainer = containerLabel.lowercase()
            val vlcLikely = normalizedContainer in supportedVideoContainers ||
                headerContainer != null ||
                normalizedContainer in vlcPreferredContainers
            CodecSupportReport(
                container = containerLabel,
                videoCodec = null,
                audioCodec = null,
                isVideoSupported = false,
                isAudioSupported = false,
                message = if (vlcLikely) {
                    "MediaExtractor could not parse $containerLabel container — LibVLC universal demuxer will handle playback."
                } else {
                    "Unable to inspect tracks: ${e.localizedMessage ?: "Unsupported container or inaccessible file"}"
                },
                detectedByHeader = headerContainer != null,
                hasHardwareDecoder = false,
                hasSoftwareDecoder = false,
                isDrmProtected = false,
                vlcCanHandle = vlcLikely
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun diagnoseFailure(
        context: Context,
        uri: Uri,
        containerExtension: String,
        errorDetail: String,
        attemptedEngines: List<ActivePlaybackEngine>,
        decoderPreference: DecoderPreference
    ): PlaybackDiagnosticReport {
        val report = inspectUri(context, uri, containerExtension)
        val rawLower = errorDetail.lowercase()

        val canOpenStream = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read()
            }
        }
        val streamErr = canOpenStream.exceptionOrNull()
        val firstByte = canOpenStream.getOrNull()

        val cause: MediaFailureCause = when {
            streamErr is SecurityException || "permission" in rawLower || "eacces" in rawLower ->
                MediaFailureCause.PERMISSION_MISSING
            streamErr is FileNotFoundException || "enoent" in rawLower || "file_not_found" in rawLower ->
                MediaFailureCause.UNREADABLE_SOURCE
            report.isDrmProtected || "drm" in rawLower || "widevine" in rawLower || "crypto" in rawLower ->
                MediaFailureCause.DRM_PROTECTED
            firstByte == -1 ->
                MediaFailureCause.CORRUPTED_FILE
            "out of memory" in rawLower || "no_memory" in rawLower || "resource" in rawLower || "insufficient" in rawLower ->
                MediaFailureCause.INSUFFICIENT_RESOURCES
            "decoder_init" in rawLower || "mediacodec" in rawLower || "configure failed" in rawLower ->
                MediaFailureCause.DECODER_INIT_FAILURE
            report.videoCodec != null && !report.isVideoSupported ->
                MediaFailureCause.UNSUPPORTED_VIDEO_CODEC
            report.audioCodec != null && !report.isAudioSupported ->
                MediaFailureCause.UNSUPPORTED_AUDIO_CODEC
            "corrupt" in rawLower || "malformed" in rawLower || "invalid" in rawLower || "parsing_container_malformed" in rawLower ->
                MediaFailureCause.CORRUPTED_FILE
            "unrecognized" in rawLower || "container" in rawLower || report.container.lowercase() !in supportedVideoContainers ->
                MediaFailureCause.UNSUPPORTED_CONTAINER
            else ->
                MediaFailureCause.DECODER_INIT_FAILURE
        }

        val userMessage = when (cause) {
            MediaFailureCause.PERMISSION_MISSING ->
                "LT PLAYER does not have permission to read this file. Re-open it via Android Picker or grant storage access."
            MediaFailureCause.UNREADABLE_SOURCE ->
                "The media file could not be found or was moved from storage."
            MediaFailureCause.DRM_PROTECTED ->
                "This file is DRM-encrypted and cannot be decoded by local software or hardware players."
            MediaFailureCause.CORRUPTED_FILE ->
                "The file header or media frames appear truncated or corrupted."
            MediaFailureCause.INSUFFICIENT_RESOURCES ->
                "Not enough free memory or active hardware decoder slots are available."
            MediaFailureCause.DECODER_INIT_FAILURE ->
                "The selected decoder failed to initialize for ${report.videoCodec ?: report.container}."
            MediaFailureCause.UNSUPPORTED_VIDEO_CODEC ->
                "Video stream (${report.videoCodec ?: "unknown"}) has no compatible decoder under the current engine/decoder mode."
            MediaFailureCause.UNSUPPORTED_AUDIO_CODEC ->
                "Audio stream (${report.audioCodec ?: "unknown"}) is unsupported by the current decoder."
            MediaFailureCause.UNSUPPORTED_CONTAINER ->
                "Container ${report.container} could not be demuxed by ${attemptedEngines.joinToString(" -> ") { it.label }}."
        }

        val actions = buildList {
            if (ActivePlaybackEngine.VLC !in attemptedEngines) {
                add("Switch Engine to LibVLC")
            }
            if (decoderPreference != DecoderPreference.SOFTWARE) {
                add("Retry with Software Decoding")
            }
            if (cause == MediaFailureCause.PERMISSION_MISSING) {
                add("Re-select File via Picker")
            }
            add("Open Playback Compatibility Settings")
        }

        return PlaybackDiagnosticReport(
            cause = cause,
            container = report.container,
            videoCodec = report.videoCodec,
            audioCodec = report.audioCodec,
            attemptedEngines = attemptedEngines,
            decoderPreference = decoderPreference,
            technicalSummary = "${report.message} | Detail: $errorDetail",
            userMessage = userMessage,
            recoveryActions = actions
        )
    }

    fun getDeviceDecoderInventory(): List<Pair<String, Boolean>> {
        return getDetailedDecoderInventory().map { item ->
            val modeTag = buildList {
                if (item.hardwareSupported) add("HW")
                if (item.softwareSupported) add("SW")
                if (item.vlcSupported) add("LibVLC")
            }.joinToString("/")
            "${item.label}${if (modeTag.isNotBlank()) " [$modeTag]" else ""}" to
                (item.hardwareSupported || item.softwareSupported || item.vlcSupported)
        }
    }

    fun getDetailedDecoderInventory(): List<DecoderCapabilityItem> {
        val targets = listOf(
            "H.264 / AVC" to "video/avc",
            "H.265 / HEVC" to "video/hevc",
            "AV1" to "video/av01",
            "VP8" to "video/x-vnd.on2.vp8",
            "VP9" to "video/x-vnd.on2.vp9",
            "MPEG-2" to "video/mpeg2",
            "MPEG-4" to "video/mp4v-es",
            "Xvid / DivX" to "video/xvid",
            "Theora" to "video/theora",
            "AAC" to "audio/mp4a-latm",
            "MP3" to "audio/mpeg",
            "FLAC" to "audio/flac",
            "WAV / PCM" to "audio/raw",
            "Opus" to "audio/opus",
            "Vorbis" to "audio/vorbis",
            "AC3" to "audio/ac3",
            "E-AC3" to "audio/eac3",
            "DTS" to "audio/vnd.dts",
            "WMA" to "audio/x-ms-wma",
            "ALAC" to "audio/alac"
        )
        return targets.map { (label, mime) ->
            DecoderCapabilityItem(
                label = label,
                mimeType = mime,
                hardwareSupported = hasHardwareDecoder(mime),
                softwareSupported = hasSoftwareDecoder(mime),
                vlcSupported = isVlcSupportedCodec(mime)
            )
        }
    }
}

package com.example.editor

import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.example.data.model.SubtitleCue
import com.example.data.scanner.SubtitleParser
import kotlin.math.abs

@OptIn(UnstableApi::class)
object VideoEditorSpecBuilder {

    fun evenDimension(px: Int): Int {
        if (px <= 0) return 0
        val clamped = px.coerceIn(128, 3840)
        return if (clamped % 2 == 0) clamped else clamped - 1
    }

    fun normalizeConfig(config: VideoEditorConfig): VideoEditorConfig {
        val dur = config.durationMs.coerceAtLeast(1_000L)
        val trimStart = config.trimStartMs.coerceIn(0L, (dur - 400L).coerceAtLeast(0L))
        val trimEnd = config.trimEndMs.coerceIn((trimStart + 400L).coerceAtMost(dur), dur)

        val cutStart = config.cutStartMs.coerceIn(0L, (dur - 400L).coerceAtLeast(0L))
        val cutEnd = config.cutEndMs.coerceIn((cutStart + 300L).coerceAtMost(dur), dur)

        val splitMin = (dur / 10L).coerceAtMost(400L)
        val splitMax = (dur - splitMin).coerceAtLeast(splitMin + 100L)
        val splitPos = config.splitPositionMs.coerceIn(splitMin, splitMax.coerceAtMost(dur))

        val normalizedRot = (((config.rotationDegrees % 360) + 360) % 360).let {
            (it / 90) * 90
        }

        val w = if (config.resizeWidth > 0) evenDimension(config.resizeWidth) else 0
        val h = if (config.resizeHeight > 0) evenDimension(config.resizeHeight) else 0
        val fps = config.customFrameRate.let { if (it <= 0) 0 else it.coerceIn(1, 120) }

        return config.copy(
            durationMs = dur,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            cutStartMs = cutStart,
            cutEndMs = cutEnd,
            splitPositionMs = splitPos,
            rotationDegrees = normalizedRot,
            resizeWidth = w,
            resizeHeight = h,
            customFrameRate = fps,
            cropInsetLeft = config.cropInsetLeft.coerceIn(0f, 0.42f),
            cropInsetRight = config.cropInsetRight.coerceIn(0f, 0.42f),
            cropInsetTop = config.cropInsetTop.coerceIn(0f, 0.42f),
            cropInsetBottom = config.cropInsetBottom.coerceIn(0f, 0.42f),
            speedMultiplier = config.speedMultiplier.coerceIn(0.25f, 3.0f),
            brightness = config.brightness.coerceIn(-0.6f, 0.6f),
            contrast = config.contrast.coerceIn(-0.6f, 0.6f),
            saturation = config.saturation.coerceIn(-100f, 100f),
            overlayTextSizeScale = config.overlayTextSizeScale.coerceIn(0.4f, 1.5f),
            subtitleDelayMs = config.subtitleDelayMs.coerceIn(-10_000L, 10_000L),
            originalAudioVolume = config.originalAudioVolume.coerceIn(0f, 2.0f),
            selectedAudioTrackIndex = config.selectedAudioTrackIndex.coerceAtLeast(0),
            backgroundMusicVolume = config.backgroundMusicVolume.coerceIn(0.05f, 1.0f)
        )
    }

    fun shouldRemoveOriginalAudio(config: VideoEditorConfig): Boolean {
        return config.muteOriginalAudio || config.originalAudioVolume <= 0.01f
    }

    fun computeCropBounds(config: VideoEditorConfig): CropBounds {
        val safe = normalizeConfig(config)
        val presetAspect = safe.cropPreset.targetAspect
        if (presetAspect != null && safe.sourceWidth > 0 && safe.sourceHeight > 0) {
            val rotatedSwapped = safe.rotationDegrees == 90 || safe.rotationDegrees == 270
            val w = (if (rotatedSwapped) safe.sourceHeight else safe.sourceWidth).toFloat()
            val h = (if (rotatedSwapped) safe.sourceWidth else safe.sourceHeight).toFloat()
            val srcAspect = (w / h).coerceAtLeast(0.1f)
            return if (srcAspect > presetAspect) {
                val halfWidthNdc = (presetAspect / srcAspect).coerceIn(0.2f, 1f)
                CropBounds(left = -halfWidthNdc, right = halfWidthNdc, bottom = -1f, top = 1f)
            } else {
                val halfHeightNdc = (srcAspect / presetAspect).coerceIn(0.2f, 1f)
                CropBounds(left = -1f, right = 1f, bottom = -halfHeightNdc, top = halfHeightNdc)
            }
        }

        var left = -1f + 2f * safe.cropInsetLeft
        var right = 1f - 2f * safe.cropInsetRight
        var bottom = -1f + 2f * safe.cropInsetBottom
        var top = 1f - 2f * safe.cropInsetTop

        if (right - left < 0.16f) {
            val midX = (left + right) / 2f
            left = (midX - 0.08f).coerceIn(-1f, 0.84f)
            right = (left + 0.16f).coerceAtMost(1f)
        }
        if (top - bottom < 0.16f) {
            val midY = (bottom + top) / 2f
            bottom = (midY - 0.08f).coerceIn(-1f, 0.84f)
            top = (bottom + 0.16f).coerceAtMost(1f)
        }
        return CropBounds(left = left, right = right, bottom = bottom, top = top)
    }

    /**
     * Returns clip timestamp segments `(startMs, endMs)` for TRIM, CUT, or SPLIT.
     */
    fun buildSingleVideoSegmentsMs(config: VideoEditorConfig): List<Pair<Long, Long>> {
        val safe = normalizeConfig(config)
        val dur = safe.durationMs
        return when (safe.timelineMode) {
            VideoTimelineMode.TRIM -> listOf(safe.trimStartMs to safe.trimEndMs)
            VideoTimelineMode.CUT -> {
                val segments = mutableListOf<Pair<Long, Long>>()
                if (safe.cutStartMs > 250L) {
                    segments += 0L to safe.cutStartMs
                }
                if (safe.cutEndMs < dur - 250L) {
                    segments += safe.cutEndMs to dur
                }
                if (segments.isEmpty()) listOf(0L to dur) else segments
            }
            VideoTimelineMode.SPLIT -> {
                when (safe.splitExportChoice) {
                    VideoSplitExportChoice.PART_1 -> listOf(0L to safe.splitPositionMs)
                    VideoSplitExportChoice.PART_2 -> listOf(safe.splitPositionMs to dur)
                    VideoSplitExportChoice.BOTH -> listOf(
                        0L to safe.splitPositionMs,
                        safe.splitPositionMs to dur
                    )
                }
            }
            VideoTimelineMode.MERGE -> listOf(0L to dur)
        }
    }

    fun resolveTargetShortSidePx(
        config: VideoEditorConfig,
        requiresUniformSequenceResolution: Boolean
    ): Int? {
        if (config.resizeWidth > 0 && config.resizeHeight > 0) return null
        config.exportResolution.shortSidePx?.let { return it }
        if (!requiresUniformSequenceResolution) return null
        val short = minOf(config.sourceWidth, config.sourceHeight)
        return when {
            short <= 0 -> 720
            short > 1080 -> 1080
            else -> short.coerceAtLeast(360)
        }
    }

    fun buildVideoEffects(
        config: VideoEditorConfig,
        includeOverlays: Boolean,
        segmentStartOffsetMs: Long = 0L,
        targetShortSidePx: Int? = null
    ): List<Effect> {
        val safe = normalizeConfig(config)
        val effects = mutableListOf<Effect>()

        // 1. Crop
        val crop = computeCropBounds(safe)
        if (!crop.isFullFrame) {
            effects += Crop(crop.left, crop.right, crop.bottom, crop.top)
        }

        // 2. Rotate & Flip
        if (safe.rotationDegrees != 0 || safe.flipHorizontal || safe.flipVertical) {
            val scaleX = if (safe.flipHorizontal) -1f else 1f
            val scaleY = if (safe.flipVertical) -1f else 1f
            val ccwDegrees = ((360 - safe.rotationDegrees) % 360).toFloat()
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(scaleX, scaleY)
                .setRotationDegrees(ccwDegrees)
                .build()
        }

        // 3. Brightness, Contrast, Saturation & Filters
        if (abs(safe.brightness) > 0.01f) {
            effects += Brightness(safe.brightness)
        }
        if (abs(safe.contrast) > 0.01f) {
            effects += Contrast(safe.contrast)
        }
        if (abs(safe.saturation) > 1f) {
            effects += HslAdjustment.Builder()
                .adjustSaturation(safe.saturation)
                .build()
        }

        when (safe.filterPreset) {
            VideoFilterPreset.NONE -> Unit
            VideoFilterPreset.VIVID -> {
                effects += HslAdjustment.Builder().adjustSaturation(38f).build()
                effects += Contrast(0.14f)
            }
            VideoFilterPreset.WARM -> effects += WarmRgbMatrix()
            VideoFilterPreset.COOL -> effects += CoolRgbMatrix()
            VideoFilterPreset.GRAYSCALE -> effects += RgbFilter.createGrayscaleFilter()
            VideoFilterPreset.SEPIA -> effects += SepiaRgbMatrix()
            VideoFilterPreset.INVERT -> effects += RgbFilter.createInvertedFilter()
        }

        // 4. Text Overlay (with custom scale) & Subtitle Burn-in (with timing offset)
        if (includeOverlays) {
            val overlays = mutableListOf<TextureOverlay>()
            if (safe.overlayText.isNotBlank()) {
                overlays += buildStaticTextOverlay(
                    text = safe.overlayText.trim(),
                    textColor = safe.overlayTextColorHex.toInt(),
                    anchorY = safe.overlayTextPosition.anchorY,
                    sizeScale = safe.overlayTextSizeScale
                )
            }
            if (safe.subtitleEnabled && safe.subtitleCues.isNotEmpty()) {
                overlays += buildDynamicSubtitleOverlay(
                    cues = safe.subtitleCues,
                    segmentStartOffsetMs = segmentStartOffsetMs,
                    speedMultiplier = safe.speedMultiplier,
                    subtitleDelayMs = safe.subtitleDelayMs
                )
            }
            if (overlays.isNotEmpty()) {
                effects += OverlayEffect(overlays)
            }
        }

        // 5. Custom Resize OR Preset Resolution Scaling
        if (safe.resizeWidth > 0 && safe.resizeHeight > 0) {
            effects += Presentation.createForWidthAndHeight(
                safe.resizeWidth,
                safe.resizeHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        } else if (targetShortSidePx != null && targetShortSidePx > 0) {
            effects += Presentation.createForShortSide(targetShortSidePx)
        }

        return effects
    }

    fun inspectSourceAudioTracks(context: Context, uri: Uri): List<SourceAudioTrackInfo> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val result = mutableListOf<SourceAudioTrackInfo>()
            var audioIdx = 0
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    val lang = runCatching { fmt.getString(MediaFormat.KEY_LANGUAGE) }.getOrNull()
                    val ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                    val sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                    val langLabel = lang?.uppercase()?.takeIf { it != "UND" } ?: "Track ${audioIdx + 1}"
                    val codecShort = mime.substringAfter('/').uppercase()
                    val label = buildString {
                        append(langLabel)
                        append(" • $codecShort ${ch}ch")
                        if (sr > 0) append(" (${sr / 1000}kHz)")
                    }
                    result += SourceAudioTrackInfo(
                        indexInContainer = i,
                        audioOrderIndex = audioIdx,
                        label = label,
                        language = lang,
                        mimeType = mime
                    )
                    audioIdx++
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun buildStaticTextOverlay(
        text: String,
        textColor: Int,
        anchorY: Float,
        sizeScale: Float
    ): TextOverlay {
        val spanned = SpannableString(" $text ").apply {
            setSpan(ForegroundColorSpan(textColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                BackgroundColorSpan(Color.argb(175, 0, 0, 0)),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(RelativeSizeSpan(sizeScale.coerceIn(0.4f, 1.5f)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, anchorY)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
        return TextOverlay.createStaticTextOverlay(spanned, settings)
    }

    private fun buildDynamicSubtitleOverlay(
        cues: List<SubtitleCue>,
        segmentStartOffsetMs: Long,
        speedMultiplier: Float,
        subtitleDelayMs: Long
    ): TextOverlay {
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -0.78f)
            .setOverlayFrameAnchor(0f, 0f)
            .build()

        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val sourceTimeMs = segmentStartOffsetMs +
                    ((presentationTimeUs / 1000.0) * speedMultiplier.toDouble()).toLong()
                val active = SubtitleParser.findCueAt(cues, sourceTimeMs, delayMs = subtitleDelayMs)
                if (active == null || active.text.isBlank()) {
                    return SpannableString(" ").apply {
                        setSpan(
                            ForegroundColorSpan(Color.TRANSPARENT),
                            0,
                            1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                val txt = " ${active.text.trim()} "
                return SpannableString(txt).apply {
                    setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(
                        BackgroundColorSpan(Color.argb(200, 12, 16, 22)),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(RelativeSizeSpan(0.62f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            override fun getOverlaySettings(presentationTimeUs: Long) = settings
        }
    }

    class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed.coerceIn(0.25f, 3.0f)
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private class WarmRgbMatrix : RgbMatrix {
        private val matrix = floatArrayOf(
            1.12f, 0f, 0f, 0f,
            0f, 1.02f, 0f, 0f,
            0f, 0f, 0.86f, 0f,
            0f, 0f, 0f, 1f
        )

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

    private class CoolRgbMatrix : RgbMatrix {
        private val matrix = floatArrayOf(
            0.88f, 0f, 0f, 0f,
            0f, 1.02f, 0f, 0f,
            0f, 0f, 1.15f, 0f,
            0f, 0f, 0f, 1f
        )

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

    private class SepiaRgbMatrix : RgbMatrix {
        private val matrix = floatArrayOf(
            0.393f, 0.349f, 0.272f, 0f,
            0.769f, 0.686f, 0.534f, 0f,
            0.189f, 0.168f, 0.131f, 0f,
            0f, 0f, 0f, 1f
        )

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }
}

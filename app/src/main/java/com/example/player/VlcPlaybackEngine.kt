package com.example.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.model.ActiveDecoderMode
import com.example.data.model.AspectRatioMode
import com.example.data.model.AudioTrackOption
import com.example.data.model.DecoderPreference
import com.example.data.model.SubtitleTrackOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlaybackEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    private val onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    private val onTracksUpdated: (audio: List<AudioTrackOption>, subtitles: List<SubtitleTrackOption>) -> Unit,
    private val onDecoderResolved: (ActiveDecoderMode) -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var attachedLayout: VLCVideoLayout? = null
    private var activePfd: ParcelFileDescriptor? = null

    private var pendingSeekMs: Long = 0L
    private var pendingSpeed: Float = 1.0f
    private var pendingAudioDelayMs: Long = 0L
    private var pendingSubDelayMs: Long = 0L
    private var pendingBoostPercent: Int = 100
    private var pendingPreferredAudioLang: String = "und"
    private var pendingAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    private var preferredAudioAppliedForCurrentMedia = false
    private var lastKnownDurationMs: Long = 0L
    private var currentDecoderPreference: DecoderPreference = DecoderPreference.AUTO

    private fun ensurePlayer(): MediaPlayer? {
        mediaPlayer?.let { return it }
        return runCatching {
            val options = arrayListOf(
                "--audio-time-stretch",
                "--subsdec-encoding=UTF-8",
                "--avcodec-threads=0",
                "--network-caching=1500",
                "--file-caching=500"
            )
            val vlc = LibVLC(context.applicationContext, options)
            val mp = MediaPlayer(vlc)
            mp.setEventListener { event ->
                scope.launch(Dispatchers.Main.immediate) {
                    handleVlcEvent(mp, event)
                }
            }
            libVlc = vlc
            mediaPlayer = mp
            mp
        }.onFailure { err ->
            onError("LibVLC engine initialization failed: ${err.localizedMessage ?: "native error"}")
        }.getOrNull()
    }

    private fun handleVlcEvent(mp: MediaPlayer, event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                onStateChanged(false, true)
            }
            MediaPlayer.Event.Buffering -> {
                val pct = event.buffering
                onStateChanged(mp.isPlaying, pct < 99.5f)
            }
            MediaPlayer.Event.Playing -> {
                onStateChanged(true, false)
                if (pendingSeekMs > 0L) {
                    val target = pendingSeekMs
                    pendingSeekMs = 0L
                    mp.setTime(target, true)
                }
                mp.setRate(pendingSpeed)
                mp.setVolume(pendingBoostPercent.coerceIn(0, 200))
                mp.setAudioDelay(pendingAudioDelayMs * 1000L)
                mp.setSpuDelay(pendingSubDelayMs * 1000L)
                applyAspectRatioToPlayer(mp, pendingAspectRatio)
                emitTracks(mp)
                onDecoderResolved(
                    if (currentDecoderPreference == DecoderPreference.SOFTWARE) {
                        ActiveDecoderMode.SOFTWARE
                    } else {
                        ActiveDecoderMode.HARDWARE
                    }
                )
            }
            MediaPlayer.Event.Paused -> {
                onStateChanged(false, false)
            }
            MediaPlayer.Event.Stopped -> {
                onStateChanged(false, false)
            }
            MediaPlayer.Event.TimeChanged -> {
                val dur = mp.length.takeIf { it > 0L } ?: lastKnownDurationMs
                onProgress(event.timeChanged.coerceAtLeast(0L), dur)
            }
            MediaPlayer.Event.LengthChanged -> {
                val len = event.lengthChanged.coerceAtLeast(0L)
                if (len > 0L) lastKnownDurationMs = len
                onProgress(mp.time.coerceAtLeast(0L), lastKnownDurationMs)
            }
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESSelected,
            MediaPlayer.Event.Vout -> {
                emitTracks(mp)
            }
            MediaPlayer.Event.EndReached -> {
                onStateChanged(false, false)
                onEnded()
            }
            MediaPlayer.Event.EncounteredError -> {
                onStateChanged(false, false)
                onError("LibVLC could not decode or demux this media stream.")
            }
        }
    }

    private fun emitTracks(mp: MediaPlayer) {
        val currentAudioId = mp.audioTrack
        val currentSpuId = mp.spuTrack

        val audioOptions = mp.audioTracks
            ?.filter { it.id != -1 }
            ?.mapIndexed { idx, desc ->
                AudioTrackOption(
                    id = "vlc_aud_${desc.id}",
                    label = desc.name ?: "Audio Track ${idx + 1}",
                    language = desc.name,
                    channels = 2,
                    codec = "LibVLC",
                    groupIndex = VLC_GROUP_SENTINEL,
                    trackIndex = desc.id,
                    isSelected = desc.id == currentAudioId
                )
            }.orEmpty()

        if (!preferredAudioAppliedForCurrentMedia &&
            audioOptions.size > 1 &&
            pendingPreferredAudioLang != "und"
        ) {
            val match = audioOptions.firstOrNull {
                it.label.contains(pendingPreferredAudioLang, ignoreCase = true)
            }
            if (match != null && !match.isSelected) {
                preferredAudioAppliedForCurrentMedia = true
                mp.setAudioTrack(match.trackIndex)
            } else {
                preferredAudioAppliedForCurrentMedia = true
            }
        }

        val subOptions = mp.spuTracks
            ?.filter { it.id != -1 }
            ?.mapIndexed { idx, desc ->
                SubtitleTrackOption(
                    id = "vlc_sub_${desc.id}",
                    label = "${desc.name ?: "Track ${idx + 1}"} (LibVLC)",
                    language = desc.name,
                    isEmbedded = true,
                    groupIndex = VLC_GROUP_SENTINEL,
                    trackIndex = desc.id
                )
            }.orEmpty()

        onTracksUpdated(audioOptions, subOptions)
    }

    fun attachLayout(layout: VLCVideoLayout) {
        val mp = ensurePlayer() ?: return
        if (attachedLayout === layout) return
        runCatching {
            if (attachedLayout != null) {
                mp.detachViews()
            }
            mp.attachViews(layout, null, true, false)
            attachedLayout = layout
            applyAspectRatioToPlayer(mp, pendingAspectRatio)
        }
    }

    fun detachLayout(layout: VLCVideoLayout?) {
        val mp = mediaPlayer ?: return
        if (layout == null || attachedLayout === layout) {
            runCatching { mp.detachViews() }
            attachedLayout = null
        }
    }

    fun playUri(
        uri: Uri,
        startPositionMs: Long,
        decoderPreference: DecoderPreference,
        speed: Float,
        aspectRatio: AspectRatioMode,
        audioDelayMs: Long,
        subDelayMs: Long,
        audioBoostPercent: Int,
        preferredAudioLanguage: String
    ) {
        val mp = ensurePlayer() ?: return
        val vlc = libVlc ?: return

        stopPlaybackOnly()

        currentDecoderPreference = decoderPreference
        pendingSeekMs = startPositionMs.coerceAtLeast(0L)
        pendingSpeed = speed.coerceIn(0.25f, 4.0f)
        pendingAspectRatio = aspectRatio
        pendingAudioDelayMs = audioDelayMs
        pendingSubDelayMs = subDelayMs
        pendingBoostPercent = audioBoostPercent.coerceIn(100, 200)
        pendingPreferredAudioLang = preferredAudioLanguage
        preferredAudioAppliedForCurrentMedia = false
        lastKnownDurationMs = 0L

        val media: Media = try {
            if (uri.scheme == "content") {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    activePfd = pfd
                    Media(vlc, pfd.fileDescriptor)
                } else {
                    Media(vlc, uri)
                }
            } else {
                Media(vlc, uri)
            }
        } catch (err: Exception) {
            onError("Unable to open media descriptor: ${err.localizedMessage ?: "Access denied"}")
            return
        }

        when (decoderPreference) {
            DecoderPreference.AUTO -> media.setHWDecoderEnabled(true, false)
            DecoderPreference.HARDWARE -> media.setHWDecoderEnabled(true, true)
            DecoderPreference.SOFTWARE -> {
                media.setHWDecoderEnabled(false, false)
                media.addOption(":codec=avcodec")
            }
        }

        mp.media = media
        media.release()
        mp.play()
    }

    fun play() {
        mediaPlayer?.play()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun currentPositionMs(): Long = mediaPlayer?.time?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long = mediaPlayer?.length?.takeIf { it > 0L } ?: lastKnownDurationMs

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        val target = positionMs.coerceAtLeast(0L)
        if (mp.hasMedia()) {
            mp.setTime(target, true)
        } else {
            pendingSeekMs = target
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        pendingSpeed = speed.coerceIn(0.25f, 4.0f)
        mediaPlayer?.setRate(pendingSpeed)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        pendingAspectRatio = mode
        mediaPlayer?.let { applyAspectRatioToPlayer(it, mode) }
    }

    private fun applyAspectRatioToPlayer(mp: MediaPlayer, mode: AspectRatioMode) {
        val scaleType = when (mode) {
            AspectRatioMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectRatioMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectRatioMode.CROP -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectRatioMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_16_9
        }
        runCatching { mp.videoScale = scaleType }
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer?.setAudioTrack(trackId)
        mediaPlayer?.let { emitTracks(it) }
    }

    fun selectEmbeddedSubtitleTrack(trackId: Int?) {
        mediaPlayer?.setSpuTrack(trackId ?: -1)
    }

    fun attachExternalSubtitle(uri: Uri) {
        mediaPlayer?.addSlave(IMedia.Slave.Type.Subtitle, uri, true)
    }

    fun setAudioDelayMs(delayMs: Long) {
        pendingAudioDelayMs = delayMs.coerceIn(-10_000L, 10_000L)
        mediaPlayer?.setAudioDelay(pendingAudioDelayMs * 1000L)
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        pendingSubDelayMs = delayMs.coerceIn(-10_000L, 10_000L)
        mediaPlayer?.setSpuDelay(pendingSubDelayMs * 1000L)
    }

    fun setAudioBoostPercent(boostPercent: Int) {
        pendingBoostPercent = boostPercent.coerceIn(100, 200)
        mediaPlayer?.setVolume(pendingBoostPercent)
    }

    fun stopPlaybackOnly() {
        runCatching { mediaPlayer?.stop() }
        runCatching { activePfd?.close() }
        activePfd = null
    }

    fun release() {
        stopPlaybackOnly()
        runCatching { mediaPlayer?.detachViews() }
        attachedLayout = null
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { libVlc?.release() }
        libVlc = null
    }

    companion object {
        const val VLC_GROUP_SENTINEL = -2
    }
}

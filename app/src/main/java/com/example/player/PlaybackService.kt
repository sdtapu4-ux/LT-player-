package com.example.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.model.DecoderPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var currentDecoderPref: DecoderPreference = DecoderPreference.AUTO

    companion object {
        private val _audioSessionId = MutableStateFlow(0)
        val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

        private val _requestedDecoderPref = MutableStateFlow(DecoderPreference.AUTO)
        val requestedDecoderPref: StateFlow<DecoderPreference> = _requestedDecoderPref.asStateFlow()

        fun setDesiredDecoderPreference(pref: DecoderPreference) {
            _requestedDecoderPref.value = pref
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        currentDecoderPref = _requestedDecoderPref.value
        val player = buildConfiguredExoPlayer(this, currentDecoderPref)
        _audioSessionId.value = player.audioSessionId

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        serviceScope.launch {
            _requestedDecoderPref.drop(1).collect { desired ->
                if (desired != currentDecoderPref) {
                    applyDecoderPreferenceSwap(desired)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyDecoderPreferenceSwap(desired: DecoderPreference) {
        val session = mediaSession ?: return
        val oldPlayer = session.player
        val wasPlaying = oldPlayer.isPlaying
        val pos = oldPlayer.currentPosition.coerceAtLeast(0L)
        val idx = oldPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val count = oldPlayer.mediaItemCount
        val items = (0 until count).map { oldPlayer.getMediaItemAt(it) }

        currentDecoderPref = desired
        val newPlayer = buildConfiguredExoPlayer(this, desired)
        if (items.isNotEmpty()) {
            newPlayer.setMediaItems(items, idx.coerceIn(0, items.lastIndex), pos)
            newPlayer.prepare()
            newPlayer.playWhenReady = wasPlaying
        }
        session.player = newPlayer
        _audioSessionId.value = newPlayer.audioSessionId
        oldPlayer.release()
    }

    @OptIn(UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val desired = _requestedDecoderPref.value
        if (desired != currentDecoderPref) {
            applyDecoderPreferenceSwap(desired)
        }
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    private fun buildConfiguredExoPlayer(context: Context, decoderPref: DecoderPreference): ExoPlayer {
        val codecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaultList = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            when (decoderPref) {
                DecoderPreference.AUTO -> defaultList
                DecoderPreference.HARDWARE -> {
                    val hwFirst = defaultList.sortedBy { info -> if (isSoftwareMedia3Codec(info)) 1 else 0 }
                    hwFirst
                }
                DecoderPreference.SOFTWARE -> {
                    val swFirst = defaultList.sortedBy { info -> if (isSoftwareMedia3Codec(info)) 0 else 1 }
                    swFirst
                }
            }
        }

        val extMode = when (decoderPref) {
            DecoderPreference.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderPreference.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderPreference.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(decoderPref != DecoderPreference.HARDWARE)
            .setMediaCodecSelector(codecSelector)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun isSoftwareMedia3Codec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.softwareOnly
        }
        val lower = info.name.lowercase()
        return lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw.")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        _audioSessionId.value = 0
        super.onDestroy()
    }
}

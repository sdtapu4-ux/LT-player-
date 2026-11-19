package com.example.player

import com.example.data.model.ActivePlaybackEngine
import com.example.data.model.DecoderPreference
import com.example.data.model.PlaybackEnginePreference

sealed interface EngineFallbackDecision {
    data class SwitchEngine(
        val nextEngine: ActivePlaybackEngine,
        val nextDecoder: DecoderPreference,
        val reason: String
    ) : EngineFallbackDecision

    data class RetryWithSoftwareDecoder(
        val engine: ActivePlaybackEngine,
        val reason: String
    ) : EngineFallbackDecision

    data class TerminalFailure(
        val reason: String
    ) : EngineFallbackDecision
}

/**
 * Pure-Kotlin deterministic policy that governs engine selection, hardware->software decoder
 * fallback, and automatic Media3 -> LibVLC switching while strictly preventing infinite retry loops.
 */
object PlaybackEnginePolicy {

    fun selectInitialEngine(preference: PlaybackEnginePreference): ActivePlaybackEngine {
        return when (preference) {
            PlaybackEnginePreference.AUTO -> ActivePlaybackEngine.MEDIA3
            PlaybackEnginePreference.MEDIA3 -> ActivePlaybackEngine.MEDIA3
            PlaybackEnginePreference.VLC -> ActivePlaybackEngine.VLC
        }
    }

    fun decideOnFailure(
        preference: PlaybackEnginePreference,
        failedEngine: ActivePlaybackEngine,
        attemptedEngines: Set<ActivePlaybackEngine>,
        decoderPreference: DecoderPreference,
        softwareFallbackAlreadyTriedInEngine: Boolean,
        isDecoderInitFailure: Boolean
    ): EngineFallbackDecision {
        // 1. If decoder initialization failed on HW/Auto inside current engine and SW hasn't been tried yet
        // when forced to a single engine (MEDIA3 or VLC), retry Software decoding in that engine first.
        if (preference != PlaybackEnginePreference.AUTO &&
            isDecoderInitFailure &&
            decoderPreference == DecoderPreference.AUTO &&
            !softwareFallbackAlreadyTriedInEngine
        ) {
            return EngineFallbackDecision.RetryWithSoftwareDecoder(
                engine = failedEngine,
                reason = "Hardware decoder failed on ${failedEngine.label}; retrying software decoder."
            )
        }

        // 2. In AUTO mode, Media3 tries first and falls back to LibVLC exactly once per load.
        if (preference == PlaybackEnginePreference.AUTO &&
            failedEngine == ActivePlaybackEngine.MEDIA3 &&
            ActivePlaybackEngine.VLC !in attemptedEngines
        ) {
            return EngineFallbackDecision.SwitchEngine(
                nextEngine = ActivePlaybackEngine.VLC,
                nextDecoder = decoderPreference,
                reason = "Media3 ExoPlayer could not play this media; automatically switched to LibVLC."
            )
        }

        // 3. Inside LibVLC in AUTO mode: if HW decoder fails and SW hasn't been tried yet, fall back to SW decoding inside LibVLC.
        if (preference == PlaybackEnginePreference.AUTO &&
            failedEngine == ActivePlaybackEngine.VLC &&
            isDecoderInitFailure &&
            decoderPreference == DecoderPreference.AUTO &&
            !softwareFallbackAlreadyTriedInEngine
        ) {
            return EngineFallbackDecision.RetryWithSoftwareDecoder(
                engine = ActivePlaybackEngine.VLC,
                reason = "Hardware decoding failed in LibVLC; falling back to LibVLC software decoding."
            )
        }

        // 4. Loop prevention: never bounce back to Media3 after LibVLC already failed.
        return EngineFallbackDecision.TerminalFailure(
            reason = if (ActivePlaybackEngine.VLC in attemptedEngines) {
                "All configured playback engines (${attemptedEngines.joinToString(" -> ") { it.label }}) failed."
            } else {
                "Selected engine ${failedEngine.label} failed to play this file."
            }
        )
    }
}

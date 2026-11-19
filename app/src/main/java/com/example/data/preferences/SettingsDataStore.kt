package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.AspectRatioMode
import com.example.data.model.DecoderPreference
import com.example.data.model.PlaybackEnginePreference
import com.example.data.model.SubtitlePreferences
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ltDataStore: DataStore<Preferences> by preferencesDataStore(name = "lt_player_prefs")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_INDEX = intPreferencesKey("avatar_index")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PRIVACY_HIDE_THUMBS = booleanPreferencesKey("privacy_hide_thumbs")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        val SUB_ENABLED = booleanPreferencesKey("sub_enabled")
        val SUB_FONT_SIZE = floatPreferencesKey("sub_font_size")
        val SUB_TEXT_COLOR = longPreferencesKey("sub_text_color")
        val SUB_BG_OPACITY = floatPreferencesKey("sub_bg_opacity")
        val SUB_BOTTOM_PADDING = floatPreferencesKey("sub_bottom_padding")
        val SUB_DELAY_MS = longPreferencesKey("sub_delay_ms")
        val SUB_ENCODING = stringPreferencesKey("sub_encoding")

        val ENGINE_PREFERENCE = stringPreferencesKey("engine_preference")
        val DECODER_PREFERENCE = stringPreferencesKey("decoder_preference")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val AUDIO_DELAY_MS = longPreferencesKey("audio_delay_ms")
        val AUDIO_BOOST_PERCENT = intPreferencesKey("audio_boost_percent")
    }

    val settingsFlow: Flow<AppSettings> = context.ltDataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "LT Creator",
            avatarIndex = prefs[Keys.AVATAR_INDEX] ?: 0,
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            defaultAspectRatio = runCatching {
                AspectRatioMode.valueOf(prefs[Keys.DEFAULT_ASPECT_RATIO] ?: AspectRatioMode.FIT.name)
            }.getOrDefault(AspectRatioMode.FIT),
            gestureBrightnessEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
            gestureVolumeEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
            gestureSeekEnabled = prefs[Keys.GESTURE_SEEK] ?: true,
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
            backgroundAudioEnabled = prefs[Keys.BACKGROUND_AUDIO] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            privacyHideRecentThumbs = prefs[Keys.PRIVACY_HIDE_THUMBS] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            subtitlePrefs = SubtitlePreferences(
                enabled = prefs[Keys.SUB_ENABLED] ?: true,
                fontSizeSp = prefs[Keys.SUB_FONT_SIZE] ?: 18f,
                textColorHex = prefs[Keys.SUB_TEXT_COLOR] ?: 0xFFFFFFFF,
                backgroundOpacity = prefs[Keys.SUB_BG_OPACITY] ?: 0.55f,
                verticalBottomPaddingDp = prefs[Keys.SUB_BOTTOM_PADDING] ?: 56f,
                delayMs = prefs[Keys.SUB_DELAY_MS] ?: 0L,
                encoding = prefs[Keys.SUB_ENCODING] ?: "UTF-8"
            ),
            enginePreference = runCatching {
                PlaybackEnginePreference.valueOf(
                    prefs[Keys.ENGINE_PREFERENCE] ?: PlaybackEnginePreference.AUTO.name
                )
            }.getOrDefault(PlaybackEnginePreference.AUTO),
            decoderPreference = runCatching {
                DecoderPreference.valueOf(
                    prefs[Keys.DECODER_PREFERENCE] ?: DecoderPreference.AUTO.name
                )
            }.getOrDefault(DecoderPreference.AUTO),
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "und",
            audioDelayMs = (prefs[Keys.AUDIO_DELAY_MS] ?: 0L).coerceIn(-10_000L, 10_000L),
            audioBoostPercent = (prefs[Keys.AUDIO_BOOST_PERCENT] ?: 100).coerceIn(100, 200)
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.ltDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateLanguage(code: String) {
        context.ltDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun updateProfile(name: String, avatarIndex: Int) {
        context.ltDataStore.edit {
            it[Keys.DISPLAY_NAME] = name.ifBlank { "LT Creator" }
            it[Keys.AVATAR_INDEX] = avatarIndex
        }
    }

    suspend fun updatePlayerDefaults(
        speed: Float? = null,
        aspectRatio: AspectRatioMode? = null,
        gestureBrightness: Boolean? = null,
        gestureVolume: Boolean? = null,
        gestureSeek: Boolean? = null,
        autoPlayNext: Boolean? = null,
        keepScreenAwake: Boolean? = null,
        backgroundAudio: Boolean? = null,
        notificationsEnabled: Boolean? = null,
        privacyHideThumbs: Boolean? = null
    ) {
        context.ltDataStore.edit { prefs ->
            speed?.let { prefs[Keys.DEFAULT_SPEED] = it }
            aspectRatio?.let { prefs[Keys.DEFAULT_ASPECT_RATIO] = it.name }
            gestureBrightness?.let { prefs[Keys.GESTURE_BRIGHTNESS] = it }
            gestureVolume?.let { prefs[Keys.GESTURE_VOLUME] = it }
            gestureSeek?.let { prefs[Keys.GESTURE_SEEK] = it }
            autoPlayNext?.let { prefs[Keys.AUTO_PLAY_NEXT] = it }
            keepScreenAwake?.let { prefs[Keys.KEEP_SCREEN_AWAKE] = it }
            backgroundAudio?.let { prefs[Keys.BACKGROUND_AUDIO] = it }
            notificationsEnabled?.let { prefs[Keys.NOTIFICATIONS_ENABLED] = it }
            privacyHideThumbs?.let { prefs[Keys.PRIVACY_HIDE_THUMBS] = it }
        }
    }

    suspend fun updateCompatibilitySettings(
        enginePreference: PlaybackEnginePreference? = null,
        decoderPreference: DecoderPreference? = null,
        preferredAudioLanguage: String? = null,
        audioDelayMs: Long? = null,
        audioBoostPercent: Int? = null
    ) {
        context.ltDataStore.edit { prefs ->
            enginePreference?.let { prefs[Keys.ENGINE_PREFERENCE] = it.name }
            decoderPreference?.let { prefs[Keys.DECODER_PREFERENCE] = it.name }
            preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            audioDelayMs?.let { prefs[Keys.AUDIO_DELAY_MS] = it.coerceIn(-10_000L, 10_000L) }
            audioBoostPercent?.let { prefs[Keys.AUDIO_BOOST_PERCENT] = it.coerceIn(100, 200) }
        }
    }

    suspend fun updateSubtitlePreferences(subPrefs: SubtitlePreferences) {
        context.ltDataStore.edit { prefs ->
            prefs[Keys.SUB_ENABLED] = subPrefs.enabled
            prefs[Keys.SUB_FONT_SIZE] = subPrefs.fontSizeSp
            prefs[Keys.SUB_TEXT_COLOR] = subPrefs.textColorHex
            prefs[Keys.SUB_BG_OPACITY] = subPrefs.backgroundOpacity
            prefs[Keys.SUB_BOTTOM_PADDING] = subPrefs.verticalBottomPaddingDp
            prefs[Keys.SUB_DELAY_MS] = subPrefs.delayMs
            prefs[Keys.SUB_ENCODING] = subPrefs.encoding
        }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.ltDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun resetAllSettings() {
        context.ltDataStore.edit { prefs ->
            val onboarded = prefs[Keys.ONBOARDING_DONE] ?: true
            prefs.clear()
            prefs[Keys.ONBOARDING_DONE] = onboarded
        }
    }
}

package com.bruma.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val videoQuality: Int = 1080,
    val videoFormat: String = "MP4",
    val audioFormat: String = "MP3",
    val audioQuality: Int = 320,
    val maxConcurrent: Int = 3,
    val wifiOnly: Boolean = false,
    val pauseLowBattery: Boolean = true,
    val embedMetadata: Boolean = true,
    val cropCover: Boolean = true,
    val clipboard: Boolean = true,
    val folder: String = "Bruma",
    val theme: String = "SYSTEM",
    val palette: String = "BRUMA",
    val reduceTransparency: Boolean = false,
    val onboardingDone: Boolean = false,
    val notifAsked: Boolean = false,
    val engineVersion: String = "",
    val engineCheckedAt: Long = 0,
)

class SettingsRepository(
    private val context: Context,
) {
    companion object Keys {
        val VIDEO_QUALITY = intPreferencesKey("video_quality")
        val VIDEO_FORMAT = stringPreferencesKey("video_format")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val AUDIO_QUALITY = intPreferencesKey("audio_quality")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val PAUSE_LOW_BATTERY = booleanPreferencesKey("pause_low_battery")
        val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
        val CROP_COVER = booleanPreferencesKey("crop_cover")
        val CLIPBOARD = booleanPreferencesKey("clipboard")
        val FOLDER = stringPreferencesKey("folder")
        val THEME = stringPreferencesKey("theme")
        val PALETTE = stringPreferencesKey("palette")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NOTIF_ASKED = booleanPreferencesKey("notif_asked")
        val ENGINE_VERSION = stringPreferencesKey("engine_version")
        val ENGINE_CHECKED_AT = longPreferencesKey("engine_checked_at")
    }

    val flow: Flow<AppSettings> =
        context.dataStore.data.map { p ->
            val d = AppSettings()
            AppSettings(
                videoQuality = p[VIDEO_QUALITY] ?: d.videoQuality,
                videoFormat = p[VIDEO_FORMAT] ?: d.videoFormat,
                audioFormat = p[AUDIO_FORMAT] ?: d.audioFormat,
                audioQuality = p[AUDIO_QUALITY] ?: d.audioQuality,
                maxConcurrent = p[MAX_CONCURRENT] ?: d.maxConcurrent,
                wifiOnly = p[WIFI_ONLY] ?: d.wifiOnly,
                pauseLowBattery = p[PAUSE_LOW_BATTERY] ?: d.pauseLowBattery,
                embedMetadata = p[EMBED_METADATA] ?: d.embedMetadata,
                cropCover = p[CROP_COVER] ?: d.cropCover,
                clipboard = p[CLIPBOARD] ?: d.clipboard,
                folder = p[FOLDER] ?: d.folder,
                theme = p[THEME] ?: d.theme,
                palette = p[PALETTE] ?: d.palette,
                reduceTransparency = p[REDUCE_TRANSPARENCY] ?: d.reduceTransparency,
                onboardingDone = p[ONBOARDING_DONE] ?: d.onboardingDone,
                notifAsked = p[NOTIF_ASKED] ?: d.notifAsked,
                engineVersion = p[ENGINE_VERSION] ?: d.engineVersion,
                engineCheckedAt = p[ENGINE_CHECKED_AT] ?: d.engineCheckedAt,
            )
        }

    suspend fun current(): AppSettings = flow.first()

    suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        context.dataStore.edit { it[key] = value }
    }
}

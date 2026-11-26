package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ExportCodec
import com.example.domain.model.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.editorDataStore by preferencesDataStore(name = "kinocut_prefs")

data class StudioSettings(
    val snapToGrid: Boolean = true,
    val showRuleOfThirds: Boolean = false,
    val showAudioMeters: Boolean = true,
    val defaultResolution: ExportResolution = ExportResolution.FHD_1080P,
    val defaultFps: Int = 60,
    val defaultCodec: ExportCodec = ExportCodec.H264
)

class EditorPreferences(private val context: Context) {
    private object Keys {
        val SNAP_TO_GRID = booleanPreferencesKey("snap_to_grid")
        val RULE_OF_THIRDS = booleanPreferencesKey("rule_of_thirds")
        val AUDIO_METERS = booleanPreferencesKey("audio_meters")
        val DEFAULT_RES = stringPreferencesKey("default_res")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val DEFAULT_CODEC = stringPreferencesKey("default_codec")
    }

    val settingsFlow: Flow<StudioSettings> = context.editorDataStore.data.map { prefs ->
        StudioSettings(
            snapToGrid = prefs[Keys.SNAP_TO_GRID] ?: true,
            showRuleOfThirds = prefs[Keys.RULE_OF_THIRDS] ?: false,
            showAudioMeters = prefs[Keys.AUDIO_METERS] ?: true,
            defaultResolution = prefs[Keys.DEFAULT_RES]?.let {
                runCatching { ExportResolution.valueOf(it) }.getOrNull()
            } ?: ExportResolution.FHD_1080P,
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 60,
            defaultCodec = prefs[Keys.DEFAULT_CODEC]?.let {
                runCatching { ExportCodec.valueOf(it) }.getOrNull()
            } ?: ExportCodec.H264
        )
    }

    suspend fun setSnapToGrid(enabled: Boolean) {
        context.editorDataStore.edit { it[Keys.SNAP_TO_GRID] = enabled }
    }

    suspend fun setRuleOfThirds(enabled: Boolean) {
        context.editorDataStore.edit { it[Keys.RULE_OF_THIRDS] = enabled }
    }

    suspend fun setShowAudioMeters(enabled: Boolean) {
        context.editorDataStore.edit { it[Keys.AUDIO_METERS] = enabled }
    }

    suspend fun setExportDefaults(resolution: ExportResolution, fps: Int, codec: ExportCodec) {
        context.editorDataStore.edit {
            it[Keys.DEFAULT_RES] = resolution.name
            it[Keys.DEFAULT_FPS] = fps
            it[Keys.DEFAULT_CODEC] = codec.name
        }
    }
}

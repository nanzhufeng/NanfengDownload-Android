package com.nanzhufeng.videodownloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class AppSettings(
    val defaultResolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
    val customTreeUri: String? = null,
    val inputDraft: String = "",
)

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultResolution(value: ResolutionPreset)

    suspend fun saveInputDraft(value: String)
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class PreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSettings() }

    override suspend fun setDefaultResolution(value: ResolutionPreset) {
        dataStore.edit { preferences -> preferences[DEFAULT_RESOLUTION] = value.name }
    }

    override suspend fun saveInputDraft(value: String) {
        dataStore.edit { preferences -> preferences[INPUT_DRAFT] = value }
    }

    private fun Preferences.toSettings() = AppSettings(
        defaultResolution = this[DEFAULT_RESOLUTION]
            ?.let { runCatching { ResolutionPreset.valueOf(it) }.getOrNull() }
            ?: ResolutionPreset.UP_TO_720P,
        customTreeUri = this[CUSTOM_TREE_URI],
        inputDraft = this[INPUT_DRAFT].orEmpty(),
    )

    private companion object {
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val CUSTOM_TREE_URI = stringPreferencesKey("custom_tree_uri")
        val INPUT_DRAFT = stringPreferencesKey("input_draft")
    }
}

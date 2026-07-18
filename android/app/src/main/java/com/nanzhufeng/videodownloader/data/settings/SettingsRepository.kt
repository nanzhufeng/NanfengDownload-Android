package com.nanzhufeng.videodownloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class FileNameRule {
    DATE_AND_TITLE,
    TITLE_ONLY,
    CREATOR_AND_TITLE,
}

data class AppSettings(
    val defaultResolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
    val customTreeUri: String? = null,
    val customTreeName: String? = null,
    val inputDraft: String = "",
    val autoResumeNetwork: Boolean = true,
    val maxConcurrentDownloads: Int = 1,
    val fileNameRule: FileNameRule = FileNameRule.DATE_AND_TITLE,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultResolution(value: ResolutionPreset)

    suspend fun saveInputDraft(value: String)

    suspend fun setAutoResumeNetwork(value: Boolean) = Unit

    suspend fun setMaxConcurrentDownloads(value: Int) = Unit

    suspend fun setFileNameRule(value: FileNameRule) = Unit

    suspend fun setCustomTree(uri: String?, displayName: String?) = Unit
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

    override suspend fun setAutoResumeNetwork(value: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_RESUME_NETWORK] = value }
    }

    override suspend fun setMaxConcurrentDownloads(value: Int) {
        require(value in 1..3) { "同时下载任务数必须在 1 到 3 之间" }
        dataStore.edit { preferences -> preferences[MAX_CONCURRENT_DOWNLOADS] = value }
    }

    override suspend fun setFileNameRule(value: FileNameRule) {
        dataStore.edit { preferences -> preferences[FILE_NAME_RULE] = value.name }
    }

    override suspend fun setCustomTree(uri: String?, displayName: String?) {
        dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(CUSTOM_TREE_URI)
                preferences.remove(CUSTOM_TREE_NAME)
            } else {
                preferences[CUSTOM_TREE_URI] = uri
                preferences[CUSTOM_TREE_NAME] = displayName.orEmpty()
            }
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        defaultResolution = this[DEFAULT_RESOLUTION]
            ?.let { runCatching { ResolutionPreset.valueOf(it) }.getOrNull() }
            ?: ResolutionPreset.UP_TO_720P,
        customTreeUri = this[CUSTOM_TREE_URI],
        customTreeName = this[CUSTOM_TREE_NAME]?.takeIf(String::isNotBlank),
        inputDraft = this[INPUT_DRAFT].orEmpty(),
        autoResumeNetwork = this[AUTO_RESUME_NETWORK] ?: true,
        maxConcurrentDownloads = (this[MAX_CONCURRENT_DOWNLOADS] ?: 1).coerceIn(1, 3),
        fileNameRule = this[FILE_NAME_RULE]
            ?.let { runCatching { FileNameRule.valueOf(it) }.getOrNull() }
            ?: FileNameRule.DATE_AND_TITLE,
    )

    private companion object {
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val CUSTOM_TREE_URI = stringPreferencesKey("custom_tree_uri")
        val CUSTOM_TREE_NAME = stringPreferencesKey("custom_tree_name")
        val INPUT_DRAFT = stringPreferencesKey("input_draft")
        val AUTO_RESUME_NETWORK = booleanPreferencesKey("auto_resume_network")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val FILE_NAME_RULE = stringPreferencesKey("file_name_rule")
    }
}

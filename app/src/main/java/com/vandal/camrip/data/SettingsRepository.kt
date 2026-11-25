package com.vandal.camrip.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val qualityKey = stringPreferencesKey("export_quality")
    private val themeKey = stringPreferencesKey("theme")
    private val accentKey = stringPreferencesKey("accent")

    val quality: Flow<String> = context.settingsDataStore.data.map { it[qualityKey] ?: "1080p" }
    val theme: Flow<String> = context.settingsDataStore.data.map { it[themeKey] ?: "dark" }
    val accent: Flow<String> = context.settingsDataStore.data.map { it[accentKey] ?: "pink" }

    suspend fun setQuality(value: String) { context.settingsDataStore.edit { it[qualityKey] = value } }
    suspend fun setTheme(value: String) { context.settingsDataStore.edit { it[themeKey] = value } }
    suspend fun setAccent(value: String) { context.settingsDataStore.edit { it[accentKey] = value } }

    fun clearCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

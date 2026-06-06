package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingsDao: SettingsDao) {

    // Always returns a non-null flow containing either the saved config or default settings
    val settingsFlow: Flow<AppSettings> = settingsDao.getSettingsFlow()
        .map { it ?: AppSettings() }

    suspend fun getSettingsDirect(): AppSettings {
        return settingsDao.getSettingsDirect() ?: AppSettings()
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        settingsDao.updateSettings(update)
    }

    suspend fun setServiceRunning(running: Boolean) {
        settingsDao.updateSettings { it.copy(isServiceRunning = running) }
    }
}

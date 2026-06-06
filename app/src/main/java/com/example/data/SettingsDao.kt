package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    @Transaction
    suspend fun updateSettings(updateBlock: (AppSettings) -> AppSettings) {
        val current = getSettingsDirect() ?: AppSettings()
        val updated = updateBlock(current)
        saveSettings(updated)
    }
}

package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.service.VolumeService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SettingsRepository

    val settingsState: StateFlow<AppSettings>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SettingsRepository(database.settingsDao())
        settingsState = repository.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppSettings()
            )
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(selectedTheme = themeName) }
        }
    }

    fun updateButtonSize(size: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(buttonSize = size) }
        }
    }

    fun updateDockedButtonSize(size: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dockedButtonSize = size) }
        }
    }

    fun updateOpacityActive(opacity: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(opacityActive = opacity) }
        }
    }

    fun updateOpacityIdle(opacity: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(opacityIdle = opacity) }
        }
    }

    fun updateHaptic(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(hapticFeedback = enabled) }
        }
    }

    fun updateHideToCornerWhenIdle(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(hideToCornerWhenIdle = enabled) }
        }
    }

    fun updateIdleTimeout(seconds: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(idleTimeoutSeconds = seconds) }
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun toggleService(context: Context) {
        val app = getApplication<Application>()
        val isRunning = settingsState.value.isServiceRunning
        val intent = Intent(app, VolumeService::class.java)

        if (isRunning) {
            app.stopService(intent)
            viewModelScope.launch {
                repository.setServiceRunning(false)
            }
        } else {
            if (isOverlayPermissionGranted(context)) {
                app.startService(intent)
                viewModelScope.launch {
                    repository.setServiceRunning(true)
                }
            }
        }
    }

    fun startServiceIfPermitted(context: Context) {
        val app = getApplication<Application>()
        if (isOverlayPermissionGranted(context)) {
            val intent = Intent(app, VolumeService::class.java)
            app.startService(intent)
            viewModelScope.launch {
                repository.setServiceRunning(true)
            }
        }
    }

    fun getOverlayPermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
    }
}

package com.claw.passvault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claw.passvault.security.BiometricAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val darkMode: String = "system", // "system", "light", "dark"
    val biometricEnabled: Boolean = false,
    val hasBiometric: Boolean = false,
    val driveBackupEnabled: Boolean = false,
    val isDriveSignedIn: Boolean = false,
    val autoLockMinutes: Int = 5,
    val showChangePassword: Boolean = false,
    val passwordChanged: Boolean = false,
    val passwordChangeError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.value = _state.value.copy(
            darkMode = prefs.getString("dark_mode", "system") ?: "system",
            biometricEnabled = BiometricAuthManager.isBiometricEnabled(application),
            hasBiometric = BiometricAuthManager.canUseBiometric(application),
            driveBackupEnabled = prefs.getBoolean("drive_backup_enabled", false),
            autoLockMinutes = prefs.getInt("auto_lock_minutes", 5)
        )
    }

    fun setDarkMode(mode: String) {
        prefs.edit().putString("dark_mode", mode).apply()
        _state.value = _state.value.copy(darkMode = mode)
    }

    fun toggleBiometric(enabled: Boolean) {
        BiometricAuthManager.setBiometricEnabled(application, enabled)
        _state.value = _state.value.copy(biometricEnabled = enabled)
    }

    fun setDriveBackup(enabled: Boolean) {
        prefs.edit().putBoolean("drive_backup_enabled", enabled).apply()
        _state.value = _state.value.copy(driveBackupEnabled = enabled)
    }

    fun setAutoLock(minutes: Int) {
        prefs.edit().putInt("auto_lock_minutes", minutes).apply()
        _state.value = _state.value.copy(autoLockMinutes = minutes)
    }

    fun updateDriveSignedIn(signedIn: Boolean) {
        _state.value = _state.value.copy(isDriveSignedIn = signedIn)
    }
}

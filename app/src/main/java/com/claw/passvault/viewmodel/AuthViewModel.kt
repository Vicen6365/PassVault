package com.claw.passvault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claw.passvault.security.BiometricAuthManager
import com.claw.passvault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

data class AuthState(
    val isAuthenticated: Boolean = false,
    val isFirstRun: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasBiometric: Boolean = false,
    val biometricEnabled: Boolean = false,
    val showBiometricPrompt: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        _state.value = _state.value.copy(
            isFirstRun = !prefs.getBoolean("vault_initialized", false),
            isAuthenticated = false,
            hasBiometric = BiometricAuthManager.canUseBiometric(application),
            biometricEnabled = BiometricAuthManager.isBiometricEnabled(application)
        )
    }

    fun createVault(masterPassword: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    CryptoManager.generateAndStoreVaultKey()
                    CryptoManager.generateBiometricKey()

                    // Store a verification token to check master password later
                    val derivedKey = CryptoManager.deriveKey(masterPassword)
                    val verificationToken = CryptoManager.encrypt("PASSVAULT_OK", derivedKey)
                    prefs.edit()
                        .putBoolean("vault_initialized", true)
                        .putString("verification_token", verificationToken)
                        .apply()
                }
                _state.value = _state.value.copy(
                    isFirstRun = false,
                    isAuthenticated = true,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error al crear la bóveda: ${e.message}"
                )
            }
        }
    }

    fun unlock(masterPassword: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val success = withContext(Dispatchers.IO) {
                    val token = prefs.getString("verification_token", null) ?: return@withContext false
                    val derivedKey = CryptoManager.deriveKey(masterPassword)
                    try {
                        val decrypted = CryptoManager.decrypt(token, derivedKey)
                        decrypted == "PASSVAULT_OK"
                    } catch (e: Exception) {
                        false
                    }
                }
                if (success) {
                    val vaultKey = CryptoManager.getVaultKey()
                    _state.value = _state.value.copy(isAuthenticated = true, isLoading = false)
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Contraseña incorrecta"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun showBiometric() {
        _state.value = _state.value.copy(showBiometricPrompt = true)
    }

    fun biometricAuthenticated() {
        _state.value = _state.value.copy(
            isAuthenticated = true,
            showBiometricPrompt = false
        )
    }

    fun biometricFailed() {
        _state.value = _state.value.copy(showBiometricPrompt = false)
    }

    fun lock() {
        CryptoManager.clearCache()
        _state.value = _state.value.copy(isAuthenticated = false)
    }

    fun toggleBiometric(enabled: Boolean) {
        BiometricAuthManager.setBiometricEnabled(application, enabled)
        _state.value = _state.value.copy(biometricEnabled = enabled)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

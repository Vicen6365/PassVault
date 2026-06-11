package com.claw.passvault.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Manages biometric authentication (fingerprint, face, etc.)
 */
object BiometricAuthManager {
    private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"

    fun canUseBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        // Try STRONG first (fingerprint/iris), fallback to WEAK (face, etc.)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS ||
                biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Desbloquear PassVault",
        subtitle: String = "Usa tu huella para desbloquear",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Usar contraseña")
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            })

        try {
            val crypto = CryptoManager.getBiometricKey()
            if (crypto != null) {
                // Use crypto-based auth (more secure)
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, crypto)
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                // Fallback: auth without crypto
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometric key invalidated (e.g., new fingerprint enrolled)
            CryptoManager.generateBiometricKey()
            onError("Huella cambiada. Inicia sesión con contraseña primero.")
        } catch (e: Exception) {
            // No biometric key yet, authenticate without crypto
            biometricPrompt.authenticate(promptInfo)
        }
    }
}

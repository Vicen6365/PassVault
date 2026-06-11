package com.claw.passvault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles all cryptographic operations for PassVault.
 * Uses AES-256-GCM for encryption and PBKDF2 for key derivation.
 */
object CryptoManager {
    private const val KEYSTORE_ALIAS = "passvault_vault_key"
    private const val KEY_ALIAS_AUTH = "passvault_auth_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12   // bytes
    private const val PBKDF2_ITERATIONS = 600_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT = "PassVault_Secure_Salt_2026" // In production, use per-user random salt

    private var cachedVaultKey: SecretKey? = null

    /** Derive a key from master password using PBKDF2 */
    fun deriveKey(password: String): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(),
            SALT.toByteArray(),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Encrypt plaintext with AES-256-GCM. Returns Base64(iv + ciphertext) */
    fun encrypt(plaintext: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // Random 12-byte IV
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Prepend IV to ciphertext
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Decrypt Base64(iv + ciphertext) with AES-256-GCM */
    fun decrypt(encryptedData: String, key: SecretKey): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** Generate a random AES-256 key stored in Android Keystore */
    fun generateAndStoreVaultKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // vault key doesn't require auth
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    /** Get vault key from Keystore */
    fun getVaultKey(): SecretKey? {
        return cachedVaultKey ?: run {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(KEYSTORE_ALIAS, null)?.let { key ->
                (key as? SecretKey)?.also { cachedVaultKey = it }
            }
        }
    }

    /** Generate a biometric-bound key for secure unlock */
    fun generateBiometricKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_AUTH,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1) // Every time
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    /** Get biometric-bound key (will prompt for auth when used) */
    fun getBiometricKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS_AUTH, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    /** Check if vault key exists */
    fun hasVaultKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /** Check if biometric key exists */
    fun hasBiometricKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias(KEY_ALIAS_AUTH)
        } catch (e: Exception) {
            false
        }
    }

    /** Generate a random password for the generator */
    fun generatePassword(length: Int = 20, includeSpecial: Boolean = true): String {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digits = "0123456789"
        val special = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val chars = lower + upper + digits + if (includeSpecial) special else ""
        val secureRandom = java.security.SecureRandom()

        // Ensure at least one of each type
        val sb = StringBuilder()
        sb.append(lower[secureRandom.nextInt(lower.length)])
        sb.append(upper[secureRandom.nextInt(upper.length)])
        sb.append(digits[secureRandom.nextInt(digits.length)])
        if (includeSpecial) sb.append(special[secureRandom.nextInt(special.length)])

        for (i in sb.length until length) {
            sb.append(chars[secureRandom.nextInt(chars.length)])
        }

        // Shuffle using SecureRandom
        val result = sb.toString().toMutableList()
        for (i in result.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result.joinToString("")
    }

    fun clearCache() {
        cachedVaultKey = null
    }
}

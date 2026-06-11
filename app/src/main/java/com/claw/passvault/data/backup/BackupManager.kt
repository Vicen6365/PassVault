package com.claw.passvault.data.backup

import android.content.Context
import android.util.Base64
import com.claw.passvault.data.model.PasswordEntry
import com.claw.passvault.security.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey

/**
 * Handles backup/restore to local files and Google Drive.
 * All backups are encrypted with the vault key before upload.
 */
class BackupManager(private val context: Context) {

    private val backupFile = File(context.filesDir, "passvault_backup.enc")

    /** Create an encrypted JSON backup file */
    fun createLocalBackup(entries: List<PasswordEntry>, key: SecretKey): File {
        val json = JSONArray()
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("title", entry.title)
                put("username", entry.username)
                put("password", entry.password)
                put("notes", entry.notes)
                put("category", entry.category)
                put("createdAt", entry.createdAt)
                put("updatedAt", entry.updatedAt)
            }
            json.put(obj)
        }

        val plaintext = json.toString()
        val encrypted = CryptoManager.encrypt(plaintext, key)
        backupFile.writeText(encrypted)
        return backupFile
    }

    /** Restore entries from an encrypted local backup */
    fun restoreLocalBackup(key: SecretKey): List<PasswordEntry>? {
        if (!backupFile.exists()) return null
        return try {
            val encrypted = backupFile.readText()
            val plaintext = CryptoManager.decrypt(encrypted, key)
            val json = JSONArray(plaintext)
            val entries = mutableListOf<PasswordEntry>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                entries.add(PasswordEntry(
                    title = obj.getString("title"),
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    notes = obj.optString("notes", ""),
                    category = obj.optString("category", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
            entries
        } catch (e: Exception) {
            null
        }
    }

    /** Create encrypted backup data suitable for Drive upload */
    fun createBackupData(entries: List<PasswordEntry>, key: SecretKey): String {
        val json = JSONArray()
        for (entry in entries) {
            json.put(JSONObject().apply {
                put("title", entry.title)
                put("username", entry.username)
                put("password", entry.password)
                put("notes", entry.notes)
                put("category", entry.category)
                put("createdAt", entry.createdAt)
                put("updatedAt", entry.updatedAt)
            })
        }
        return CryptoManager.encrypt(json.toString(), key)
    }

    /** Restore entries from encrypted backup data (from Drive) */
    fun restoreFromData(encryptedData: String, key: SecretKey): List<PasswordEntry>? {
        return try {
            val plaintext = CryptoManager.decrypt(encryptedData, key)
            val json = JSONArray(plaintext)
            val entries = mutableListOf<PasswordEntry>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                entries.add(PasswordEntry(
                    title = obj.getString("title"),
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    notes = obj.optString("notes", ""),
                    category = obj.optString("category", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
            entries
        } catch (e: Exception) {
            null
        }
    }
}

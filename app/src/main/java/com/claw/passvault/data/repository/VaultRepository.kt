package com.claw.passvault.data.repository

import com.claw.passvault.data.local.PasswordDao
import com.claw.passvault.data.model.PasswordEntry
import com.claw.passvault.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.crypto.SecretKey

class VaultRepository(private val dao: PasswordDao) {

    private var vaultKey: SecretKey? = null

    fun setVaultKey(key: SecretKey?) {
        vaultKey = key
    }

    fun getAllPasswords(): Flow<List<PasswordEntry>> {
        return dao.getAll().map { entries ->
            val key = vaultKey ?: return@map entries
            entries.map { decryptEntry(it, key) }
        }
    }

    fun searchPasswords(query: String): Flow<List<PasswordEntry>> {
        return dao.search(query).map { entries ->
            val key = vaultKey ?: return@map entries
            entries.map { decryptEntry(it, key) }
        }
    }

    fun getByCategory(category: String): Flow<List<PasswordEntry>> {
        return dao.getByCategory(category).map { entries ->
            val key = vaultKey ?: return@map entries
            entries.map { decryptEntry(it, key) }
        }
    }

    suspend fun getById(id: Long): PasswordEntry? {
        val entry = dao.getById(id) ?: return null
        val key = vaultKey ?: return entry
        return decryptEntry(entry, key)
    }

    suspend fun save(entry: PasswordEntry): Long {
        val key = vaultKey ?: return dao.insert(entry)
        val encrypted = encryptEntry(entry, key)
        return dao.insert(encrypted.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun update(entry: PasswordEntry) {
        val key = vaultKey ?: run { dao.update(entry); return }
        val encrypted = encryptEntry(entry, key)
        dao.update(encrypted.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(entry: PasswordEntry) = dao.delete(entry)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun count(): Int = dao.count()

    /** Export all entries decrypted (raw passwords visible — handle with care) */
    suspend fun exportAll(): List<PasswordEntry> {
        val rawEntries = dao.getAll().first()
        val key = vaultKey ?: return rawEntries
        return rawEntries.map { decryptEntry(it, key) }
    }

    private fun encryptEntry(entry: PasswordEntry, key: SecretKey): PasswordEntry {
        return entry.copy(
            username = CryptoManager.encrypt(entry.username, key),
            password = CryptoManager.encrypt(entry.password, key),
            notes = CryptoManager.encrypt(entry.notes, key)
        )
    }

    private fun decryptEntry(entry: PasswordEntry, key: SecretKey): PasswordEntry {
        return try {
            entry.copy(
                username = CryptoManager.decrypt(entry.username, key),
                password = CryptoManager.decrypt(entry.password, key),
                notes = CryptoManager.decrypt(entry.notes, key)
            )
        } catch (e: Exception) {
            entry
        }
    }
}

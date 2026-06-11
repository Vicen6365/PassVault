package com.claw.passvault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claw.passvault.data.local.VaultDatabase
import com.claw.passvault.data.model.Category
import com.claw.passvault.data.model.PasswordEntry
import com.claw.passvault.data.repository.VaultRepository
import com.claw.passvault.data.backup.BackupManager
import com.claw.passvault.data.backup.DriveBackupManager
import com.claw.passvault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(
    val passwords: List<PasswordEntry> = emptyList(),
    val filteredPasswords: List<PasswordEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "ALL",
    val isLoading: Boolean = false,
    val error: String? = null,
    val backupSuccess: Boolean = false,
    val backupError: String? = null,
    val generatedPassword: String = ""
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val repository: VaultRepository
    private val backupManager = BackupManager(application)
    val driveManager = DriveBackupManager(application)

    init {
        val db = VaultDatabase.getInstance(application)
        repository = VaultRepository(db.passwordDao())
        repository.setVaultKey(CryptoManager.getVaultKey())
    }

    fun loadPasswords() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            repository.getAllPasswords().collect { passwords ->
                _state.value = _state.value.copy(
                    passwords = passwords,
                    isLoading = false
                )
                applyFilters()
            }
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilters()
    }

    fun filterByCategory(category: String) {
        _state.value = _state.value.copy(selectedCategory = category)
        applyFilters()
    }

    private fun applyFilters() {
        val all = _state.value.passwords
        val query = _state.value.searchQuery.lowercase()
        val category = _state.value.selectedCategory

        val filtered = all.filter { entry ->
            val matchesQuery = query.isEmpty() ||
                    entry.title.lowercase().contains(query) ||
                    entry.username.lowercase().contains(query)
            val matchesCategory = category == "ALL" || entry.category == category
            matchesQuery && matchesCategory
        }
        _state.value = _state.value.copy(filteredPasswords = filtered)
    }

    fun saveEntry(entry: PasswordEntry) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (entry.id == 0L) {
                        repository.save(entry)
                    } else {
                        repository.update(entry)
                    }
                }
                triggerBackup()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error al guardar: ${e.message}")
            }
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteById(id)
                }
                triggerBackup()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error al eliminar: ${e.message}")
            }
        }
    }

    fun generatePassword() {
        val password = CryptoManager.generatePassword()
        _state.value = _state.value.copy(generatedPassword = password)
    }

    fun triggerBackup() {
        viewModelScope.launch {
            if (!driveManager.isBackupEnabled()) return@launch
            try {
                val entries = withContext(Dispatchers.IO) {
                    repository.exportAll()
                }
                val key = CryptoManager.getVaultKey() ?: return@launch
                val data = backupManager.createBackupData(entries, key)
                val success = driveManager.backupToDrive(data)
                _state.value = _state.value.copy(
                    backupSuccess = success,
                    backupError = if (!success) "Error al respaldar en Drive" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(backupError = "Error backup: ${e.message}")
            }
        }
    }

    fun restoreFromDrive() {
        viewModelScope.launch {
            try {
                val data = driveManager.restoreFromDrive() ?: run {
                    _state.value = _state.value.copy(backupError = "No hay backup en Drive")
                    return@launch
                }
                val key = CryptoManager.getVaultKey() ?: return@launch
                val entries = backupManager.restoreFromData(data, key)
                if (entries != null) {
                    withContext(Dispatchers.IO) {
                        for (entry in entries) {
                            repository.save(entry)
                        }
                    }
                    _state.value = _state.value.copy(backupSuccess = true)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(backupError = "Error al restaurar: ${e.message}")
            }
        }
    }

    fun createLocalBackup(): java.io.File? {
        return try {
            val key = CryptoManager.getVaultKey() ?: return null
            runBlockingOnMain {
                val entries = repository.exportAll()
                backupManager.createLocalBackup(entries, key)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun runBlockingOnMain(block: suspend () -> java.io.File): java.io.File {
        // Simple blocking helper for synchronous backup file creation
        var result: java.io.File? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModelScope.launch(Dispatchers.IO) {
            result = block()
            latch.countDown()
        }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return result ?: throw RuntimeException("Backup timeout")
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, backupError = null, backupSuccess = false)
    }
}

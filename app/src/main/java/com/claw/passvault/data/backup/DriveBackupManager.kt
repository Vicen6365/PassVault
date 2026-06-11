package com.claw.passvault.data.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Manages Google Drive backup using REST API.
 * Stores a single encrypted backup file in the app's hidden appDataFolder.
 */
class DriveBackupManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("drive_prefs", Context.MODE_PRIVATE)
    private val backupFileName = "passvault_backup.enc"

    private val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    suspend fun handleSignInResult(data: Intent?): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(java.lang.Exception::class.java)
            account != null
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        signInClient.signOut()
        prefs.edit().remove("backup_enabled").apply()
    }

    fun isBackupEnabled(): Boolean = prefs.getBoolean("backup_enabled", false)
    fun setBackupEnabled(enabled: Boolean) = prefs.edit().putBoolean("backup_enabled", enabled).apply()

    suspend fun backupToDrive(encryptedData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
            val drive = getDriveService(account)

            // Search for existing backup file
            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$backupFileName'")
                .execute()

            if (existing.files.isNotEmpty()) {
                // Update existing
                val fileId = existing.files[0].id
                val content = ByteArrayContent("application/octet-stream", encryptedData.toByteArray())
                drive.files().update(fileId, null, content).execute()
            } else {
                // Create new
                val metadata = File()
                    .setName(backupFileName)
                    .setParents(listOf("appDataFolder"))
                val content = ByteArrayContent("application/octet-stream", encryptedData.toByteArray())
                drive.files().create(metadata, content).execute()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreFromDrive(): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val drive = getDriveService(account)

            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$backupFileName'")
                .execute()

            if (existing.files.isEmpty()) return@withContext null

            val fileId = existing.files[0].id
            val output = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(output)
            String(output.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account!!

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("PassVault")
            .build()
    }
}

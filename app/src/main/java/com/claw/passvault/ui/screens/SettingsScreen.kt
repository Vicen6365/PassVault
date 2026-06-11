package com.claw.passvault.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claw.passvault.viewmodel.AuthViewModel
import com.claw.passvault.viewmodel.SettingsViewModel
import com.claw.passvault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    vaultViewModel: VaultViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onLock: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val vaultState by vaultViewModel.state.collectAsState()
    val context = LocalContext.current
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // APPEARANCE SECTION
            SectionLabel("Apariencia")
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = "Modo oscuro",
                subtitle = when (state.darkMode) {
                    "system" -> "Según el sistema"
                    "dark" -> "Siempre oscuro"
                    "light" -> "Siempre claro"
                    else -> "Sistema"
                },
                onClick = { showDarkModeDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // SECURITY SECTION
            SectionLabel("Seguridad")
            if (state.hasBiometric) {
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Desbloqueo con huella",
                    subtitle = "Usar huella dactilar para desbloquear",
                    trailing = {
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = { viewModel.toggleBiometric(it) }
                        )
                    }
                )
            }
            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Auto-bloqueo",
                subtitle = when (state.autoLockMinutes) {
                    0 -> "Desactivado"
                    1 -> "Tras 1 minuto"
                    else -> "Tras ${state.autoLockMinutes} minutos"
                },
                onClick = { showAutoLockDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Bloquear ahora",
                subtitle = "Cierra la sesión inmediatamente",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onLock
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // BACKUP SECTION
            SectionLabel("Respaldo")
            SettingsItem(
                icon = Icons.Default.Cloud,
                title = "Google Drive",
                subtitle = if (state.driveBackupEnabled) "Respaldo automático activo"
                    else "Conecta tu cuenta de Google",
                trailing = {
                    if (state.isDriveSignedIn) {
                        Switch(
                            checked = state.driveBackupEnabled,
                            onCheckedChange = { viewModel.setDriveBackup(it) }
                        )
                    }
                },
                onClick = {
                    if (!state.isDriveSignedIn) {
                        context.startActivity(vaultViewModel.driveManager.getSignInIntent())
                        viewModel.updateDriveSignedIn(true)
                    }
                }
            )
            if (state.isDriveSignedIn && state.driveBackupEnabled) {
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = "Respaldar ahora",
                    subtitle = "Sube la bóveda a Drive manualmente",
                    onClick = { vaultViewModel.triggerBackup() }
                )
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = "Restaurar desde Drive",
                    subtitle = "Recupera tu bóveda desde la nube",
                    onClick = { vaultViewModel.restoreFromDrive() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ABOUT SECTION
            SectionLabel("Acerca de")
            SettingsItem(
                icon = Icons.Default.Info,
                title = "PassVault v1.0.0",
                subtitle = "Gestor de contraseñas seguro · Creado por Claw 🐾"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Backup status
        LaunchedEffect(vaultState.backupSuccess) {
            if (vaultState.backupSuccess) vaultViewModel.clearError()
        }
    }

    // Dark mode dialog
    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("Modo oscuro") },
            text = {
                Column {
                    listOf(
                        Triple("system", "Según el sistema", "Usa el tema del dispositivo"),
                        Triple("dark", "Siempre oscuro", "Modo oscuro permanente"),
                        Triple("light", "Siempre claro", "Modo claro permanente")
                    ).forEach { (mode, title, desc) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setDarkMode(mode)
                                showDarkModeDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.darkMode == mode,
                                onClick = {
                                    viewModel.setDarkMode(mode)
                                    showDarkModeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDarkModeDialog = false }) { Text("Cancelar") } }
        )
    }

    // Auto-lock dialog
    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Auto-bloqueo") },
            text = {
                Column {
                    listOf(0 to "Desactivado", 1 to "1 minuto", 5 to "5 minutos",
                        15 to "15 minutos", 30 to "30 minutos").forEach { (min, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setAutoLock(min)
                                showAutoLockDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.autoLockMinutes == min,
                                onClick = {
                                    viewModel.setAutoLock(min)
                                    showAutoLockDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAutoLockDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

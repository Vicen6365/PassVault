package com.claw.passvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.claw.passvault.data.model.Category
import com.claw.passvault.data.model.PasswordEntry
import com.claw.passvault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    entryId: Long,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.OTHER.name) }
    var showPassword by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var passwordLength by remember { mutableIntStateOf(20) }
    var includeSpecial by remember { mutableStateOf(true) }

    val isEditing = entryId != 0L

    // Load existing entry for editing
    LaunchedEffect(entryId) {
        if (isEditing) {
            // We'll load from the state list
            val entry = state.passwords.find { it.id == entryId }
            if (entry != null) {
                title = entry.title
                username = entry.username
                password = entry.password
                notes = entry.notes
                selectedCategory = entry.category
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar contraseña" else "Nueva contraseña") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            viewModel.deleteEntry(entryId)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, "Eliminar",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                leadingIcon = { Icon(Icons.Default.Label, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario / Email") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Password with generator
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showGenerator = !showGenerator }) {
                            Icon(Icons.Default.AutoAwesome, "Generar")
                        }
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                "Mostrar"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium
            )

            // Password strength indicator
            if (password.isNotEmpty()) {
                val strength = calculateStrength(password)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .padding(horizontal = 2.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { if (index < strength.level) 1f else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = strength.color,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = strength.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = strength.color
                )
            }

            // Category selector
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Category.entries.filter { it != Category.ALL }.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.name,
                        onClick = { selectedCategory = cat.name },
                        label = { Text("${cat.icon} ${cat.label}",
                            style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas") },
                leadingIcon = { Icon(Icons.Default.Notes, null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.saveEntry(
                            PasswordEntry(
                                id = entryId,
                                title = title,
                                username = username,
                                password = password,
                                notes = notes,
                                category = selectedCategory,
                                createdAt = if (isEditing) 0 else System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar")
            }
        }

        // Password generator dialog
        if (showGenerator) {
            AlertDialog(
                onDismissRequest = { showGenerator = false },
                icon = { Icon(Icons.Default.AutoAwesome, null) },
                title = { Text("Generar contraseña") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Longitud: $passwordLength")
                        Slider(
                            value = passwordLength.toFloat(),
                            onValueChange = { passwordLength = it.toInt() },
                            valueRange = 8f..64f,
                            steps = 55
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Incluir símbolos")
                            Switch(
                                checked = includeSpecial,
                                onCheckedChange = { includeSpecial = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.generatePassword()
                        showGenerator = false
                    }) {
                        Text("Generar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGenerator = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Update password when generated
        LaunchedEffect(state.generatedPassword) {
            if (state.generatedPassword.isNotEmpty()) {
                password = state.generatedPassword
            }
        }
    }
}

data class PasswordStrength(val level: Int, val label: String, val color: androidx.compose.ui.graphics.Color)

fun calculateStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++

    return when {
        score <= 1 -> PasswordStrength(0, "Débil", com.claw.passvault.ui.theme.StrengthWeak)
        score == 2 -> PasswordStrength(1, "Regular", com.claw.passvault.ui.theme.StrengthFair)
        score == 3 -> PasswordStrength(2, "Buena", com.claw.passvault.ui.theme.StrengthGood)
        score == 4 -> PasswordStrength(3, "Fuerte", com.claw.passvault.ui.theme.StrengthStrong)
        else -> PasswordStrength(4, "Muy fuerte", com.claw.passvault.ui.theme.StrengthVeryStrong)
    }
}

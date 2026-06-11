package com.claw.passvault.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.claw.passvault.ui.screens.*
import com.claw.passvault.viewmodel.AuthViewModel
import com.claw.passvault.viewmodel.VaultViewModel
import com.claw.passvault.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Vault : Screen("vault")
    data object AddEdit : Screen("add_edit/{entryId}") {
        fun createRoute(entryId: Long = 0L) = "add_edit/$entryId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun PassVaultNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    vaultViewModel: VaultViewModel,
    settingsViewModel: SettingsViewModel,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onUnlocked = {
                    navController.navigate(Screen.Vault.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Vault.route) {
            VaultScreen(
                viewModel = vaultViewModel,
                authViewModel = authViewModel,
                onAddEdit = { entryId ->
                    navController.navigate(Screen.AddEdit.createRoute(entryId))
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.AddEdit.route) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")?.toLongOrNull() ?: 0L
            AddEditScreen(
                entryId = entryId,
                viewModel = vaultViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                vaultViewModel = vaultViewModel,
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onLock = {
                    authViewModel.lock()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

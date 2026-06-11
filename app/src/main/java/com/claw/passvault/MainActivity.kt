package com.claw.passvault

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.claw.passvault.ui.navigation.PassVaultNavGraph
import com.claw.passvault.ui.navigation.Screen
import com.claw.passvault.ui.theme.PassVaultTheme
import com.claw.passvault.viewmodel.AuthViewModel
import com.claw.passvault.viewmodel.SettingsViewModel
import com.claw.passvault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val vaultViewModel: VaultViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            val navController = rememberNavController()

            // Dark mode from settings
            val context = LocalContext.current
            val darkModePref = remember {
                context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                    .getString("dark_mode", "system") ?: "system"
            }
            val systemDark = isSystemInDarkTheme()
            val isDark = when (darkModePref) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            // Detect foldable / large screen via WindowSizeClass
            val windowSizeClass = calculateWindowSizeClass(this)

            PassVaultTheme(darkTheme = isDark) {
                val authState by authViewModel.state.collectAsState()
                val startDest = if (authState.isAuthenticated)
                    Screen.Vault.route else Screen.Login.route

                PassVaultNavGraph(
                    navController = navController,
                    authViewModel = authViewModel,
                    vaultViewModel = vaultViewModel,
                    settingsViewModel = settingsViewModel,
                    startDestination = startDest
                )
            }
        }
    }
}

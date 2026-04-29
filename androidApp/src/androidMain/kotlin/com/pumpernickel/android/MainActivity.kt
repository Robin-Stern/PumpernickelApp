package com.pumpernickel.android

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pumpernickel.android.ui.navigation.MainScreen
import com.pumpernickel.android.ui.screens.TutorialOverlay
import com.pumpernickel.android.ui.theme.PumpernickelTheme
import com.pumpernickel.presentation.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val themeMode by settingsViewModel.appTheme.collectAsState()
            val accentColorKey by settingsViewModel.accentColor.collectAsState()
            val hasSeenTutorial by settingsViewModel.hasSeenTutorial.collectAsState()

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* result handled silently; LocationProvider checks permission before each call */ }

            LaunchedEffect(Unit) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            PumpernickelTheme(
                themeMode = themeMode,
                accentColorKey = accentColorKey
            ) {
                Box(Modifier.fillMaxSize()) {
                    MainScreen()
                    if (!hasSeenTutorial) {
                        TutorialOverlay(onFinished = { settingsViewModel.setHasSeenTutorial(true) })
                    }
                }
            }
        }
    }
}

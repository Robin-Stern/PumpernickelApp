package com.pumpernickel.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pumpernickel.android.ui.navigation.MainScreen
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

            PumpernickelTheme(
                themeMode = themeMode,
                accentColorKey = accentColorKey
            ) {
                MainScreen()
            }
        }
    }
}

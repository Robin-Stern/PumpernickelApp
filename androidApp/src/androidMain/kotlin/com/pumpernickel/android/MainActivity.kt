package com.pumpernickel.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.pumpernickel.android.ui.navigation.MainScreen
import com.pumpernickel.android.ui.screens.TutorialOverlay
import com.pumpernickel.android.ui.theme.PumpernickelTheme
import com.pumpernickel.feature.biometric.BiometricGateActivityHolder
import com.pumpernickel.feature.photo.PhotoCaptureLauncherActivityHolder
import com.pumpernickel.feature.photo.PhotoCaptureLauncherHost
import com.pumpernickel.presentation.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : FragmentActivity() {

    // Held as a field so onDestroy can detach the same instance.
    private lateinit var photoCaptureHost: PhotoCaptureLauncherHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 17 — biometric gate (D-17-15) + photo capture launchers
        // (D-17-13/14). Holders MUST be wired before setContent because
        // PhotoCaptureLauncherHost.<init> calls registerForActivityResult,
        // which requires the Activity to be in CREATED (not yet STARTED).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        BiometricGateActivityHolder.attach(this)
        photoCaptureHost = PhotoCaptureLauncherHost(activity = this, context = applicationContext)
        PhotoCaptureLauncherActivityHolder.attach(photoCaptureHost)

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val themeMode by settingsViewModel.appTheme.collectAsState()
            val accentColorKey by settingsViewModel.accentColor.collectAsState()
            val hasSeenTutorial by settingsViewModel.hasSeenTutorial.collectAsState()

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

    override fun onDestroy() {
        // Detach FIRST so any in-flight reads of the holder during teardown
        // don't see a stale reference to a destroyed Activity.
        BiometricGateActivityHolder.detach(this)
        PhotoCaptureLauncherActivityHolder.detach(photoCaptureHost)
        super.onDestroy()
    }
}

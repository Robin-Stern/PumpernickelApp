package com.pumpernickel.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pumpernickel.android.ui.navigation.MainScreen
import com.pumpernickel.android.ui.screens.TutorialOverlay
import com.pumpernickel.android.ui.theme.PumpernickelTheme
import com.pumpernickel.infrastructure.ai.OAuthBrowserLauncherHost
import com.pumpernickel.infrastructure.permissions.PermissionActivityHolder
import com.pumpernickel.infrastructure.progresspic.BiometricGateActivityHolder
import com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncherActivityHolder
import com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncherHost
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
        BiometricGateActivityHolder.attach(this)
        // Phase 19 — permission launchers MUST register before setContent.
        PermissionActivityHolder.attach(this)
        photoCaptureHost = PhotoCaptureLauncherHost(activity = this, context = applicationContext)
        PhotoCaptureLauncherActivityHolder.attach(photoCaptureHost)

        // Async AI generation needs POST_NOTIFICATIONS (Android 13+) for foreground-service notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        // Phase 22 — initial intent path: if the app was relaunched by an OAuth redirect
        // (cold start while CustomTab in background), the redirect URI is in `intent`
        // rather than arriving via onNewIntent. Forward defensively.
        intent?.let { initial ->
            if (initial.data?.scheme == "pumpernickel-oauth") {
                OAuthBrowserLauncherHost.handleRedirect(initial)
            }
        }

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

    /**
     * Phase 22 (D-22-01) — Chrome CustomTabs redirect intercept. The
     * `<intent-filter>` registered on this activity catches
     * `pumpernickel-oauth://callback?code=...` URLs; with launchMode=singleTop,
     * Android delivers them here instead of starting a new activity.
     *
     * Defensive: only handle intents whose data scheme matches; everything else
     * is forwarded to super so platform features (deep links etc.) keep
     * working. Today there are no other deep-link consumers — this is just
     * future-proofing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data?.scheme == "pumpernickel-oauth") {
            OAuthBrowserLauncherHost.handleRedirect(intent)
        }
        setIntent(intent)  // so subsequent getIntent() reads see the latest
    }

    override fun onDestroy() {
        // Detach FIRST so any in-flight reads of the holder during teardown
        // don't see a stale reference to a destroyed Activity.
        BiometricGateActivityHolder.detach(this)
        PermissionActivityHolder.detach()
        PhotoCaptureLauncherActivityHolder.detach(photoCaptureHost)
        super.onDestroy()
    }
}

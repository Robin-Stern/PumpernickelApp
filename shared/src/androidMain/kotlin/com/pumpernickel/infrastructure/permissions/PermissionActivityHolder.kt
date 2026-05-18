package com.pumpernickel.infrastructure.permissions

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * D-19-12 — strong-held handle to MainActivity so AndroidPermissionController
 * (resolved via Koin from non-Activity scope) can launch permission
 * dialogs. Pattern: identical to BiometricGateActivityHolder /
 * PhotoCaptureLauncherActivityHolder — attached in MainActivity.onCreate
 * BEFORE setContent (so the controller can register
 * ActivityResultContracts launchers before they would be needed).
 */
object PermissionActivityHolder {

    private var activity: ComponentActivity? = null
    private var locationLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingLocationCallback: ((Map<String, Boolean>) -> Unit)? = null

    private var backgroundLocationLauncher: ActivityResultLauncher<String>? = null
    private var pendingBackgroundCallback: ((Boolean) -> Unit)? = null

    private var notificationsLauncher: ActivityResultLauncher<String>? = null
    private var pendingNotificationsCallback: ((Boolean) -> Unit)? = null

    fun attach(activity: ComponentActivity) {
        this.activity = activity

        locationLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            pendingLocationCallback?.invoke(grants)
            pendingLocationCallback = null
        }

        backgroundLocationLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            pendingBackgroundCallback?.invoke(granted)
            pendingBackgroundCallback = null
        }

        notificationsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            pendingNotificationsCallback?.invoke(granted)
            pendingNotificationsCallback = null
        }
    }

    fun detach() {
        activity = null
        locationLauncher = null
        backgroundLocationLauncher = null
        notificationsLauncher = null
        pendingLocationCallback = null
        pendingBackgroundCallback = null
        pendingNotificationsCallback = null
    }

    internal fun launchLocation(
        permissions: Array<String>,
        callback: (Map<String, Boolean>) -> Unit
    ) {
        val launcher = locationLauncher
        if (launcher == null) {
            // No activity -> simulate "denied" so caller resumes.
            callback(permissions.associateWith { false })
            return
        }
        pendingLocationCallback = callback
        launcher.launch(permissions)
    }

    internal fun launchBackgroundLocation(callback: (Boolean) -> Unit) {
        val launcher = backgroundLocationLauncher
        if (launcher == null) {
            callback(false)
            return
        }
        pendingBackgroundCallback = callback
        launcher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    internal fun launchNotifications(callback: (Boolean) -> Unit) {
        val launcher = notificationsLauncher
        if (launcher == null) {
            callback(false)
            return
        }
        pendingNotificationsCallback = callback
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

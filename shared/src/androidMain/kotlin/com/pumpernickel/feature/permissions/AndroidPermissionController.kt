package com.pumpernickel.feature.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pumpernickel.domain.permissions.LocationPermissionStatus
import com.pumpernickel.domain.permissions.PermissionController
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidPermissionController(
    context: Context
) : PermissionController {

    private val appContext: Context = context.applicationContext

    override suspend fun currentLocationStatus(): LocationPermissionStatus {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            return LocationPermissionStatus.DENIED
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LocationPermissionStatus.ALWAYS
        }
        val bg = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return if (bg == PackageManager.PERMISSION_GRANTED) {
            LocationPermissionStatus.ALWAYS
        } else {
            LocationPermissionStatus.WHEN_IN_USE
        }
    }

    override suspend fun requestWhenInUse(): LocationPermissionStatus =
        suspendCancellableCoroutine { cont ->
            PermissionActivityHolder.launchLocation(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            ) { _ ->
                cont.resume(
                    runCatching {
                        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    }.fold(
                        onSuccess = { result ->
                            if (result == PackageManager.PERMISSION_GRANTED) LocationPermissionStatus.WHEN_IN_USE
                            else LocationPermissionStatus.DENIED
                        },
                        onFailure = { LocationPermissionStatus.DENIED }
                    )
                )
            }
        }

    override suspend fun requestAlways(): LocationPermissionStatus =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                cont.resume(LocationPermissionStatus.ALWAYS)
                return@suspendCancellableCoroutine
            }
            PermissionActivityHolder.launchBackgroundLocation { granted ->
                cont.resume(
                    if (granted) LocationPermissionStatus.ALWAYS
                    else LocationPermissionStatus.WHEN_IN_USE
                )
            }
        }

    override suspend fun requestNotifications(): Boolean =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            PermissionActivityHolder.launchNotifications { granted -> cont.resume(granted) }
        }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}

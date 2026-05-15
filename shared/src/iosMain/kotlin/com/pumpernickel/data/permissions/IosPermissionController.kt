@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.data.permissions

import com.pumpernickel.domain.permissions.LocationPermissionStatus
import com.pumpernickel.domain.permissions.PermissionController
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * D-19-12 — iOS actual for PermissionController.
 *
 *  - requestWhenInUse() must be called before requestAlways() (iOS rule).
 *  - requestAlways() will silently return WHEN_IN_USE if the user dismisses
 *    the prompt or has not previously granted WhenInUse — by design (D-19-09).
 *  - openAppSettings() launches Settings.app via UIApplicationOpenSettingsURLString.
 */
class IosPermissionController : PermissionController {

    private val locationManager = CLLocationManager()
    private val delegate = AuthStatusDelegate()

    init {
        locationManager.delegate = delegate
    }

    override suspend fun currentLocationStatus(): LocationPermissionStatus =
        toDomain(CLLocationManager.authorizationStatus())

    override suspend fun requestWhenInUse(): LocationPermissionStatus =
        suspendCancellableCoroutine { cont ->
            delegate.onceOnAuthorizationChange { status -> cont.resume(toDomain(status)) }
            locationManager.requestWhenInUseAuthorization()
        }

    override suspend fun requestAlways(): LocationPermissionStatus =
        suspendCancellableCoroutine { cont ->
            delegate.onceOnAuthorizationChange { status -> cont.resume(toDomain(status)) }
            locationManager.requestAlwaysAuthorization()
        }

    override suspend fun requestNotifications(): Boolean =
        suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
                ) { granted, _ -> cont.resume(granted) }
        }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }

    private fun toDomain(status: CLAuthorizationStatus): LocationPermissionStatus = when (status) {
        kCLAuthorizationStatusNotDetermined -> LocationPermissionStatus.NOT_DETERMINED
        kCLAuthorizationStatusDenied -> LocationPermissionStatus.DENIED
        kCLAuthorizationStatusRestricted -> LocationPermissionStatus.RESTRICTED
        kCLAuthorizationStatusAuthorizedWhenInUse -> LocationPermissionStatus.WHEN_IN_USE
        kCLAuthorizationStatusAuthorizedAlways -> LocationPermissionStatus.ALWAYS
        else -> LocationPermissionStatus.NOT_DETERMINED
    }
}

/**
 * One-shot authorization-change delegate. CLLocationManager retains the
 * delegate weakly so we hold it strongly in the controller.
 */
private class AuthStatusDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    private var pending: ((CLAuthorizationStatus) -> Unit)? = null

    fun onceOnAuthorizationChange(block: (CLAuthorizationStatus) -> Unit) {
        pending = block
    }

    override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
        // Skip initial NOT_DETERMINED emission that fires on delegate-attach.
        if (didChangeAuthorizationStatus == kCLAuthorizationStatusNotDetermined) return
        val cb = pending
        pending = null
        cb?.invoke(didChangeAuthorizationStatus)
    }
}

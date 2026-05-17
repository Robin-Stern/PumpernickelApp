@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.data.location

import com.pumpernickel.domain.location.GeoPoint
import com.pumpernickel.domain.location.LocationProvider
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.darwin.NSObject
import kotlin.coroutines.resume

class IosLocationProvider : LocationProvider {

    private val delegate = LocationDelegate()
    private val manager = CLLocationManager().also { it.delegate = delegate }

    override suspend fun getCurrentLocation(): GeoPoint? {
        val status = CLLocationManager.authorizationStatus()
        if (status != kCLAuthorizationStatusAuthorizedWhenInUse &&
            status != kCLAuthorizationStatusAuthorizedAlways
        ) {
            return null
        }
        return delegate.requestSingleFix(manager)
    }
}

private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    private var cont: CancellableContinuation<GeoPoint?>? = null

    suspend fun requestSingleFix(manager: CLLocationManager): GeoPoint? =
        // CLLocationManager.requestLocation MUST be on main thread — same constraint as
        // requestWhenInUseAuthorization. Without this, iOS silently swallows the call and
        // the didUpdateLocations delegate callback never fires.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { c ->
                cont = c
                manager.requestLocation()
                c.invokeOnCancellation {
                    manager.stopUpdatingLocation()
                    cont = null
                }
            }
        }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        if (loc == null) {
            cont?.resume(null)
            cont = null
            return
        }
        loc.coordinate.useContents {
            cont?.resume(GeoPoint(latitude, longitude))
        }
        cont = null
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: platform.Foundation.NSError
    ) {
        cont?.resume(null)
        cont = null
    }
}

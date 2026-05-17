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
        println("[LocProvider] getCurrentLocation: authorizationStatus=$status (whenInUse=$kCLAuthorizationStatusAuthorizedWhenInUse, always=$kCLAuthorizationStatusAuthorizedAlways)")
        if (status != kCLAuthorizationStatusAuthorizedWhenInUse &&
            status != kCLAuthorizationStatusAuthorizedAlways
        ) {
            println("[LocProvider] AUTHORIZATION NOT GRANTED — returning null. status=$status")
            return null
        }
        println("[LocProvider] auth OK, requesting single fix")
        val result = delegate.requestSingleFix(manager)
        println("[LocProvider] single fix completed: result=$result")
        return result
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
                println("[LocDelegate] calling manager.requestLocation() (Main thread)")
                manager.requestLocation()
                c.invokeOnCancellation {
                    println("[LocDelegate] coroutine cancelled — stopping updates")
                    manager.stopUpdatingLocation()
                    cont = null
                }
            }
        }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        println("[LocDelegate] didUpdateLocations callback — count=${didUpdateLocations.size}")
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        if (loc == null) {
            println("[LocDelegate] last location is null — resuming with null")
            cont?.resume(null)
            cont = null
            return
        }
        loc.coordinate.useContents {
            println("[LocDelegate] resuming continuation with lat=$latitude lng=$longitude")
            cont?.resume(GeoPoint(latitude, longitude))
        }
        cont = null
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: platform.Foundation.NSError
    ) {
        println("[LocDelegate] didFailWithError: domain=${didFailWithError.domain} code=${didFailWithError.code} desc=${didFailWithError.localizedDescription}")
        cont?.resume(null)
        cont = null
    }
}

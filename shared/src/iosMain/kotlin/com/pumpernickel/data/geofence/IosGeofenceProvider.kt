@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.data.geofence

import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.geofence.PendingGeofenceExit
import com.pumpernickel.domain.geofence.PendingGeofenceExitStore
import com.pumpernickel.domain.location.GeoPoint
import platform.posix.time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.CoreLocation.CLRegionState
import platform.darwin.NSObject
import platform.Foundation.NSError
import kotlinx.cinterop.ObjCSignatureOverride

/**
 * D-19-01 — iOS actual for GeofenceProvider. Uses CLCircularRegion + the
 * CLLocationManager delegate's didEnter/didExit/monitoringDidFail callbacks.
 *
 * Background contract (D-19-02): UIBackgroundModes = ["location"] in
 * Info.plist + NSLocationAlwaysAndWhenInUseUsageDescription is required
 * for the OS to wake the app on a geofence-EXIT event. When only
 * WhenInUse is granted, events fire only while the app is in foreground —
 * this is a soft-degrade path (D-19-10) surfaced by the UI banner.
 *
 * Cold-start (D-19-04) — BLOCKER-19-1/19-2 fix:
 *   - This is the SINGLE CLLocationManager that monitors regions across
 *     app lifecycles. iOS rebinds the delegate to whatever instance is
 *     attached on app launch — Koin re-creates this `single` and AppDelegate
 *     resolves it FIRST (before SwiftUI renders) so the delegate is wired
 *     by the time the OS posts didExitRegion.
 *   - On every EXIT, the delegate ALSO persists a PendingGeofenceExit to
 *     DataStore (via PendingGeofenceExitStore) so that Wave 3's
 *     WorkoutSessionViewModel.checkForActiveSession() can reconcile the
 *     event even if the process is killed before the VM observer subscribes
 *     to `events`.
 *
 * Pattern: strong delegate ref, MutableSharedFlow with replay=0 and
 * extraBufferCapacity=16, OnBufferOverflow.SUSPEND (queue, don't drop).
 *
 * @param pendingGeofenceExitStore injected via Koin (SettingsRepository
 *                                 implements PendingGeofenceExitStore).
 */
class IosGeofenceProvider(
    private val pendingGeofenceExitStore: PendingGeofenceExitStore
) : GeofenceProvider {

    private val _events = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    override val events: SharedFlow<GeofenceEvent> = _events.asSharedFlow()

    // Background-safe scope for persisting the cold-start sentinel. SupervisorJob
    // so a DataStore failure doesn't propagate cancellation up the delegate chain.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val delegate = GeofenceDelegate(_events, pendingGeofenceExitStore, backgroundScope)
    private val manager = CLLocationManager().also {
        it.delegate = delegate
        // pausesLocationUpdatesAutomatically defaults to true — keep it; we
        // are not using continuous updates, only CLCircularRegion events.
        it.allowsBackgroundLocationUpdates = true
    }

    override suspend fun register(
        center: GeoPoint,
        radiusMeters: Double,
        id: String
    ): Result<Unit> {
        // Idempotency: unregister any region with the same id first so
        // re-register with same id doesn't duplicate events.
        unregister(id)

        // CLLocationManager.maximumRegionMonitoringDistance is typically
        // ~70km on iOS; our 50m radius is well within bounds.
        val coord = CLLocationCoordinate2DMake(center.lat, center.lon)
        val region = CLCircularRegion(
            center = coord,
            radius = radiusMeters,
            identifier = id
        ).apply {
            notifyOnEntry = true
            notifyOnExit = true
        }

        manager.startMonitoringForRegion(region)
        // Synchronously request the current state — if the user is already
        // inside the radius at register-time, we synthesize a single ENTER
        // event so the chip shows "In Zone" without waiting for movement.
        manager.requestStateForRegion(region)
        return Result.success(Unit)
    }

    override suspend fun unregister(id: String) {
        @Suppress("UNCHECKED_CAST")
        (manager.monitoredRegions as Set<CLRegion>).forEach { r ->
            if (r.identifier == id) manager.stopMonitoringForRegion(r)
        }
    }
}

/**
 * Strong-held delegate (CLLocationManager retains weakly — same pattern as
 * IosLocationProvider.LocationDelegate). Persists EXIT events to DataStore
 * BEFORE emitting on the SharedFlow so cold-start reconciliation works
 * even when the app's process is short-lived (D-19-04).
 */
private class GeofenceDelegate(
    private val sink: MutableSharedFlow<GeofenceEvent>,
    private val pendingStore: PendingGeofenceExitStore,
    private val scope: CoroutineScope
) : NSObject(), CLLocationManagerDelegateProtocol {

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didEnterRegion: CLRegion) {
        val id = didEnterRegion.identifier ?: return
        // ENTER cancels any pending exit sentinel — if the user came back
        // before reconciliation, the EXIT must not replay on next launch.
        scope.launch {
            try { pendingStore.setPendingExit(null) } catch (_: Throwable) { /* best effort */ }
        }
        sink.tryEmit(GeofenceEvent.Enter(id))
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) {
        val id = didExitRegion.identifier ?: return
        // BLOCKER-19-2 fix: persist sentinel FIRST. Extract workoutId from
        // the region-id format "active-workout-{startTimeMillis}". If the
        // region was registered with that schema we recover the Long; on
        // parse failure we use 0L as a degraded id (ledger eventKey still
        // includes exitTimeMillis so dedupe holds).
        val workoutId = parseWorkoutIdFromRegionId(id)
        val exitTimeMillis = time(null) * 1000L
        scope.launch {
            try {
                pendingStore.setPendingExit(
                    PendingGeofenceExit(
                        workoutId = workoutId,
                        exitTimeMillis = exitTimeMillis,
                        regionId = id
                    )
                )
            } catch (_: Throwable) { /* best effort — the SharedFlow path still fires */ }
        }
        sink.tryEmit(GeofenceEvent.Exit(id))
    }

    override fun locationManager(
        manager: CLLocationManager,
        monitoringDidFailForRegion: CLRegion?,
        withError: NSError
    ) {
        val id = monitoringDidFailForRegion?.identifier ?: "unknown"
        sink.tryEmit(GeofenceEvent.Error(id, withError.localizedDescription ?: "monitoring failed"))
    }

    override fun locationManager(
        manager: CLLocationManager,
        didDetermineState: CLRegionState,
        forRegion: CLRegion
    ) {
        // Emit ENTER if user is already inside on register (so chip is
        // immediately "In Zone").
        if (didDetermineState == CLRegionState.CLRegionStateInside) {
            val id = forRegion.identifier ?: return
            sink.tryEmit(GeofenceEvent.Enter(id))
        }
    }

    /**
     * Parse "active-workout-{startTimeMillis}" → Long. Returns 0L on failure
     * (the ledger eventKey already includes exitTimeMillis so the dedupe
     * key is still unique).
     */
    private fun parseWorkoutIdFromRegionId(regionId: String): Long {
        val prefix = "active-workout-"
        if (!regionId.startsWith(prefix)) return 0L
        return regionId.removePrefix(prefix).toLongOrNull() ?: 0L
    }
}

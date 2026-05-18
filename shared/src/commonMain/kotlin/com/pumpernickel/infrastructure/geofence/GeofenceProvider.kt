package com.pumpernickel.infrastructure.geofence

import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.location.GeoPoint
import kotlinx.coroutines.flow.SharedFlow

/**
 * D-19-03 — platform-agnostic geofence registration interface. Actuals:
 *  - iosMain: IosGeofenceProvider (CLLocationManager + CLCircularRegion)
 *  - androidMain: AndroidGeofenceProvider (Play Services GeofencingClient)
 *
 * Contract:
 *  - register() is idempotent for the SAME id+center+radius (re-registering
 *    the same region must not duplicate events).
 *  - unregister() of an unknown id is a no-op (no error).
 *  - events is a hot SharedFlow with extraBufferCapacity >= 16, replay=0 —
 *    matches the pattern from GamificationEngine._unlockEvents (queue, don't drop).
 *  - On cold-start launch (OS woke the app for a geofence event), the actual
 *    persists the EXIT event into PendingGeofenceExitStore in addition to
 *    emitting on `events`. The VM's resume path (Plan 05) consumes the store
 *    to handle the case where the SharedFlow event fires before the observer
 *    is attached. See D-19-04 for the cold-start contract.
 *  - Returning Result.failure(...) from register() does NOT also emit on events;
 *    the caller chooses how to surface registration failure.
 */
interface GeofenceProvider {
    /**
     * @param center anchor point captured at first logged set (existing
     *               gymLocation in WorkoutSessionViewModel)
     * @param radiusMeters geofence radius — pass XpFormula.GYM_RADIUS_METERS (50.0)
     * @param id stable region identifier; VM uses "active-workout-{startTimeMillis}"
     */
    suspend fun register(
        center: GeoPoint,
        radiusMeters: Double,
        id: String
    ): Result<Unit>

    suspend fun unregister(id: String)

    val events: SharedFlow<GeofenceEvent>
}

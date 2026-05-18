package com.pumpernickel.domain.geofence

/**
 * D-19-03 — events emitted by GeofenceProvider.events. Three transitions:
 *  - ENTER: device entered the registered region
 *  - EXIT: device left the registered region (triggers grace period in VM)
 *  - ERROR: registration failed at OS layer (e.g. Android GEOFENCE_NOT_AVAILABLE,
 *    iOS region-monitoring failure). UI surfaces this as Inactive chip + banner.
 *
 * `regionId` matches the `id` passed to GeofenceProvider.register(). The VM
 * uses the workoutId-derived id ("active-workout-{startTimeMillis}") to ignore
 * stale events from previous workouts.
 */
sealed class GeofenceEvent {
    abstract val regionId: String

    data class Enter(override val regionId: String) : GeofenceEvent()
    data class Exit(override val regionId: String) : GeofenceEvent()
    data class Error(override val regionId: String, val message: String) : GeofenceEvent()
}

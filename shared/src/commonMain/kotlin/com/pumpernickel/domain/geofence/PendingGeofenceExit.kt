package com.pumpernickel.domain.geofence

/**
 * D-19-04 — payload persisted by both platform actuals when the OS delivers a
 * geofence EXIT event. The Wave 3 ViewModel reconciles by calling
 * PendingGeofenceExitStore.consumePendingExit() on resume (checkForActiveSession)
 * — bridging the cold-start gap where the BroadcastReceiver (Android) or
 * AppDelegate-driven CLLocationManager (iOS) fires before the VM exists.
 *
 * @param workoutId start-time-millis of the active workout when the exit fired
 *                  (the canonical region-id workoutId — see ActiveSessionEntity
 *                  KDoc: the session table is a singleton id=1, so startTimeMillis
 *                  is the only unique-per-workout identifier pre-save)
 * @param exitTimeMillis OS-reported timestamp of the EXIT event (used in
 *                       the ledger eventKey for dedupe)
 * @param regionId raw `CLRegion.identifier` / `Geofence.requestId` — e.g.
 *                 "active-workout-1715600000000". VM checks this matches
 *                 the resumed session's expected regionId before consuming.
 */
data class PendingGeofenceExit(
    val workoutId: Long,
    val exitTimeMillis: Long,
    val regionId: String
)

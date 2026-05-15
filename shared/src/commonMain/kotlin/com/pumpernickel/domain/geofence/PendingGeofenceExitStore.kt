package com.pumpernickel.domain.geofence

/**
 * D-19-04 — cold-start sentinel store. Implemented by SettingsRepository in
 * commonMain (DataStore Preferences). One pending exit at a time — newer
 * writes overwrite older.
 *
 * Write paths:
 *  - iOS IosGeofenceProvider.didExitRegion (warm + cold-start both go through
 *    the same delegate; persistence here closes the cold-start gap if the
 *    process exits before the VM subscribes).
 *  - Android GeofenceBroadcastReceiver.onReceive via goAsync() — writes the
 *    sentinel before its ~10s receiver budget elapses.
 *
 * Read/consume path:
 *  - WorkoutSessionViewModel.checkForActiveSession() (Plan 05) — calls
 *    consumePendingExit() BEFORE subscribing to geofenceProvider.events, and
 *    if a payload matches the resumed session's regionId, invokes
 *    handleGeofenceExitGraceExpired(...) directly (no grace period — the OS
 *    has already reported the EXIT).
 *
 * setPendingExit(null) is the explicit-clear path; pass null when the user
 * re-enters and the VM wants to invalidate any pending sentinel.
 */
interface PendingGeofenceExitStore {
    /**
     * Persist (or clear if null) the latest pending EXIT.
     * Caller MUST be inside an existing coroutine scope; this suspends on the
     * underlying DataStore edit.
     */
    suspend fun setPendingExit(exit: PendingGeofenceExit?)

    /**
     * Atomically read AND clear the stored exit, returning the payload (or
     * null if none was pending). Implementation MUST use a single DataStore
     * edit{} block so a crash mid-consume can't leak a duplicate replay.
     */
    suspend fun consumePendingExit(): PendingGeofenceExit?

    /**
     * Non-clearing read — used by Settings detail view diagnostics (Plan 06/07
     * may add). Implementation reads but does NOT clear. Safe to call from
     * non-VM surfaces.
     */
    suspend fun peekPendingExit(): PendingGeofenceExit?
}

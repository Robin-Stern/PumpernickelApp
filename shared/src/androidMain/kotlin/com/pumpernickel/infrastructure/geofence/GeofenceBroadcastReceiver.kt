package com.pumpernickel.infrastructure.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.geofence.PendingGeofenceExit
import com.pumpernickel.domain.geofence.PendingGeofenceExitStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

/**
 * D-19-04 — registered statically in AndroidManifest so it survives app
 * process death. When Play Services delivers a transition, this receiver
 * runs in a 10-second-bounded background thread.
 *
 * Strategy (BLOCKER-19-3 fix):
 *  1. Parse the GeofencingEvent.
 *  2. Publish into AndroidGeofenceProvider.SHARED_EVENTS for the warm path
 *     (VM observer if subscribing).
 *  3. For EXIT transitions: call goAsync() and persist a PendingGeofenceExit
 *     into DataStore via Koin-resolved PendingGeofenceExitStore. This is
 *     small (3 Preferences keys ~24 bytes) and finishes well within the
 *     ~10s budget. Wave 3 VM reconciles on next launch.
 *
 * The receiver does NOT bootstrap Koin if it isn't running — assume the
 * Application class has run (`Application.onCreate` initializes Koin via
 * `startKoin{}`). If `GlobalContext.getOrNull()` returns null, we
 * silently skip the persistence (the SharedFlow path was the only chance,
 * and there's no consumer anyway).
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            AndroidGeofenceProvider.SHARED_EVENTS.tryEmit(
                GeofenceEvent.Error(
                    regionId = "unknown",
                    message = "GeofenceEvent error code ${event.errorCode}"
                )
            )
            return
        }

        val transition = event.geofenceTransition
        val triggered = event.triggeringGeofences ?: return

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                triggered.forEach { g ->
                    AndroidGeofenceProvider.SHARED_EVENTS.tryEmit(GeofenceEvent.Enter(g.requestId))
                }
                // Clear any pending exit sentinel — user came back before reconciliation.
                clearPendingExitAsync()
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // Warm path
                triggered.forEach { g ->
                    AndroidGeofenceProvider.SHARED_EVENTS.tryEmit(GeofenceEvent.Exit(g.requestId))
                }
                // Cold-start path: persist sentinel via goAsync()
                val pendingResult = goAsync()
                val exitTimeMillis = System.currentTimeMillis()
                val regionIds = triggered.mapNotNull { it.requestId }
                backgroundScope.launch {
                    try {
                        val store = resolvePendingStore() ?: return@launch
                        withTimeoutOrNull(8_000L) {
                            // Only one geofence per app in Phase 19 — but loop defensively.
                            regionIds.forEach { regionId ->
                                store.setPendingExit(
                                    PendingGeofenceExit(
                                        workoutId = parseWorkoutIdFromRegionId(regionId),
                                        exitTimeMillis = exitTimeMillis,
                                        regionId = regionId
                                    )
                                )
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> Unit
        }
    }

    /** Resolve PendingGeofenceExitStore from Koin; returns null if Koin isn't up. */
    private fun resolvePendingStore(): PendingGeofenceExitStore? {
        return try {
            // GlobalContext.getOrNull() returns null if Koin hasn't started yet.
            GlobalContext.getOrNull()?.get<PendingGeofenceExitStore>()
        } catch (_: Throwable) {
            null
        }
    }

    private fun clearPendingExitAsync() {
        val pendingResult = goAsync()
        backgroundScope.launch {
            try {
                val store = resolvePendingStore() ?: return@launch
                withTimeoutOrNull(4_000L) { store.setPendingExit(null) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Parse "active-workout-{startTimeMillis}" -> Long. Returns 0L on failure
     * (ledger eventKey still includes exitTimeMillis for uniqueness).
     */
    private fun parseWorkoutIdFromRegionId(regionId: String): Long {
        val prefix = "active-workout-"
        if (!regionId.startsWith(prefix)) return 0L
        return regionId.removePrefix(prefix).toLongOrNull() ?: 0L
    }
}

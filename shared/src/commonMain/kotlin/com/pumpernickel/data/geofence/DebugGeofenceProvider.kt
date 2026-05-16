package com.pumpernickel.data.geofence

import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.location.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * DEBUG-only GeofenceProvider. Replaces the real iOS/Android actuals in DEBUG
 * builds so Phase 19 flows can be exercised on Simulator/Emulator without real GPS.
 *
 * WARNING: Must NEVER be bound in release builds. The Koin override is gated by
 * `BuildConfig.DEBUG` on Android and `#if DEBUG` on iOS — see PumpernickelApplication.kt
 * and AppDelegate.swift for the wiring.
 *
 * Trigger methods are imperative — UI calls e.g. `triggerExit(regionId)` to emit
 * a single GeofenceEvent.Exit on `events`. The VM filters by regionId, so callers
 * must pass the CURRENT active region id ("active-workout-${startTimeMillis}").
 */
class DebugGeofenceProvider : GeofenceProvider {

    private val _events = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )
    override val events: SharedFlow<GeofenceEvent> = _events.asSharedFlow()

    // Mirror the real provider's contract: register stores the last id; ENTER may
    // be auto-emitted by the platform actual on first register, but for debug we
    // stay silent until the user taps a trigger button (cleaner UAT semantics).
    private var lastRegisteredId: String? = null

    val lastRegisteredRegionId: String? get() = lastRegisteredId

    override suspend fun register(
        center: GeoPoint,
        radiusMeters: Double,
        id: String
    ): Result<Unit> {
        lastRegisteredId = id
        return Result.success(Unit)
    }

    override suspend fun unregister(id: String) {
        if (lastRegisteredId == id) lastRegisteredId = null
    }

    /** Emit GeofenceEvent.Enter for the given regionId. */
    fun triggerEnter(regionId: String) {
        _events.tryEmit(GeofenceEvent.Enter(regionId))
    }

    /** Emit GeofenceEvent.Exit for the given regionId — VM should start grace period. */
    fun triggerExit(regionId: String) {
        _events.tryEmit(GeofenceEvent.Exit(regionId))
    }

    /** Emit GeofenceEvent.Error — VM should transition chip to Inactive. */
    fun triggerError(regionId: String, message: String = "Debug-triggered error") {
        _events.tryEmit(GeofenceEvent.Error(regionId, message))
    }
}

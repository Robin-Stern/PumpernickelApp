package com.pumpernickel.feature.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.pumpernickel.domain.geofence.GeofenceEvent
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.location.GeoPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * D-19-01 — Android actual for GeofenceProvider. Uses Play Services
 * GeofencingClient.addGeofences with a PendingIntent that targets
 * GeofenceBroadcastReceiver. D-19-04: BroadcastReceiver is registered
 * statically in AndroidManifest so the OS can wake the app even when killed.
 *
 * Companion object holds the singleton MutableSharedFlow so the static
 * BroadcastReceiver (instantiated by the framework, not by Koin) can
 * publish events into the same Flow the ViewModel observes.
 *
 * D-19-02: `addGeofences` requires ACCESS_BACKGROUND_LOCATION (API 29+).
 * When only ACCESS_FINE_LOCATION is granted, the call returns
 * GEOFENCE_NOT_AVAILABLE — surfaced as GeofenceEvent.Error and the UI
 * banner per D-19-10.
 */
class AndroidGeofenceProvider(
    context: Context,
    private val client: GeofencingClient =
        LocationServices.getGeofencingClient(context.applicationContext)
) : GeofenceProvider {

    private val appContext: Context = context.applicationContext

    override val events: SharedFlow<GeofenceEvent> = SHARED_EVENTS.asSharedFlow()

    @SuppressLint("MissingPermission")
    override suspend fun register(
        center: GeoPoint,
        radiusMeters: Double,
        id: String
    ): Result<Unit> {
        if (!hasRequiredPermissions()) {
            SHARED_EVENTS.tryEmit(GeofenceEvent.Error(id, "Missing background location permission"))
            return Result.failure(SecurityException("Missing ACCESS_BACKGROUND_LOCATION"))
        }

        // Idempotency: remove any existing geofence with this id first.
        unregister(id)

        val geofence = Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(center.lat, center.lon, radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.addGeofences(request, geofencePendingIntent(appContext))
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    SHARED_EVENTS.tryEmit(GeofenceEvent.Error(id, e.message ?: "addGeofences failed"))
                    cont.resume(Result.failure(e))
                }
        }
    }

    override suspend fun unregister(id: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            client.removeGeofences(listOf(id))
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }   // best-effort
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bg = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (bg != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    companion object {
        /**
         * D-19-04 — shared MutableSharedFlow accessed by both the
         * Koin-resolved instance (read via `events`) and the
         * framework-instantiated GeofenceBroadcastReceiver (write).
         */
        internal val SHARED_EVENTS = MutableSharedFlow<GeofenceEvent>(
            replay = 0,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.SUSPEND
        )

        const val ACTION_TRANSITION = "com.pumpernickel.geofence.TRANSITION"
        const val REQUEST_CODE = 19_2024

        fun geofencePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
                action = ACTION_TRANSITION
            }
            // FLAG_MUTABLE required since API 31 for receivers that re-use the intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }
    }
}

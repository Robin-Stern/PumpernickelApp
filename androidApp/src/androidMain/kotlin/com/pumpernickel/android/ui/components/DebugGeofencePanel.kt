package com.pumpernickel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.domain.geofence.GeofenceProvider
import org.koin.compose.koinInject

/**
 * DEBUG-only Settings panel. Lets the user manually emit Enter/Exit/Error events
 * for the currently-active workout's region id. Not wired into release builds.
 *
 * Usage: wrap call site with `if (BuildConfig.DEBUG) { DebugGeofencePanel() }`.
 */
@Composable
fun DebugGeofencePanel(modifier: Modifier = Modifier) {
    val provider: GeofenceProvider = koinInject()
    val workoutRepository: WorkoutRepository = koinInject()
    val debug = provider as? DebugGeofenceProvider

    var activeRegionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val active = workoutRepository.getActiveSession()
        activeRegionId = active?.startTimeMillis?.let { "active-workout-$it" }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "DEBUG — Geofence Mock",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Region: ${activeRegionId ?: "(no active workout)"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val enabled = debug != null && activeRegionId != null
            Button(
                onClick = { activeRegionId?.let { debug?.triggerEnter(it) } },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) { Text("Enter") }
            Button(
                onClick = { activeRegionId?.let { debug?.triggerExit(it) } },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.weight(1f)
            ) { Text("Exit") }
            Button(
                onClick = { activeRegionId?.let { debug?.triggerError(it) } },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) { Text("Error") }
        }
        if (debug == null) {
            Text(
                text = "GeofenceProvider is NOT a DebugGeofenceProvider — release binding leaked into debug build?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

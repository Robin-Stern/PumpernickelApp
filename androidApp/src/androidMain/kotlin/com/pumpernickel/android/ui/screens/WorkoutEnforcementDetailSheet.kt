package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pumpernickel.android.R
import com.pumpernickel.domain.geofence.EarlyExitBudget
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.domain.permissions.LocationPermissionStatus
import com.pumpernickel.domain.permissions.PermissionController
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEnforcementDetailSheet(onDismiss: () -> Unit) {
    val permissionController: PermissionController = koinInject()
    val earlyExitTracker: EarlyExitTracker = koinInject()
    val budget by earlyExitTracker.budget.collectAsState(
        initial = EarlyExitBudget(used = 0, remaining = EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH, yearMonth = "")
    )
    var status by remember { mutableStateOf(LocationPermissionStatus.NOT_DETERMINED) }

    LaunchedEffect(Unit) {
        status = permissionController.currentLocationStatus()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.settings_workout_enforcement),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.workout_enforcement_detail_how_title),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.workout_enforcement_detail_how_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.workout_enforcement_detail_status_title),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (status) {
                    LocationPermissionStatus.ALWAYS -> stringResource(R.string.workout_enforcement_detail_status_always)
                    LocationPermissionStatus.WHEN_IN_USE -> stringResource(R.string.workout_enforcement_detail_status_wiu)
                    else -> stringResource(R.string.workout_enforcement_detail_status_denied)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionController.openAppSettings() }) {
                Text(stringResource(R.string.workout_enforcement_detail_open_settings))
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.workout_enforcement_detail_early_exits_title),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.workout_enforcement_detail_early_exits_body,
                    budget.used,
                    EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH,
                    nextMonthGermanNameForDetail(budget.yearMonth)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun nextMonthGermanNameForDetail(currentYm: String): String {
    val parts = currentYm.split("-")
    if (parts.size != 2) return ""
    val month = parts[1].toIntOrNull() ?: return ""
    val nextMonth = if (month == 12) 1 else month + 1
    val germanMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )
    return germanMonths.getOrNull(nextMonth - 1) ?: ""
}

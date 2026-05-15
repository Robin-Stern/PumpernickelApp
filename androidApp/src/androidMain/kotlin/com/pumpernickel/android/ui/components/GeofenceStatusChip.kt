package com.pumpernickel.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pumpernickel.android.R
import com.pumpernickel.presentation.workout.GeofenceUiState

/**
 * D-19-16 — 4-state Material AssistChip for the WorkoutSessionScreen TopAppBar.
 * Passive (no onClick) — state changes via VM observation only.
 */
@Composable
fun GeofenceStatusChip(state: GeofenceUiState, modifier: Modifier = Modifier) {
    val style = chipStyleFor(state)
    AssistChip(
        onClick = { /* passive */ },
        enabled = false,
        modifier = modifier,
        label = {
            Text(
                text = style.label,
                fontFamily = if (style.useMonospaceDigit) FontFamily.Monospace else null
            )
        },
        leadingIcon = {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.foreground,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = style.background,
            labelColor = style.foreground,
            disabledContainerColor = style.background,
            disabledLabelColor = style.foreground,
            disabledLeadingIconContentColor = style.foreground
        )
    )
}

private data class ChipStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val background: Color,
    val foreground: Color,
    val useMonospaceDigit: Boolean
)

@Composable
private fun chipStyleFor(state: GeofenceUiState): ChipStyle = when (state) {
    is GeofenceUiState.InZone -> ChipStyle(
        icon = Icons.Filled.LocationOn,
        label = stringResource(R.string.geofence_chip_in_zone),
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        foreground = MaterialTheme.colorScheme.primary,
        useMonospaceDigit = false
    )
    is GeofenceUiState.GracePeriod -> {
        val r = state.remainingSeconds
        val m = r / 60
        val s = r % 60
        val formatted = "%d:%02d".format(m, s)
        ChipStyle(
            icon = Icons.Filled.Warning,
            label = stringResource(R.string.geofence_chip_grace, formatted),
            background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
            foreground = MaterialTheme.colorScheme.tertiary,
            useMonospaceDigit = true
        )
    }
    is GeofenceUiState.Exited -> ChipStyle(
        icon = Icons.Filled.Cancel,
        label = stringResource(R.string.geofence_chip_exited),
        background = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        foreground = MaterialTheme.colorScheme.error,
        useMonospaceDigit = false
    )
    else -> ChipStyle(
        icon = Icons.Filled.LocationDisabled,
        label = stringResource(R.string.geofence_chip_inactive),
        background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
        useMonospaceDigit = false
    )
}

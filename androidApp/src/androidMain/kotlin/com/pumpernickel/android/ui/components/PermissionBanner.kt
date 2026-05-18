package com.pumpernickel.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pumpernickel.android.R

enum class PermissionBannerVariant { WHEN_IN_USE_ONLY, DENIED }

/**
 * D-19-10 + D-19-11 — persistent warning banner. Tap → invokes onTap which
 * the caller wires to opens system Settings via AndroidPermissionController.openAppSettings.
 */
@Composable
fun PermissionBanner(
    variant: PermissionBannerVariant,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes = when (variant) {
        PermissionBannerVariant.WHEN_IN_USE_ONLY -> R.string.geofence_banner_wiu_title
        PermissionBannerVariant.DENIED -> R.string.geofence_banner_denied_title
    }
    val bodyRes = when (variant) {
        PermissionBannerVariant.WHEN_IN_USE_ONLY -> R.string.geofence_banner_wiu_body
        PermissionBannerVariant.DENIED -> R.string.geofence_banner_denied_body
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

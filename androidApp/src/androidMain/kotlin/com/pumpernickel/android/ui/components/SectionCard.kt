package com.pumpernickel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared visual tokens for the Nutrition screens (Phase 18 visual alignment with iOS).
 *
 * The iOS pages render Form sections on `secondarySystemBackground` with rounded
 * 12-pt corners and section headers in caption-secondary. These composables map
 * those tokens onto Material 3:
 *  - [SectionCard]: a `surfaceContainer`-tinted Card with consistent 12.dp radius
 *    and 16.dp interior padding, optionally preceded by an uppercase-secondary
 *    section title — the closest Compose analogue to iOS' Form sections.
 *  - [PrimaryActionButton] / [TonalActionButton]: the two button variants iOS
 *    uses — full-accent primary at height 48, tinted secondary at height 44 —
 *    so save/cancel/barcode buttons read identically across both screens.
 *
 * Spacing convention used by callers:
 *  - 16.dp between sections (LazyColumn `verticalArrangement.spacedBy(16.dp)`),
 *  - 12.dp between rows inside a section.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors()
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TonalActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

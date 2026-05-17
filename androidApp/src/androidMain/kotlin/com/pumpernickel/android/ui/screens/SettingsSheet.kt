package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.theme.accentPresets
import com.pumpernickel.domain.geofence.EarlyExitBudget
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.presentation.settings.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val accentColorKey by viewModel.accentColor.collectAsState()
    // D-quick-vn7 — Debug-Modus toggle + Geofence grace-period picker.
    val debugModeEnabled by viewModel.debugModeEnabled.collectAsState()
    val gracePeriodSeconds by viewModel.gracePeriodSeconds.collectAsState()

    var showWorkoutEnforcementSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Appearance (Theme) ──
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            val themeOptions = listOf("system", "light", "dark")
            val themeLabels = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, key ->
                    SegmentedButton(
                        selected = appTheme == key,
                        onClick = { viewModel.setAppTheme(key) },
                        shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                        label = { Text(themeLabels[index]) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Accent Color ──
            Text(
                text = stringResource(R.string.settings_accent_color),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(accentPresets) { preset ->
                    val isSelected = accentColorKey == preset.key
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(preset.color, CircleShape)
                            .then(
                                if (isSelected) Modifier
                                    .border(3.dp, Color.White, CircleShape)
                                    .shadow(6.dp, CircleShape, ambientColor = preset.color, spotColor = preset.color)
                                else Modifier
                            )
                            .clickable { viewModel.setAccentColor(preset.key) }
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = preset.name,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Weight Unit ──
            Text(
                text = stringResource(R.string.settings_weight_unit),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = weightUnit == WeightUnit.KG,
                    onClick = { viewModel.setWeightUnit(WeightUnit.KG) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("kg") }
                )
                SegmentedButton(
                    selected = weightUnit == WeightUnit.LBS,
                    onClick = { viewModel.setWeightUnit(WeightUnit.LBS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("lbs") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tutorial ──
            Text(
                text = "Tutorial",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.setHasSeenTutorial(false); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tutorial erneut anzeigen")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Gamification ──
            Text(
                text = "Gamification",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onNavigateToAchievements()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Training (Phase 19 — Workout Enforcement) ──
            val earlyExitTracker: EarlyExitTracker = koinInject()
            val budget by earlyExitTracker.budget.collectAsState(
                initial = EarlyExitBudget(
                    used = 0,
                    remaining = EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH,
                    yearMonth = ""
                )
            )
            Text(
                text = stringResource(R.string.settings_training_section),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWorkoutEnforcementSheet = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_workout_enforcement),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(
                            R.string.settings_workout_enforcement_subtitle,
                            budget.used,
                            EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH,
                            nextMonthGermanName(budget.yearMonth)
                        ),
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

            // D-quick-vn7 — user-controllable Debug section (replaces the inline DebugGeofencePanel).
            if (com.pumpernickel.android.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Debug",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Debug-Modus toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Debug-Modus",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Zeigt Debug-Steuerung im Workout-Screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = debugModeEnabled,
                        onCheckedChange = { viewModel.setDebugModeEnabled(it) }
                    )
                }

                // Grace-Period picker (ExposedDropdownMenuBox — 5 options, segmented row would be cramped)
                val graceOptions = listOf(
                    5L to "5 Sek.",
                    10L to "10 Sek.",
                    30L to "30 Sek.",
                    60L to "1 Min.",
                    300L to "5 Min."
                )
                var graceExpanded by remember { mutableStateOf(false) }
                val currentLabel = graceOptions.firstOrNull { it.first == gracePeriodSeconds }?.second
                    ?: "${gracePeriodSeconds} Sek."

                ExposedDropdownMenuBox(
                    expanded = graceExpanded,
                    onExpandedChange = { graceExpanded = !graceExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Grace-Period (Demo)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = graceExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = graceExpanded,
                        onDismissRequest = { graceExpanded = false }
                    ) {
                        graceOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setGracePeriodSeconds(value)
                                    graceExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── KI-Einstellungen (D-18-05) ──
            Text(
                text = "KI / BYOK",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onNavigateToAiSettings()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "KI-Einstellungen",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showWorkoutEnforcementSheet) {
            WorkoutEnforcementDetailSheet(
                onDismiss = { showWorkoutEnforcementSheet = false }
            )
        }
    }
}

private fun nextMonthGermanName(currentYm: String): String {
    val parts = currentYm.split("-")
    if (parts.size != 2) return ""
    val year = parts[0].toIntOrNull() ?: return ""
    val month = parts[1].toIntOrNull() ?: return ""
    val nextMonth = if (month == 12) 1 else month + 1
    val germanMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )
    return germanMonths.getOrNull(nextMonth - 1) ?: ""
}

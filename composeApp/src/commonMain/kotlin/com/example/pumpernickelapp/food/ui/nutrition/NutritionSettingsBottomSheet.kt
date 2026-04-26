package com.example.pumpernickelapp.food.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pumpernickelapp.food.domain.ActivityLevel
import com.example.pumpernickelapp.food.domain.Gender
import com.example.pumpernickelapp.food.domain.GoalType
import org.jetbrains.compose.resources.stringResource
import pumpernickelapp.composeapp.generated.resources.Res
import pumpernickelapp.composeapp.generated.resources.action_save
import pumpernickelapp.composeapp.generated.resources.label_activity_level
import pumpernickelapp.composeapp.generated.resources.label_age
import pumpernickelapp.composeapp.generated.resources.label_daily_calories
import pumpernickelapp.composeapp.generated.resources.label_gender
import pumpernickelapp.composeapp.generated.resources.label_goal
import pumpernickelapp.composeapp.generated.resources.label_height
import pumpernickelapp.composeapp.generated.resources.label_weight
import pumpernickelapp.composeapp.generated.resources.title_nutrition_settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionSettingsBottomSheet(
    viewModel: NutritionSettingsViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.onEvent(NutritionSettingsEvent.OnSavedConsumed)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(Res.string.title_nutrition_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            uiState.calculatedKcal?.let { kcal ->
                CaloriePreviewCard(kcal)
            }

            // Gender
            Text(stringResource(Res.string.label_gender), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Gender.entries.forEachIndexed { index, gender ->
                    SegmentedButton(
                        selected = uiState.gender == gender,
                        onClick = { viewModel.onEvent(NutritionSettingsEvent.OnGenderChanged(gender)) },
                        shape = SegmentedButtonDefaults.itemShape(index, Gender.entries.size),
                        label = { Text(gender.displayName) }
                    )
                }
            }

            // Age, Weight, Height
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.age,
                    onValueChange = { viewModel.onEvent(NutritionSettingsEvent.OnAgeChanged(it)) },
                    label = { Text(stringResource(Res.string.label_age)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = uiState.weightKg,
                    onValueChange = { viewModel.onEvent(NutritionSettingsEvent.OnWeightChanged(it)) },
                    label = { Text(stringResource(Res.string.label_weight)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = uiState.heightCm,
                    onValueChange = { viewModel.onEvent(NutritionSettingsEvent.OnHeightChanged(it)) },
                    label = { Text(stringResource(Res.string.label_height)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Activity Level
            Text(stringResource(Res.string.label_activity_level), style = MaterialTheme.typography.labelLarge)
            ActivityLevelDropdown(
                selected = uiState.activityLevel,
                onSelected = { viewModel.onEvent(NutritionSettingsEvent.OnActivityLevelChanged(it)) }
            )

            // Goal
            Text(stringResource(Res.string.label_goal), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GoalType.entries.forEachIndexed { index, goal ->
                    SegmentedButton(
                        selected = uiState.goalType == goal,
                        onClick = { viewModel.onEvent(NutritionSettingsEvent.OnGoalTypeChanged(goal)) },
                        shape = SegmentedButtonDefaults.itemShape(index, GoalType.entries.size),
                        label = { Text(goal.displayName) }
                    )
                }
            }

            Button(
                onClick = { viewModel.onEvent(NutritionSettingsEvent.OnSaveClicked) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.action_save))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CaloriePreviewCard(kcal: Int) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.label_daily_calories),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "$kcal kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityLevelDropdown(
    selected: ActivityLevel,
    onSelected: (ActivityLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ActivityLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.displayName) },
                    onClick = {
                        onSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodUnit
import com.example.pumpernickelapp.food.ui.components.MacroRow
import org.jetbrains.compose.resources.stringResource
import pumpernickelapp.composeapp.generated.resources.Res
import pumpernickelapp.composeapp.generated.resources.action_cancel
import pumpernickelapp.composeapp.generated.resources.action_delete
import pumpernickelapp.composeapp.generated.resources.action_log_entry
import pumpernickelapp.composeapp.generated.resources.action_save
import pumpernickelapp.composeapp.generated.resources.label_amount
import pumpernickelapp.composeapp.generated.resources.action_update
import pumpernickelapp.composeapp.generated.resources.heading_saved_foods
import pumpernickelapp.composeapp.generated.resources.hint_search
import pumpernickelapp.composeapp.generated.resources.label_calories
import pumpernickelapp.composeapp.generated.resources.label_carbs
import pumpernickelapp.composeapp.generated.resources.label_fat
import pumpernickelapp.composeapp.generated.resources.label_name
import pumpernickelapp.composeapp.generated.resources.label_protein
import pumpernickelapp.composeapp.generated.resources.label_sugar
import pumpernickelapp.composeapp.generated.resources.msg_no_foods
import pumpernickelapp.composeapp.generated.resources.msg_no_foods_found
import pumpernickelapp.composeapp.generated.resources.title_food_edit
import pumpernickelapp.composeapp.generated.resources.title_food_entry
import pumpernickelapp.composeapp.generated.resources.unit_gram
import pumpernickelapp.composeapp.generated.resources.unit_ml
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryScreen(viewModel: FoodEntryViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
    val isEditing = uiState.editingFoodId != null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {
                Text(stringResource(if (isEditing) Res.string.title_food_edit else Res.string.title_food_entry))
            })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnNameChanged(it)) },
                    label = { Text(stringResource(Res.string.label_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.calories,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnCaloriesChanged(it)) },
                    label = { Text(stringResource(Res.string.label_calories)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.protein,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnProteinChanged(it)) },
                        label = { Text(stringResource(Res.string.label_protein)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.fat,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnFatChanged(it)) },
                        label = { Text(stringResource(Res.string.label_fat)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.carbs,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnCarbsChanged(it)) },
                        label = { Text(stringResource(Res.string.label_carbs)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.sugar,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSugarChanged(it)) },
                        label = { Text(stringResource(Res.string.label_sugar)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FoodUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = uiState.unit == unit,
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnUnitChanged(unit)) },
                            shape = SegmentedButtonDefaults.itemShape(index, FoodUnit.entries.size),
                            label = { Text(stringResource(if (unit == FoodUnit.GRAM) Res.string.unit_gram else Res.string.unit_ml)) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BarcodeScannerButton(
                        onBarcodeScanned = { viewModel.onEvent(FoodEntryEvent.OnBarcodeScanned(it)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.isLookingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                if (uiState.barcode.isNotEmpty()) {
                    Text(
                        text = "Barcode: ${uiState.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                uiState.errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                uiState.successMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                if (isEditing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnCancelEdit) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                        Button(
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.action_update))
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.heading_saved_foods), style = MaterialTheme.typography.titleMedium)
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSearchQueryChanged(it)) },
                    label = { Text(stringResource(Res.string.hint_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (filteredFoods.isEmpty()) {
                item {
                    Text(
                        stringResource(if (uiState.searchQuery.isBlank()) Res.string.msg_no_foods else Res.string.msg_no_foods_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredFoods.reversed(), key = { it.id }) { food ->
                    FoodSwipeCard(
                        food = food,
                        onDelete = { viewModel.onEvent(FoodEntryEvent.OnFoodDeleted(food)) },
                        onEdit = { viewModel.onEvent(FoodEntryEvent.OnFoodSelected(food)) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    uiState.pendingLogFood?.let { food ->
        LogAmountDialog(
            food = food,
            onConfirm = { amount -> viewModel.onEvent(FoodEntryEvent.OnConfirmLogAmount(food, amount)) },
            onDismiss = { viewModel.onEvent(FoodEntryEvent.OnDismissLogDialog) }
        )
    }
}

@Composable
private fun LogAmountDialog(
    food: Food,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm)
            }) { Text(stringResource(Res.string.action_log_entry)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(Res.string.label_amount) + " (${food.unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    "${food.calories.roundToInt()} kcal / 100 ${food.unit.label}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSwipeCard(food: Food, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            onDelete()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Card(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        food.name,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${food.calories.roundToInt()} kcal/100${food.unit.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                MacroRow(protein = food.protein, fat = food.fat, carbs = food.carbohydrates, sugar = food.sugar)
            }
        }
    }
}

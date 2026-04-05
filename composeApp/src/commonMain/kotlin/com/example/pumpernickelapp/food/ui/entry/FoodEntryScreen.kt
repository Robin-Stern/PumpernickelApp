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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodUnit
import com.example.pumpernickelapp.food.ui.components.MacroRow
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
                Text(if (isEditing) "Lebensmittel bearbeiten" else "Lebensmittel erfassen")
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
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.calories,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnCaloriesChanged(it)) },
                    label = { Text("Kalorien (kcal) *") },
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
                        label = { Text("Protein (g) *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.fat,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnFatChanged(it)) },
                        label = { Text("Fett (g) *") },
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
                        label = { Text("Kohlenhydrate (g) *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.sugar,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSugarChanged(it)) },
                        label = { Text("davon Zucker (g) *") },
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
                            label = { Text(if (unit == FoodUnit.GRAM) "Gramm (g)" else "Milliliter (ml)") }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.barcode,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnBarcodeChanged(it)) },
                    label = { Text("Barcode (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            item {
                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                uiState.successMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isEditing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnCancelEdit) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Abbrechen")
                        }
                        Button(
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Aktualisieren")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Speichern")
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Gespeicherte Lebensmittel", style = MaterialTheme.typography.titleMedium)
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSearchQueryChanged(it)) },
                    label = { Text("Suchen…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (filteredFoods.isEmpty()) {
                item {
                    Text(
                        if (uiState.searchQuery.isBlank()) "Noch keine Lebensmittel gespeichert."
                        else "Keine Lebensmittel gefunden.",
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSwipeCard(food: Food, onDelete: () -> Unit, onEdit: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )
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
                    Text("🗑 Löschen", fontWeight = FontWeight.Bold)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(food.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${food.calories.roundToInt()} kcal/100${food.unit.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                MacroRow(
                    protein = food.protein,
                    fat = food.fat,
                    carbs = food.carbohydrates,
                    sugar = food.sugar
                )
            }
        }
    }
}

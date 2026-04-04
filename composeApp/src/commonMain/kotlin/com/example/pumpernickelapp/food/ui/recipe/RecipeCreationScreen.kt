@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.pumpernickelapp.food.ui.components.MacroRow
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCreationScreen(viewModel: RecipeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.creationState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Neues Rezept") },
                navigationIcon = {
                    TextButton(onClick = { viewModel.onEvent(RecipeEvent.OnNavigateToList) }) {
                        Text("← Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                OutlinedTextField(
                    value = state.recipeName,
                    onValueChange = { viewModel.onEvent(RecipeEvent.OnRecipeNameChanged(it)) },
                    label = { Text("Rezeptname *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onEvent(RecipeEvent.OnSearchQueryChanged(it)) },
                    label = { Text("Lebensmittel suchen…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (state.searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = if (state.searchQuery.isBlank()) "Zuletzt hinzugefügt" else "Suchergebnisse",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.searchResults, key = { it.id }) { food ->
                    FoodSwipeToAddItem(
                        food = food,
                        onSelected = { viewModel.onEvent(RecipeEvent.OnFoodSelected(food)) }
                    )
                }
            }

            if (state.ingredients.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Zutaten:", style = MaterialTheme.typography.labelLarge)
                }
                items(state.ingredients.size) { index ->
                    val entry = state.ingredients[index]
                    val amount = entry.amountGrams.toDoubleOrNull() ?: 0.0
                    val factor = amount / 100.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.food.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${(entry.food.calories * factor).roundToInt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MacroRow(
                                protein = entry.food.protein * factor,
                                fat     = entry.food.fat * factor,
                                carbs   = entry.food.carbohydrates * factor,
                                sugar   = entry.food.sugar * factor
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = entry.amountGrams,
                            onValueChange = { viewModel.onEvent(RecipeEvent.OnIngredientAmountChanged(index, it)) },
                            label = { Text(entry.food.unit.label) },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { viewModel.onEvent(RecipeEvent.OnIngredientRemoved(index)) }) {
                            Text("✕")
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Gesamt: ${state.totals.calories.roundToInt()} kcal",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MacroRow(
                            protein = state.totals.protein,
                            fat     = state.totals.fat,
                            carbs   = state.totals.carbs,
                            sugar   = state.totals.sugar
                        )
                    }
                }
            }

            item {
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.onEvent(RecipeEvent.OnSaveClicked) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rezept speichern")
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSwipeToAddItem(food: Food, onSelected: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onSelected()
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "+ Hinzufügen",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(food.name, fontWeight = FontWeight.Medium)
                Text(
                    "${food.calories.roundToInt()} kcal/100${food.unit.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MacroRow(
                protein = food.protein,
                fat     = food.fat,
                carbs   = food.carbohydrates,
                sugar   = food.sugar
            )
        }
        HorizontalDivider()
    }
}

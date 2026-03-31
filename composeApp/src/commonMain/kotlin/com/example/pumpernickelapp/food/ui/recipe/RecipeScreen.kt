@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pumpernickelapp.food.domain.Food
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(viewModel: RecipeViewModel, modifier: Modifier = Modifier) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Rezepte") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Neues Rezept erstellen", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = uiState.recipeName,
                    onValueChange = { viewModel.onEvent(RecipeEvent.OnRecipeNameChanged(it)) },
                    label = { Text("Rezeptname *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(RecipeEvent.OnSearchQueryChanged(it)) },
                    label = { Text("Lebensmittel suchen…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (uiState.searchResults.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            uiState.searchResults.forEach { food ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onEvent(RecipeEvent.OnFoodSelected(food)) }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(food.name)
                                    Text(
                                        "${food.calories.roundToInt()} kcal/100g",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (uiState.ingredients.isNotEmpty()) {
                item {
                    Text("Zutaten:", style = MaterialTheme.typography.labelLarge)
                }
                items(uiState.ingredients.size) { index ->
                    val entry = uiState.ingredients[index]
                    val kcal = (entry.food.calories * (entry.amountGrams.toDoubleOrNull() ?: 0.0) / 100.0).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.food.name, fontWeight = FontWeight.Medium)
                            Text(
                                "$kcal kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = entry.amountGrams,
                            onValueChange = { viewModel.onEvent(RecipeEvent.OnIngredientAmountChanged(index, it)) },
                            label = { Text("g") },
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
                    Text(
                        "Gesamt: ${uiState.totalCalories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                uiState.successMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { viewModel.onEvent(RecipeEvent.OnSaveClicked) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rezept speichern")
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Gespeicherte Rezepte", style = MaterialTheme.typography.titleMedium)
            }

            if (recipes.isEmpty()) {
                item { Text("Noch keine Rezepte vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(recipes) { recipe ->
                    RecipeCard(recipe = recipe, viewModel = viewModel)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Food.Recipe, viewModel: RecipeViewModel) {
    val totalKcal = viewModel.calculateRecipeCalories(recipe).roundToInt()
    val foodMap = viewModel.foods.collectAsStateWithLifecycle().value.associateBy { it.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$totalKcal kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            recipe.ingredients.forEach { ingredient ->
                val food = foodMap[ingredient.foodId]
                val kcal = if (food != null)
                    (food.calories * ingredient.amountGrams / 100.0).roundToInt()
                else 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(food?.name ?: "Unbekannt", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${ingredient.amountGrams.roundToInt()} g  •  $kcal kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

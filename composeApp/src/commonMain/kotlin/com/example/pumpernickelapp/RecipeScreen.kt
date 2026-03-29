@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(viewModel: FoodViewModel, modifier: Modifier = Modifier) {
    val recipes by viewModel.recipes.collectAsState()

    // Formular-State
    var recipeName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<Food>()) }

    // Zutaten: (Food, Gramm-String)
    val ingredients = remember { mutableStateListOf<Pair<Food, String>>() }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val totalCalories = ingredients.sumOf { (food, amountStr) ->
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        food.calories * amount / 100.0
    }

    fun saveRecipe() {
        errorMessage = null
        successMessage = null
        when {
            recipeName.isBlank() -> errorMessage = "Rezeptname darf nicht leer sein."
            ingredients.isEmpty() -> errorMessage = "Mindestens eine Zutat hinzufügen."
            ingredients.any { (_, g) -> g.toDoubleOrNull() == null || g.toDoubleOrNull()!! <= 0 } ->
                errorMessage = "Alle Mengenangaben müssen eine Zahl > 0 sein."
            else -> {
                val recipeIngredients = ingredients.map { (food, amountStr) ->
                    RecipeIngredient(foodId = food.id, amountGrams = amountStr.toDouble())
                }
                viewModel.addRecipe(Food.Recipe(name = recipeName.trim(), ingredients = recipeIngredients))
                recipeName = ""
                searchQuery = ""
                searchResults = emptyList()
                ingredients.clear()
                successMessage = "Rezept gespeichert!"
            }
        }
    }

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
            // --- Neues Rezept ---
            item {
                Spacer(Modifier.height(4.dp))
                Text("Neues Rezept erstellen", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    label = { Text("Rezeptname *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Suchleiste
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchResults = viewModel.searchFoods(it)
                    },
                    label = { Text("Lebensmittel suchen…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Suchergebnisse
            if (searchResults.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            searchResults.forEach { food ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (ingredients.none { it.first.id == food.id }) {
                                                ingredients.add(food to "100")
                                            }
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        }
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

            // Zutaten-Liste mit Mengenangabe
            if (ingredients.isNotEmpty()) {
                item {
                    Text("Zutaten:", style = MaterialTheme.typography.labelLarge)
                }
                items(ingredients.size) { index ->
                    val (food, amount) = ingredients[index]
                    val kcal = (food.calories * (amount.toDoubleOrNull() ?: 0.0) / 100.0).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, fontWeight = FontWeight.Medium)
                            Text("$kcal kcal", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { ingredients[index] = food to it },
                            label = { Text("g") },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { ingredients.removeAt(index) }) {
                            Text("✕")
                        }
                    }
                }
                item {
                    Text(
                        "Gesamt: ${totalCalories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                successMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { saveRecipe() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Rezept speichern")
                }
            }

            // --- Gespeicherte Rezepte ---
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
private fun RecipeCard(recipe: Food.Recipe, viewModel: FoodViewModel) {
    val totalKcal = viewModel.calculateRecipeCalories(recipe).roundToInt()
    val foodMap = viewModel.foods.collectAsState().value.associateBy { it.id }

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
                    Text(
                        food?.name ?: "Unbekannt",
                        style = MaterialTheme.typography.bodySmall
                    )
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

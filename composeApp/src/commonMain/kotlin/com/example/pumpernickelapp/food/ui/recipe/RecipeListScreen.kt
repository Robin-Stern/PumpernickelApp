@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.ui.components.MacroRow
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(viewModel: RecipeViewModel, modifier: Modifier = Modifier) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    var favoriteDeleteCandidate by remember { mutableStateOf<Food.Recipe?>(null) }

    favoriteDeleteCandidate?.let { recipe ->
        AlertDialog(
            onDismissRequest = { favoriteDeleteCandidate = null },
            title = { Text(recipe.name) },
            text = { Text("Was möchtest du tun?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(RecipeEvent.OnRecipeDeleted(recipe))
                        favoriteDeleteCandidate = null
                    }
                ) {
                    Text("Rezept löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(RecipeEvent.OnRecipeFavoriteToggled(recipe))
                        favoriteDeleteCandidate = null
                    }
                ) {
                    Text("Aus Favoriten entfernen")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Rezepte") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(RecipeEvent.OnShowCreation) }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
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

            if (recipes.isEmpty()) {
                item {
                    Text(
                        "Noch keine Rezepte vorhanden. Tippe auf + um ein Rezept zu erstellen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(recipes, key = { it.id }) { recipe ->
                    RecipeSwipeCard(
                        recipe = recipe,
                        viewModel = viewModel,
                        onDelete = {
                            if (recipe.isFavorite) {
                                favoriteDeleteCandidate = recipe
                            } else {
                                viewModel.onEvent(RecipeEvent.OnRecipeDeleted(recipe))
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeSwipeCard(
    recipe: Food.Recipe,
    viewModel: RecipeViewModel,
    onDelete: () -> Unit
) {
    val currentRecipe by rememberUpdatedState(recipe)
    val currentOnDelete by rememberUpdatedState(onDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    viewModel.onEvent(RecipeEvent.OnRecipeFavoriteToggled(currentRecipe))
                    false  // Karte bleibt, nur Favorit-Status ändert sich
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnDelete()
                    !currentRecipe.isFavorite  // Bei Favorit: Dialog zeigen, Karte bleibt
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            val (backgroundColor, label, alignment) = when {
                offset > 0f -> Triple(Color(0xFFFFF9C4), "★ Favorit", Alignment.CenterStart)
                offset < 0f -> Triple(MaterialTheme.colorScheme.errorContainer, "🗑 Löschen", Alignment.CenterEnd)
                else -> Triple(Color.Transparent, "", Alignment.Center)
            }
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    if (label.isNotEmpty()) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        RecipeCard(recipe = currentRecipe, viewModel = viewModel)
    }
}

@Composable
private fun RecipeCard(recipe: Food.Recipe, viewModel: RecipeViewModel) {
    val macros = viewModel.calculateRecipeMacros(recipe)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recipe.isFavorite) {
                        Text("★", color = Color(0xFFFBC02D), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        recipe.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "${macros.calories.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            MacroRow(
                protein = macros.protein,
                fat     = macros.fat,
                carbs   = macros.carbs,
                sugar   = macros.sugar
            )
            HorizontalDivider()
            recipe.ingredients.forEach { ingredient ->
                val food = foodMap[ingredient.foodId]
                val factor = ingredient.amountGrams / 100.0
                val kcal = if (food != null) (food.calories * factor).roundToInt() else 0
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
                if (food != null) {
                    MacroRow(
                        protein = food.protein * factor,
                        fat     = food.fat * factor,
                        carbs   = food.carbohydrates * factor,
                        sugar   = food.sugar * factor
                    )
                }
            }
        }
    }
}

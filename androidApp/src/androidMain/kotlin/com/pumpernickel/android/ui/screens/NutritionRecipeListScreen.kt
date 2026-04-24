package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.navigation.NutritionRecipeCreationRoute
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.presentation.nutrition.RecipeListEvent
import com.pumpernickel.presentation.nutrition.RecipeListViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionRecipeListScreen(
    navController: NavController,
    viewModel: RecipeListViewModel = koinViewModel()
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    var favoriteDeleteCandidate by remember { mutableStateOf<Recipe?>(null) }

    favoriteDeleteCandidate?.let { recipe ->
        AlertDialog(
            onDismissRequest = { favoriteDeleteCandidate = null },
            title = { Text(recipe.name) },
            text = { Text(stringResource(R.string.dialog_recipe_action_prompt)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(RecipeListEvent.OnRecipeDeleted(recipe)); favoriteDeleteCandidate = null }) {
                    Text(stringResource(R.string.action_delete_recipe), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(RecipeListEvent.OnRecipeFavoriteToggled(recipe)); favoriteDeleteCandidate = null }) {
                    Text(stringResource(R.string.action_remove_favorite))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_recipes)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(NutritionRecipeCreationRoute) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.title_new_recipe))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (recipes.isEmpty()) {
                item { Text(stringResource(R.string.msg_no_recipes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(recipes, key = { it.id }) { recipe ->
                    RecipeSwipeCard(
                        recipe = recipe, viewModel = viewModel,
                        onDelete = {
                            if (recipe.isFavorite) favoriteDeleteCandidate = recipe
                            else viewModel.onEvent(RecipeListEvent.OnRecipeDeleted(recipe))
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeSwipeCard(recipe: Recipe, viewModel: RecipeListViewModel, onDelete: () -> Unit) {
    val currentRecipe by rememberUpdatedState(recipe)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.3f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { viewModel.onEvent(RecipeListEvent.OnRecipeFavoriteToggled(currentRecipe)); false }
                SwipeToDismissBoxValue.EndToStart -> { currentOnDelete(); !currentRecipe.isFavorite }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.targetValue
            val (bg, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(NutritionColors.favoriteBackground, stringResource(R.string.action_favorite), Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.errorContainer, stringResource(R.string.action_delete_recipe), Alignment.CenterEnd)
                else -> Triple(Color.Transparent, "", Alignment.Center)
            }
            Card(modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = bg)) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = alignment) {
                    if (label.isNotEmpty()) Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        RecipeCard(recipe = currentRecipe, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeCard(recipe: Recipe, viewModel: RecipeListViewModel) {
    val macros = viewModel.calculateMacros(recipe)
    val foodMap = viewModel.foods.collectAsStateWithLifecycle().value.associateBy { it.id }
    var expanded by remember { mutableStateOf(false) }
    val totalGrams = recipe.ingredients.sumOf { it.amountGrams }.roundToInt()

    Card(
        onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (recipe.isFavorite) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (recipe.isFavorite) Text("\u2605", color = NutritionColors.favoriteStar, style = MaterialTheme.typography.titleMedium)
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${macros.calories.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.recipe_total_grams, totalGrams), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MacroRow(protein = macros.protein, fat = macros.fat, carbs = macros.carbs, sugar = macros.sugar)
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    recipe.ingredients.forEach { ingredient ->
                        val food = foodMap[ingredient.foodId]
                        val factor = ingredient.amountGrams / 100.0
                        val kcal = if (food != null) (food.calories * factor).roundToInt() else 0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(food?.name ?: stringResource(R.string.label_unknown_food), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${ingredient.amountGrams.roundToInt()} ${food?.unit?.label ?: "g"}  \u2022  $kcal kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(
                if (expanded) stringResource(R.string.recipe_ingredients_hide) else stringResource(R.string.recipe_ingredients_count, recipe.ingredients.size),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

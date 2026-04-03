package com.example.pumpernickelapp.food.ui.recipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecipeScreen(viewModel: RecipeViewModel, modifier: Modifier = Modifier) {
    val showCreation by viewModel.showCreation.collectAsStateWithLifecycle()
    if (showCreation) {
        RecipeCreationScreen(viewModel = viewModel, modifier = modifier)
    } else {
        RecipeListScreen(viewModel = viewModel, modifier = modifier)
    }
}

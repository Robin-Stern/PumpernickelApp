package com.example.pumpernickelapp.food.ui.recipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun RecipeScreen(
    listViewModel: RecipeListViewModel,
    creationViewModel: RecipeCreationViewModel,
    modifier: Modifier = Modifier
) {
    var showCreation by remember { mutableStateOf(false) }

    LaunchedEffect(creationViewModel) {
        creationViewModel.savedEvent.collect {
            listViewModel.refresh()
            showCreation = false
        }
    }

    if (showCreation) {
        RecipeCreationScreen(
            viewModel = creationViewModel,
            onNavigateBack = { showCreation = false },
            modifier = modifier
        )
    } else {
        RecipeListScreen(
            viewModel = listViewModel,
            onCreateRecipe = {
                creationViewModel.reset()
                showCreation = true
            },
            modifier = modifier
        )
    }
}

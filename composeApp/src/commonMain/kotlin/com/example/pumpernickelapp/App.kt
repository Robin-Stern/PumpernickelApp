package com.example.pumpernickelapp

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.pumpernickelapp.food.ui.entry.FoodEntryScreen
import com.example.pumpernickelapp.food.ui.entry.FoodEntryViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeCreationViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeListViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import pumpernickelapp.composeapp.generated.resources.Res
import pumpernickelapp.composeapp.generated.resources.tab_food
import pumpernickelapp.composeapp.generated.resources.tab_recipes

@Composable
@Preview
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme {
            val foodEntryViewModel: FoodEntryViewModel = koinViewModel()
            val recipeListViewModel: RecipeListViewModel = koinViewModel()
            val recipeCreationViewModel: RecipeCreationViewModel = koinViewModel()
            var selectedTab by remember { mutableIntStateOf(0) }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text(stringResource(Res.string.tab_food)) },
                            icon = {}
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text(stringResource(Res.string.tab_recipes)) },
                            icon = {}
                        )
                    }
                }
            ) { innerPadding ->
                when (selectedTab) {
                    0 -> FoodEntryScreen(
                        viewModel = foodEntryViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                    1 -> RecipeScreen(
                        listViewModel = recipeListViewModel,
                        creationViewModel = recipeCreationViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

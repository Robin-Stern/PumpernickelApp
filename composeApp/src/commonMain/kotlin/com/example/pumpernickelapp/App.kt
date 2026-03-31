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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pumpernickelapp.food.ui.entry.FoodEntryScreen
import com.example.pumpernickelapp.food.ui.entry.FoodEntryViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeScreen
import com.example.pumpernickelapp.food.ui.recipe.RecipeViewModel

@Composable
@Preview
fun App() {
    MaterialTheme {
        val foodEntryViewModel: FoodEntryViewModel = viewModel { FoodEntryViewModel() }
        val recipeViewModel: RecipeViewModel = viewModel { RecipeViewModel() }
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Lebensmittel") },
                        icon = {}
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Rezepte") },
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
                    viewModel = recipeViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

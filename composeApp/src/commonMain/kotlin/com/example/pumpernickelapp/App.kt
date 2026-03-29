package com.example.pumpernickelapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel: FoodViewModel = viewModel { FoodViewModel() }
        FoodEntryScreen(viewModel = viewModel)
    }
}

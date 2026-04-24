package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlaceholderScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout") }
            )
        }
    ) { innerPadding ->
        PlaceholderScreen(
            icon = Icons.Filled.FitnessCenter,
            title = "Workout",
            message = "Workout templates and session tracking. Coming in the next update.",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

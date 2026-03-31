package com.pumpernickel.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pumpernickel.android.ui.screens.PlaceholderScreen
import com.pumpernickel.android.ui.screens.WorkoutPlaceholderScreen

enum class TopLevelTab(
    val label: String,
    val icon: ImageVector,
) {
    WORKOUT("Workout", Icons.Filled.FitnessCenter),
    OVERVIEW("Overview", Icons.Filled.BarChart),
    NUTRITION("Nutrition", Icons.Filled.Restaurant)
}

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val workoutNavController: NavHostController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Workout tab — always composed to preserve back stack
            AnimatedVisibility(visible = selectedTab == 0) {
                NavHost(
                    navController = workoutNavController,
                    startDestination = TemplateListRoute
                ) {
                    composable<TemplateListRoute> {
                        WorkoutPlaceholderScreen()
                    }
                }
            }

            // Overview tab
            AnimatedVisibility(visible = selectedTab == 1) {
                PlaceholderScreen(
                    icon = Icons.Filled.BarChart,
                    title = "Overview",
                    message = "Track your training progress and stats. Coming soon."
                )
            }

            // Nutrition tab
            AnimatedVisibility(visible = selectedTab == 2) {
                PlaceholderScreen(
                    icon = Icons.Filled.Restaurant,
                    title = "Nutrition",
                    message = "Log meals and track your macros. Coming soon."
                )
            }
        }
    }
}

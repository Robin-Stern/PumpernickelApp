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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pumpernickel.android.ui.screens.CreateExerciseScreen
import com.pumpernickel.android.ui.screens.ExerciseCatalogScreen
import com.pumpernickel.android.ui.screens.ExerciseDetailScreen
import com.pumpernickel.android.ui.screens.ExercisePickerScreen
import com.pumpernickel.android.ui.screens.PlaceholderScreen
import com.pumpernickel.android.ui.screens.TemplateEditorScreen
import com.pumpernickel.android.ui.screens.TemplateListScreen
import com.pumpernickel.android.ui.screens.WorkoutHistoryDetailScreen
import com.pumpernickel.android.ui.screens.WorkoutHistoryListScreen
import com.pumpernickel.android.ui.screens.WorkoutSessionScreen
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import org.koin.compose.viewmodel.koinViewModel

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
                        TemplateListScreen(navController = workoutNavController)
                    }
                    composable<TemplateEditorRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<TemplateEditorRoute>()
                        TemplateEditorScreen(
                            templateId = route.templateId,
                            navController = workoutNavController
                        )
                    }
                    composable<ExercisePickerRoute> { backStackEntry ->
                        val parentEntry = remember(backStackEntry) {
                            workoutNavController.getBackStackEntry<TemplateEditorRoute>()
                        }
                        val editorViewModel: TemplateEditorViewModel = koinViewModel(
                            viewModelStoreOwner = parentEntry
                        )
                        ExercisePickerScreen(
                            onExerciseSelected = { id, name, muscles ->
                                editorViewModel.addExercise(id, name, muscles)
                                workoutNavController.popBackStack()
                            },
                            navController = workoutNavController
                        )
                    }
                    composable<WorkoutSessionRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<WorkoutSessionRoute>()
                        WorkoutSessionScreen(
                            templateId = route.templateId,
                            navController = workoutNavController
                        )
                    }
                    composable<ExerciseCatalogRoute> {
                        ExerciseCatalogScreen(navController = workoutNavController)
                    }
                    composable<ExerciseDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ExerciseDetailRoute>()
                        ExerciseDetailScreen(
                            exerciseId = route.exerciseId,
                            navController = workoutNavController
                        )
                    }
                    composable<CreateExerciseRoute> {
                        CreateExerciseScreen(navController = workoutNavController)
                    }
                    composable<WorkoutHistoryListRoute> {
                        WorkoutHistoryListScreen(navController = workoutNavController)
                    }
                    composable<WorkoutHistoryDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<WorkoutHistoryDetailRoute>()
                        WorkoutHistoryDetailScreen(
                            workoutId = route.workoutId,
                            navController = workoutNavController
                        )
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

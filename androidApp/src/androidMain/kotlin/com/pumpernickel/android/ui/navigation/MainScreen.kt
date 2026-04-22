package com.pumpernickel.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.screens.CreateExerciseScreen
import com.pumpernickel.android.ui.screens.ExerciseCatalogScreen
import com.pumpernickel.android.ui.screens.ExerciseDetailScreen
import com.pumpernickel.android.ui.screens.ExercisePickerScreen
import com.pumpernickel.android.ui.screens.OverviewScreen
import com.pumpernickel.android.ui.screens.PlaceholderScreen
import com.pumpernickel.android.ui.screens.TemplateEditorScreen
import com.pumpernickel.android.ui.screens.TemplateListScreen
import com.pumpernickel.android.ui.screens.WorkoutHistoryDetailScreen
import com.pumpernickel.android.ui.screens.WorkoutHistoryListScreen
import com.pumpernickel.android.ui.screens.WorkoutSessionScreen
import com.pumpernickel.android.ui.screens.NutritionFoodEntryScreen
import com.pumpernickel.android.ui.screens.NutritionRecipeListScreen
import com.pumpernickel.android.ui.screens.NutritionRecipeCreationScreen
import com.pumpernickel.android.ui.screens.NutritionDailyLogScreen
import com.pumpernickel.android.ui.screens.UnlockModalHost
import com.pumpernickel.presentation.nutrition.RecipeListViewModel
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import org.koin.compose.viewmodel.koinViewModel

enum class TopLevelTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    WORKOUT(R.string.tab_workout, Icons.Filled.FitnessCenter),
    OVERVIEW(R.string.tab_overview, Icons.Filled.BarChart),
    NUTRITION(R.string.tab_nutrition, Icons.Filled.Restaurant)
}

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val workoutNavController: NavHostController = rememberNavController()
    val nutritionNavController: NavHostController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
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
            Box(modifier = Modifier.fillMaxSize().alpha(if (selectedTab == 0) 1f else 0f)) {
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
            Box(modifier = Modifier.fillMaxSize().alpha(if (selectedTab == 1) 1f else 0f)) {
                OverviewScreen()
            }

            // Nutrition tab — always composed to preserve back stack
            Box(modifier = Modifier.fillMaxSize().alpha(if (selectedTab == 2) 1f else 0f)) {
                NavHost(
                    navController = nutritionNavController,
                    startDestination = NutritionDailyLogRoute
                ) {
                    composable<NutritionDailyLogRoute> {
                        NutritionDailyLogScreen(navController = nutritionNavController)
                    }
                    composable<NutritionFoodEntryRoute> {
                        NutritionFoodEntryScreen(navController = nutritionNavController)
                    }
                    composable<NutritionRecipeListRoute> {
                        NutritionRecipeListScreen(navController = nutritionNavController)
                    }
                    composable<NutritionRecipeCreationRoute> {
                        val parentEntry = remember(it) {
                            nutritionNavController.getBackStackEntry<NutritionRecipeListRoute>()
                        }
                        val listViewModel: RecipeListViewModel = koinViewModel(
                            viewModelStoreOwner = parentEntry
                        )
                        NutritionRecipeCreationScreen(
                            listViewModel = listViewModel,
                            navController = nutritionNavController
                        )
                    }
                }
            }
        }
    }

        // D-19 + D-20: root-level unlock modal host. Must sit at the composition
        // root so AlertDialog overlays the current tab regardless of selection.
        UnlockModalHost()
    }
}

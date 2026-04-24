package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.CreateExerciseRoute
import com.pumpernickel.android.ui.navigation.ExerciseDetailRoute
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCatalogScreen(navController: NavHostController) {
    val viewModel: ExerciseCatalogViewModel = koinViewModel()
    val exercises by viewModel.exercises.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedMuscleGroup by viewModel.selectedMuscleGroup.collectAsState()
    var showAnatomyPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Exercises") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(CreateExerciseRoute) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Exercise",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search exercises...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true
            )

            // Filter chip row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    IconButton(onClick = { showAnatomyPicker = true }) {
                        Icon(Icons.Default.Accessibility, contentDescription = "Body Map Filter")
                    }
                }
                items(MuscleGroup.entries) { group ->
                    val isSelected = selectedMuscleGroup == group
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                viewModel.onMuscleGroupSelected(null)
                            } else {
                                viewModel.onMuscleGroupSelected(group)
                            }
                        },
                        label = { Text(group.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Exercise list
            if (exercises.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(exercises, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = {
                                exercise.primaryMuscles.firstOrNull()?.displayName?.let {
                                    Text(it)
                                }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = equipmentIcon(exercise.equipment),
                                    contentDescription = exercise.equipment,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate(ExerciseDetailRoute(exerciseId = exercise.id))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAnatomyPicker) {
        AnatomyPickerSheet(
            selectedGroup = selectedMuscleGroup?.dbName,
            onConfirm = { dbName ->
                MuscleGroup.fromDbName(dbName)?.let { viewModel.onMuscleGroupSelected(it) }
            },
            onDismiss = { showAnatomyPicker = false }
        )
    }
}

private fun equipmentIcon(equipment: String?): ImageVector {
    return when (equipment?.lowercase()) {
        "barbell" -> Icons.Default.FitnessCenter
        "dumbbell" -> Icons.Default.FitnessCenter
        "cable" -> Icons.Default.LinearScale
        "machine" -> Icons.Default.Settings
        "body only", null -> Icons.Default.Accessibility
        else -> Icons.Default.FitnessCenter
    }
}

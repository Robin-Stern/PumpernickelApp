package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: String, navController: NavHostController) {
    val viewModel: ExerciseDetailViewModel = koinViewModel()
    val exercise by viewModel.exercise.collectAsState()

    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val ex = exercise
        if (ex == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Primary Muscles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Primary Muscles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ex.primaryMuscles.isEmpty()) {
                        Text(
                            text = "None",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ex.primaryMuscles) { muscle ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(muscle.displayName) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                // Secondary Muscles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Secondary Muscles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ex.secondaryMuscles.isEmpty()) {
                        Text(
                            text = "None",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ex.secondaryMuscles) { muscle ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(muscle.displayName) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Metadata
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataRow(label = "Equipment", value = ex.equipment ?: "None")
                    MetadataRow(label = "Level", value = ex.level.replaceFirstChar { it.uppercase() })
                    MetadataRow(
                        label = "Force",
                        value = ex.force?.replaceFirstChar { it.uppercase() } ?: "None"
                    )
                    MetadataRow(
                        label = "Mechanic",
                        value = ex.mechanic?.replaceFirstChar { it.uppercase() } ?: "None"
                    )
                    MetadataRow(
                        label = "Category",
                        value = ex.category.replaceFirstChar { it.uppercase() }
                    )
                }

                HorizontalDivider()

                // Instructions
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (ex.instructions.isEmpty()) {
                        Text(
                            text = "No instructions available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ex.instructions.forEachIndexed { index, instruction ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = instruction,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.model.SessionExercise

@Composable
fun ExerciseOverviewSheetContent(
    exercises: List<SessionExercise>,
    currentExerciseIndex: Int,
    onSelect: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val completedIndices = (0 until currentExerciseIndex).filter { idx ->
        exercises[idx].sets.any { it.isCompleted }
    }
    val pendingStart = currentExerciseIndex + 1
    val pendingExercises = if (pendingStart < exercises.size) {
        exercises.subList(pendingStart, exercises.size)
    } else {
        emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Top bar: title + Done button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercise Order",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }

        // Completed section
        if (completedIndices.isNotEmpty()) {
            item {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(completedIndices) { _, index ->
                val exercise = exercises[index]
                val completedCount = exercise.sets.count { it.isCompleted }
                ListItem(
                    headlineContent = {
                        Text(
                            text = exercise.exerciseName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "$completedCount/${exercise.targetSets} sets",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF4CAF50)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable { onSelect(index) }
                )
            }
            item { HorizontalDivider() }
        }

        // Current section
        item {
            Text(
                text = "Current",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            val currentExercise = exercises[currentExerciseIndex]
            val completedCount = currentExercise.sets.count { it.isCompleted }
            ListItem(
                headlineContent = {
                    Text(
                        text = currentExercise.exerciseName,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(text = "$completedCount/${currentExercise.targetSets} sets")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Current exercise",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    if (currentExerciseIndex + 1 < exercises.size) {
                        TextButton(onClick = {
                            onSkip()
                            onDismiss()
                        }) {
                            Text(
                                text = "Skip",
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // Up Next section
        if (pendingExercises.isNotEmpty()) {
            item { HorizontalDivider() }
            item {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(
                items = pendingExercises,
                key = { _, exercise -> exercise.exerciseId }
            ) { relativeIndex, exercise ->
                val completedCount = exercise.sets.count { it.isCompleted }
                ListItem(
                    headlineContent = {
                        Text(text = exercise.exerciseName)
                    },
                    supportingContent = {
                        Text(text = "$completedCount/${exercise.targetSets} sets")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Pending exercise",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onMove(relativeIndex, relativeIndex - 1) },
                                enabled = relativeIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up"
                                )
                            }
                            IconButton(
                                onClick = { onMove(relativeIndex, relativeIndex + 2) },
                                enabled = relativeIndex < pendingExercises.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down"
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable {
                        onSelect(pendingStart + relativeIndex)
                    }
                )
            }
        }

        // Bottom padding so content clears the sheet handle area
        item {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

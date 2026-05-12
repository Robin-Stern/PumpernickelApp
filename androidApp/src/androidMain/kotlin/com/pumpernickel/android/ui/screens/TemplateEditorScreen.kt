package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.ExercisePickerRoute
import com.pumpernickel.domain.model.TemplateExercise
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(templateId: Long?, navController: NavHostController) {
    val viewModel: TemplateEditorViewModel = koinViewModel()

    LaunchedEffect(templateId) {
        if (templateId != null) {
            viewModel.loadTemplate(templateId)
        }
    }

    val name by viewModel.name.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is TemplateEditorViewModel.SaveResult.Success -> {
                viewModel.clearSaveResult()
                navController.popBackStack()
            }
            is TemplateEditorViewModel.SaveResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearSaveResult()
            }
            null -> { /* no-op */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (templateId != null) "Edit Template" else "New Template") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = isFormValid && !isSaving
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Template name field
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.onNameChanged(it) },
                    label = { Text("Template Name") },
                    placeholder = { Text("e.g., Push Day") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Exercises header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exercises",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            navController.navigate(
                                ExercisePickerRoute(templateId = templateId ?: -1L)
                            )
                        }
                    ) {
                        Text("Add Exercise")
                    }
                }
                HorizontalDivider()
            }

            // Empty exercises state
            if (exercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No exercises added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Exercise items
            itemsIndexed(exercises, key = { _, ex -> ex.id }) { index, exercise ->
                ExerciseTargetRow(
                    exercise = exercise,
                    index = index,
                    totalCount = exercises.size,
                    onUpdateTargets = { sets, reps, restSec ->
                        viewModel.updateExerciseTargets(exercise.id, sets, reps, restSec)
                    },
                    onUpdateSetReps = { setIdx, reps ->
                        viewModel.updateSetTarget(exercise.id, setIdx, reps)
                    },
                    onRemove = { viewModel.removeExercise(exercise.id) },
                    onMoveUp = {
                        if (index > 0) viewModel.moveExercise(index, index - 1)
                    },
                    onMoveDown = {
                        if (index < exercises.size - 1) viewModel.moveExercise(index, index + 2)
                    }
                )
                HorizontalDivider()
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ExerciseTargetRow(
    exercise: TemplateExercise,
    index: Int,
    totalCount: Int,
    onUpdateTargets: (sets: Int, reps: Int, restSec: Int) -> Unit,
    onUpdateSetReps: (setIndex: Int, reps: Int) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var setsText by remember(exercise.id) { mutableStateOf("${exercise.targetSets}") }
    var repsText by remember(exercise.id) { mutableStateOf("${exercise.targetReps}") }
    var restText by remember(exercise.id) { mutableStateOf("${exercise.restPeriodSec}") }

    // Drop-set support: per-set reps mirror — iOS TemplateEditorView.swift:252-274.
    var isExpanded by remember(exercise.id) { mutableStateOf(exercise.perSetReps != null) }
    var perSetRepsTexts by remember(exercise.id) {
        mutableStateOf(
            (exercise.perSetReps ?: List(exercise.targetSets) { exercise.targetReps })
                .map { it.toString() }
        )
    }
    // Re-sync drafts when the source model changes (set count adjusted, viewModel reordered).
    LaunchedEffect(exercise.perSetReps, exercise.targetSets, exercise.targetReps) {
        perSetRepsTexts = (exercise.perSetReps ?: List(exercise.targetSets) { exercise.targetReps })
            .map { it.toString() }
    }

    fun commitChanges() {
        val sets = setsText.toIntOrNull() ?: exercise.targetSets
        val reps = repsText.toIntOrNull() ?: exercise.targetReps
        val restSec = restText.toIntOrNull() ?: exercise.restPeriodSec
        onUpdateTargets(sets, reps, restSec)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Exercise name row with reorder + delete controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                exercise.primaryMuscles.firstOrNull()?.let { muscle ->
                    Text(
                        text = muscle.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Move up button
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            // Move down button
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            // Delete button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Exercise",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        // Inline target fields row + drop-set expand toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sets field
            CompactTargetField(
                label = "Sets",
                value = setsText,
                onValueChange = { setsText = it; commitChanges() },
                modifier = Modifier.width(60.dp),
                keyboardType = KeyboardType.Number
            )
            // Reps field
            CompactTargetField(
                label = "Reps",
                value = repsText,
                onValueChange = { repsText = it; commitChanges() },
                modifier = Modifier.width(60.dp),
                keyboardType = KeyboardType.Number
            )
            // Rest field
            CompactTargetField(
                label = "Rest (s)",
                value = restText,
                onValueChange = { restText = it; commitChanges() },
                modifier = Modifier.width(70.dp),
                keyboardType = KeyboardType.Number
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { isExpanded = !isExpanded }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Drop-Sets ausblenden"
                    else "Drop-Sets bearbeiten",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Collapsible per-set reps section for drop sets.
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val setCount = exercise.targetSets
                if (setCount > 0) {
                    for (idx in 0 until setCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Set ${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp)
                            )
                            val draft = perSetRepsTexts.getOrNull(idx) ?: "0"
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { newValue ->
                                    perSetRepsTexts = perSetRepsTexts.toMutableList().also { list ->
                                        while (list.size <= idx) list.add("0")
                                        list[idx] = newValue
                                    }
                                    newValue.toIntOrNull()?.let { parsed ->
                                        onUpdateSetReps(idx, parsed)
                                    }
                                },
                                label = { Text("Reps") },
                                modifier = Modifier.width(110.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTargetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true
        )
    }
}


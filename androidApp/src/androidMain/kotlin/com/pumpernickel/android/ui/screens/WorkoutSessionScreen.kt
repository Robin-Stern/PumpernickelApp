package com.pumpernickel.android.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.components.RepsPicker
import com.pumpernickel.android.ui.components.WeightPicker
import com.pumpernickel.domain.model.CompletedExercise
import com.pumpernickel.domain.model.SessionExercise
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.presentation.workout.RestState
import com.pumpernickel.presentation.workout.WorkoutSessionState
import com.pumpernickel.presentation.workout.WorkoutSessionViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    templateId: Long,
    navController: NavHostController
) {
    val viewModel: WorkoutSessionViewModel = koinViewModel()
    val sessionState by viewModel.sessionState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val preFill by viewModel.preFill.collectAsState()
    val previousPerformance by viewModel.previousPerformance.collectAsState()
    val personalBest by viewModel.personalBest.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    var selectedReps by remember { mutableIntStateOf(0) }
    var selectedWeightKgX10 by remember { mutableIntStateOf(0) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showExerciseOverview by remember { mutableStateOf(false) }
    var showSetInput by remember { mutableStateOf(false) }

    // Edit sheet state (used in Active completed sets and Reviewing recap)
    var showEditSheet by remember { mutableStateOf(false) }
    var editExerciseIndex by remember { mutableIntStateOf(0) }
    var editSetIndex by remember { mutableIntStateOf(0) }
    var editSelectedReps by remember { mutableIntStateOf(0) }
    var editSelectedWeightKgX10 by remember { mutableIntStateOf(0) }

    // Sync pre-fill values into local picker state (D-05)
    LaunchedEffect(preFill) {
        selectedReps = preFill.reps
        selectedWeightKgX10 = snapToWeightStep(preFill.weightKgX10)
    }

    // Start workout on first composition
    LaunchedEffect(Unit) {
        viewModel.startWorkout(templateId)
    }

    when (val state = sessionState) {
        is WorkoutSessionState.Active -> {
            // Reset showSetInput when exercise/set changes (matching iOS onChange behavior)
            LaunchedEffect(state.currentSetIndex, state.currentExerciseIndex) {
                showSetInput = false
            }

            ActiveWorkoutContent(
                active = state,
                elapsedSeconds = elapsedSeconds,
                selectedReps = selectedReps,
                selectedWeightKgX10 = selectedWeightKgX10,
                showSetInput = showSetInput,
                showAbandonDialog = showAbandonDialog,
                showExerciseOverview = showExerciseOverview,
                previousPerformance = previousPerformance,
                personalBest = personalBest,
                weightUnit = weightUnit,
                onRepsChanged = { selectedReps = it },
                onWeightChanged = { selectedWeightKgX10 = it },
                onShowSetInput = { showSetInput = it },
                onShowAbandonDialog = { showAbandonDialog = it },
                onShowExerciseOverview = { showExerciseOverview = it },
                onCompleteSet = { reps, weight ->
                    viewModel.completeSet(reps, weight)
                },
                onSkipRest = { viewModel.skipRest() },
                onSkipExercise = { viewModel.skipExercise() },
                onJumpToExercise = { index -> viewModel.jumpToExercise(index) },
                onReorderExercise = { from, to -> viewModel.reorderExercise(from, to) },
                onEnterReview = { viewModel.enterReview() },
                onSaveReviewedWorkout = { viewModel.saveReviewedWorkout() },
                onDiscardWorkout = {
                    viewModel.discardWorkout()
                    navController.popBackStack()
                },
                onPopBackStack = { navController.popBackStack() },
                onEditCompletedSet = { exIdx, setIdx, reps, weightKgX10 ->
                    editExerciseIndex = exIdx
                    editSetIndex = setIdx
                    editSelectedReps = reps
                    editSelectedWeightKgX10 = snapToWeightStep(weightKgX10)
                    showEditSheet = true
                }
            )
            // Edit sheet for Active state completed sets
            if (showEditSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showEditSheet = false }
                ) {
                    EditSetSheetContent(
                        setIndex = editSetIndex,
                        selectedReps = editSelectedReps,
                        selectedWeightKgX10 = editSelectedWeightKgX10,
                        weightUnit = weightUnit,
                        onRepsChanged = { editSelectedReps = it },
                        onWeightChanged = { editSelectedWeightKgX10 = it },
                        onSave = {
                            viewModel.editCompletedSet(
                                editExerciseIndex, editSetIndex,
                                editSelectedReps, editSelectedWeightKgX10
                            )
                            showEditSheet = false
                        },
                        onCancel = { showEditSheet = false }
                    )
                }
            }
        }
        is WorkoutSessionState.Reviewing -> {
            RecapContent(
                reviewing = state,
                weightUnit = weightUnit,
                onSaveWorkout = { viewModel.saveReviewedWorkout() },
                onEditSet = { exIdx, setIdx, reps, weightKgX10 ->
                    editExerciseIndex = exIdx
                    editSetIndex = setIdx
                    editSelectedReps = reps
                    editSelectedWeightKgX10 = snapToWeightStep(weightKgX10)
                    showEditSheet = true
                }
            )
            if (showEditSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showEditSheet = false }
                ) {
                    EditSetSheetContent(
                        setIndex = editSetIndex,
                        selectedReps = editSelectedReps,
                        selectedWeightKgX10 = editSelectedWeightKgX10,
                        weightUnit = weightUnit,
                        onRepsChanged = { editSelectedReps = it },
                        onWeightChanged = { editSelectedWeightKgX10 = it },
                        onSave = {
                            viewModel.editCompletedSet(
                                editExerciseIndex, editSetIndex,
                                editSelectedReps, editSelectedWeightKgX10
                            )
                            showEditSheet = false
                        },
                        onCancel = { showEditSheet = false }
                    )
                }
            }
        }
        is WorkoutSessionState.Finished -> {
            FinishedContent(
                finished = state,
                onDone = {
                    viewModel.resetToIdle()
                    navController.popBackStack()
                }
            )
        }
        is WorkoutSessionState.Idle -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutContent(
    active: WorkoutSessionState.Active,
    elapsedSeconds: Long,
    selectedReps: Int,
    selectedWeightKgX10: Int,
    showSetInput: Boolean,
    showAbandonDialog: Boolean,
    showExerciseOverview: Boolean,
    previousPerformance: Map<String, CompletedExercise>,
    personalBest: Map<String, Int>,
    weightUnit: WeightUnit,
    onRepsChanged: (Int) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onShowSetInput: (Boolean) -> Unit,
    onShowAbandonDialog: (Boolean) -> Unit,
    onShowExerciseOverview: (Boolean) -> Unit,
    onCompleteSet: (Int, Int) -> Unit,
    onSkipRest: () -> Unit,
    onSkipExercise: () -> Unit,
    onJumpToExercise: (Int) -> Unit,
    onReorderExercise: (Int, Int) -> Unit,
    onEnterReview: () -> Unit,
    onSaveReviewedWorkout: () -> Unit,
    onDiscardWorkout: () -> Unit,
    onPopBackStack: () -> Unit,
    onEditCompletedSet: (exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) -> Unit = { _, _, _, _ -> }
) {
    val exercises = active.exercises
    val exIdx = active.currentExerciseIndex
    val setIdx = active.currentSetIndex
    val exercise = exercises[exIdx]

    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(active.templateName) },
                navigationIcon = {
                    IconButton(onClick = {
                        val hasCompletedSets = exercises.any { ex -> ex.sets.any { it.isCompleted } }
                        if (hasCompletedSets) {
                            onShowAbandonDialog(true)
                        } else {
                            onDiscardWorkout()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close workout"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Workout actions"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Skip Exercise") },
                                onClick = {
                                    showMenu = false
                                    onSkipExercise()
                                },
                                enabled = exIdx + 1 < exercises.size
                            )
                            DropdownMenuItem(
                                text = { Text("Exercise Overview") },
                                onClick = {
                                    showMenu = false
                                    onShowExerciseOverview(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Finish Workout") },
                                onClick = {
                                    showMenu = false
                                    onEnterReview()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Header section
            HeaderSection(
                exercise = exercise,
                exIdx = exIdx,
                setIdx = setIdx,
                totalExercises = exercises.size,
                elapsedSeconds = elapsedSeconds,
                previousPerformance = previousPerformance,
                personalBest = personalBest,
                weightUnit = weightUnit
            )

            // 2. Rest timer OR set input
            when (val restState = active.restState) {
                is RestState.Resting -> {
                    RestTimerSection(
                        restState = restState,
                        onSkip = onSkipRest
                    )
                }
                is RestState.RestComplete -> {
                    RestCompleteSection(onContinue = onSkipRest)
                }
                is RestState.NotResting -> {
                    if (showSetInput) {
                        SetInputSection(
                            selectedReps = selectedReps,
                            selectedWeightKgX10 = selectedWeightKgX10,
                            weightUnit = weightUnit,
                            onRepsChanged = onRepsChanged,
                            onWeightChanged = onWeightChanged,
                            onCompleteSet = {
                                @Suppress("DEPRECATION")
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(
                                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                    )
                                } else {
                                    vibrator?.vibrate(50)
                                }
                                onCompleteSet(selectedReps, selectedWeightKgX10)
                            },
                            isEnabled = selectedReps > 0
                        )
                    } else {
                        MinimalSetScreen(
                            setNumber = setIdx + 1,
                            exerciseName = exercise.exerciseName,
                            onTap = { onShowSetInput(true) }
                        )
                    }
                }
            }

            // 3. Completed sets section
            CompletedSetsSection(
                exercise = exercise,
                exerciseIndex = exIdx,
                weightUnit = weightUnit,
                onEditSet = onEditCompletedSet
            )
        }
    }

    // Abandon dialog (D-13, D-14, D-15)
    if (showAbandonDialog) {
        val completedSetsCount = active.exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }
        AlertDialog(
            onDismissRequest = { onShowAbandonDialog(false) },
            title = { Text("Abandon Workout?") },
            text = {
                Text(
                    "Exercise ${active.currentExerciseIndex + 1}/${active.exercises.size}, " +
                        "$completedSetsCount sets completed"
                )
            },
            confirmButton = {
                Button(onClick = {
                    onEnterReview()
                    onSaveReviewedWorkout()
                    onPopBackStack()
                    onShowAbandonDialog(false)
                }) { Text("Save & Exit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onShowAbandonDialog(false) }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onDiscardWorkout()
                        onShowAbandonDialog(false)
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // Exercise overview sheet (D-09)
    if (showExerciseOverview) {
        ModalBottomSheet(
            onDismissRequest = { onShowExerciseOverview(false) }
        ) {
            ExerciseOverviewSheetContent(
                exercises = exercises,
                currentExerciseIndex = exIdx,
                onSelect = { index ->
                    onJumpToExercise(index)
                    onShowExerciseOverview(false)
                },
                onMove = { from, to ->
                    onReorderExercise(from, to)
                },
                onSkip = {
                    onSkipExercise()
                },
                onDismiss = { onShowExerciseOverview(false) }
            )
        }
    }
}

// MARK: - Header Section

@Composable
private fun HeaderSection(
    exercise: SessionExercise,
    exIdx: Int,
    setIdx: Int,
    totalExercises: Int,
    elapsedSeconds: Long,
    previousPerformance: Map<String, CompletedExercise>,
    personalBest: Map<String, Int>,
    weightUnit: WeightUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Exercise ${exIdx + 1} of $totalExercises",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatElapsed(elapsedSeconds),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = exercise.exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Set ${setIdx + 1} of ${exercise.targetSets}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        // Previous performance (D-08, D-09)
        val prevExercise = previousPerformance[exercise.exerciseId]
        if (prevExercise != null) {
            val prevText = formatPreviousPerformance(prevExercise, weightUnit)
            if (prevText.isNotEmpty()) {
                Text(
                    text = "Last: $prevText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Personal best (ENTRY-07)
        val pbKgX10 = personalBest[exercise.exerciseId]
        if (pbKgX10 != null) {
            Text(
                text = "PB: ${weightUnit.formatWeight(pbKgX10)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2196F3), // semantic "info blue" — no M3 equivalent
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// MARK: - Rest Timer Section

@Composable
private fun RestTimerSection(
    restState: RestState.Resting,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Rest",
            style = MaterialTheme.typography.headlineSmall
        )

        val isAlmostDone = restState.remainingSeconds <= 3
        Text(
            text = restState.remainingSeconds.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
            color = if (isAlmostDone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        val progress = if (restState.totalSeconds > 0) {
            restState.remainingSeconds.toFloat() / restState.totalSeconds.toFloat()
        } else {
            0f
        }
        CircularProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        )

        TextButton(onClick = onSkip) {
            Text(
                text = "Skip Rest",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Rest Complete Section

@Composable
private fun RestCompleteSection(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Rest Complete!",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(48.dp)
        ) {
            Text("Continue")
        }
    }
}

// MARK: - Minimal Set Screen

@Composable
private fun MinimalSetScreen(
    setNumber: Int,
    exerciseName: String,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "SET",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = setNumber.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "Tap when done",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

// MARK: - Set Input Section

@Composable
private fun SetInputSection(
    selectedReps: Int,
    selectedWeightKgX10: Int,
    weightUnit: WeightUnit,
    onRepsChanged: (Int) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onCompleteSet: () -> Unit,
    isEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            RepsPicker(
                selectedReps = selectedReps,
                onRepsSelected = onRepsChanged,
                modifier = Modifier.weight(1f)
            )
            WeightPicker(
                selectedWeightKgX10 = selectedWeightKgX10,
                onWeightSelected = onWeightChanged,
                weightUnit = weightUnit,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = onCompleteSet,
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(48.dp)
        ) {
            Text("Complete Set")
        }
    }
}

// MARK: - Completed Sets Section

@Composable
private fun CompletedSetsSection(
    exercise: SessionExercise,
    exerciseIndex: Int,
    weightUnit: WeightUnit,
    onEditSet: (exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) -> Unit = { _, _, _, _ -> }
) {
    val completedSets = exercise.sets.filter { it.isCompleted }
    if (completedSets.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Completed Sets",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        completedSets.forEach { set ->
            val reps = set.actualReps ?: 0
            val weight = set.actualWeightKgX10 ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        onEditSet(exerciseIndex, set.setIndex, reps, weight)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Set ${set.setIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$reps reps",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = weightUnit.formatWeight(weight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// MARK: - Reviewing State

@Composable
private fun RecapContent(
    reviewing: WorkoutSessionState.Reviewing,
    weightUnit: WeightUnit,
    onSaveWorkout: () -> Unit,
    onEditSet: (exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) -> Unit
) {
    val completedExercises = reviewing.exercises.filter { ex -> ex.sets.any { it.isCompleted } }
    val totalSets = completedExercises.sumOf { ex -> ex.sets.count { it.isCompleted } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary header card (D-19)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Workout Recap",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reviewing.templateName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = completedExercises.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exercises",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalSets.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDuration(reviewing.durationMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Exercise sections (D-20)
        reviewing.exercises.forEachIndexed { originalIndex, exercise ->
            val completedSets = exercise.sets.filter { it.isCompleted }
            if (completedSets.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exercise.exerciseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${completedSets.size} sets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // Set rows — tappable for edit (D-21)
                        completedSets.forEach { set ->
                            val reps = set.actualReps ?: 0
                            val weight = set.actualWeightKgX10 ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onEditSet(originalIndex, set.setIndex, reps, weight)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Set ${set.setIndex + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "$reps reps",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = weightUnit.formatWeight(weight),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // Save Workout button (D-22)
        Button(
            onClick = onSaveWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Save Workout", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EditSetSheetContent(
    setIndex: Int,
    selectedReps: Int,
    selectedWeightKgX10: Int,
    weightUnit: WeightUnit,
    onRepsChanged: (Int) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Edit Set ${setIndex + 1}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            RepsPicker(
                selectedReps = selectedReps,
                onRepsSelected = onRepsChanged,
                modifier = Modifier.weight(1f)
            )
            WeightPicker(
                selectedWeightKgX10 = selectedWeightKgX10,
                onWeightSelected = onWeightChanged,
                weightUnit = weightUnit,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Save", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.height(32.dp))
    }
}

// MARK: - Finished State

@Composable
private fun FinishedContent(
    finished: WorkoutSessionState.Finished,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Workout Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow("Workout", finished.workoutName)
                SummaryRow("Duration", formatDuration(finished.durationMillis))
                SummaryRow("Exercises", finished.totalExercises.toString())
                SummaryRow("Sets", finished.totalSets.toString())
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// MARK: - Helpers

private fun snapToWeightStep(kgX10: Int): Int = ((kgX10 + 12) / 25) * 25

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%dh %02dm", h, m)
    else String.format("%dm %02ds", m, s)
}

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

private fun formatPreviousPerformance(exercise: CompletedExercise, weightUnit: WeightUnit): String {
    val sets = exercise.sets
    if (sets.isEmpty()) return ""
    val first = sets[0]
    val allSame = sets.all {
        it.actualReps == first.actualReps && it.actualWeightKgX10 == first.actualWeightKgX10
    }
    return if (allSame) {
        "${sets.size}x${first.actualReps} @ ${weightUnit.formatWeight(first.actualWeightKgX10)}"
    } else {
        sets.joinToString(", ") { "${it.actualReps}x${weightUnit.formatWeight(it.actualWeightKgX10)}" }
    }
}

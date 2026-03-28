package com.pumpernickel.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.model.CompletedExercise
import com.pumpernickel.domain.model.CompletedSet
import com.pumpernickel.domain.model.CompletedWorkout
import com.pumpernickel.domain.model.SessionExercise
import com.pumpernickel.domain.model.SessionSet
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// -- State definitions --

sealed class WorkoutSessionState {
    data object Idle : WorkoutSessionState()

    data class Active(
        val templateId: Long,
        val templateName: String,
        val exercises: List<SessionExercise>,
        val currentExerciseIndex: Int,
        val currentSetIndex: Int,
        val startTimeMillis: Long,
        val restState: RestState = RestState.NotResting
    ) : WorkoutSessionState()

    data class Finished(
        val workoutName: String,
        val durationMillis: Long,
        val totalSets: Int,
        val totalExercises: Int
    ) : WorkoutSessionState()
}

sealed class RestState {
    data object NotResting : RestState()
    data class Resting(val remainingSeconds: Int, val totalSeconds: Int) : RestState()
    data object RestComplete : RestState()
}

// -- ViewModel --

class WorkoutSessionViewModel(
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _sessionState = MutableStateFlow<WorkoutSessionState>(WorkoutSessionState.Idle)
    @NativeCoroutinesState
    val sessionState: StateFlow<WorkoutSessionState> = _sessionState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    @NativeCoroutinesState
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _hasActiveSession = MutableStateFlow(false)
    @NativeCoroutinesState
    val hasActiveSession: StateFlow<Boolean> = _hasActiveSession.asStateFlow()

    private var timerJob: Job? = null
    private var elapsedJob: Job? = null

    // -- Public methods --

    /**
     * Check Room for an unfinished active session.
     * SwiftUI observes hasActiveSessionFlow via asyncSequence and shows the
     * resume/discard prompt when this emits true (D-14, WORK-09).
     */
    fun checkForActiveSession() {
        viewModelScope.launch {
            _hasActiveSession.value = workoutRepository.hasActiveSession()
        }
    }

    /**
     * Start a new workout from a template (D-03, WORK-01).
     * Maps TemplateExercises to SessionExercises with pre-filled targets (D-09).
     */
    fun startWorkout(templateId: Long) {
        viewModelScope.launch {
            val template = templateRepository.getTemplateById(templateId).first() ?: return@launch

            val exercises = template.exercises.map { te ->
                SessionExercise(
                    exerciseId = te.exerciseId,
                    exerciseName = te.exerciseName,
                    targetSets = te.targetSets,
                    targetReps = te.targetReps,
                    targetWeightKgX10 = te.targetWeightKgX10,
                    restPeriodSec = te.restPeriodSec,
                    sets = (0 until te.targetSets).map { idx ->
                        SessionSet(
                            setIndex = idx,
                            targetReps = te.targetReps,
                            targetWeightKgX10 = te.targetWeightKgX10,
                            actualReps = null,
                            actualWeightKgX10 = null,
                            isCompleted = false
                        )
                    }
                )
            }

            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            workoutRepository.createActiveSession(templateId, template.name, now)

            _sessionState.value = WorkoutSessionState.Active(
                templateId = templateId,
                templateName = template.name,
                exercises = exercises,
                currentExerciseIndex = 0,
                currentSetIndex = 0,
                startTimeMillis = now
            )
            _hasActiveSession.value = true
            startElapsedTicker()
        }
    }

    /**
     * Resume a previously interrupted workout from Room data (D-14, WORK-09).
     * Reconstructs full session state from the template + completed sets.
     */
    fun resumeWorkout() {
        viewModelScope.launch {
            val activeSession = workoutRepository.getActiveSession() ?: return@launch
            val template = templateRepository.getTemplateById(activeSession.templateId).first()
                ?: return@launch

            // Build exercises from template
            val exercises = template.exercises.map { te ->
                SessionExercise(
                    exerciseId = te.exerciseId,
                    exerciseName = te.exerciseName,
                    targetSets = te.targetSets,
                    targetReps = te.targetReps,
                    targetWeightKgX10 = te.targetWeightKgX10,
                    restPeriodSec = te.restPeriodSec,
                    sets = (0 until te.targetSets).map { idx ->
                        SessionSet(
                            setIndex = idx,
                            targetReps = te.targetReps,
                            targetWeightKgX10 = te.targetWeightKgX10,
                            actualReps = null,
                            actualWeightKgX10 = null,
                            isCompleted = false
                        )
                    }
                )
            }

            // Overlay completed sets from Room
            val updatedExercises = exercises.mapIndexed { exIdx, exercise ->
                val completedForExercise = activeSession.completedSets
                    .filter { it.exerciseIndex == exIdx }
                val updatedSets = exercise.sets.map { set ->
                    val completed = completedForExercise.find { it.setIndex == set.setIndex }
                    if (completed != null) {
                        set.copy(
                            actualReps = completed.actualReps,
                            actualWeightKgX10 = completed.actualWeightKgX10,
                            isCompleted = true
                        )
                    } else {
                        set
                    }
                }
                exercise.copy(sets = updatedSets)
            }

            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val elapsed = (now - activeSession.startTimeMillis) / 1000

            _sessionState.value = WorkoutSessionState.Active(
                templateId = activeSession.templateId,
                templateName = activeSession.templateName,
                exercises = updatedExercises,
                currentExerciseIndex = activeSession.currentExerciseIndex,
                currentSetIndex = activeSession.currentSetIndex,
                startTimeMillis = activeSession.startTimeMillis
            )
            startElapsedTicker(elapsed)
        }
    }

    /**
     * Complete the current set with actual reps and weight (D-10, WORK-02, WORK-03).
     * Persists to Room immediately for crash recovery (D-13).
     * Starts rest timer after completion (D-04).
     */
    fun completeSet(reps: Int, weightKgX10: Int) {
        viewModelScope.launch {
            val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch
            val exIdx = active.currentExerciseIndex
            val setIdx = active.currentSetIndex

            // Update the set in the exercises list
            val updatedExercises = active.exercises.mapIndexed { eIdx, exercise ->
                if (eIdx == exIdx) {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setIndex == setIdx) {
                                set.copy(
                                    actualReps = reps,
                                    actualWeightKgX10 = weightKgX10,
                                    isCompleted = true
                                )
                            } else set
                        }
                    )
                } else exercise
            }

            // Persist to Room before updating state
            workoutRepository.saveCompletedSet(
                exIdx, setIdx, reps, weightKgX10,
                kotlin.time.Clock.System.now().toEpochMilliseconds()
            )

            // Compute next cursor position
            val currentExercise = active.exercises[exIdx]
            val nextCursor = computeNextCursor(exIdx, setIdx, active.exercises)

            // Update cursor in Room
            workoutRepository.updateCursor(nextCursor.first, nextCursor.second)

            // Get rest period for current exercise
            val restPeriodSec = currentExercise.restPeriodSec

            // Update state with completed set, new cursor, and rest state
            _sessionState.value = active.copy(
                exercises = updatedExercises,
                currentExerciseIndex = nextCursor.first,
                currentSetIndex = nextCursor.second,
                restState = RestState.NotResting
            )

            // Start rest timer
            if (restPeriodSec > 0) {
                startRestTimer(restPeriodSec)
            }
        }
    }

    /**
     * Skip the active rest timer (D-05).
     * Cancels the countdown and sets state to NotResting.
     */
    fun skipRest() {
        timerJob?.cancel()
        val current = _sessionState.value as? WorkoutSessionState.Active ?: return
        _sessionState.value = current.copy(restState = RestState.NotResting)
    }

    /**
     * Edit a previously completed set's values (D-11).
     */
    fun editCompletedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int) {
        viewModelScope.launch {
            val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch

            val updatedExercises = active.exercises.mapIndexed { eIdx, exercise ->
                if (eIdx == exerciseIndex) {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setIndex == setIndex) {
                                set.copy(
                                    actualReps = reps,
                                    actualWeightKgX10 = weightKgX10
                                )
                            } else set
                        }
                    )
                } else exercise
            }

            workoutRepository.updateSetValues(exerciseIndex, setIndex, reps, weightKgX10)
            _sessionState.value = active.copy(exercises = updatedExercises)
        }
    }

    /**
     * Jump to a specific exercise (D-02).
     * Finds the first incomplete set in the target exercise.
     */
    fun jumpToExercise(exerciseIndex: Int) {
        timerJob?.cancel()
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return
        if (exerciseIndex < 0 || exerciseIndex >= active.exercises.size) return

        val targetExercise = active.exercises[exerciseIndex]
        val firstIncompleteSet = targetExercise.sets
            .indexOfFirst { !it.isCompleted }
            .let { if (it == -1) 0 else it }

        _sessionState.value = active.copy(
            currentExerciseIndex = exerciseIndex,
            currentSetIndex = firstIncompleteSet,
            restState = RestState.NotResting
        )
    }

    /**
     * Finish the workout (D-16, D-17, D-18, WORK-07, WORK-08).
     * Saves completed workout to history and clears active session.
     */
    fun finishWorkout() {
        viewModelScope.launch {
            val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch

            timerJob?.cancel()
            elapsedJob?.cancel()

            val endTimeMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val durationMillis = endTimeMillis - active.startTimeMillis

            // Build CompletedWorkout from active state (only completed sets)
            val completedExercises = active.exercises.mapIndexedNotNull { order, exercise ->
                val completedSets = exercise.sets
                    .filter { it.isCompleted }
                    .map { set ->
                        CompletedSet(
                            setIndex = set.setIndex,
                            actualReps = set.actualReps ?: 0,
                            actualWeightKgX10 = set.actualWeightKgX10 ?: 0
                        )
                    }
                if (completedSets.isNotEmpty()) {
                    CompletedExercise(
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.exerciseName,
                        exerciseOrder = order,
                        sets = completedSets
                    )
                } else null
            }

            val completedWorkout = CompletedWorkout(
                id = 0,
                templateId = active.templateId,
                name = active.templateName,
                startTimeMillis = active.startTimeMillis,
                endTimeMillis = endTimeMillis,
                durationMillis = durationMillis,
                exercises = completedExercises
            )

            workoutRepository.saveCompletedWorkout(completedWorkout)
            workoutRepository.clearActiveSession()

            _hasActiveSession.value = false

            val totalSets = completedExercises.sumOf { it.sets.size }
            _sessionState.value = WorkoutSessionState.Finished(
                workoutName = active.templateName,
                durationMillis = durationMillis,
                totalSets = totalSets,
                totalExercises = completedExercises.size
            )
        }
    }

    /**
     * Discard an active workout without saving (D-14 discard option).
     */
    fun discardWorkout() {
        viewModelScope.launch {
            timerJob?.cancel()
            elapsedJob?.cancel()
            workoutRepository.clearActiveSession()
            _hasActiveSession.value = false
            _sessionState.value = WorkoutSessionState.Idle
        }
    }

    /**
     * Reset to idle after the Finished screen is dismissed.
     */
    fun resetToIdle() {
        _sessionState.value = WorkoutSessionState.Idle
        _elapsedSeconds.value = 0L
    }

    // -- Private helpers --

    /**
     * Compute the next cursor position after completing a set.
     * Returns (exerciseIndex, setIndex).
     */
    private fun computeNextCursor(
        currentExerciseIndex: Int,
        currentSetIndex: Int,
        exercises: List<SessionExercise>
    ): Pair<Int, Int> {
        val currentExercise = exercises[currentExerciseIndex]
        // More sets in current exercise?
        if (currentSetIndex + 1 < currentExercise.sets.size) {
            return Pair(currentExerciseIndex, currentSetIndex + 1)
        }
        // More exercises?
        if (currentExerciseIndex + 1 < exercises.size) {
            return Pair(currentExerciseIndex + 1, 0)
        }
        // Last set of last exercise -- keep cursor at end
        return Pair(currentExerciseIndex, currentSetIndex)
    }

    /**
     * Rest timer with wall-clock anchoring to avoid drift (Research Pitfall 1).
     */
    private fun startRestTimer(durationSeconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
            var remaining = durationSeconds
            updateRestState(RestState.Resting(remaining, durationSeconds))
            while (remaining > 0) {
                delay(1000L)
                remaining = durationSeconds - ((kotlin.time.Clock.System.now().toEpochMilliseconds() - startTime) / 1000).toInt()
                if (remaining < 0) remaining = 0
                updateRestState(RestState.Resting(remaining, durationSeconds))
            }
            updateRestState(RestState.RestComplete)
        }
    }

    /**
     * Elapsed time ticker that increments every second while workout is active.
     */
    private fun startElapsedTicker(initialSeconds: Long = 0L) {
        _elapsedSeconds.value = initialSeconds
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedSeconds.value++
            }
        }
    }

    /**
     * Update the rest state within the current Active session state.
     */
    private fun updateRestState(restState: RestState) {
        val current = _sessionState.value as? WorkoutSessionState.Active ?: return
        _sessionState.value = current.copy(restState = restState)
    }
}

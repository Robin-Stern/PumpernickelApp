package com.pumpernickel.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.model.CompletedExercise
import com.pumpernickel.domain.model.CompletedSet
import com.pumpernickel.domain.model.CompletedWorkout
import com.pumpernickel.domain.model.SessionExercise
import com.pumpernickel.domain.model.SessionSet
import com.pumpernickel.domain.model.SetPreFill
import com.pumpernickel.domain.model.WeightUnit
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

    data class Reviewing(
        val templateId: Long,
        val templateName: String,
        val exercises: List<SessionExercise>,
        val startTimeMillis: Long,
        val durationMillis: Long
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
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository
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

    // Previous performance keyed by exerciseId (HIST-04, D-08, D-09)
    private val _previousPerformance = MutableStateFlow<Map<String, CompletedExercise>>(emptyMap())
    @NativeCoroutinesState
    val previousPerformance: StateFlow<Map<String, CompletedExercise>> = _previousPerformance.asStateFlow()

    // Pre-fill values for current set (ENTRY-04, ENTRY-05)
    private val _preFill = MutableStateFlow(SetPreFill(reps = 0, weightKgX10 = 0))
    @NativeCoroutinesState
    val preFill: StateFlow<SetPreFill> = _preFill.asStateFlow()

    // Personal best keyed by exerciseId (ENTRY-07)
    private val _personalBest = MutableStateFlow<Map<String, Int>>(emptyMap())
    @NativeCoroutinesState
    val personalBest: StateFlow<Map<String, Int>> = _personalBest.asStateFlow()

    // Weight unit for display (NAV-03, D-15)
    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    private var timerJob: Job? = null
    private var elapsedJob: Job? = null
    private var templateOriginalIndices: MutableList<Int> = mutableListOf()

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
                    targetWeightKgX10 = 0,
                    restPeriodSec = te.restPeriodSec,
                    sets = (0 until te.targetSets).map { idx ->
                        SessionSet(
                            setIndex = idx,
                            targetReps = te.perSetReps?.getOrNull(idx) ?: te.targetReps,
                            targetWeightKgX10 = 0,
                            actualReps = null,
                            actualWeightKgX10 = null,
                            isCompleted = false
                        )
                    }
                )
            }

            // Load previous performance (HIST-04, D-09)
            val previousWorkout = workoutRepository.getPreviousPerformance(templateId)
            if (previousWorkout != null) {
                _previousPerformance.value = previousWorkout.exercises.associateBy { it.exerciseId }
            } else {
                _previousPerformance.value = emptyMap()
            }

            // Load personal bests (ENTRY-07)
            val exerciseIds = exercises.map { it.exerciseId }
            _personalBest.value = workoutRepository.getPersonalBests(exerciseIds)

            // Initialize template-original index mapping (FLOW-03, FLOW-04)
            templateOriginalIndices = exercises.indices.toMutableList()

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
            // Emit initial pre-fill for first set of first exercise (ENTRY-05)
            _preFill.value = computePreFill(exercises[0], 0)
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

            // Load previous performance for resumed workout (HIST-04, D-09)
            val previousWorkout = workoutRepository.getPreviousPerformance(activeSession.templateId)
            if (previousWorkout != null) {
                _previousPerformance.value = previousWorkout.exercises.associateBy { it.exerciseId }
            } else {
                _previousPerformance.value = emptyMap()
            }

            // Build exercises from template
            val exercises = template.exercises.map { te ->
                SessionExercise(
                    exerciseId = te.exerciseId,
                    exerciseName = te.exerciseName,
                    targetSets = te.targetSets,
                    targetReps = te.targetReps,
                    targetWeightKgX10 = 0,
                    restPeriodSec = te.restPeriodSec,
                    sets = (0 until te.targetSets).map { idx ->
                        SessionSet(
                            setIndex = idx,
                            targetReps = te.perSetReps?.getOrNull(idx) ?: te.targetReps,
                            targetWeightKgX10 = 0,
                            actualReps = null,
                            actualWeightKgX10 = null,
                            isCompleted = false
                        )
                    }
                )
            }

            // Load personal bests (ENTRY-07)
            val exerciseIdsForPb = exercises.map { it.exerciseId }
            _personalBest.value = workoutRepository.getPersonalBests(exerciseIdsForPb)

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
                            isCompleted = true,
                            rir = completed.rir
                        )
                    } else {
                        set
                    }
                }
                exercise.copy(sets = updatedSets)
            }

            // Apply persisted exercise order (D-08, FLOW-04)
            val orderString = activeSession.exerciseOrder
            val (orderedExercises, originalIndices) = if (orderString.isNotEmpty()) {
                val indices = orderString.split(",").mapNotNull { it.toIntOrNull() }
                if (indices.size == updatedExercises.size) {
                    val reordered = indices.map { idx -> updatedExercises[idx] }
                    Pair(reordered, indices.toMutableList())
                } else {
                    // Malformed order string -- fall back to template order
                    Pair(updatedExercises, updatedExercises.indices.toMutableList())
                }
            } else {
                // Pre-migration session or no reorder -- use template order
                Pair(updatedExercises, updatedExercises.indices.toMutableList())
            }
            templateOriginalIndices = originalIndices

            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val elapsed = (now - activeSession.startTimeMillis) / 1000

            _sessionState.value = WorkoutSessionState.Active(
                templateId = activeSession.templateId,
                templateName = activeSession.templateName,
                exercises = orderedExercises,
                currentExerciseIndex = activeSession.currentExerciseIndex,
                currentSetIndex = activeSession.currentSetIndex,
                startTimeMillis = activeSession.startTimeMillis
            )
            // Emit pre-fill for resumed cursor position
            val resumeExercise = orderedExercises[activeSession.currentExerciseIndex]
            _preFill.value = computePreFill(resumeExercise, activeSession.currentSetIndex)
            startElapsedTicker(elapsed)
        }
    }

    /**
     * Complete the current set with actual reps and weight (D-10, WORK-02, WORK-03).
     * Persists to Room immediately for crash recovery (D-13).
     * Starts rest timer after completion (D-04).
     */
    fun completeSet(reps: Int, weightKgX10: Int, rir: Int = 2) {
        viewModelScope.launch {
            // ENTRY-06: Reject 0-rep sets
            if (reps <= 0) return@launch
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
                                    isCompleted = true,
                                    rir = rir
                                )
                            } else set
                        }
                    )
                } else exercise
            }

            // Persist to Room using template-original index (FLOW-04)
            val templateExIdx = if (templateOriginalIndices.isNotEmpty()) {
                templateOriginalIndices[exIdx]
            } else {
                exIdx
            }
            workoutRepository.saveCompletedSet(
                templateExIdx, setIdx, reps, weightKgX10,
                kotlin.time.Clock.System.now().toEpochMilliseconds(),
                rir
            )

            // Compute next cursor position
            val currentExercise = active.exercises[exIdx]
            val nextCursor = computeNextCursor(exIdx, setIdx, active.exercises)

            // Last set of last exercise -- auto-transition to review
            if (nextCursor.first == exIdx && nextCursor.second == setIdx) {
                workoutRepository.updateCursor(nextCursor.first, nextCursor.second)
                // Update exercises with the completed set before entering review
                _sessionState.value = active.copy(exercises = updatedExercises)
                enterReview()
                return@launch
            }

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

            // Update pre-fill for the new cursor position (ENTRY-04)
            val nextExercise = updatedExercises[nextCursor.first]
            _preFill.value = computePreFill(nextExercise, nextCursor.second)

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
     * Handles both Active and Reviewing states for recap editing (Pitfall 1).
     */
    fun editCompletedSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int, rir: Int = 2) {
        viewModelScope.launch {
            val currentState = _sessionState.value
            val exercises = when (currentState) {
                is WorkoutSessionState.Active -> currentState.exercises
                is WorkoutSessionState.Reviewing -> currentState.exercises
                else -> return@launch
            }

            val updatedExercises = exercises.mapIndexed { eIdx, exercise ->
                if (eIdx == exerciseIndex) {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setIndex == setIndex) {
                                set.copy(
                                    actualReps = reps,
                                    actualWeightKgX10 = weightKgX10,
                                    rir = rir
                                )
                            } else set
                        }
                    )
                } else exercise
            }

            workoutRepository.updateSetValues(exerciseIndex, setIndex, reps, weightKgX10, rir)

            _sessionState.value = when (currentState) {
                is WorkoutSessionState.Active -> currentState.copy(exercises = updatedExercises)
                is WorkoutSessionState.Reviewing -> currentState.copy(exercises = updatedExercises)
                else -> return@launch
            }
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

        // Update pre-fill for jumped-to exercise (ENTRY-05 for first set, ENTRY-04 for subsequent)
        _preFill.value = computePreFill(targetExercise, firstIncompleteSet)
    }

    /**
     * Reorder a pending exercise within the exercises list (D-01, D-02, FLOW-03).
     * from/to are relative to the pending sublist (exercises after currentExerciseIndex),
     * using move(fromOffset, toOffset) semantics matching SwiftUI .onMove.
     */
    fun reorderExercise(from: Int, to: Int) {
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return
        val pendingStart = active.currentExerciseIndex + 1
        // Convert from/to from pending-relative to absolute indices
        val absFrom = pendingStart + from
        val absTo = pendingStart + to
        if (absFrom < pendingStart || absFrom >= active.exercises.size) return
        if (absTo < pendingStart || absTo > active.exercises.size) return  // toOffset can equal size (append)

        val list = active.exercises.toMutableList()
        val item = list.removeAt(absFrom)
        // After removal, if absTo > absFrom, the effective insertion index is absTo - 1
        val insertAt = if (absTo > absFrom) absTo - 1 else absTo
        list.add(insertAt, item)

        // Reorder templateOriginalIndices in parallel
        val idxItem = templateOriginalIndices.removeAt(absFrom)
        templateOriginalIndices.add(insertAt, idxItem)

        _sessionState.value = active.copy(exercises = list)
        persistExerciseOrder()
    }

    /**
     * Skip the current exercise and place it after the next exercise (FLOW-07).
     * Example: [A, B, C] skip A → [B, A, C] (cursor stays at index 0, now on B).
     * No-op on the last exercise (Pitfall 3).
     */
    fun skipExercise() {
        timerJob?.cancel()
        val active = _sessionState.value as? WorkoutSessionState.Active ?: return
        val currentIdx = active.currentExerciseIndex
        if (currentIdx + 1 >= active.exercises.size) return  // Last exercise: no-op

        // Move skipped exercise to after the next exercise in the queue
        val list = active.exercises.toMutableList()
        val skipped = list.removeAt(currentIdx)
        list.add(currentIdx + 1, skipped)

        // Mirror the same reorder in templateOriginalIndices
        if (templateOriginalIndices.size > currentIdx) {
            val origIdx = templateOriginalIndices.removeAt(currentIdx)
            templateOriginalIndices.add(currentIdx + 1, origIdx)
        }

        // currentIdx now points to what was the next exercise
        val nextExercise = list[currentIdx]
        val firstIncompleteSet = nextExercise.sets
            .indexOfFirst { !it.isCompleted }
            .let { if (it == -1) 0 else it }

        _sessionState.value = active.copy(
            exercises = list,
            currentExerciseIndex = currentIdx,
            currentSetIndex = firstIncompleteSet,
            restState = RestState.NotResting
        )
        _preFill.value = computePreFill(nextExercise, firstIncompleteSet)

        viewModelScope.launch {
            workoutRepository.updateCursor(currentIdx, firstIncompleteSet)
        }
        persistExerciseOrder()
    }

    /**
     * Enter the workout review/recap screen (D-01, FLOW-01).
     * Cancels timers and transitions to Reviewing state without saving.
     * Active session in Room stays intact for crash recovery (Pitfall 2).
     */
    fun enterReview() {
        viewModelScope.launch {
            val active = _sessionState.value as? WorkoutSessionState.Active ?: return@launch

            timerJob?.cancel()
            elapsedJob?.cancel()

            val endTimeMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val durationMillis = endTimeMillis - active.startTimeMillis

            _sessionState.value = WorkoutSessionState.Reviewing(
                templateId = active.templateId,
                templateName = active.templateName,
                exercises = active.exercises,
                startTimeMillis = active.startTimeMillis,
                durationMillis = durationMillis
            )
        }
    }

    /**
     * Save the reviewed workout to history and transition to Finished (D-02, WORK-07, WORK-08).
     * Performs the save logic previously in finishWorkout(), from the Reviewing state.
     */
    fun saveReviewedWorkout() {
        viewModelScope.launch {
            val reviewing = _sessionState.value as? WorkoutSessionState.Reviewing ?: return@launch

            val completedExercises = reviewing.exercises.mapIndexedNotNull { order, exercise ->
                val completedSets = exercise.sets
                    .filter { it.isCompleted }
                    .map { set ->
                        CompletedSet(
                            setIndex = set.setIndex,
                            actualReps = set.actualReps ?: 0,
                            actualWeightKgX10 = set.actualWeightKgX10 ?: 0,
                            rir = set.rir ?: 2
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

            val endTimeMillis = reviewing.startTimeMillis + reviewing.durationMillis

            val completedWorkout = CompletedWorkout(
                id = 0,
                templateId = reviewing.templateId,
                name = reviewing.templateName,
                startTimeMillis = reviewing.startTimeMillis,
                endTimeMillis = endTimeMillis,
                durationMillis = reviewing.durationMillis,
                exercises = completedExercises
            )

            workoutRepository.saveCompletedWorkout(completedWorkout)
            workoutRepository.clearActiveSession()

            _hasActiveSession.value = false

            val totalSets = completedExercises.sumOf { it.sets.size }
            _sessionState.value = WorkoutSessionState.Finished(
                workoutName = reviewing.templateName,
                durationMillis = reviewing.durationMillis,
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
            _previousPerformance.value = emptyMap()
            _personalBest.value = emptyMap()
            templateOriginalIndices.clear()
        }
    }

    /**
     * Reset to idle after the Finished screen is dismissed.
     */
    fun resetToIdle() {
        _sessionState.value = WorkoutSessionState.Idle
        _elapsedSeconds.value = 0L
        _previousPerformance.value = emptyMap()
        _personalBest.value = emptyMap()
        _preFill.value = SetPreFill(reps = 0, weightKgX10 = 0)
        templateOriginalIndices.clear()
    }

    // -- Private helpers --

    /**
     * Persist the current exercise order to Room for crash recovery (D-07, FLOW-04).
     */
    private fun persistExerciseOrder() {
        val orderString = templateOriginalIndices.joinToString(",")
        viewModelScope.launch {
            workoutRepository.updateExerciseOrder(orderString)
        }
    }

    /**
     * Compute pre-fill values for the given set.
     * Set 1+: previous set's actual reps and weight (ENTRY-04).
     * Set 0: previous workout performance if available, then template targets (ENTRY-05).
     */
    private fun computePreFill(
        exercise: SessionExercise,
        setIndex: Int
    ): SetPreFill {
        if (setIndex > 0) {
            val prevSet = exercise.sets.getOrNull(setIndex - 1)
            if (prevSet != null && prevSet.isCompleted && prevSet.actualReps != null && prevSet.actualWeightKgX10 != null) {
                return SetPreFill(
                    reps = prevSet.actualReps,
                    weightKgX10 = prevSet.actualWeightKgX10
                )
            }
        }
        // Check previous workout performance for this exercise
        val prevPerf = _previousPerformance.value[exercise.exerciseId]
        if (prevPerf != null && prevPerf.sets.isNotEmpty()) {
            val matchingSet = prevPerf.sets.firstOrNull { it.setIndex == setIndex }
                ?: prevPerf.sets.last()
            return SetPreFill(
                reps = matchingSet.actualReps,
                weightKgX10 = matchingSet.actualWeightKgX10
            )
        }
        // Final fallback: use per-set targets if available, else exercise-level targets
        val targetSet = exercise.sets.getOrNull(setIndex)
        return SetPreFill(
            reps = targetSet?.targetReps ?: exercise.targetReps,
            weightKgX10 = targetSet?.targetWeightKgX10 ?: exercise.targetWeightKgX10
        )
    }

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

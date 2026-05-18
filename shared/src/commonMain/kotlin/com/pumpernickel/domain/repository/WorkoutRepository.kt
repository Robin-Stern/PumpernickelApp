package com.pumpernickel.domain.repository

import com.pumpernickel.data.db.ExerciseSetRirDto
import com.pumpernickel.domain.model.CompletedWorkout
import com.pumpernickel.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for workout persistence (active sessions + completed history).
 *
 * Phase 20 (Plan 20-02) note: This interface lives in `domain/repository/` to honour
 * the dependency-rule. One method still leaks a data-layer type
 * (`getExerciseSetRirSince` returns `List<ExerciseSetRirDto>` from `data.db.*`) —
 * tracked as a follow-up Smell-1 residue. Replacing it with a domain DTO is a
 * separate refactor (touches the DAO signature too); intentionally out of scope
 * for the mechanical move in Plan 20-02.
 */
interface WorkoutRepository {
    // Active session (crash recovery - WORK-09)
    suspend fun hasActiveSession(): Boolean
    suspend fun createActiveSession(templateId: Long, templateName: String, startTimeMillis: Long)
    suspend fun getActiveSession(): ActiveSessionData?
    suspend fun saveCompletedSet(exerciseIndex: Int, setIndex: Int, actualReps: Int, actualWeightKgX10: Int, completedAtMillis: Long, rir: Int = 2)
    suspend fun updateSetValues(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int, rir: Int)
    suspend fun updateCursor(exerciseIndex: Int, setIndex: Int)
    suspend fun clearActiveSession()

    // Exercise reorder (FLOW-03, FLOW-04)
    suspend fun updateExerciseOrder(order: String)

    // Completed workouts (WORK-07)
    /**
     * Persists the completed workout + its exercises + its sets. Returns the
     * new workoutId (the autoincrement row id of the inserted CompletedWorkoutEntity).
     * D-20: gamification engine uses this id to load the just-saved sets for XP computation.
     */
    suspend fun saveCompletedWorkout(workout: CompletedWorkout): Long

    /**
     * D-19-14 — Persist a workout that was auto-aborted by Phase 19's geofence
     * grace-period timeout. Sets `abandoned = true` on the row so history UI and
     * future analytics can distinguish from normal completions. Returns the new
     * workoutId so the caller (GamificationEngine.processAbandonedWorkout from
     * Wave 3) can award volume-XP via the standard ledger flow.
     *
     * Mirrors saveCompletedWorkout exactly except for the abandoned flag — the
     * caller must populate `workout.exercises` with only the sets that were
     * actually logged before the exit.
     */
    suspend fun saveAbandonedWorkout(workout: CompletedWorkout): Long

    // History queries (HIST-01, HIST-02, HIST-03, HIST-04)
    fun getWorkoutSummaries(): Flow<List<WorkoutSummary>>
    suspend fun getWorkoutDetail(workoutId: Long): CompletedWorkout?
    suspend fun getPreviousPerformance(templateId: Long): CompletedWorkout?

    // Personal best (ENTRY-07)
    suspend fun getPersonalBests(exerciseIds: List<String>): Map<String, Int>

    // Overview: exercise set RIR data since a given timestamp
    // NOTE: Plan-20-02 follow-up smell — `ExerciseSetRirDto` is a `data.db.*` type
    // leaking into the domain interface. Mechanical move keeps the signature 1:1;
    // replacing with a domain DTO is a separate refactor.
    suspend fun getExerciseSetRirSince(sinceMillis: Long): List<ExerciseSetRirDto>
}

// Domain-level representation of active session data (no Room entity leakage).
// Kept in this file because both data classes are part of the WorkoutRepository
// contract (return / parameter shapes of getActiveSession()).
data class ActiveSessionData(
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val completedSets: List<ActiveSessionSetData>,
    val exerciseOrder: String = ""
)

data class ActiveSessionSetData(
    val exerciseIndex: Int,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    val completedAtMillis: Long,
    val rir: Int = 2
)

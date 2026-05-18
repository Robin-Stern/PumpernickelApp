package com.pumpernickel.domain.gamification

/**
 * Plan 20-08 (Smell 3): narrow domain records the [GamificationEngine] consumes
 * via Repository interfaces. Each record carries ONLY the fields the engine
 * actually reads — kept small on purpose so the surface that data-layer
 * implementations must satisfy stays minimal.
 *
 * Mappers from the Room entities live next to the corresponding repository
 * implementation (under data/repository/, alongside each RepositoryImpl).
 */

/** Engine-side projection of a completed workout row (analogous to `CompletedWorkoutEntity`). */
data class CompletedWorkoutRecord(
    val id: Long,
    val startTimeMillis: Long
)

/** Engine-side projection of a completed-workout exercise row. */
data class CompletedExerciseRecord(
    val id: Long,
    val workoutId: Long,
    val exerciseId: String
)

/** Engine-side projection of a completed set row. */
data class CompletedSetRecord(
    val workoutExerciseId: Long,
    val actualReps: Int,
    val actualWeightKgX10: Int
)

/**
 * Engine-side projection of an XP-ledger row. Used by PR aggregation in
 * [GamificationEngine.buildSnapshot]; closes the Plan 20-04 Smell 3 leak by
 * keeping the engine off of `data.db.XpLedgerEntity`.
 */
data class XpLedgerRecord(
    val source: String,
    val eventKey: String,
    val xpAmount: Int,
    val awardedAtMillis: Long,
    val retroactive: Boolean
)

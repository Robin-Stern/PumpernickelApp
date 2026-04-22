package com.pumpernickel.domain.gamification

/**
 * Pure rule evaluator. Takes a `ProgressSnapshot` + the currently-stored
 * `AchievementProgress` list, returns the set of tier unlocks and the
 * updated progress values. No DB, no IO, no Koin.
 *
 * Covers all 12 achievement families defined in `AchievementCatalog` (D-14).
 * Unknown families are silently skipped for forward-compatibility.
 */
object AchievementRules {

    /**
     * Evaluate all achievement tiers against the current snapshot.
     *
     * @param snapshot aggregated user-stats as of right now.
     * @param currentStates current AchievementProgress rows (from GamificationRepository.achievements).
     * @return evaluation result with per-achievement updated progress + list of achievement IDs
     *         that newly qualify for unlock (not already unlocked).
     */
    fun evaluate(
        snapshot: ProgressSnapshot,
        currentStates: List<AchievementProgress>
    ): RuleEvaluation {
        val updatedProgress = mutableMapOf<String, Long>()
        val toUnlock = mutableListOf<String>()

        for (state in currentStates) {
            val def = state.def
            val newProgress = progressFor(def, snapshot) ?: continue   // Unknown family — skip.
            updatedProgress[def.id] = newProgress
            if (!state.isUnlocked && newProgress >= def.threshold) {
                toUnlock.add(def.id)
            }
        }

        return RuleEvaluation(
            updatedProgress = updatedProgress.toMap(),
            toUnlock = toUnlock.toList()
        )
    }

    /**
     * Map an achievement catalog def to the current progress value from the snapshot.
     * Returns null for any unrecognised family (forward-compat safety).
     */
    private fun progressFor(def: AchievementDef, s: ProgressSnapshot): Long? = when (def.family) {
        "volume"                     -> s.lifetimeVolumeKgReps
        "volume-single-session"      -> s.bestSingleSessionVolumeKgReps
        "consistency-longest-streak" -> s.longestWorkoutStreakDays.toLong()
        "consistency-total-workouts" -> s.totalWorkouts
        "consistency-nutrition-days" -> s.totalNutritionGoalDays
        "consistency-nutrition-streak" -> s.longestNutritionStreakDays.toLong()
        "pr-hunter-total"            -> s.totalPrsSet
        "pr-hunter-breadth"          -> s.distinctExercisesWithPr.toLong()
        "pr-hunter-multi-session"    -> s.bestPrsInSingleSession.toLong()
        "variety-exercises"          -> s.distinctExercisesTrained.toLong()
        "variety-front-coverage"     -> s.distinctFrontGroupsTrained.toLong()
        "variety-back-coverage"      -> s.distinctBackGroupsTrained.toLong()
        else                         -> null   // Unknown family — skip (forward-compat).
    }
}

/**
 * Snapshot of user-wide stats used to evaluate achievements.
 * Engine computes this from Room aggregates + MuscleRegionPaths joins.
 * All fields default to 0 so callers can build partial snapshots in tests.
 */
data class ProgressSnapshot(
    /** Lifetime sum(actualReps * actualWeightKg) across all completed sets (D-02 unit). */
    val lifetimeVolumeKgReps: Long = 0L,
    /** Best single-session volume (same unit) across all completed workouts. */
    val bestSingleSessionVolumeKgReps: Long = 0L,
    /** Length of the current (or longest) workout streak in days. */
    val longestWorkoutStreakDays: Int = 0,
    /** Total number of completed workouts. */
    val totalWorkouts: Long = 0L,
    /** Total distinct calendar days where a nutrition goal-day award was recorded. */
    val totalNutritionGoalDays: Long = 0L,
    /** Length of the current (or longest) nutrition goal-day streak. */
    val longestNutritionStreakDays: Int = 0,
    /** Total number of PR-source XP ledger rows (= total PRs set). */
    val totalPrsSet: Long = 0L,
    /** Count of distinct exerciseIds for which at least one PR was recorded. */
    val distinctExercisesWithPr: Int = 0,
    /** Maximum number of PRs awarded in a single workout session. */
    val bestPrsInSingleSession: Int = 0,
    /** Count of distinct exerciseIds trained across all workouts. */
    val distinctExercisesTrained: Int = 0,
    /** Count of distinct front muscle groupNames hit across trained exercises. */
    val distinctFrontGroupsTrained: Int = 0,
    /** Count of distinct back muscle groupNames hit across trained exercises. */
    val distinctBackGroupsTrained: Int = 0
)

/** Result of a single AchievementRules.evaluate() call. */
data class RuleEvaluation(
    /** achievementId → new progress value (for all states that had a matching family). */
    val updatedProgress: Map<String, Long>,
    /** Achievement IDs that newly qualify for unlock (threshold crossed, not yet unlocked). */
    val toUnlock: List<String>
)

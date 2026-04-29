package com.pumpernickel.domain.gamification

import kotlin.math.floor

/**
 * Pure XP calculation functions. No side effects, no DB, no Koin.
 * Magic numbers are starting anchors — see D-07 for tuning policy.
 */
object XpFormula {

    // ----- Per-source constants (D-02, D-03, D-06, D-17) -----
    const val PR_XP: Int = 50                      // D-03
    const val INACTIVITY_PENALTY_XP: Int = 50      // F5
    const val INACTIVITY_TIMEOUT_SECONDS: Long = 600L // F5 — 10 minutes
    const val NUTRITION_GOAL_DAY_XP: Int = 25      // Claude-discretion flat award per goal-day (D-04/D-05)

    // Streak thresholds (D-06) — flat bonus on crossing the threshold once per run.
    const val STREAK_WORKOUT_3D: Int = 25
    const val STREAK_WORKOUT_7D: Int = 100
    const val STREAK_WORKOUT_30D: Int = 500
    const val STREAK_NUTRITION_7D: Int = 100

    // Achievement tier bonuses (D-17).
    private const val ACHIEVEMENT_BRONZE_XP: Int = 25
    private const val ACHIEVEMENT_SILVER_XP: Int = 75
    private const val ACHIEVEMENT_GOLD_XP: Int = 200

    /**
     * D-02: workoutXp = floor(sum(actualReps x actualWeightKg) / 100).
     * Weight is stored as kg x 10 in CompletedWorkoutSetEntity.actualWeightKgX10 —
     * convert to kg first by dividing by 10.0.
     */
    fun workoutXp(sets: List<WorkoutSetInput>): Int {
        if (sets.isEmpty()) return 0
        val totalVolume = sets.sumOf { set ->
            val weightKg = set.actualWeightKgX10 / 10.0
            set.actualReps * weightKg
        }
        // TODO(tuning): the /100 divisor is a D-07 anchor; re-calibrate after play-testing.
        return floor(totalVolume / 100.0).toInt().coerceAtLeast(0)
    }

    fun achievementXp(tier: Tier): Int = when (tier) {
        Tier.BRONZE -> ACHIEVEMENT_BRONZE_XP
        Tier.SILVER -> ACHIEVEMENT_SILVER_XP
        Tier.GOLD -> ACHIEVEMENT_GOLD_XP
    }

    /** Map a streak threshold (3, 7, 30) + source to the flat XP bonus (D-06). */
    fun streakWorkoutXp(threshold: Int): Int = when (threshold) {
        3 -> STREAK_WORKOUT_3D
        7 -> STREAK_WORKOUT_7D
        30 -> STREAK_WORKOUT_30D
        else -> 0  // Unknown threshold — no award.
    }

    fun streakNutritionXp(threshold: Int): Int = when (threshold) {
        7 -> STREAK_NUTRITION_7D
        else -> 0
    }
}

/** Minimal input shape for XpFormula.workoutXp — decouples from Room entities. */
data class WorkoutSetInput(val actualReps: Int, val actualWeightKgX10: Int)

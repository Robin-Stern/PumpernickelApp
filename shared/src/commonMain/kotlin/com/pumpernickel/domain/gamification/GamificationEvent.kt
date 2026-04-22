package com.pumpernickel.domain.gamification

import kotlinx.datetime.LocalDate

/**
 * Every event that might award XP. See D-01 for the four first-class sources.
 * Each variant carries the dedupe-key raw material directly so the engine
 * can mint the (source, eventKey) pair without round-tripping.
 */
sealed class GamificationEvent {
    data class WorkoutCompleted(
        val workoutId: Long,
        val sets: List<WorkoutSetInput>,
        val completedAtMillis: Long
    ) : GamificationEvent()

    data class PrHit(
        val exerciseId: String,
        val workoutId: Long,
        val newOneRepMaxX10: Int,
        val hitAtMillis: Long
    ) : GamificationEvent()

    data class NutritionGoalDay(
        val date: LocalDate,
        val awardedAtMillis: Long
    ) : GamificationEvent()

    data class StreakThresholdCrossed(
        val streakKind: StreakKind,  // WORKOUT or NUTRITION
        val threshold: Int,           // 3 / 7 / 30
        val runStartEpochDay: Long,
        val crossedAtMillis: Long
    ) : GamificationEvent()

    data class AchievementUnlocked(
        val achievementId: String,
        val tier: Tier,
        val unlockedAtMillis: Long
    ) : GamificationEvent()
}

enum class StreakKind { WORKOUT, NUTRITION }

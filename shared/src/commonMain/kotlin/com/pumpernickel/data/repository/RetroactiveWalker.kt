package com.pumpernickel.data.repository

import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.ConsumptionEntryEntity
import com.pumpernickel.data.db.NutritionDao
import com.pumpernickel.domain.gamification.GamificationEngine
import com.pumpernickel.domain.gamification.NutritionGoalDayPolicy
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * D-12 / D-13. One-shot walker run on first launch after upgrade:
 * - Sentinel check via SettingsRepository.retroactiveApplied. If true → return.
 * - Walk completed_workouts ASC by startTimeMillis, replaying each through
 *   GamificationEngine.processHistoricalWorkout with a running-PB map
 *   (historical PR detection correctness, D-12).
 * - Aggregate consumption_entries by date, evaluate each against current
 *   NutritionGoals (D-04), award XP for past goal-days.
 * - On success, set retroactiveApplied = true.
 * - On throw, do NOT set the sentinel — re-try on next launch. Dedupe on
 *   (source, eventKey) guarantees no double-award (D-13).
 */
class RetroactiveWalker(
    private val engine: GamificationEngine,
    private val settingsRepo: SettingsRepository,
    private val completedWorkoutDao: CompletedWorkoutDao,
    private val nutritionDao: NutritionDao
) {

    suspend fun applyIfNeeded() {
        if (settingsRepo.retroactiveApplied.first()) return
        replay()
        settingsRepo.setRetroactiveApplied(true)
    }

    private suspend fun replay() {
        // ----- Workouts in chronological order -----
        val workouts = completedWorkoutDao.getAllWorkouts().first()
            .sortedBy { it.startTimeMillis }
        val runningPbKgX10 = mutableMapOf<String, Int>()

        for (w in workouts) {
            engine.processHistoricalWorkout(
                workoutId = w.id,
                awardedAtMillis = w.startTimeMillis,
                runningPbKgX10 = runningPbKgX10
            )
        }

        // ----- Nutrition goal-days -----
        val goals = settingsRepo.nutritionGoals.first()
        val allEntries: List<ConsumptionEntryEntity> = nutritionDao.getAllEntries()

        // Group entries by ISO date derived from timestampMillis in local TZ.
        // This matches the same derivation logic used by GamificationEngine.buildSnapshot().
        val byDate: Map<String, List<ConsumptionEntryEntity>> = allEntries.groupBy { entry ->
            entry.timestampMillis.toLocalDateString()
        }

        for ((dateIso, dayEntries) in byDate.entries.sortedBy { it.key }) {
            // Shared predicate — behaviour MUST match engine's live path (Warning-9 fix).
            if (!NutritionGoalDayPolicy.isGoalDay(dayEntries, goals)) continue
            val localDate = LocalDate.parse(dateIso)
            val awardedAtMillis = localDate.toStartOfDayMillis()
            // processHistoricalGoalDay also fires the nutrition streak bonus via
            // evaluateNutritionStreakAt, so the walker doesn't replay streaks separately.
            engine.processHistoricalGoalDay(localDate, awardedAtMillis)
        }

        // ----- Finalise: catch up any nutrition-driven achievements + rank -----
        engine.runAchievementAndRankChecksForReplay()
    }

    /** Convert epoch-milliseconds to an ISO "YYYY-MM-DD" string in the local time zone. */
    private fun Long.toLocalDateString(): String =
        Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    /** Midnight (00:00:00) epoch-milliseconds in the local time zone for this date. */
    private fun LocalDate.toStartOfDayMillis(): Long {
        val zone = TimeZone.currentSystemDefault()
        return LocalDateTime(this, LocalTime(0, 0, 0))
            .toInstant(zone)
            .toEpochMilliseconds()
    }
}

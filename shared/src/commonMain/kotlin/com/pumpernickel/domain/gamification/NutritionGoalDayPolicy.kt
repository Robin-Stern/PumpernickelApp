package com.pumpernickel.domain.gamification

import com.pumpernickel.data.db.ConsumptionEntryEntity
import com.pumpernickel.domain.model.NutritionGoals

/**
 * D-04 goal-day predicate. A day counts as a goal-day iff EVERY
 * user-configured macro is within strict +-10% of the goal. Macros with
 * 0 / "not set" goals are SKIPPED from the check (per D-04).
 * Empty day is NEVER a goal-day.
 *
 * This predicate is pure — no Room, no Koin, no clock access. Same input
 * always yields the same output. Both the live engine (D-20 / D-22) and
 * the retroactive walker (D-12) call this so live + retroactive awards
 * stay aligned (Warning 9 fix).
 *
 * Field type observations (confirmed by reading actual source files):
 *   - ConsumptionEntryEntity: stores macros as per-100g/ml values
 *     (caloriesPer100: Double, proteinPer100: Double, fatPer100: Double,
 *     carbsPer100: Double, sugarPer100: Double) plus amount: Double.
 *     Actual nutrient = (per100 / 100.0) * amount.
 *   - NutritionGoals: calorieGoal: Int, proteinGoal: Int, fatGoal: Int,
 *     carbGoal: Int, sugarGoal: Int — all non-nullable, 0 means "not set"
 *     (the domain defaults are 2500/150/80/300/50 when configured).
 *     A user who has not touched goals will see defaults — these are treated
 *     as active goals (non-zero). A goal of exactly 0 is treated as unset.
 */
object NutritionGoalDayPolicy {

    private const val TOLERANCE: Double = 0.10  // +-10% — D-04 strict

    fun isGoalDay(entries: List<ConsumptionEntryEntity>, goals: NutritionGoals): Boolean {
        if (entries.isEmpty()) return false

        // Compute actual daily totals from per-100g values * serving amount.
        val totalKcal = entries.sumOf { (it.caloriesPer100 / 100.0) * it.amount }
        val totalProtein = entries.sumOf { (it.proteinPer100 / 100.0) * it.amount }
        val totalFat = entries.sumOf { (it.fatPer100 / 100.0) * it.amount }
        val totalCarbs = entries.sumOf { (it.carbsPer100 / 100.0) * it.amount }
        val totalSugar = entries.sumOf { (it.sugarPer100 / 100.0) * it.amount }

        val checks = listOf(
            check(totalKcal, goals.calorieGoal),
            check(totalProtein, goals.proteinGoal),
            check(totalFat, goals.fatGoal),
            check(totalCarbs, goals.carbGoal),
            check(totalSugar, goals.sugarGoal)
        )
        return checks.all { it }
    }

    /**
     * Returns true when goal is 0 (unset — skipped) OR when actual is
     * within +-TOLERANCE of target.
     *
     * NutritionGoals fields are non-nullable Int; 0 means "not configured".
     */
    private fun check(actual: Double, goal: Int): Boolean {
        if (goal <= 0) return true  // 0 means "not set" — skip this macro
        val lower = goal * (1.0 - TOLERANCE)
        val upper = goal * (1.0 + TOLERANCE)
        return actual in lower..upper
    }
}

package com.pumpernickel.domain.gamification

import com.pumpernickel.data.db.ConsumptionEntryEntity
import com.pumpernickel.domain.model.NutritionGoals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutritionGoalDayPolicyTest {

    /**
     * ConsumptionEntryEntity stores macros per 100g/ml (caloriesPer100, proteinPer100, etc.)
     * and amount (grams or ml). Actual nutrient = (per100 / 100.0) * amount.
     *
     * For test simplicity: set per100 = nutrient * 100 and amount = 1.0,
     * so actual = (nutrient * 100 / 100.0) * 1.0 = nutrient.
     */
    private fun makeEntry(
        kcal: Double = 0.0,
        protein: Double = 0.0,
        fat: Double = 0.0,
        carbs: Double = 0.0,
        sugar: Double = 0.0
    ): ConsumptionEntryEntity = ConsumptionEntryEntity(
        id = "test-${kcal.toLong()}-${protein.toLong()}",
        foodId = null,
        name = "Test food",
        // Per100 * amount / 100 = nutrient. With amount=1 and per100=nutrient*100, we get nutrient.
        caloriesPer100 = kcal * 100.0,
        proteinPer100 = protein * 100.0,
        fatPer100 = fat * 100.0,
        carbsPer100 = carbs * 100.0,
        sugarPer100 = sugar * 100.0,
        unit = "GRAM",
        amount = 1.0,
        timestampMillis = 0L
    )

    /**
     * NutritionGoals has non-nullable Int fields: calorieGoal, proteinGoal, fatGoal, carbGoal, sugarGoal.
     * A value of 0 means "not set" (treated as skip in policy).
     */
    private fun makeGoals(
        kcal: Int = 0,
        protein: Int = 0,
        fat: Int = 0,
        carbs: Int = 0,
        sugar: Int = 0
    ): NutritionGoals = NutritionGoals(
        calorieGoal = kcal,
        proteinGoal = protein,
        fatGoal = fat,
        carbGoal = carbs,
        sugarGoal = sugar
    )

    @Test fun emptyDayIsNeverAGoalDay() {
        assertFalse(NutritionGoalDayPolicy.isGoalDay(emptyList(), makeGoals(kcal = 2000)))
    }

    @Test fun exactMatchHitsGoalDay() {
        val entries = listOf(makeEntry(kcal = 2000.0, protein = 150.0))
        val goals = makeGoals(kcal = 2000, protein = 150)
        assertTrue(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }

    @Test fun overBy15PctFailsGoalDay() {
        // 2000 kcal goal, consumed 2300 (15% over -> outside +-10%) -> fail
        val entries = listOf(makeEntry(kcal = 2300.0))
        val goals = makeGoals(kcal = 2000)
        assertFalse(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }

    @Test fun underBy15PctFailsGoalDay() {
        val entries = listOf(makeEntry(kcal = 1700.0))
        val goals = makeGoals(kcal = 2000)
        assertFalse(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }

    @Test fun unsetMacroIsSkipped() {
        // protein goal is 0 -> skipped. kcal exactly matches -> pass.
        val entries = listOf(makeEntry(kcal = 2000.0, protein = 9999.0))
        val goals = makeGoals(kcal = 2000, protein = 0)
        assertTrue(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }

    @Test fun zeroGoalIsTreatedAsUnset() {
        val entries = listOf(makeEntry(kcal = 2000.0, protein = 9999.0))
        val goals = makeGoals(kcal = 2000, protein = 0)
        assertTrue(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }

    @Test fun within10PctPassesGoalDay() {
        // 2000 kcal goal, consumed 2100 (5% over) -> pass
        val entries = listOf(makeEntry(kcal = 2100.0))
        val goals = makeGoals(kcal = 2000)
        assertTrue(NutritionGoalDayPolicy.isGoalDay(entries, goals))
    }
}

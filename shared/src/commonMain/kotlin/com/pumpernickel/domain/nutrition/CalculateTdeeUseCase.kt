package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.BodyProfile
import com.pumpernickel.domain.model.Gender
import com.pumpernickel.domain.model.NutritionGoals
import kotlin.math.roundToInt

class CalculateTdeeUseCase {
    operator fun invoke(profile: BodyProfile): NutritionGoals {
        val bmr = when (profile.gender) {
            Gender.Male -> 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age + 5
            Gender.Female -> 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age - 161
        }
        val kcal = (bmr * profile.activityLevel.multiplier + profile.goalType.kcalOffset).roundToInt()
        val protein = (profile.weightKg * 2).roundToInt()
        val fat = (kcal * 0.25 / 9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        val sugar = (carbs * 0.2).roundToInt()
        return NutritionGoals(
            calorieGoal = kcal,
            proteinGoal = protein,
            fatGoal = fat,
            carbGoal = carbs,
            sugarGoal = sugar
        )
    }
}

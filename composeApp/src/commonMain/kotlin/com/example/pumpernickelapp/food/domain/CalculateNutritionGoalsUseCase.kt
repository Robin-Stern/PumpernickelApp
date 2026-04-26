package com.example.pumpernickelapp.food.domain

class CalculateNutritionGoalsUseCase {
    operator fun invoke(settings: NutritionSettings): Int {
        val bmr = when (settings.gender) {
            Gender.Male -> 10 * settings.weightKg + 6.25 * settings.heightCm - 5 * settings.age + 5
            Gender.Female -> 10 * settings.weightKg + 6.25 * settings.heightCm - 5 * settings.age - 161
        }
        return (bmr * settings.activityLevel.multiplier + settings.goalType.kcalOffset).toInt()
    }
}

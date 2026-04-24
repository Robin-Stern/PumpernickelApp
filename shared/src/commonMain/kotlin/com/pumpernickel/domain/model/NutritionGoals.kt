package com.pumpernickel.domain.model

data class NutritionGoals(
    val calorieGoal: Int = 2500,
    val proteinGoal: Int = 150,
    val fatGoal: Int = 80,
    val carbGoal: Int = 300,
    val sugarGoal: Int = 50
)

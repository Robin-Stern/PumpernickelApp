package com.example.pumpernickelapp.food.domain

enum class Gender(val displayName: String) {
    Male("Männlich"),
    Female("Weiblich")
}

enum class ActivityLevel(val multiplier: Double, val displayName: String) {
    Sedentary(1.2, "Kaum Bewegung (Bürojob)"),
    Light(1.375, "Leichte Aktivität (1–3× Sport/Woche)"),
    Moderate(1.55, "Moderate Aktivität (3–5× Sport/Woche)"),
    Active(1.725, "Aktiv (6–7× Sport/Woche)"),
    VeryActive(1.9, "Sehr aktiv (täglich intensiv)")
}

enum class GoalType(val kcalOffset: Int, val displayName: String) {
    LoseWeight(-500, "Abnehmen"),
    Maintain(0, "Halten"),
    GainWeight(500, "Zunehmen")
}

data class NutritionSettings(
    val gender: Gender = Gender.Male,
    val age: Int = 25,
    val weightKg: Double = 70.0,
    val heightCm: Int = 175,
    val activityLevel: ActivityLevel = ActivityLevel.Moderate,
    val goalType: GoalType = GoalType.Maintain
)

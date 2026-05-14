package com.pumpernickel.domain.model

enum class Sex { MALE, FEMALE }

enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
    EXTRA_ACTIVE
}

data class UserPhysicalStats(
    val weightKg: Double,
    val heightCm: Int,
    val age: Int,
    val sex: Sex,
    val activityLevel: ActivityLevel
)

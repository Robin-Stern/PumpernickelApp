package com.example.pumpernickelapp.food.data

import com.example.pumpernickelapp.food.domain.ActivityLevel
import com.example.pumpernickelapp.food.domain.Gender
import com.example.pumpernickelapp.food.domain.GoalType
import com.example.pumpernickelapp.food.domain.NutritionSettings
import com.russhwolf.settings.Settings

class NutritionSettingsRepository {
    private val settings = Settings()

    fun save(s: NutritionSettings) {
        settings.putString(KEY_GENDER, s.gender.name)
        settings.putInt(KEY_AGE, s.age)
        settings.putDouble(KEY_WEIGHT, s.weightKg)
        settings.putInt(KEY_HEIGHT, s.heightCm)
        settings.putString(KEY_ACTIVITY, s.activityLevel.name)
        settings.putString(KEY_GOAL, s.goalType.name)
    }

    fun load(): NutritionSettings {
        val gender = settings.getStringOrNull(KEY_GENDER)
            ?.let { name -> Gender.entries.firstOrNull { it.name == name } }
            ?: Gender.Male
        val activityLevel = settings.getStringOrNull(KEY_ACTIVITY)
            ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }
            ?: ActivityLevel.Moderate
        val goalType = settings.getStringOrNull(KEY_GOAL)
            ?.let { name -> GoalType.entries.firstOrNull { it.name == name } }
            ?: GoalType.Maintain
        return NutritionSettings(
            gender = gender,
            age = settings.getInt(KEY_AGE, 25),
            weightKg = settings.getDouble(KEY_WEIGHT, 70.0),
            heightCm = settings.getInt(KEY_HEIGHT, 175),
            activityLevel = activityLevel,
            goalType = goalType
        )
    }

    private companion object {
        const val KEY_GENDER = "nutrition_gender"
        const val KEY_AGE = "nutrition_age"
        const val KEY_WEIGHT = "nutrition_weight"
        const val KEY_HEIGHT = "nutrition_height"
        const val KEY_ACTIVITY = "nutrition_activity"
        const val KEY_GOAL = "nutrition_goal"
    }
}

package com.pumpernickel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.BodyProfile
import com.pumpernickel.domain.model.Gender
import com.pumpernickel.domain.model.GoalType
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val hasSeenTutorialKey = booleanPreferencesKey("has_seen_tutorial")
    private val weightUnitKey = stringPreferencesKey("weight_unit")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val bodyGenderKey = stringPreferencesKey("body_gender")
    private val bodyAgeKey = stringPreferencesKey("body_age")
    private val bodyWeightKey = stringPreferencesKey("body_weight")
    private val bodyHeightKey = stringPreferencesKey("body_height")
    private val bodyActivityKey = stringPreferencesKey("body_activity")
    private val bodyGoalKey = stringPreferencesKey("body_goal")

    private val calorieGoalKey = stringPreferencesKey("calorie_goal")
    private val proteinGoalKey = stringPreferencesKey("protein_goal")
    private val fatGoalKey = stringPreferencesKey("fat_goal")
    private val carbGoalKey = stringPreferencesKey("carb_goal")
    private val sugarGoalKey = stringPreferencesKey("sugar_goal")

    val hasSeenTutorial: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[hasSeenTutorialKey] ?: false
    }

    suspend fun setHasSeenTutorial(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[hasSeenTutorialKey] = value
        }
    }

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        when (preferences[weightUnitKey]) {
            "LBS" -> WeightUnit.LBS
            else -> WeightUnit.KG
        }
    }

    val appTheme: Flow<String> = dataStore.data.map { preferences ->
        preferences[appThemeKey] ?: "system"
    }

    val accentColor: Flow<String> = dataStore.data.map { preferences ->
        preferences[accentColorKey] ?: "green"
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[weightUnitKey] = unit.name
        }
    }

    suspend fun setAppTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[appThemeKey] = theme
        }
    }

    suspend fun setAccentColor(color: String) {
        dataStore.edit { preferences ->
            preferences[accentColorKey] = color
        }
    }

    val nutritionGoals: Flow<NutritionGoals> = combine(
        dataStore.data.map { it[calorieGoalKey]?.toIntOrNull() ?: 2500 },
        dataStore.data.map { it[proteinGoalKey]?.toIntOrNull() ?: 150 },
        dataStore.data.map { it[fatGoalKey]?.toIntOrNull() ?: 80 },
        dataStore.data.map { it[carbGoalKey]?.toIntOrNull() ?: 300 },
        dataStore.data.map { it[sugarGoalKey]?.toIntOrNull() ?: 50 }
    ) { cal, pro, fat, carb, sugar ->
        NutritionGoals(cal, pro, fat, carb, sugar)
    }

    suspend fun setNutritionGoals(goals: NutritionGoals) {
        dataStore.edit { prefs ->
            prefs[calorieGoalKey] = goals.calorieGoal.toString()
            prefs[proteinGoalKey] = goals.proteinGoal.toString()
            prefs[fatGoalKey] = goals.fatGoal.toString()
            prefs[carbGoalKey] = goals.carbGoal.toString()
            prefs[sugarGoalKey] = goals.sugarGoal.toString()
        }
    }

    val bodyProfile: Flow<BodyProfile> = dataStore.data.map { prefs ->
        BodyProfile(
            gender = prefs[bodyGenderKey]?.let { name -> Gender.entries.firstOrNull { it.name == name } } ?: Gender.Male,
            age = prefs[bodyAgeKey]?.toIntOrNull() ?: 25,
            weightKg = prefs[bodyWeightKey]?.toDoubleOrNull() ?: 70.0,
            heightCm = prefs[bodyHeightKey]?.toIntOrNull() ?: 175,
            activityLevel = prefs[bodyActivityKey]?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } } ?: ActivityLevel.Moderate,
            goalType = prefs[bodyGoalKey]?.let { name -> GoalType.entries.firstOrNull { it.name == name } } ?: GoalType.Maintain
        )
    }

    suspend fun setBodyProfile(profile: BodyProfile) {
        dataStore.edit { prefs ->
            prefs[bodyGenderKey] = profile.gender.name
            prefs[bodyAgeKey] = profile.age.toString()
            prefs[bodyWeightKey] = profile.weightKg.toString()
            prefs[bodyHeightKey] = profile.heightCm.toString()
            prefs[bodyActivityKey] = profile.activityLevel.name
            prefs[bodyGoalKey] = profile.goalType.name
        }
    }
}

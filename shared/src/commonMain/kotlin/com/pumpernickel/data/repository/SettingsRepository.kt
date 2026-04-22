package com.pumpernickel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val weightUnitKey = stringPreferencesKey("weight_unit")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val calorieGoalKey = stringPreferencesKey("calorie_goal")
    private val proteinGoalKey = stringPreferencesKey("protein_goal")
    private val fatGoalKey = stringPreferencesKey("fat_goal")
    private val carbGoalKey = stringPreferencesKey("carb_goal")
    private val sugarGoalKey = stringPreferencesKey("sugar_goal")
    private val retroactiveAppliedKey = booleanPreferencesKey("gamification_retroactive_applied")

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

    /**
     * D-13: Gamification retroactive-walker sentinel. True once the one-shot
     * first-launch XP replay has completed successfully. If false or missing,
     * the RetroactiveWalker runs on next app resume and writes true on success.
     */
    val retroactiveApplied: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[retroactiveAppliedKey] ?: false
    }

    suspend fun setRetroactiveApplied(applied: Boolean) {
        dataStore.edit { preferences ->
            preferences[retroactiveAppliedKey] = applied
        }
    }
}

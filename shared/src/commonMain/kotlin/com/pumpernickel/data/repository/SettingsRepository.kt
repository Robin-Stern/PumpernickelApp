package com.pumpernickel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
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

    private val calorieGoalKey = stringPreferencesKey("calorie_goal")
    private val proteinGoalKey = stringPreferencesKey("protein_goal")
    private val fatGoalKey = stringPreferencesKey("fat_goal")
    private val carbGoalKey = stringPreferencesKey("carb_goal")
    private val sugarGoalKey = stringPreferencesKey("sugar_goal")
    private val retroactiveAppliedKey = booleanPreferencesKey("gamification_retroactive_applied")
    // D-16-10 — UserPhysicalStats persistence (kg/cm only per D-16-12).
    private val userWeightKgKey = stringPreferencesKey("user_weight_kg")
    private val userHeightCmKey = stringPreferencesKey("user_height_cm")
    private val userAgeKey = stringPreferencesKey("user_age")
    private val userSexKey = stringPreferencesKey("user_sex")
    private val userActivityKey = stringPreferencesKey("user_activity_level")
    // D-16-13 / D-16-14 — Overview-tab "set goals" banner dismissal sentinel.
    private val nutritionGoalsBannerDismissedKey = booleanPreferencesKey("nutrition_goals_banner_dismissed")

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

    /**
     * D-16-10 / D-16-11 — `null` when no stats ever stored (calculator opens with placeholders);
     * fully populated otherwise. Flow only emits a value once ALL five keys are present.
     */
    val userPhysicalStats: Flow<UserPhysicalStats?> = combine(
        dataStore.data.map { it[userWeightKgKey]?.toDoubleOrNull() },
        dataStore.data.map { it[userHeightCmKey]?.toIntOrNull() },
        dataStore.data.map { it[userAgeKey]?.toIntOrNull() },
        dataStore.data.map { raw -> raw[userSexKey]?.let { runCatching { enumValueOf<Sex>(it) }.getOrNull() } },
        dataStore.data.map { raw -> raw[userActivityKey]?.let { runCatching { enumValueOf<ActivityLevel>(it) }.getOrNull() } }
    ) { weight, height, age, sex, activity ->
        if (weight == null || height == null || age == null || sex == null || activity == null) {
            null
        } else {
            UserPhysicalStats(
                weightKg = weight,
                heightCm = height,
                age = age,
                sex = sex,
                activityLevel = activity
            )
        }
    }

    suspend fun setUserPhysicalStats(stats: UserPhysicalStats) {
        dataStore.edit { prefs ->
            prefs[userWeightKgKey] = stats.weightKg.toString()
            prefs[userHeightCmKey] = stats.heightCm.toString()
            prefs[userAgeKey] = stats.age.toString()
            prefs[userSexKey] = stats.sex.name
            prefs[userActivityKey] = stats.activityLevel.name
        }
    }

    /**
     * D-16-13 / D-16-14 — true once the user dismisses the Overview banner via "×"
     * OR successfully saves new (non-default) nutrition goals. Default false (banner visible).
     * Persisted across launches; never reset by this layer.
     */
    val nutritionGoalsBannerDismissed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[nutritionGoalsBannerDismissedKey] ?: false
    }

    suspend fun setNutritionGoalsBannerDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[nutritionGoalsBannerDismissedKey] = dismissed
        }
    }
}

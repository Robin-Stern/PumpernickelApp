package com.pumpernickel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pumpernickel.domain.geofence.EarlyExitBudget
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.domain.geofence.PendingGeofenceExit
import com.pumpernickel.domain.geofence.PendingGeofenceExitStore
import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.domain.repository.EarlyExitBudgetStore
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.infrastructure.ai.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * D-20-04 / D-20-05 — DataStore-backed monolithic settings facade.
 *
 * Implements three interfaces against the same singleton instance:
 *  - [SettingsRepository] — fat domain facade for all settings (used by ~10
 *    ViewModels + 3 Use-Cases + `GamificationEngine`).
 *  - [PendingGeofenceExitStore] — narrow domain port for cold-start EXIT
 *    sentinel (D-19-04).
 *  - [EarlyExitBudgetStore] — narrow domain port for monthly Early-Exits
 *    budget (D-19-07 / D-20-05); injected into `EarlyExitTracker`.
 *
 * All three are bound to this single instance via Koin's `bind ::class` chain
 * in `di/SharedModule.kt` so callers asking for any of them get the same
 * DataStore-backed object.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository, PendingGeofenceExitStore, EarlyExitBudgetStore {
    private val hasSeenTutorialKey = booleanPreferencesKey("has_seen_tutorial")
    private val weightUnitKey = stringPreferencesKey("weight_unit")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val accentColorKey = stringPreferencesKey("accent_color")

    // D-quick-vn7 — Debug-Modus toggle + configurable Geofence grace-period.
    private val debugModeEnabledKey = booleanPreferencesKey("debug_mode_enabled")
    private val gracePeriodSecondsKey = longPreferencesKey("grace_period_seconds")

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

    // Phase 19 — Early-Exit budget per calendar month (D-19-07)
    private val earlyExitYearMonthKey = stringPreferencesKey("early_exits_year_month")
    private val earlyExitUsedKey = intPreferencesKey("early_exits_used")

    // Phase 19 — Cold-start sentinel for geofence EXIT events (D-19-04)
    private val pendingGeofenceExitWorkoutIdKey = longPreferencesKey("pending_geofence_exit_workout_id")
    private val pendingGeofenceExitTimeMillisKey = longPreferencesKey("pending_geofence_exit_time_millis")
    private val pendingGeofenceExitRegionIdKey = stringPreferencesKey("pending_geofence_exit_region_id")

    override val hasSeenTutorial: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[hasSeenTutorialKey] ?: false
    }

    override suspend fun setHasSeenTutorial(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[hasSeenTutorialKey] = value
        }
    }

    override val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        when (preferences[weightUnitKey]) {
            "LBS" -> WeightUnit.LBS
            else -> WeightUnit.KG
        }
    }

    override val appTheme: Flow<String> = dataStore.data.map { preferences ->
        preferences[appThemeKey] ?: "system"
    }

    override val accentColor: Flow<String> = dataStore.data.map { preferences ->
        preferences[accentColorKey] ?: "green"
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[weightUnitKey] = unit.name
        }
    }

    override suspend fun setAppTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[appThemeKey] = theme
        }
    }

    override suspend fun setAccentColor(color: String) {
        dataStore.edit { preferences ->
            preferences[accentColorKey] = color
        }
    }

    /**
     * D-quick-vn7 — Debug-Modus toggle. Gates the in-workout debug overlay
     * (iOS pill + Android FAB). Defaults to true so existing users see
     * unchanged DEBUG behavior on first launch.
     */
    override val debugModeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[debugModeEnabledKey] ?: true
    }

    override suspend fun setDebugModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[debugModeEnabledKey] = enabled }
    }

    /**
     * D-quick-vn7 — grace period (seconds) before auto-abort after geofence
     * exit. Defaults to XpFormula.GEOFENCE_GRACE_PERIOD_SECONDS (300L) so
     * users who never touch the picker see unchanged behavior. The constant
     * remains the canonical default; this Flow is the source of truth for
     * the running countdown.
     */
    override val gracePeriodSeconds: Flow<Long> = dataStore.data.map { prefs ->
        prefs[gracePeriodSecondsKey] ?: com.pumpernickel.domain.gamification.XpFormula.GEOFENCE_GRACE_PERIOD_SECONDS
    }

    override suspend fun setGracePeriodSeconds(seconds: Long) {
        dataStore.edit { prefs -> prefs[gracePeriodSecondsKey] = seconds }
    }

    override val nutritionGoals: Flow<NutritionGoals> = combine(
        dataStore.data.map { it[calorieGoalKey]?.toIntOrNull() ?: 2500 },
        dataStore.data.map { it[proteinGoalKey]?.toIntOrNull() ?: 150 },
        dataStore.data.map { it[fatGoalKey]?.toIntOrNull() ?: 80 },
        dataStore.data.map { it[carbGoalKey]?.toIntOrNull() ?: 300 },
        dataStore.data.map { it[sugarGoalKey]?.toIntOrNull() ?: 50 }
    ) { cal, pro, fat, carb, sugar ->
        NutritionGoals(cal, pro, fat, carb, sugar)
    }

    override suspend fun setNutritionGoals(goals: NutritionGoals) {
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
    override val retroactiveApplied: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[retroactiveAppliedKey] ?: false
    }

    override suspend fun setRetroactiveApplied(applied: Boolean) {
        dataStore.edit { preferences ->
            preferences[retroactiveAppliedKey] = applied
        }
    }

    /**
     * D-16-10 / D-16-11 — `null` when no stats ever stored (calculator opens with placeholders);
     * fully populated otherwise. Flow only emits a value once ALL five keys are present.
     */
    override val userPhysicalStats: Flow<UserPhysicalStats?> = combine(
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

    override suspend fun setUserPhysicalStats(stats: UserPhysicalStats) {
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
    override val nutritionGoalsBannerDismissed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[nutritionGoalsBannerDismissedKey] ?: false
    }

    override suspend fun setNutritionGoalsBannerDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[nutritionGoalsBannerDismissedKey] = dismissed
        }
    }

    // D-18-06 — AI configuration. NOT secrets — the API key lives in
    // SecureKeyStore (Keychain on iOS, EncryptedSharedPreferences on Android).
    private val aiProviderPresetKey = stringPreferencesKey("ai_provider_preset")
    private val aiBaseUrlKey = stringPreferencesKey("ai_base_url")
    private val aiModelKey = stringPreferencesKey("ai_model")

    // Phase 22 — Multi-Provider AI config (D-22-09)
    private val activeProviderKey = stringPreferencesKey("active_provider")
    private val modelOpenAiKey = stringPreferencesKey("model_openai")
    private val modelTogetherKey = stringPreferencesKey("model_together")
    private val modelAnthropicKey = stringPreferencesKey("model_anthropic")
    private val baseUrlOpenAiKey = stringPreferencesKey("base_url_openai")
    private val baseUrlTogetherKey = stringPreferencesKey("base_url_together")
    private val baseUrlAnthropicKey = stringPreferencesKey("base_url_anthropic")
    private val migratedToMultiProviderKey = booleanPreferencesKey("migrated_to_multi_provider")

    override val aiProviderPreset: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiProviderPresetKey] ?: "openai"
    }

    override val aiBaseUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiBaseUrlKey] ?: "https://api.openai.com/v1"
    }

    override val aiModel: Flow<String> = dataStore.data.map { prefs ->
        prefs[aiModelKey] ?: "gpt-4o-mini"
    }

    override suspend fun setAiProviderPreset(preset: String) {
        dataStore.edit { prefs -> prefs[aiProviderPresetKey] = preset }
    }

    override suspend fun setAiBaseUrl(url: String) {
        dataStore.edit { prefs -> prefs[aiBaseUrlKey] = url }
    }

    override suspend fun setAiModel(model: String) {
        dataStore.edit { prefs -> prefs[aiModelKey] = model }
    }

    /**
     * D-19-07 — current month's Early-Exit budget. Auto-resets when the stored
     * year-month no longer matches the current local-time year-month: the Flow
     * emits a {used=0} snapshot WITHOUT touching DataStore on read (lazy reset).
     * The first write via incrementEarlyExitUsed() persists the new year-month.
     *
     * Consumed by EarlyExitTracker; the VM never reads this Flow directly.
     */
    override val earlyExits: Flow<EarlyExitBudget> = dataStore.data.map { prefs ->
        val storedYm = prefs[earlyExitYearMonthKey]
        val storedUsed = prefs[earlyExitUsedKey] ?: 0
        val currentYm = currentYearMonth()
        val effectiveUsed = if (storedYm == currentYm) storedUsed else 0
        EarlyExitBudget(
            used = effectiveUsed,
            remaining = (EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH - effectiveUsed).coerceAtLeast(0),
            yearMonth = currentYm
        )
    }

    /**
     * D-19-07 — atomically increment the Early-Exit counter for the current
     * month. If the stored year-month is stale, reset to 1 (this consumption is
     * the first exit of the new month). Capped at EARLY_EXIT_BUDGET_PER_MONTH —
     * caller must check budget first via EarlyExitTracker.consumeOne().
     */
    override suspend fun incrementEarlyExitUsed() {
        dataStore.edit { prefs ->
            val currentYm = currentYearMonth()
            val storedYm = prefs[earlyExitYearMonthKey]
            val storedUsed = if (storedYm == currentYm) (prefs[earlyExitUsedKey] ?: 0) else 0
            val nextUsed = (storedUsed + 1).coerceAtMost(EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH)
            prefs[earlyExitYearMonthKey] = currentYm
            prefs[earlyExitUsedKey] = nextUsed
        }
    }

    /**
     * Format: "YYYY-MM" in the user's local time zone. Used as the reset
     * sentinel — a calendar-month flip changes this string and triggers reset.
     */
    private fun currentYearMonth(): String {
        val now: LocalDateTime = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val month = now.monthNumber.toString().padStart(2, '0')
        return "${now.year}-$month"
    }

    // ============================================================
    // D-19-04 — PendingGeofenceExitStore implementation
    // ============================================================

    /**
     * Persist (or clear) the latest pending exit sentinel. Single edit{}
     * transaction so writes are atomic across all three keys.
     */
    override suspend fun setPendingExit(exit: PendingGeofenceExit?) {
        dataStore.edit { prefs ->
            if (exit == null) {
                prefs.remove(pendingGeofenceExitWorkoutIdKey)
                prefs.remove(pendingGeofenceExitTimeMillisKey)
                prefs.remove(pendingGeofenceExitRegionIdKey)
            } else {
                prefs[pendingGeofenceExitWorkoutIdKey] = exit.workoutId
                prefs[pendingGeofenceExitTimeMillisKey] = exit.exitTimeMillis
                prefs[pendingGeofenceExitRegionIdKey] = exit.regionId
            }
        }
    }

    /**
     * Atomically read and clear. The single edit{} block guarantees no other
     * concurrent reader can re-consume the same payload. Returns the previously
     * stored exit (or null if none was pending).
     */
    override suspend fun consumePendingExit(): PendingGeofenceExit? {
        var captured: PendingGeofenceExit? = null
        dataStore.edit { prefs ->
            val wid = prefs[pendingGeofenceExitWorkoutIdKey]
            val ts = prefs[pendingGeofenceExitTimeMillisKey]
            val rid = prefs[pendingGeofenceExitRegionIdKey]
            if (wid != null && ts != null && rid != null) {
                captured = PendingGeofenceExit(workoutId = wid, exitTimeMillis = ts, regionId = rid)
                prefs.remove(pendingGeofenceExitWorkoutIdKey)
                prefs.remove(pendingGeofenceExitTimeMillisKey)
                prefs.remove(pendingGeofenceExitRegionIdKey)
            }
        }
        return captured
    }

    /** Non-clearing read for diagnostics. */
    override suspend fun peekPendingExit(): PendingGeofenceExit? {
        val prefs = dataStore.data.first()
        val wid = prefs[pendingGeofenceExitWorkoutIdKey] ?: return null
        val ts = prefs[pendingGeofenceExitTimeMillisKey] ?: return null
        val rid = prefs[pendingGeofenceExitRegionIdKey] ?: return null
        return PendingGeofenceExit(workoutId = wid, exitTimeMillis = ts, regionId = rid)
    }

    // ============================================================
    // D-22-09 — Multi-Provider AI configuration
    // ============================================================

    override val activeProvider: Flow<ProviderId> = dataStore.data.map { prefs ->
        ProviderId.fromWireNameOrNull(prefs[activeProviderKey]) ?: ProviderId.OpenAI
    }

    override suspend fun setActiveProvider(provider: ProviderId) {
        dataStore.edit { prefs -> prefs[activeProviderKey] = provider.wireName }
    }

    override val modelByProvider: Flow<Map<ProviderId, String>> = dataStore.data.map { prefs ->
        mapOf(
            ProviderId.OpenAI to (prefs[modelOpenAiKey] ?: DEFAULT_MODEL_OPENAI),
            ProviderId.Together to (prefs[modelTogetherKey] ?: DEFAULT_MODEL_TOGETHER),
            ProviderId.Anthropic to (prefs[modelAnthropicKey] ?: DEFAULT_MODEL_ANTHROPIC)
        )
    }

    override suspend fun setModel(provider: ProviderId, model: String) {
        val key = when (provider) {
            ProviderId.OpenAI -> modelOpenAiKey
            ProviderId.Together -> modelTogetherKey
            ProviderId.Anthropic -> modelAnthropicKey
        }
        dataStore.edit { prefs -> prefs[key] = model }
    }

    override val baseUrlByProvider: Flow<Map<ProviderId, String>> = dataStore.data.map { prefs ->
        mapOf(
            ProviderId.OpenAI to (prefs[baseUrlOpenAiKey] ?: DEFAULT_BASE_URL_OPENAI),
            ProviderId.Together to (prefs[baseUrlTogetherKey] ?: DEFAULT_BASE_URL_TOGETHER),
            // D-22-01: Anthropic ist fixed — user override hat keinen Effekt (kein Setter-Path).
            ProviderId.Anthropic to DEFAULT_BASE_URL_ANTHROPIC
        )
    }

    override suspend fun setBaseUrl(provider: ProviderId, url: String) {
        val key = when (provider) {
            ProviderId.OpenAI -> baseUrlOpenAiKey
            ProviderId.Together -> baseUrlTogetherKey
            // D-22-01: Anthropic URL ist fixed — setBaseUrl(Anthropic, ...) ist ein No-Op.
            ProviderId.Anthropic -> return
        }
        dataStore.edit { prefs -> prefs[key] = url }
    }

    override val migratedToMultiProvider: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[migratedToMultiProviderKey] ?: false
    }

    override suspend fun setMigratedToMultiProvider(value: Boolean) {
        dataStore.edit { prefs -> prefs[migratedToMultiProviderKey] = value }
    }

    companion object {
        const val DEFAULT_MODEL_OPENAI = "gpt-4o-mini"
        const val DEFAULT_MODEL_TOGETHER = "google/gemma-4-31B-it"
        const val DEFAULT_MODEL_ANTHROPIC = "claude-opus-4-7"
        const val DEFAULT_BASE_URL_OPENAI = "https://api.openai.com/v1"
        const val DEFAULT_BASE_URL_TOGETHER = "https://api.together.ai/v1"
        // WR-08 — keep the "base URL" suffix-stripped version of the actual
        // AnthropicClient.MESSAGES_ENDPOINT (`https://api.anthropic.com/v1/messages`)
        // so future refactors that honor the per-provider base URL do not
        // silently route Anthropic requests to a non-existent endpoint. The
        // value mirrors the OpenAI/Together pattern (root + `/v1`).
        const val DEFAULT_BASE_URL_ANTHROPIC = "https://api.anthropic.com/v1"
    }
}

package com.pumpernickel.domain.repository

import com.pumpernickel.domain.geofence.EarlyExitBudget
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.UserPhysicalStats
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * D-20-04 — domain-side facade for all DataStore-backed user settings.
 *
 * Per D-20-04 the surface stays monolithic for Phase 20: no per-concern split,
 * no `Gateway`/`Port` rename. The interface mirrors the existing public API of
 * the DataStore-backed implementation 1:1 so the cross-cutting consumers
 * (~10 ViewModels, 3 domain Use-Cases, GamificationEngine) only need an
 * import-path swap.
 *
 * Narrow domain ports for sub-domains exist separately (Smell 13 / D-20-05):
 *  - `com.pumpernickel.domain.geofence.PendingGeofenceExitStore` — pending
 *    cold-start EXIT sentinel.
 *  - `com.pumpernickel.domain.repository.EarlyExitBudgetStore` — monthly
 *    Early-Exit budget; injected into `EarlyExitTracker` instead of the full
 *    `SettingsRepository`.
 *
 * The DataStore-backed implementation (`data.repository.SettingsRepositoryImpl`)
 * implements all three interfaces; Koin multi-binds them onto the same
 * singleton instance.
 */
interface SettingsRepository {

    // Tutorial
    val hasSeenTutorial: Flow<Boolean>
    suspend fun setHasSeenTutorial(value: Boolean)

    // Units / theme / accent
    val weightUnit: Flow<WeightUnit>
    val appTheme: Flow<String>
    val accentColor: Flow<String>
    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setAppTheme(theme: String)
    suspend fun setAccentColor(color: String)

    // Debug toggle + Geofence grace period (D-quick-vn7)
    val debugModeEnabled: Flow<Boolean>
    suspend fun setDebugModeEnabled(enabled: Boolean)
    val gracePeriodSeconds: Flow<Long>
    suspend fun setGracePeriodSeconds(seconds: Long)

    // Nutrition goals
    val nutritionGoals: Flow<NutritionGoals>
    suspend fun setNutritionGoals(goals: NutritionGoals)

    // Gamification retroactive sentinel (D-13)
    val retroactiveApplied: Flow<Boolean>
    suspend fun setRetroactiveApplied(applied: Boolean)

    // User physical stats (D-16-10 / D-16-11)
    val userPhysicalStats: Flow<UserPhysicalStats?>
    suspend fun setUserPhysicalStats(stats: UserPhysicalStats)

    // Nutrition-goals "set goals" banner dismissed sentinel (D-16-13 / D-16-14)
    val nutritionGoalsBannerDismissed: Flow<Boolean>
    suspend fun setNutritionGoalsBannerDismissed(dismissed: Boolean)

    // AI config (D-18-06)
    val aiProviderPreset: Flow<String>
    val aiBaseUrl: Flow<String>
    val aiModel: Flow<String>
    suspend fun setAiProviderPreset(preset: String)
    suspend fun setAiBaseUrl(url: String)
    suspend fun setAiModel(model: String)

    // Multi-Provider AI config (D-22-09 / Phase 22) — supersedes the single
    // aiBaseUrl/aiModel/aiProviderPreset triple above. The legacy flows remain
    // available so SettingsMigration (Plan 22-06) and any in-flight reader can
    // migrate gracefully; new call sites must use the per-provider flows.

    /** Currently active provider for all AI generation. Default: OpenAI. */
    val activeProvider: Flow<com.pumpernickel.infrastructure.ai.ProviderId>
    suspend fun setActiveProvider(provider: com.pumpernickel.infrastructure.ai.ProviderId)

    /**
     * Per-provider model id snapshot. Defaults:
     * - OpenAI:    `gpt-4o-mini`
     * - Together:  `google/gemma-4-31B-it`
     * - Anthropic: `claude-opus-4-7` (D-22-06)
     */
    val modelByProvider: Flow<Map<com.pumpernickel.infrastructure.ai.ProviderId, String>>
    suspend fun setModel(provider: com.pumpernickel.infrastructure.ai.ProviderId, model: String)

    /**
     * Per-provider base URL. Defaults:
     * - OpenAI:    `https://api.openai.com/v1`
     * - Together:  `https://api.together.ai/v1`
     * - Anthropic: `https://api.anthropic.com` (fixed; user override has no effect)
     */
    val baseUrlByProvider: Flow<Map<com.pumpernickel.infrastructure.ai.ProviderId, String>>
    suspend fun setBaseUrl(provider: com.pumpernickel.infrastructure.ai.ProviderId, url: String)

    /** One-shot migration sentinel (D-22-08); true after SettingsMigration has run successfully. */
    val migratedToMultiProvider: Flow<Boolean>
    suspend fun setMigratedToMultiProvider(value: Boolean)

    // Phase 19 — monthly Early-Exit budget (D-19-07)
    //
    // Also exposed via the narrow `EarlyExitBudgetStore` port (D-20-05);
    // `EarlyExitTracker` injects that port, not this fat interface.
    val earlyExits: Flow<EarlyExitBudget>
    suspend fun incrementEarlyExitUsed()
}

package com.pumpernickel.data.repository

import com.pumpernickel.domain.geofence.EarlyExitBudget
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.UserPhysicalStats
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.infrastructure.ai.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 22 Plan 10 test fixture — in-memory [SettingsRepository] for the
 * Migration + Dispatcher tests. Only the AI-config / migration surface is
 * meaningfully implemented; everything else is a default-flow no-op
 * (analogous to the inline-fake pattern from
 * `OpenFoodFactsAdapterBrandRankingTest`).
 *
 * Test recording: [setActiveCalls], [setModelCalls], [setBaseUrlCalls],
 * [setMigratedCalls] capture writes so assertions can verify migration
 * sequencing without re-reading the (already-changed) flows.
 */
class FakeSettingsRepository(
    initialLegacyBaseUrl: String = "https://api.openai.com/v1",
    initialLegacyModel: String = "gpt-4o-mini",
    initialMigrated: Boolean = false,
    initialActive: ProviderId = ProviderId.OpenAI
) : SettingsRepository {

    private val _activeProvider = MutableStateFlow(initialActive)
    private val _migrated = MutableStateFlow(initialMigrated)
    private val _models = MutableStateFlow(
        mapOf(
            ProviderId.OpenAI to "gpt-4o-mini",
            ProviderId.Together to "google/gemma-4-31B-it",
            ProviderId.Anthropic to "claude-opus-4-7"
        )
    )
    private val _baseUrls = MutableStateFlow(
        mapOf(
            ProviderId.OpenAI to "https://api.openai.com/v1",
            ProviderId.Together to "https://api.together.ai/v1",
            ProviderId.Anthropic to "https://api.anthropic.com"
        )
    )
    private val _legacyBaseUrl = MutableStateFlow(initialLegacyBaseUrl)
    private val _legacyModel = MutableStateFlow(initialLegacyModel)

    val setActiveCalls = mutableListOf<ProviderId>()
    val setModelCalls = mutableListOf<Pair<ProviderId, String>>()
    val setBaseUrlCalls = mutableListOf<Pair<ProviderId, String>>()
    val setMigratedCalls = mutableListOf<Boolean>()

    override val activeProvider: Flow<ProviderId> = _activeProvider.asStateFlow()
    override val modelByProvider: Flow<Map<ProviderId, String>> = _models.asStateFlow()
    override val baseUrlByProvider: Flow<Map<ProviderId, String>> = _baseUrls.asStateFlow()
    override val migratedToMultiProvider: Flow<Boolean> = _migrated.asStateFlow()
    override val aiBaseUrl: Flow<String> = _legacyBaseUrl.asStateFlow()
    override val aiModel: Flow<String> = _legacyModel.asStateFlow()
    override val aiProviderPreset: Flow<String> = MutableStateFlow("openai").asStateFlow()

    override suspend fun setActiveProvider(provider: ProviderId) {
        _activeProvider.value = provider
        setActiveCalls += provider
    }

    override suspend fun setModel(provider: ProviderId, model: String) {
        _models.value = _models.value + (provider to model)
        setModelCalls += (provider to model)
    }

    override suspend fun setBaseUrl(provider: ProviderId, url: String) {
        // Mirror the production behaviour: Anthropic baseUrl is fixed (setter no-op).
        if (provider == ProviderId.Anthropic) return
        _baseUrls.value = _baseUrls.value + (provider to url)
        setBaseUrlCalls += (provider to url)
    }

    override suspend fun setMigratedToMultiProvider(value: Boolean) {
        _migrated.value = value
        setMigratedCalls += value
    }

    override suspend fun setAiBaseUrl(url: String) {
        _legacyBaseUrl.value = url
    }

    override suspend fun setAiModel(model: String) {
        _legacyModel.value = model
    }

    // ----- unused-but-required ----------------------------------------

    override val hasSeenTutorial: Flow<Boolean> = MutableStateFlow(true).asStateFlow()
    override suspend fun setHasSeenTutorial(value: Boolean) {}
    override val weightUnit: Flow<WeightUnit> = MutableStateFlow(WeightUnit.KG).asStateFlow()
    override val appTheme: Flow<String> = MutableStateFlow("system").asStateFlow()
    override val accentColor: Flow<String> = MutableStateFlow("green").asStateFlow()
    override suspend fun setWeightUnit(unit: WeightUnit) {}
    override suspend fun setAppTheme(theme: String) {}
    override suspend fun setAccentColor(color: String) {}
    override val debugModeEnabled: Flow<Boolean> = MutableStateFlow(false).asStateFlow()
    override suspend fun setDebugModeEnabled(enabled: Boolean) {}
    override val gracePeriodSeconds: Flow<Long> = MutableStateFlow(300L).asStateFlow()
    override suspend fun setGracePeriodSeconds(seconds: Long) {}
    override val nutritionGoals: Flow<NutritionGoals> =
        MutableStateFlow(NutritionGoals(2500, 150, 80, 300, 50)).asStateFlow()
    override suspend fun setNutritionGoals(goals: NutritionGoals) {}
    override val retroactiveApplied: Flow<Boolean> = MutableStateFlow(true).asStateFlow()
    override suspend fun setRetroactiveApplied(applied: Boolean) {}
    override val userPhysicalStats: Flow<UserPhysicalStats?> = MutableStateFlow(null).asStateFlow()
    override suspend fun setUserPhysicalStats(stats: UserPhysicalStats) {}
    override val nutritionGoalsBannerDismissed: Flow<Boolean> = MutableStateFlow(false).asStateFlow()
    override suspend fun setNutritionGoalsBannerDismissed(dismissed: Boolean) {}
    override suspend fun setAiProviderPreset(preset: String) {}
    override val earlyExits: Flow<EarlyExitBudget> =
        MutableStateFlow(EarlyExitBudget(0, 2, "2026-05")).asStateFlow()
    override suspend fun incrementEarlyExitUsed() {}
}

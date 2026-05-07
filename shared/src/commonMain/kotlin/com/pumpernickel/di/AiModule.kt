package com.pumpernickel.di

import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.ai.AiPromptCatalog
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import com.pumpernickel.presentation.ai.RecipeAiViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Phase 18 — DI bindings for the AI features. Wave-3 plan 05 registers the
 * client + prompt catalog + AiSettingsViewModel; downstream Wave-4 plans
 * (06, 08) APPEND their use cases + VMs to this same module file.
 *
 * SecureKeyStore is bound platform-side in PlatformModule.{android,ios}.kt
 * (Plan 04). The OpenAICompatibleClient takes a keyProvider lambda that
 * resolves SecureKeyStore from Koin and calls readApiKey() per request —
 * the client never holds the key.
 */
val aiModule = module {
    single { AiPromptCatalog() }

    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = { get<SecureKeyStore>().readApiKey() }
        )
    }

    viewModel { AiSettingsViewModel(get(), get()) }

    // --- Recipe AI (F8) — Plan 08 additions. Keep isolated for clean wave-merge. ---
    // get() order: OpenAICompatibleClient, AiPromptCatalog, FoodRepository,
    //              SettingsRepository, CalculateDailyMacrosUseCase,
    //              CalculateRecipeMacrosUseCase, LoadConsumptionsForDateUseCase
    single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RecipeAiViewModel(get(), get()) }
    // --- End Recipe AI (Plan 08) ---
}

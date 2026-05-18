package com.pumpernickel.di

import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.domain.ai.AiPromptCatalog
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.infrastructure.ai.AiClient
import com.pumpernickel.infrastructure.ai.OpenAiCompatibleAiClient
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import com.pumpernickel.presentation.ai.RecipeAiViewModel
import com.pumpernickel.presentation.ai.WorkoutAiViewModel
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

    // Ktor adapter kept for `OpenAiCompatibleAiClient` delegation
    // (Phase 20 Plan 07 / Smell 4). Auth resolves via `SecureKeyStore` inside
    // the client per request — neither the port nor the adapter ever holds
    // the API key.
    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = { get<SecureKeyStore>().readApiKey() }
        )
    }
    // Domain port binding — use-cases (`WorkoutAiUseCase`, `RecipeAiUseCase`,
    // `AiGenerationManager`) inject `AiClient` instead of the Ktor client.
    // The adapter wraps `OpenAICompatibleClient` and performs Ktor-DTO
    // construction + SSE streaming under the hood.
    single<AiClient> { OpenAiCompatibleAiClient(get()) }

    viewModel { AiSettingsViewModel(get(), get()) }

    // --- Workout AI (F6) — Plan 06 additions. Keep isolated for clean wave-merge. ---
    single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }
    // --- End Workout AI (Plan 06) ---

    // --- Recipe AI (F8) — Plan 08 additions. Keep isolated for clean wave-merge. ---
    // get() order: OpenAICompatibleClient, AiPromptCatalog, FoodRepository,
    //              SettingsRepository, CalculateDailyMacrosUseCase,
    //              CalculateRecipeMacrosUseCase, LoadConsumptionsForDateUseCase
    single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }

    // Phase 19 — Background AI Generation
    single { com.pumpernickel.domain.ai.AiGenerationManager(get(), get(), get(), get()) }

    viewModel { WorkoutAiViewModel(get(), get(), get()) }
    viewModel { RecipeAiViewModel(get(), get(), get()) }
    // --- End Recipe AI (Plan 08) ---
}

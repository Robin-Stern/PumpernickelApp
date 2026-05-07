package com.pumpernickel.di

import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.ai.AiPromptCatalog
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.presentation.ai.AiSettingsViewModel
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

    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = { get<SecureKeyStore>().readApiKey() }
        )
    }

    viewModel { AiSettingsViewModel(get(), get()) }

    single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }
    viewModel { WorkoutAiViewModel(get(), get()) }
}

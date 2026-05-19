package com.pumpernickel.di

import com.pumpernickel.data.api.AnthropicClient
import com.pumpernickel.data.api.AnthropicOAuthClient
import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.repository.SettingsMigration
import com.pumpernickel.domain.ai.AiPromptCatalog
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.infrastructure.ai.AiClient
import com.pumpernickel.infrastructure.ai.AnthropicAiClient
import com.pumpernickel.infrastructure.ai.AnthropicOAuthFlow
import com.pumpernickel.infrastructure.ai.Credential
import com.pumpernickel.infrastructure.ai.DispatchingAiClient
import com.pumpernickel.infrastructure.ai.MigratingAiClient
import com.pumpernickel.infrastructure.ai.OpenAiCompatibleAiClient
import com.pumpernickel.infrastructure.ai.ProviderId
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import com.pumpernickel.infrastructure.ai.SecureKeyStoreAdapter
import com.pumpernickel.infrastructure.ai.SecureKeyStoreSurface
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import com.pumpernickel.presentation.ai.RecipeAiViewModel
import com.pumpernickel.presentation.ai.WorkoutAiViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Phase 22 — Multi-Provider DI wiring.
 *
 * Layer cake (call order from a Use-Case perspective):
 *   Use-Case → AiClient (= MigratingAiClient) → DispatchingAiClient → {OpenAiCompatibleAiClient, AnthropicAiClient}
 *
 * MigratingAiClient is the outermost layer: it runs SettingsMigration.run()
 * lazily on first call (idempotent via DataStore sentinel) before delegating
 * to the dispatcher. This avoids any Application/AppDelegate-level
 * initialization hook (CONTEXT.md Discretion: migration trigger).
 */
val aiModule = module {
    single { AiPromptCatalog() }

    // === Provider-1: OpenAI / Together (OpenAI-compatible) ===
    // Key resolution: active provider's ApiKey credential. Used for both
    // OpenAI- and Together-active states (both dispatch to this adapter via
    // DispatchingAiClient).
    single {
        OpenAICompatibleClient(
            client = get(),
            keyProvider = {
                val active = get<SettingsRepository>().activeProvider.first()
                val provider = if (active == ProviderId.Anthropic) ProviderId.OpenAI else active
                when (val cred = get<SecureKeyStore>().readCredential(provider)) {
                    is Credential.ApiKey -> cred.value
                    else -> null
                }
            }
        )
    }
    single { OpenAiCompatibleAiClient(get()) }

    // === Provider-2: Anthropic ===
    // AnthropicClient owns its own credential lookup (OAuth + API-key dispatch
    // in ensureFreshCredential) so no keyProvider lambda needed at Koin level.
    single { AnthropicOAuthClient(client = get()) }
    single {
        AnthropicClient(
            client = get(),
            secureKeyStore = get(),
            oauthClient = get(),
            oauthClientId = AnthropicOAuthFlow.CLIENT_ID
        )
    }
    single { AnthropicAiClient(get()) }

    // === OAuth flow orchestrator (consumed by AiSettingsViewModel in Plan 08) ===
    single {
        AnthropicOAuthFlow(
            httpClient = get(),
            browserLauncher = get()
        )
    }

    // === Migration (lazy, idempotent) ===
    // SecureKeyStoreAdapter bridges the platform-`expect class` SecureKeyStore
    // to the SecureKeyStoreSurface interface — see Plan 22-10 SUMMARY for the
    // testability rationale.
    single<SecureKeyStoreSurface> { SecureKeyStoreAdapter(get()) }
    single { SettingsMigration(settingsRepository = get(), secureKeyStore = get<SecureKeyStoreSurface>()) }

    // === Provider switch + lazy migration wrapper ===
    // The AiClient binding is wrapped so the very first AI call triggers migration.
    // Use-cases inject `AiClient` — they get the migrating wrapper transparently.
    single {
        // DispatchingAiClient constructor was widened from
        // (OpenAiCompatibleAiClient, AnthropicAiClient) to (AiClient, AiClient)
        // for testability — see Plan 22-10 SUMMARY. Explicit `get<...>()` types
        // keep Koin resolving the concrete adapters (the AiClient binding is
        // the MigratingAiClient wrapper below, which we must NOT inject here
        // or we'd recurse).
        DispatchingAiClient(
            settings = get(),
            openAiAdapter = get<OpenAiCompatibleAiClient>(),
            anthropicAdapter = get<AnthropicAiClient>()
        )
    }
    single<AiClient> {
        MigratingAiClient(delegate = get<DispatchingAiClient>(), migration = get())
    }

    // === Use-cases + VMs (unchanged from Phase 18 — they inject AiClient) ===
    viewModel { AiSettingsViewModel(get(), get(), get()) }
    single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }
    single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { com.pumpernickel.domain.ai.AiGenerationManager(get(), get(), get(), get()) }
    viewModel { WorkoutAiViewModel(get(), get(), get()) }
    viewModel { RecipeAiViewModel(get(), get(), get()) }
}

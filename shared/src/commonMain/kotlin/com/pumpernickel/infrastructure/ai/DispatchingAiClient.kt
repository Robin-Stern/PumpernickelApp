package com.pumpernickel.infrastructure.ai

import com.pumpernickel.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * D-22-04 — runtime dispatcher across provider-specific [AiClient] adapters.
 * Reads [SettingsRepository.activeProvider] per call (cheap — single DataStore
 * snapshot) and delegates to the matching adapter. This is the schlankste
 * provider-switch strategy from PATTERNS.md "Option C":
 *  - no Koin module reload
 *  - no runBlocking
 *  - no Flow-observable factory plumbing
 *  - re-evaluates per call so a Settings UI provider-switch is visible to
 *    the very next AI call, no app restart required
 *
 * Use-cases continue to inject `AiClient` — they remain provider-agnostic.
 *
 * Note: `OpenAi` and `Together` BOTH dispatch to [openAiAdapter] because they
 * share the same wire format (OpenAI-compatible /chat/completions). The
 * caller-supplied `baseUrl` argument is what distinguishes them — see
 * [com.pumpernickel.domain.ai.WorkoutAiUseCase] /
 * [com.pumpernickel.domain.ai.RecipeAiUseCase] callers passing the per-provider
 * base URL.
 */
class DispatchingAiClient(
    private val settings: SettingsRepository,
    private val openAiAdapter: AiClient,
    private val anthropicAdapter: AiClient
) : AiClient {

    override suspend fun completeJsonSchema(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String = resolve().completeJsonSchema(baseUrl, model, systemPrompt, userPrompt, schema, onProgress)

    override suspend fun completeJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String = resolve().completeJsonObject(baseUrl, model, systemPrompt, userPrompt, onProgress)

    private suspend fun resolve(): AiClient = when (settings.activeProvider.first()) {
        ProviderId.OpenAI, ProviderId.Together -> openAiAdapter
        ProviderId.Anthropic -> anthropicAdapter
    }
}

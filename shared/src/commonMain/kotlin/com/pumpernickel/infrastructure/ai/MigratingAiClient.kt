package com.pumpernickel.infrastructure.ai

import com.pumpernickel.data.repository.SettingsMigration

/**
 * D-22-08 — outermost [AiClient] wrapper that runs [SettingsMigration.run] on
 * every call. The migration is idempotent (sentinel guarded), so post-first-call
 * the overhead is one DataStore read.
 *
 * Rationale (PATTERNS.md "no application/AppDelegate trigger"): the migration
 * lives in the AiClient call path because anyone who needs an AiClient
 * implicitly also needs the migration to have run. Users who never trigger
 * an AI call have nothing to migrate — and pay no cost.
 */
class MigratingAiClient(
    private val delegate: AiClient,
    private val migration: SettingsMigration
) : AiClient {

    override suspend fun completeJsonSchema(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        schema: AiJsonSchema,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        migration.run()
        return delegate.completeJsonSchema(baseUrl, model, systemPrompt, userPrompt, schema, onProgress)
    }

    override suspend fun completeJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): String {
        migration.run()
        return delegate.completeJsonObject(baseUrl, model, systemPrompt, userPrompt, onProgress)
    }
}

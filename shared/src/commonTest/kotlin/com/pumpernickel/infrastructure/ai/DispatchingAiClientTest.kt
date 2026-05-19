package com.pumpernickel.infrastructure.ai

import com.pumpernickel.data.repository.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 22 Plan 10 / D-22-13 — unit-tests for [DispatchingAiClient]. Verifies
 * that [SettingsRepository.activeProvider] is consulted on each call (no
 * caching) and the right sub-adapter receives the request.
 *
 * Plan 10 widened the ctor signature from `(OpenAiCompatibleAiClient,
 * AnthropicAiClient)` to `(AiClient, AiClient)` so a [RecordingAiClient]
 * fake can be substituted. The widening is safe because Koin's bindings stay
 * untouched: `single { DispatchingAiClient(get(), get<OpenAiCompatibleAiClient>(),
 * get<AnthropicAiClient>()) }` continues to resolve concrete impls.
 */
class DispatchingAiClientTest {

    /** Records which method was called and returns the canned label. */
    private class RecordingAiClient(val label: String) : AiClient {
        val calls = mutableListOf<String>()
        override suspend fun completeJsonSchema(
            baseUrl: String,
            model: String,
            systemPrompt: String,
            userPrompt: String,
            schema: AiJsonSchema,
            onProgress: (content: String, reasoning: String) -> Unit
        ): String {
            calls += "schema:$label"
            return "ok-$label"
        }

        override suspend fun completeJsonObject(
            baseUrl: String,
            model: String,
            systemPrompt: String,
            userPrompt: String,
            onProgress: (content: String, reasoning: String) -> Unit
        ): String {
            calls += "object:$label"
            return "ok-$label"
        }
    }

    private val dummySchema = AiJsonSchema(name = "Probe", schemaJson = "{}")

    @Test
    fun dispatch_activeOpenAi_callsOpenAiAdapter() = runTest {
        val settings = FakeSettingsRepository(initialActive = ProviderId.OpenAI)
        val openAi = RecordingAiClient("openai")
        val anth = RecordingAiClient("anthropic")
        val d = DispatchingAiClient(settings, openAi, anth)

        val result = d.completeJsonSchema("u", "m", "s", "u", dummySchema) { _, _ -> }

        assertEquals("ok-openai", result)
        assertEquals(listOf("schema:openai"), openAi.calls)
        assertTrue(anth.calls.isEmpty(), "Anthropic adapter must NOT be touched when active=OpenAI")
    }

    @Test
    fun dispatch_activeTogether_callsOpenAiAdapter() = runTest {
        // Together shares the OpenAI-compatible /chat/completions wire format,
        // so DispatchingAiClient.resolve() routes both to openAiAdapter.
        val settings = FakeSettingsRepository(initialActive = ProviderId.Together)
        val openAi = RecordingAiClient("openai")
        val anth = RecordingAiClient("anthropic")
        val d = DispatchingAiClient(settings, openAi, anth)

        d.completeJsonObject("u", "m", "s", "u") { _, _ -> }

        assertEquals(listOf("object:openai"), openAi.calls)
        assertTrue(anth.calls.isEmpty())
    }

    @Test
    fun dispatch_activeAnthropic_callsAnthropicAdapter() = runTest {
        val settings = FakeSettingsRepository(initialActive = ProviderId.Anthropic)
        val openAi = RecordingAiClient("openai")
        val anth = RecordingAiClient("anthropic")
        val d = DispatchingAiClient(settings, openAi, anth)

        d.completeJsonSchema("u", "m", "s", "u", dummySchema) { _, _ -> }

        assertEquals(listOf("schema:anthropic"), anth.calls)
        assertTrue(openAi.calls.isEmpty())
    }

    @Test
    fun dispatch_afterActiveProviderSwitch_routesNewCallsToNewAdapter() = runTest {
        val settings = FakeSettingsRepository(initialActive = ProviderId.OpenAI)
        val openAi = RecordingAiClient("openai")
        val anth = RecordingAiClient("anthropic")
        val d = DispatchingAiClient(settings, openAi, anth)

        // First call goes to OpenAI.
        d.completeJsonObject("u", "m", "s", "u") { _, _ -> }
        // User toggles provider in Settings.
        settings.setActiveProvider(ProviderId.Anthropic)
        // Next call must route to Anthropic — per-call resolution, no cache.
        d.completeJsonObject("u", "m", "s", "u") { _, _ -> }

        assertEquals(1, openAi.calls.size, "OpenAI got the first call only")
        assertEquals(1, anth.calls.size, "Anthropic got the post-switch call only")
    }
}

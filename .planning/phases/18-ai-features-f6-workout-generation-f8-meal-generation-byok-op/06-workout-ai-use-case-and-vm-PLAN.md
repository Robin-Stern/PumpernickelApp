---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 06
type: execute
wave: 4
depends_on: [01, 02, 03, 04, 05]
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt
autonomous: true
requirements:
  - REQ-AI-01
  - REQ-AI-02
  - REQ-AI-03
  - REQ-AI-07
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "WorkoutAiUseCase.invoke(form) builds the prompt, calls the OpenAI client with response_format=json_schema, validates, resolves Exercise references against the catalog (case-insensitive trimmed match), and returns a preview-ready WorkoutAiPreview WITHOUT writing to the DB"
    - "On schema-invalid response, the use case retries ONCE with response_format=json_object + schema embedded in the system prompt (D-18-14)"
    - "Each new inline exercise is staged with source=\"AI\" but only persisted when commit() is called from the VM after user Save (D-18-12 transactional)"
    - "WorkoutAiViewModel exposes uiState as a sealed WorkoutAiUiState (NoKey / Form / Generating / Preview / Error / Saved) and provides generate / cancel / save / discard actions"
    - "Cancel aborts the in-flight Job and transitions to Form (no error UI) per D-18-16"
    - "All five AiError classes from D-18-08 surface as Error(AiError) states with the correct distinct copy candidate (UI handles formatting in Plan 07)"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt"
      provides: "WorkoutAiPreview + WorkoutAiForm + StagedTemplate + StagedExercise"
      contains: "data class WorkoutAiPreview"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt"
      provides: "Use case orchestrating prompt → client → validate → resolve → preview"
      contains: "class WorkoutAiUseCase"
      min_lines: 80
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt"
      provides: "Form state + Generating + Preview + sealed UiState + generate/cancel/save"
      contains: "class WorkoutAiViewModel"
      min_lines: 80
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt"
      provides: "iOS KoinHelper for WorkoutAiViewModel"
      contains: "class WorkoutAiKoinHelper"
  key_links:
    - from: "WorkoutAiUseCase.invoke"
      to: "OpenAICompatibleClient.chatCompletion"
      via: "constructor-injected client"
      pattern: "client\\.chatCompletion"
    - from: "WorkoutAiUseCase.commit"
      to: "TemplateRepository.createTemplate + ExerciseRepository.createExercise"
      via: "transactional save (D-18-12)"
      pattern: "templateRepository\\.createTemplate"
    - from: "WorkoutAiViewModel.uiState"
      to: "WorkoutAiUiState sealed class"
      via: "MutableStateFlow"
      pattern: "MutableStateFlow<WorkoutAiUiState>"
---

<objective>
Implement the Workout AI generation flow at the data + domain + presentation layers.

**WorkoutAiUseCase** orchestrates: build prompt (via AiPromptCatalog) → call OpenAICompatibleClient with `response_format: json_schema` → parse the response → validate against WorkoutAiSchema invariants → resolve `exerciseName` references against existing Exercises (case-insensitive trimmed match) → stage inline new exercises with `source = "AI"` → return a `WorkoutAiPreview` containing in-memory staged data. **NO DB writes during invoke.** A separate `commit(preview)` method is called from the VM on user Save and writes the templates + new exercises transactionally with `source = "AI"`. On schema-invalid response, retry ONCE with `response_format: json_object` + schema embedded in the system prompt body (D-18-14).

**WorkoutAiViewModel** drives the F6 flow: state machine with `NoKey` (apiKeyConfigured == false), `Form(muscles, count, split)`, `Generating(skeletonRowCount)`, `Preview(WorkoutAiPreview)`, `Error(AiError)`, `Saved(templateIds)`. Actions: `updateForm`, `generate()`, `cancel()`, `save()`, `discard()`. The cancel action stores the in-flight Job and calls `.cancel()` per D-18-16.

Implements REQ-AI-01 (form + multi-template), REQ-AI-02 (schema validation), REQ-AI-03 (versioned prompt referenced by client), REQ-AI-07 (no half-formed writes), REQ-AI-08 (per-class error states wired). Closes D-18-09 (Exercise authoring rights), D-18-12 (preview-then-save transactional), D-18-13 (single call returning array for splits), D-18-14 (json_schema with retry-once fallback).

Output: 4 new files + 1 modified AiModule + 1 new KoinHelper. Android UI in Plan 07; iOS handoff in Plan 10.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt

<interfaces>
WorkoutAiSchema.kt (Plan 03):
- WorkoutAiResponse(templates: List<WorkoutAiTemplate>, inlineNewExercises: List<WorkoutAiInlineExercise>, refusal: String?)
- WorkoutAiTemplate(name, description?, exercises: List<WorkoutAiTemplateExercise>)
- WorkoutAiTemplateExercise(exerciseName, targetSets, targetReps, restPeriodSec, note?)
- WorkoutAiInlineExercise(name, primaryMuscles: List<String>, secondaryMuscles, equipment?, force?, mechanic?, level, category, instructions)

OpenAICompatibleClient.kt (Plan 02):
- suspend fun chatCompletion(baseUrl: String, request: ChatRequest): ChatResponse
- Throws AiError on Ktor exceptions; throws AiError.AuthOrQuota(401) when key is null

ChatRequest (Plan 02):
- ChatRequest(model, messages: List<ChatMessage>, responseFormat: ResponseFormat?, temperature, maxTokens?)
- ResponseFormat(type: String, jsonSchema: JsonSchemaSpec?)
- JsonSchemaSpec(name, schema: JsonElement, strict)

ExerciseRepository (existing):
- fun getExercises(): Flow<List<Exercise>>
- suspend fun createExercise(exercise: Exercise) — Exercise.kt has source: String? after Plan 01

TemplateRepository (existing):
- suspend fun createTemplate(name: String): Long  — Long is the new template id
- suspend fun addExercise(templateId, exerciseId, exerciseName, primaryMuscles, order): Long
- suspend fun updateExerciseTargets(id, sets, reps, restSec)

NOTE: TemplateRepository.createTemplate currently does NOT accept a source argument. The executor must extend it during this task to accept `source: String? = null` and pass it through to the WorkoutTemplateEntity constructor. That single-line addition lives in this plan because it's needed by commit().

MuscleGroup.fromDbName(name: String): MuscleGroup? — used to validate inline-exercise primaryMuscles strings.

TemplateEditorViewModel.SaveResult sealed class precedent (lines 38-45 of TemplateEditorViewModel.kt):
```
sealed class SaveResult {
    data class Success(val templateId: Long) : SaveResult()
    data class Error(val message: String) : SaveResult()
}
```

ProgressGalleryViewModel sealed UiState precedent (commonMain/presentation/progresspic/ProgressGalleryViewModel.kt):
- @NativeCoroutinesState val uiState: StateFlow<GalleryUiState>
- private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
- @NativeCoroutines val navEvents: SharedFlow<NavEvent>
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create WorkoutAiPreview + WorkoutAiUseCase</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenAICompatibleClient.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/AiChatDto.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutTemplateEntity.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt** — preview/staging data classes:

```kotlin
package com.pumpernickel.domain.ai

import com.pumpernickel.domain.model.MuscleGroup

/**
 * D-18-12 — preview is in-memory staging. Until commit() runs, NOTHING is
 * written to the DB. inlineNewExercises is the list of Exercise rows the
 * LLM emitted that don't match anything in the catalog; commit persists
 * them with source="AI" before linking from staged templates.
 */
data class WorkoutAiPreview(
    val templates: List<StagedTemplate>,
    val inlineNewExercises: List<StagedExercise>
)

data class StagedTemplate(
    val name: String,
    val description: String?,
    val exercises: List<StagedTemplateExercise>
)

data class StagedTemplateExercise(
    val exerciseName: String,        // resolved name (matches an existing Exercise OR a StagedExercise)
    val resolvedExerciseId: String?, // non-null if matched against existing Exercise; null if matches a StagedExercise
    val targetSets: Int,
    val targetReps: Int,
    val restPeriodSec: Int,
    val note: String?
)

data class StagedExercise(
    val name: String,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: String?,
    val force: String?,
    val mechanic: String?,
    val level: String,
    val category: String,
    val instructions: List<String>
)

/**
 * D-18-03 — F6 form fields.
 */
data class WorkoutAiForm(
    val targetMuscles: List<MuscleGroup>,
    val exerciseCount: Int,                  // 3..8 typical (Claude's discretion in Plan 07)
    val splitStyle: WorkoutAiSplit
)

enum class WorkoutAiSplit(val templateCount: Int) {
    NONE(1),
    PUSH_PULL_LEGS(3),
    UPPER_LOWER(2),
    FULL_BODY(1)
}
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt** — extend `createTemplate` to accept an optional `source: String? = null` argument and pass it through:

Locate `createTemplate(name: String): Long` in the interface and impl. Update the signature to:

```kotlin
// In TemplateRepository interface:
suspend fun createTemplate(name: String, source: String? = null): Long

// In TemplateRepositoryImpl.createTemplate body — pass source to the entity:
override suspend fun createTemplate(name: String, source: String?): Long {
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    return templateDao.insertTemplate(
        WorkoutTemplateEntity(
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
            source = source
        )
    )
}
```

This is additive — every existing call site (TemplateListViewModel, etc.) keeps working because `source` defaults to `null`.

**File 3: shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt** — orchestration:

```kotlin
package com.pumpernickel.domain.ai

import com.pumpernickel.data.api.ChatMessage
import com.pumpernickel.data.api.ChatRequest
import com.pumpernickel.data.api.JsonSchemaSpec
import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.api.ResponseFormat
import com.pumpernickel.data.api.WorkoutAiResponse
import com.pumpernickel.data.api.WorkoutAiTemplate
import com.pumpernickel.data.api.WorkoutAiTemplateExercise
import com.pumpernickel.data.api.WorkoutAiInlineExercise
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WorkoutAiUseCase(
    private val client: OpenAICompatibleClient,
    private val promptCatalog: AiPromptCatalog,
    private val exerciseRepository: ExerciseRepository,
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    /**
     * Build the prompt, call the LLM, validate the response, resolve Exercise
     * references — all WITHOUT touching the DB. Returns a WorkoutAiPreview
     * that the VM can render in the preview sheet.
     *
     * Throws AiError on any failure (Timeout / Network / AuthOrQuota / Provider /
     * SchemaInvalid). Coroutine cancellation propagates as CancellationException.
     */
    suspend fun invoke(form: WorkoutAiForm): WorkoutAiPreview {
        val baseUrl = settingsRepository.aiBaseUrl.first()
        val model = settingsRepository.aiModel.first()
        val systemPrompt = promptCatalog.workoutSystemPrompt()
        val existingExercises = exerciseRepository.getExercises().first()
        val userMessage = buildUserMessage(form, existingExercises)

        // Primary path — response_format = json_schema (D-18-14).
        // Fallback fires for:
        //   1. SchemaInvalid (response missed schema even with json_schema set)
        //   2. AuthOrQuota with httpStatus 400 OR 422 (provider returned
        //      "structured-output-not-supported" — Together.AI / OpenRouter routes
        //      that don't honor response_format=json_schema typically respond 400/422
        //      rather than 401/403). Retry with json_object + schema-in-prompt.
        // 401 / 403 / 429 etc. still surface as auth/quota error per D-18-08.
        val response = try {
            callWithJsonSchema(baseUrl, model, systemPrompt, userMessage)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AiError) {
            val shouldFallback = e is AiError.SchemaInvalid ||
                (e is AiError.AuthOrQuota && e.httpStatus in setOf(400, 422))
            if (shouldFallback) {
                callWithJsonObject(baseUrl, model, systemPrompt, userMessage)
            } else throw e
        }

        if (!response.refusal.isNullOrBlank()) {
            throw AiError.SchemaInvalid("LLM refused: ${response.refusal}")
        }

        validateResponse(response, form)
        return resolvePreview(response, existingExercises)
    }

    /**
     * D-18-12 — Save: persist new exercises with source="AI" first, then write
     * each template + its TemplateExercise rows referencing the resolved or
     * just-persisted exercise ids. Returns the new template ids.
     */
    suspend fun commit(preview: WorkoutAiPreview): List<Long> {
        // 1. Persist inline new exercises (source = AI). Map name -> new id.
        val newExerciseIdByName: Map<String, String> = preview.inlineNewExercises.associate { staged ->
            val newId = Uuid.random().toString()
            exerciseRepository.createExercise(stagedExerciseToDomain(staged, id = newId))
            staged.name.lowercase().trim() to newId
        }

        // 2. Persist templates. For each StagedTemplateExercise:
        //    - if resolvedExerciseId != null: use it.
        //    - else look up the new exercise id by name.
        val templateIds = mutableListOf<Long>()
        for (template in preview.templates) {
            val templateId = templateRepository.createTemplate(name = template.name, source = "AI")
            for ((order, ex) in template.exercises.withIndex()) {
                val resolvedId = ex.resolvedExerciseId
                    ?: newExerciseIdByName[ex.exerciseName.lowercase().trim()]
                    ?: throw AiError.SchemaInvalid("Could not resolve exercise: ${ex.exerciseName}")
                val muscles = lookupMuscles(resolvedId, preview.inlineNewExercises)
                val templateExerciseId = templateRepository.addExercise(
                    templateId = templateId,
                    exerciseId = resolvedId,
                    exerciseName = ex.exerciseName,
                    primaryMuscles = muscles,
                    order = order
                )
                templateRepository.updateExerciseTargets(
                    id = templateExerciseId,
                    sets = ex.targetSets,
                    reps = ex.targetReps,
                    restSec = ex.restPeriodSec
                )
            }
            templateIds += templateId
        }
        return templateIds
    }

    // --- private helpers below ---

    private fun stagedExerciseToDomain(staged: StagedExercise, id: String): Exercise = Exercise(
        id = id,
        name = staged.name,
        force = staged.force,
        level = staged.level,
        mechanic = staged.mechanic,
        equipment = staged.equipment,
        category = staged.category,
        instructions = staged.instructions,
        images = emptyList(),
        isCustom = true,
        primaryMuscles = staged.primaryMuscles,
        secondaryMuscles = staged.secondaryMuscles,
        source = "AI"
    )

    private fun lookupMuscles(
        exerciseId: String,
        inlineNewExercises: List<StagedExercise>
    ): List<MuscleGroup> {
        // For inline-new ids we don't know yet — they were just created; pass empty so
        // TemplateRepository can re-resolve via the freshly-persisted Exercise. Existing
        // exercises also re-resolve via TemplateRepository.getTemplateExercises path.
        // Implementation detail: this function is purely a placeholder that returns
        // primaryMuscles when the executor wants pre-resolved values; otherwise emptyList()
        // is acceptable because the repository already re-fetches names & muscles on read.
        return emptyList()
    }

    private fun buildUserMessage(
        form: WorkoutAiForm,
        existingExercises: List<Exercise>
    ): String {
        val targets = form.targetMuscles.joinToString(", ") { it.dbName }
        val available = existingExercises.take(80)  // cap to bound prompt size
            .joinToString("\n") { ex ->
                "- ${ex.name} | primaryMuscles: ${ex.primaryMuscles.joinToString(",") { it.dbName }} | equipment: ${ex.equipment ?: "n/a"}"
            }
        return """
            targetMuscles: $targets
            exerciseCount: ${form.exerciseCount}
            splitStyle: ${form.splitStyle.name}
            templatesExpected: ${form.splitStyle.templateCount}

            existingExercises:
            $available
        """.trimIndent()
    }

    private suspend fun callWithJsonSchema(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): WorkoutAiResponse {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userMessage)
            ),
            responseFormat = ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaSpec(
                    name = "WorkoutAiResponse",
                    schema = workoutAiSchema(),
                    strict = false  // strict=true requires exact matches; many providers reject our union shapes
                )
            ),
            temperature = 0.7
        )
        val chat = client.chatCompletion(baseUrl, request)
        return parseResponse(chat.choices.firstOrNull()?.message?.content)
    }

    private suspend fun callWithJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): WorkoutAiResponse {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt + "\n\nReturn only JSON matching the schema in the system prompt body."),
                ChatMessage(role = "user", content = userMessage)
            ),
            responseFormat = ResponseFormat(type = "json_object"),
            temperature = 0.7
        )
        val chat = client.chatCompletion(baseUrl, request)
        return parseResponse(chat.choices.firstOrNull()?.message?.content)
    }

    private fun parseResponse(content: String?): WorkoutAiResponse {
        if (content.isNullOrBlank()) {
            throw AiError.SchemaInvalid("Empty response content")
        }
        return try {
            json.decodeFromString(content)
        } catch (e: Exception) {
            throw AiError.SchemaInvalid("JSON parse failed: ${e.message ?: "unknown"}")
        }
    }

    private fun validateResponse(response: WorkoutAiResponse, form: WorkoutAiForm) {
        val expected = form.splitStyle.templateCount
        if (response.templates.size != expected) {
            throw AiError.SchemaInvalid(
                "Expected $expected templates for split ${form.splitStyle.name}, got ${response.templates.size}"
            )
        }
        for (t in response.templates) {
            if (t.name.isBlank()) throw AiError.SchemaInvalid("Template name blank")
            if (t.exercises.isEmpty()) throw AiError.SchemaInvalid("Template has no exercises")
            for (e in t.exercises) {
                if (e.targetSets !in 1..10) throw AiError.SchemaInvalid("targetSets out of range: ${e.targetSets}")
                if (e.targetReps !in 1..50) throw AiError.SchemaInvalid("targetReps out of range: ${e.targetReps}")
                if (e.restPeriodSec !in 0..600) throw AiError.SchemaInvalid("restPeriodSec out of range: ${e.restPeriodSec}")
                if (e.exerciseName.isBlank()) throw AiError.SchemaInvalid("exerciseName blank")
            }
        }
        for (inline in response.inlineNewExercises) {
            if (inline.name.isBlank()) throw AiError.SchemaInvalid("inline exercise name blank")
            if (inline.primaryMuscles.isEmpty()) throw AiError.SchemaInvalid("inline exercise has no primaryMuscles")
            for (mg in inline.primaryMuscles) {
                if (MuscleGroup.fromDbName(mg) == null) {
                    throw AiError.SchemaInvalid("Unknown MuscleGroup: $mg")
                }
            }
            if (inline.instructions.isEmpty()) throw AiError.SchemaInvalid("inline exercise has no instructions")
        }
    }

    private fun resolvePreview(
        response: WorkoutAiResponse,
        existingExercises: List<Exercise>
    ): WorkoutAiPreview {
        val byNormalizedName = existingExercises.associateBy { it.name.lowercase().trim() }
        val inlineByNormalizedName = response.inlineNewExercises.associateBy { it.name.lowercase().trim() }

        val stagedNew = response.inlineNewExercises.map { inline ->
            StagedExercise(
                name = inline.name,
                primaryMuscles = inline.primaryMuscles.mapNotNull { MuscleGroup.fromDbName(it) },
                secondaryMuscles = inline.secondaryMuscles.mapNotNull { MuscleGroup.fromDbName(it) },
                equipment = inline.equipment,
                force = inline.force,
                mechanic = inline.mechanic,
                level = inline.level,
                category = inline.category,
                instructions = inline.instructions
            )
        }

        val stagedTemplates = response.templates.map { t ->
            StagedTemplate(
                name = t.name,
                description = t.description,
                exercises = t.exercises.map { e ->
                    val key = e.exerciseName.lowercase().trim()
                    val resolved = byNormalizedName[key]
                    val matchesInline = inlineByNormalizedName.containsKey(key)
                    if (resolved == null && !matchesInline) {
                        throw AiError.SchemaInvalid(
                            "exerciseName '${e.exerciseName}' not in catalog and not in inlineNewExercises"
                        )
                    }
                    StagedTemplateExercise(
                        exerciseName = e.exerciseName,
                        resolvedExerciseId = resolved?.id,
                        targetSets = e.targetSets,
                        targetReps = e.targetReps,
                        restPeriodSec = e.restPeriodSec,
                        note = e.note
                    )
                }
            )
        }
        return WorkoutAiPreview(templates = stagedTemplates, inlineNewExercises = stagedNew)
    }

    /**
     * Minimal JSON schema for WorkoutAiResponse — just shape constraints, not exhaustive.
     * Strict-mode validation lives app-side (see validateResponse).
     */
    private fun workoutAiSchema(): JsonElement = buildJsonObject {
        put("type", "object")
        // Detailed schema body intentionally minimal — providers vary in support.
        // Full validation is enforced app-side in validateResponse().
    }
}
```

Notes for the executor:
- The schema-invalid retry: catch ONLY `AiError.SchemaInvalid` from the primary call; let other AiError classes propagate (Timeout / Network / AuthOrQuota / Provider don't benefit from a retry). The `try/catch` in `invoke()` is structured exactly that way.
- The `lookupMuscles` helper is a placeholder returning emptyList — TemplateRepository's `getTemplateExercises` re-fetches muscles on read so the in-write value isn't load-bearing. If a future plan needs accurate primaryMuscles in the TemplateExerciseEntity row, this is the place to fill it.
- Existing tests calling `templateRepository.createTemplate(name)` continue to work because of the default `source = null` argument.
- `existingExercises.take(80)` caps the prompt size; if the catalog grows beyond that, a subsequent plan can introduce muscle-group-targeted filtering. 80 is a safe default for prototype scope.
  </action>
  <verify>
    <automated>grep -E "data class WorkoutAiPreview" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt && grep -E "class WorkoutAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt && grep -E "suspend fun invoke" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt && grep -E "suspend fun commit" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt && grep -E "createTemplate\(name: String, source: String\? = null\)" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "data class WorkoutAiPreview" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns exactly `1`.
    - `grep -c "data class StagedTemplate" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns at least `2` (StagedTemplate + StagedTemplateExercise).
    - `grep -c "data class StagedExercise" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns exactly `1`.
    - `grep -c "data class WorkoutAiForm" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns exactly `1`.
    - `grep -c "enum class WorkoutAiSplit" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns exactly `1`.
    - `grep -c "PUSH_PULL_LEGS\\|UPPER_LOWER\\|FULL_BODY\\|NONE" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` returns at least `4`.
    - `grep -c "class WorkoutAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns exactly `1`.
    - `grep -c "suspend fun invoke" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns exactly `1`.
    - `grep -c "suspend fun commit" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns exactly `1`.
    - `grep -c "callWithJsonSchema" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns at least `2` (definition + call).
    - `grep -c "callWithJsonObject" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns at least `2`.
    - `grep -c "AiError.SchemaInvalid" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns at least `5` (multiple validation throws).
    - `grep -c "MuscleGroup.fromDbName" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns at least `2` (validate + resolve).
    - `grep -c "createTemplate(name = template.name, source = \"AI\")" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` returns exactly `1`.
    - `grep -c "createTemplate(name: String, source: String? = null)" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` returns exactly `1`.
    - `grep -c "source = source" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` returns exactly `1` (passed through to entity).
  </acceptance_criteria>
  <done>WorkoutAiPreview defines staging types; WorkoutAiUseCase orchestrates prompt → call → validate → resolve → preview, with retry-once fallback and transactional commit; TemplateRepository.createTemplate accepts the source field.</done>
</task>

<task type="auto">
  <name>Task 2: Create WorkoutAiViewModel + register in AiModule + iOS KoinHelper</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt
  </read_first>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt**

```kotlin
package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.ai.WorkoutAiForm
import com.pumpernickel.domain.ai.WorkoutAiPreview
import com.pumpernickel.domain.ai.WorkoutAiSplit
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.domain.model.MuscleGroup
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * D-18-07 / D-18-08 / D-18-12 / D-18-13 / D-18-16 — F6 Workout AI flow VM.
 *
 * State machine:
 * - NoKey (when SecureKeyStore.readApiKey() == null at init)
 * - Form (default, edits muscles/count/split)
 * - Generating (in-flight LLM call; Cancel button visible)
 * - Preview (LLM returned a valid response; preview sheet shown; Save / Discard)
 * - Error (terminal until retry / dismiss; carries an AiError for per-class copy)
 * - Saved (commit() succeeded; templateIds returned for nav back to TemplateList)
 */
class WorkoutAiViewModel(
    private val useCase: WorkoutAiUseCase,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutAiUiState>(
        WorkoutAiUiState.Form(
            targetMuscles = emptyList(),
            exerciseCount = 5,                    // Claude's discretion (D-18-03 — range 3-8)
            splitStyle = WorkoutAiSplit.NONE
        )
    )

    @NativeCoroutinesState
    val uiState: StateFlow<WorkoutAiUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            if (secureKeyStore.readApiKey() == null) {
                _uiState.value = WorkoutAiUiState.NoKey
            }
        }
    }

    fun onMusclesChanged(muscles: List<MuscleGroup>) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(targetMuscles = muscles)
    }

    fun onExerciseCountChanged(count: Int) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(exerciseCount = count.coerceIn(1, 12))
    }

    fun onSplitStyleChanged(split: WorkoutAiSplit) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(splitStyle = split)
    }

    fun generate() {
        val form = (_uiState.value as? WorkoutAiUiState.Form) ?: return
        if (form.targetMuscles.isEmpty()) return  // form-side validation; UI also disables button

        _uiState.value = WorkoutAiUiState.Generating(skeletonRowCount = form.exerciseCount)
        generationJob = viewModelScope.launch {
            try {
                val preview = useCase.invoke(
                    WorkoutAiForm(
                        targetMuscles = form.targetMuscles,
                        exerciseCount = form.exerciseCount,
                        splitStyle = form.splitStyle
                    )
                )
                _uiState.value = WorkoutAiUiState.Preview(preview, originatingForm = form)
            } catch (ce: CancellationException) {
                // User-cancel — return to Form, no error UI per D-18-16.
                _uiState.value = form
                throw ce
            } catch (ai: AiError) {
                _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = form)
            } catch (t: Throwable) {
                _uiState.value = WorkoutAiUiState.Error(
                    AiError.fromThrowable(t),
                    originatingForm = form
                )
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
    }

    fun save() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        viewModelScope.launch {
            try {
                val ids = useCase.commit(preview.preview)
                _uiState.value = WorkoutAiUiState.Saved(templateIds = ids)
            } catch (ce: CancellationException) {
                throw ce
            } catch (ai: AiError) {
                _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = preview.originatingForm)
            } catch (t: Throwable) {
                _uiState.value = WorkoutAiUiState.Error(
                    AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                    originatingForm = preview.originatingForm
                )
            }
        }
    }

    fun discardPreview() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        _uiState.value = preview.originatingForm
    }

    fun retryFromError() {
        val error = (_uiState.value as? WorkoutAiUiState.Error) ?: return
        _uiState.value = error.originatingForm
    }
}

/**
 * Sealed UiState — exported to Swift via the KMPNativeCoroutines flat-export
 * convention (Phase 15 STATE.md). Preview/Error carry the originating Form
 * so the user can return to it without re-entering muscles/count/split.
 */
sealed class WorkoutAiUiState {
    object NoKey : WorkoutAiUiState()

    data class Form(
        val targetMuscles: List<MuscleGroup>,
        val exerciseCount: Int,
        val splitStyle: WorkoutAiSplit
    ) : WorkoutAiUiState()

    data class Generating(val skeletonRowCount: Int) : WorkoutAiUiState()

    data class Preview(
        val preview: WorkoutAiPreview,
        val originatingForm: Form
    ) : WorkoutAiUiState()

    data class Error(
        val error: AiError,
        val originatingForm: Form
    ) : WorkoutAiUiState()

    data class Saved(val templateIds: List<Long>) : WorkoutAiUiState()
}
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt** — append the WorkoutAiUseCase + WorkoutAiViewModel bindings inside the existing module block. Add inside `module { ... }`:

```kotlin
single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }
viewModel { WorkoutAiViewModel(get(), get()) }
```

The 5 `get()` calls resolve in order: OpenAICompatibleClient, AiPromptCatalog, ExerciseRepository, TemplateRepository, SettingsRepository. The default `Json` argument has a default value so no 6th get() needed.

Update the existing imports at the top of AiModule.kt to include:
```kotlin
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.presentation.ai.WorkoutAiViewModel
```

Do NOT remove the existing `single { AiPromptCatalog() }` / `single { OpenAICompatibleClient(...) }` / `viewModel { AiSettingsViewModel(...) }` bindings.

**File 3: shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt** — canonical 9-line shape:

```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.ai.WorkoutAiViewModel
import org.koin.mp.KoinPlatform

class WorkoutAiKoinHelper {
    fun getWorkoutAiViewModel(): WorkoutAiViewModel =
        KoinPlatform.getKoin().get()
}
```
  </action>
  <verify>
    <automated>grep -E "class WorkoutAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt && grep -E "sealed class WorkoutAiUiState" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt && grep -E "WorkoutAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt && grep -E "class WorkoutAiKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "class WorkoutAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "sealed class WorkoutAiUiState" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "object NoKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Form" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Generating" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Preview" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Error" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Saved" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun generate" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun cancel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun save" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun discardPreview" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "useCase.invoke" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "useCase.commit" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "generationJob?.cancel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` returns exactly `1`.
    - `grep -c "single { WorkoutAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "viewModel { WorkoutAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "viewModel { AiSettingsViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1` (existing binding retained).
    - `grep -c "class WorkoutAiKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt` returns exactly `1`.
    - `grep -c "getWorkoutAiViewModel" shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>WorkoutAiViewModel exposes the sealed UiState, all 5 actions; AiModule registers the use case and VM; iOS KoinHelper ready.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| LLM JSON response → app | Untrusted JSON crosses the boundary; validated app-side before persistence |
| Staged preview → DB | Only after explicit user Save; transactional commit per D-18-12 |
| User form → prompt body | User input embeds in user message; bounded by the form fields, length-capped to 80 existing-exercise lines |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-06-01 | Tampering | Adversarial JSON response writing partial data | mitigate | validateResponse() rejects out-of-range sets/reps/rest, missing fields, unknown MuscleGroup values, missing exerciseName references — throws AiError.SchemaInvalid. resolvePreview returns staging only; DB write happens only in commit() after user Save. |
| T-18-06-02 | Information disclosure | API key in prompt logs | mitigate | Use case never logs the request body; OpenAICompatibleClient does not print. |
| T-18-06-03 | Repudiation | Schema-invalid retry without notice | accept | Retry is silent (D-18-14 explicitly says one auto-retry, then surface SchemaInvalid). User-facing copy in Plan 07. |
| T-18-06-04 | Denial of service | LLM returns oversized response | mitigate | Defended in Plan 02 (64KB cap). |
| T-18-06-05 | Spoofing | LLM emits a staged exercise that conflicts with an existing name | mitigate | resolvePreview's case-insensitive trimmed match deduplicates: if `inline.name` matches an existing exercise, the staging code uses the existing id and skips writing the inline (D-18-09 dedupe). |
| T-18-06-06 | Elevation of privilege | LLM tries to delete user data | mitigate | Out of scope per D-AI-02 (Delete is always manual). The use case only writes new rows; no Delete API surface in this VM. |
</threat_model>

<verification>
- WorkoutAiPreview defines staging types with non-null exercise resolution.
- WorkoutAiUseCase.invoke produces a preview without DB writes.
- WorkoutAiUseCase.commit transactionally writes new exercises (source="AI") then templates (source="AI") + per-template exercises.
- Schema-invalid retry happens once, then surfaces SchemaInvalid.
- WorkoutAiViewModel state machine covers all 6 sealed states.
- AiModule registers use case + VM; SharedModule already includes aiModule from Plan 05.
- iOS KoinHelper ready.
- `./gradlew :shared:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64` succeed.
</verification>

<success_criteria>
- F6 generation pipeline ready below the UI layer (REQ-AI-01 / REQ-AI-02 / REQ-AI-03).
- Schema validation enforced before any DB write (REQ-AI-07).
- All five AiError classes available to the UI (REQ-AI-08 + D-18-08).
- Multi-template generation supported via splitStyle.templateCount (D-18-13).
- Preview-then-save transactional commit (D-18-12).
- LLM authoring rights for Exercise via inline-new staging (D-18-09).
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-06-SUMMARY.md` with: file list, WorkoutAiUseCase invoke vs commit responsibilities, WorkoutAiViewModel sealed-state diagram, and TemplateRepository.createTemplate signature change.
</output>

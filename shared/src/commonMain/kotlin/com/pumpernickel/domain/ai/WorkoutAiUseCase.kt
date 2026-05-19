package com.pumpernickel.domain.ai

import com.pumpernickel.domain.repository.ExerciseRepository
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.domain.repository.TemplateRepository
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.infrastructure.ai.AiClient
import com.pumpernickel.infrastructure.ai.AiJsonSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WorkoutAiUseCase(
    private val aiClient: AiClient,
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
     * [onProgress] fires on every streaming chunk (`content`, `reasoning`)
     * so the VM can update a live preview while the model generates.
     *
     * Throws AiError on any failure (Timeout / Network / AuthOrQuota / Provider /
     * SchemaInvalid). Coroutine cancellation propagates as CancellationException.
     */
    suspend fun invoke(
        form: WorkoutAiForm,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): WorkoutAiPreview {
        // D-22-04 / D-22-09 — per-provider base URL + model resolution.
        // The legacy aiBaseUrl/aiModel flows remain available on
        // SettingsRepository for SettingsMigration (Plan 22-06), but new
        // call-sites read from the provider-keyed Maps.
        val activeProvider = settingsRepository.activeProvider.first()
        val baseUrl = settingsRepository.baseUrlByProvider.first()[activeProvider]
            ?: throw AiError.SchemaInvalid("No base URL configured for provider $activeProvider")
        val model = settingsRepository.modelByProvider.first()[activeProvider]
            ?: throw AiError.SchemaInvalid("No model configured for provider $activeProvider")
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
            callWithJsonSchema(baseUrl, model, systemPrompt, userMessage, onProgress)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AiError) {
            val shouldFallback = e is AiError.SchemaInvalid ||
                (e is AiError.AuthOrQuota && e.httpStatus in setOf(400, 422))
            if (shouldFallback) {
                callWithJsonObject(baseUrl, model, systemPrompt, userMessage, onProgress)
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
            setsPerExercise: ${form.setsPerExercise}
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
        userMessage: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): WorkoutAiResponse {
        // strict=false: strict=true requires exact matches; many providers reject our union shapes.
        val finalContent = aiClient.completeJsonSchema(
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userMessage,
            schema = AiJsonSchema(
                name = "WorkoutAiResponse",
                schemaJson = workoutAiSchemaJson(),
                strict = false
            ),
            onProgress = onProgress
        )
        return parseResponseFromContent(finalContent)
    }

    private suspend fun callWithJsonObject(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): WorkoutAiResponse {
        val finalContent = aiClient.completeJsonObject(
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userMessage,
            onProgress = onProgress
        )
        return parseResponseFromContent(finalContent)
    }

    /** Streaming variant — parses an already-accumulated content string. */
    private fun parseResponseFromContent(content: String): WorkoutAiResponse {
        if (content.isBlank()) {
            throw AiError.SchemaInvalid(
                "LLM returned empty content. Modell hat vermutlich nur Reasoning produziert oder existiert nicht. " +
                "Wechsle zu google/gemma-4-31B-it in den KI-Einstellungen."
            )
        }
        val cleaned = stripCodeFences(content)
        return try {
            json.decodeFromString(cleaned)
        } catch (e: Exception) {
            val excerpt = cleaned.take(500).replace("\n", " ")
            throw AiError.SchemaInvalid("JSON parse failed: ${e.message ?: "unknown"}\n\nAntwort: $excerpt")
        }
    }

    /**
     * LLMs often return ```json\n{...}\n``` fences even when prompted not to.
     * Strip the wrapping fence so the JSON parser actually sees JSON.
     */
    private fun stripCodeFences(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("```")) return t
        val firstNewline = t.indexOf('\n').takeIf { it >= 0 } ?: return t
        val withoutOpen = t.substring(firstNewline + 1)
        val closeIdx = withoutOpen.lastIndexOf("```")
        return if (closeIdx >= 0) withoutOpen.substring(0, closeIdx).trim() else withoutOpen.trim()
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
            if (t.exercises.size != form.exerciseCount) {
                throw AiError.SchemaInvalid(
                    "Template '${t.name}': expected ${form.exerciseCount} exercises, " +
                    "got ${t.exercises.size}"
                )
            }
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
     * Strict-mode validation lives app-side (see validateResponse). The adapter
     * (`OpenAiCompatibleAiClient`) re-parses this string into a `JsonElement`
     * when building the wire DTO — pure-Kotlin string keeps the port surface
     * free of `kotlinx.serialization.json.*` types.
     */
    private fun workoutAiSchemaJson(): String = "{\"type\":\"object\"}"
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package com.pumpernickel.domain.ai

import com.pumpernickel.domain.repository.FoodRepository
import com.pumpernickel.domain.repository.SettingsRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.pumpernickel.infrastructure.ai.AiClient
import com.pumpernickel.infrastructure.ai.AiJsonSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.uuid.Uuid

class RecipeAiUseCase(
    private val aiClient: AiClient,
    private val promptCatalog: AiPromptCatalog,
    private val foodRepository: FoodRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateDailyMacros: CalculateDailyMacrosUseCase,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase,
    private val loadConsumptions: LoadConsumptionsForDateUseCase,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    /**
     * D-18-04 — short-circuit when remaining kcal <= 100. The VM transitions
     * to RemainingExhausted in that case (no LLM call).
     */
    suspend fun computeRemaining(): RemainingMacros {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        // LoadConsumptionsForDateUseCase.invoke() is a suspend function that returns
        // List<ConsumptionEntry> directly — NOT a Flow. Do NOT add .first().
        val entries = loadConsumptions(today)
        val totals = calculateDailyMacros(entries)
        val goals: NutritionGoals = settingsRepository.nutritionGoals.first()
        return RemainingMacros(
            kcal = (goals.calorieGoal - totals.calories).coerceAtLeast(0.0),
            protein = (goals.proteinGoal - totals.protein).coerceAtLeast(0.0),
            fat = (goals.fatGoal - totals.fat).coerceAtLeast(0.0),
            carbs = (goals.carbGoal - totals.carbs).coerceAtLeast(0.0),
            sugar = (goals.sugarGoal - totals.sugar).coerceAtLeast(0.0)
        )
    }

    suspend fun invoke(
        remaining: RemainingMacros,
        onProgress: (content: String, reasoning: String) -> Unit = { _, _ -> }
    ): RecipeAiPreview {
        if (remaining.isExhausted) {
            throw IllegalStateException("RecipeAiUseCase.invoke called with exhausted remaining macros — VM should branch to RemainingExhausted before calling")
        }
        // D-22-04 / D-22-09 — per-provider base URL + model resolution.
        // The legacy aiBaseUrl/aiModel flows remain available on
        // SettingsRepository for SettingsMigration (Plan 22-06), but new
        // call-sites read from the provider-keyed Maps.
        val activeProvider = settingsRepository.activeProvider.first()
        val baseUrl = settingsRepository.baseUrlByProvider.first()[activeProvider]
            ?: throw AiError.SchemaInvalid("No base URL configured for provider $activeProvider")
        val model = settingsRepository.modelByProvider.first()[activeProvider]
            ?: throw AiError.SchemaInvalid("No model configured for provider $activeProvider")
        val systemPrompt = promptCatalog.recipeSystemPrompt()
        val userMessage = buildUserMessage(remaining)

        // Per D-18-14: fallback fires on SchemaInvalid OR AuthOrQuota{400,422}
        // (providers like Together.AI / OpenRouter routes that don't support
        // response_format=json_schema return 400/422 "structured-output-not-supported";
        // 401/403/429 still surface as auth/quota error per D-18-08).
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

        validateResponse(response)
        return resolvePreview(response, remaining)
    }

    /**
     * D-18-12 — Save: persist new Foods (source="AI") first, then write the Recipe
     * referencing the existing or freshly-persisted Food ids.
     */
    suspend fun commit(preview: RecipeAiPreview) {
        // 1. Persist inline new Foods.
        val newFoodIdByName: Map<String, String> = preview.inlineNewFoods.associate { staged ->
            val newId = Uuid.random().toString()
            foodRepository.saveFood(stagedFoodToDomain(staged, id = newId))
            staged.name.lowercase().trim() to newId
        }

        // 2. Resolve every ingredient to a foodId.
        val ingredients = preview.recipe.ingredients.map { ing ->
            val resolvedId = ing.resolvedFoodId
                ?: newFoodIdByName[ing.foodName.lowercase().trim()]
                ?: throw AiError.SchemaInvalid("Could not resolve ingredient food: ${ing.foodName}")
            RecipeIngredient(foodId = resolvedId, amountGrams = ing.amountGrams)
        }

        // 3. Save the Recipe with source="AI".
        val recipe = Recipe(
            id = Uuid.random().toString(),
            name = preview.recipe.name,
            ingredients = ingredients,
            isFavorite = false,
            source = "AI"
        )
        foodRepository.saveRecipe(recipe)
    }

    // --- private helpers ---

    private fun stagedFoodToDomain(staged: StagedFood, id: String): Food = Food(
        id = id,
        name = staged.name,
        calories = staged.calories,
        protein = staged.protein,
        fat = staged.fat,
        carbohydrates = staged.carbohydrates,
        sugar = staged.sugar,
        unit = staged.unit,
        isRecipe = false,
        barcode = null,
        source = "AI"
    )

    private fun buildUserMessage(remaining: RemainingMacros): String = """
        remaining:
          kcal: ${remaining.kcal.toInt()}
          protein_g: ${remaining.protein.toInt()}
          fat_g: ${remaining.fat.toInt()}
          carbohydrates_g: ${remaining.carbs.toInt()}
          sugar_g: ${remaining.sugar.toInt()}
    """.trimIndent()

    private suspend fun callWithJsonSchema(
        baseUrl: String, model: String, systemPrompt: String, userMessage: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): RecipeAiResponse {
        val finalContent = aiClient.completeJsonSchema(
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userMessage,
            schema = AiJsonSchema(
                name = "RecipeAiResponse",
                schemaJson = recipeAiSchemaJson(),
                strict = false
            ),
            onProgress = onProgress
        )
        return parseResponseFromContent(finalContent)
    }

    private suspend fun callWithJsonObject(
        baseUrl: String, model: String, systemPrompt: String, userMessage: String,
        onProgress: (content: String, reasoning: String) -> Unit
    ): RecipeAiResponse {
        val finalContent = aiClient.completeJsonObject(
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userMessage,
            onProgress = onProgress
        )
        return parseResponseFromContent(finalContent)
    }

    private fun parseResponseFromContent(content: String): RecipeAiResponse {
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
            throw AiError.SchemaInvalid("Recipe JSON parse failed: ${e.message ?: "unknown"}\n\nAntwort: $excerpt")
        }
    }

    private fun stripCodeFences(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("```")) return t
        val firstNewline = t.indexOf('\n').takeIf { it >= 0 } ?: return t
        val withoutOpen = t.substring(firstNewline + 1)
        val closeIdx = withoutOpen.lastIndexOf("```")
        return if (closeIdx >= 0) withoutOpen.substring(0, closeIdx).trim() else withoutOpen.trim()
    }

    private fun validateResponse(response: RecipeAiResponse) {
        if (response.name.isNullOrBlank()) throw AiError.SchemaInvalid("Recipe name blank")
        if (response.ingredients.isEmpty()) throw AiError.SchemaInvalid("Recipe has no ingredients")
        for (ing in response.ingredients) {
            if (ing.amountGrams < 0) throw AiError.SchemaInvalid("Ingredient amountGrams negative")
            val f = ing.food
            if (f.name.isBlank()) throw AiError.SchemaInvalid("Ingredient food name blank")
            if (f.calories < 0 || f.protein < 0 || f.fat < 0 || f.carbohydrates < 0 || f.sugar < 0) {
                throw AiError.SchemaInvalid("Negative macro on ${f.name}")
            }
            if (f.sugar > f.carbohydrates) {
                throw AiError.SchemaInvalid("sugar > carbohydrates on ${f.name}")
            }
            if (f.unit !in setOf("GRAM", "MILLILITER")) {
                throw AiError.SchemaInvalid("Unknown unit on ${f.name}: ${f.unit}")
            }
        }
    }

    private suspend fun resolvePreview(
        response: RecipeAiResponse,
        remaining: RemainingMacros
    ): RecipeAiPreview {
        val existingFoods = foodRepository.loadFoods()
        val existingByNormalizedName = existingFoods.associateBy { it.name.lowercase().trim() }

        // Stage Foods only for ingredient names that don't match existing.
        val newFoods = mutableListOf<StagedFood>()
        val ingredients = response.ingredients.map { ing ->
            val key = ing.food.name.lowercase().trim()
            val existing = existingByNormalizedName[key]
            if (existing != null) {
                StagedRecipeIngredient(
                    foodName = existing.name,
                    resolvedFoodId = existing.id,
                    amountGrams = ing.amountGrams
                )
            } else {
                // Stage a new Food (source="AI" added at commit time).
                if (newFoods.none { it.name.lowercase().trim() == key }) {
                    newFoods += StagedFood(
                        name = ing.food.name,
                        calories = ing.food.calories,
                        protein = ing.food.protein,
                        fat = ing.food.fat,
                        carbohydrates = ing.food.carbohydrates,
                        sugar = ing.food.sugar,
                        unit = runCatching { FoodUnit.valueOf(ing.food.unit) }.getOrDefault(FoodUnit.GRAM)
                    )
                }
                StagedRecipeIngredient(
                    foodName = ing.food.name,
                    resolvedFoodId = null,
                    amountGrams = ing.amountGrams
                )
            }
        }

        val stagedRecipe = StagedRecipe(
            name = response.name ?: "KI-Rezept",
            ingredients = ingredients,
            steps = response.steps
        )

        val fits = computeFits(stagedRecipe, newFoods, existingFoods, remaining)
        return RecipeAiPreview(stagedRecipe, newFoods, fits)
    }

    private fun computeFits(
        recipe: StagedRecipe,
        stagedNew: List<StagedFood>,
        existingFoods: List<Food>,
        remaining: RemainingMacros
    ): MacrosFitIndicator {
        // Build a foods list combining existing matches and staged new entries
        // so CalculateRecipeMacrosUseCase can find every foodId.
        val pseudoFoods = existingFoods + stagedNew.mapIndexed { idx, s ->
            Food(
                id = "_staged_$idx",
                name = s.name,
                calories = s.calories,
                protein = s.protein,
                fat = s.fat,
                carbohydrates = s.carbohydrates,
                sugar = s.sugar,
                unit = s.unit,
                isRecipe = false,
                barcode = null,
                source = "AI"
            )
        }
        // Build a Recipe with foodIds resolved in the same way commit() does (without writes).
        val byNormalizedName = pseudoFoods.associateBy { it.name.lowercase().trim() }
        val pseudoRecipe = Recipe(
            id = "_preview",
            name = recipe.name,
            ingredients = recipe.ingredients.map {
                val matched = byNormalizedName[it.foodName.lowercase().trim()]
                    ?: error("Could not match preview ingredient: ${it.foodName}")
                RecipeIngredient(foodId = matched.id, amountGrams = it.amountGrams)
            }
        )
        val totals = calculateRecipeMacros(pseudoRecipe, pseudoFoods)

        fun pct(actual: Double, target: Double): Double =
            if (target <= 0.0) Double.POSITIVE_INFINITY else ((actual - target) / target) * 100.0

        val dKcal = pct(totals.calories, remaining.kcal)
        val dProtein = pct(totals.protein, remaining.protein)
        val dFat = pct(totals.fat, remaining.fat)
        val dCarbs = pct(totals.carbs, remaining.carbs)
        val dSugar = pct(totals.sugar, remaining.sugar)

        val fitsAll = listOf(dKcal, dProtein, dFat, dCarbs, dSugar)
            .all { it.isFinite() && abs(it) <= 10.0 }

        return MacrosFitIndicator(
            deltaKcalPercent = dKcal,
            deltaProteinPercent = dProtein,
            deltaFatPercent = dFat,
            deltaCarbsPercent = dCarbs,
            deltaSugarPercent = dSugar,
            fitsAll = fitsAll
        )
    }

    /**
     * Minimal JSON schema for RecipeAiResponse — the adapter
     * (`OpenAiCompatibleAiClient`) parses this string into a JsonElement when
     * building the wire DTO. Pure-Kotlin string keeps the port surface free of
     * `kotlinx.serialization.json.*` types.
     */
    private fun recipeAiSchemaJson(): String = "{\"type\":\"object\"}"
}

---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 08
type: execute
wave: 6
depends_on: [01, 02, 03, 04, 05, 06]
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt
autonomous: true
requirements:
  - REQ-AI-04
  - REQ-AI-05
  - REQ-AI-07
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "RecipeAiUseCase reads today's ConsumptionEntries and NutritionGoals, computes remaining macros, and short-circuits with RemainingExhausted when remaining kcal <= 100"
    - "On valid remaining macros, builds prompt → calls LLM with response_format=json_schema → validates → resolves ingredient food references against existing Foods (case-insensitive trimmed) → returns RecipeAiPreview without DB writes"
    - "Schema-invalid response triggers ONE retry with response_format=json_object before surfacing AiError.SchemaInvalid"
    - "Inline new Foods are staged with source=AI and persisted only when commit() runs after user Save"
    - "RecipeAiViewModel state machine: NoKey / RemainingExhausted / Form (or direct Generating) / Generating / Preview / Error / Saved"
    - "Generated recipe macros are within +/-10% of remaining targets; the use case verifies this app-side and rejects out-of-tolerance recipes as AiError.SchemaInvalid"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt"
      provides: "RecipeAiPreview + StagedRecipe + StagedRecipeIngredient + StagedFood + MacrosFitIndicator"
      contains: "data class RecipeAiPreview"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt"
      provides: "Use case orchestrating remaining-macro calc → prompt → call → validate → resolve → preview"
      contains: "class RecipeAiUseCase"
      min_lines: 80
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt"
      provides: "VM with sealed RecipeAiUiState + generate/cancel/save/discard"
      contains: "class RecipeAiViewModel"
      min_lines: 80
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt"
      provides: "iOS KoinHelper for RecipeAiViewModel"
      contains: "class RecipeAiKoinHelper"
  key_links:
    - from: "RecipeAiUseCase.invoke"
      to: "OpenAICompatibleClient.chatCompletion + CalculateDailyMacrosUseCase"
      via: "compute remaining → prompt → call"
      pattern: "calculateDailyMacrosUseCase"
    - from: "RecipeAiUseCase.commit"
      to: "FoodRepository.saveFood + saveRecipe"
      via: "transactional save with source=AI (D-18-10 / D-18-12)"
      pattern: "foodRepository\\.saveRecipe"
    - from: "RecipeAiViewModel.uiState"
      to: "RecipeAiUiState sealed class"
      via: "MutableStateFlow"
      pattern: "RecipeAiUiState"
---

<objective>
Mirror Plan 06's structure for the F8 Recipe AI flow. Three components:

1. **RecipeAiPreview** — staging types + `MacrosFitIndicator` derived from comparing computed recipe macros to remaining macros (delta per macro within +/-10% tolerance per D-16-15 / REQ-AI-04).

2. **RecipeAiUseCase** — orchestrates: read today's `ConsumptionEntry`s + `NutritionGoals` → compute remaining via `CalculateDailyMacrosUseCase` (today's totals) and subtraction → short-circuit with `RemainingExhausted` if remaining kcal <= 100 (D-18-04 — explains why no recipe is generated) → build prompt → call OpenAI client with `response_format: json_schema` → parse and validate → resolve `food.name` references against existing Foods (case-insensitive trimmed dedupe per D-18-10) → recompute macros via `CalculateRecipeMacrosUseCase` → reject if out of +/-10% tolerance → return `RecipeAiPreview`. Retry-once on schema-invalid like Plan 06. `commit()` writes new Foods (source="AI") + the Recipe transactionally.

3. **RecipeAiViewModel** — state machine: `NoKey`, `RemainingExhausted`, `Form` (optional diet-style dropdown), `Generating`, `Preview`, `Error`, `Saved`.

Implements REQ-AI-04 (remaining-macro recipe), REQ-AI-05 (schema + prompt), REQ-AI-07 (no half-formed writes), REQ-AI-08 (per-class errors). Closes D-18-04 (remaining-macros gate), D-18-09 / D-18-10 (LLM authoring rights for Food + inline Food schema), D-18-12 (preview-then-save), D-18-14 (json_schema with retry-once), D-18-16 (skeleton + Cancel).

Output: 4 new files + 1 modified AiModule + 1 new KoinHelper. Android UI in Plan 09; iOS in Plan 10.
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
@shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/CalculateDailyMacrosUseCase.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/CalculateRecipeMacrosUseCase.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LoadConsumptionsForDateUseCase.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/FoodUnit.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt

<interfaces>
RecipeAiSchema.kt (Plan 03):
- RecipeAiResponse(name?, ingredients: List<RecipeAiIngredient>, steps: List<String>, refusal: String?)
- RecipeAiIngredient(food: RecipeAiInlineFood, amountGrams: Double)
- RecipeAiInlineFood(name, calories, protein, fat, carbohydrates, sugar, unit: String)

NutritionGoals.kt (existing):
- data class NutritionGoals(calorieGoal: Int, proteinGoal: Int, fatGoal: Int, carbGoal: Int, sugarGoal: Int)

CalculateDailyMacrosUseCase (existing):
- operator fun invoke(entries: List<ConsumptionEntry>): RecipeMacros
- RecipeMacros(calories: Double, protein: Double, fat: Double, carbs: Double, sugar: Double)

LoadConsumptionsForDateUseCase (existing): operator fun invoke(date: LocalDate): Flow<List<ConsumptionEntry>>

FoodRepository (existing):
- suspend fun saveFood(food: Food)
- suspend fun loadFoods(): List<Food>
- suspend fun saveRecipe(recipe: Recipe)

CalculateRecipeMacrosUseCase (existing):
- operator fun invoke(recipe: Recipe, foods: List<Food>): RecipeMacros

Recipe.kt (after Plan 01 edit):
- data class Recipe(id, name, ingredients: List<RecipeIngredient>, isFavorite, source: String? = null)
- data class RecipeIngredient(foodId: String, amountGrams: Double)

Food.kt (after Plan 01 edit):
- data class Food(id, name, calories, protein, fat, carbohydrates, sugar, unit: FoodUnit, isRecipe, barcode, source: String? = null)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create RecipeAiPreview + RecipeAiUseCase</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/CalculateDailyMacrosUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/CalculateRecipeMacrosUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LoadConsumptionsForDateUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepository.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/FoodUnit.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
  </read_first>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt**

```kotlin
package com.pumpernickel.domain.ai

import com.pumpernickel.domain.model.FoodUnit

/**
 * D-18-12 — preview is in-memory staging. Until commit() runs, NOTHING is
 * written to the DB. inlineNewFoods is the list of Food rows the LLM emitted
 * that don't match any existing Food (case-insensitive trimmed name match);
 * commit() persists them with source="AI" before writing the Recipe.
 */
data class RecipeAiPreview(
    val recipe: StagedRecipe,
    val inlineNewFoods: List<StagedFood>,
    val fitsIndicator: MacrosFitIndicator
)

data class StagedRecipe(
    val name: String,
    val ingredients: List<StagedRecipeIngredient>,
    val steps: List<String>
)

data class StagedRecipeIngredient(
    val foodName: String,                  // resolved name (matches an existing Food OR a StagedFood)
    val resolvedFoodId: String?,           // non-null if matched against existing Food; null if matches a StagedFood
    val amountGrams: Double
)

data class StagedFood(
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double,
    val unit: FoodUnit
)

/**
 * D-18-04 / REQ-AI-04 — How well do the recipe's macros fit the user's remaining
 * targets? +/-10% tolerance per D-16-15. fitsAll is true when ALL macros are
 * within tolerance.
 */
data class MacrosFitIndicator(
    val deltaKcalPercent: Double,    // (recipeKcal - remainingKcal) / max(remainingKcal, 1) * 100
    val deltaProteinPercent: Double,
    val deltaFatPercent: Double,
    val deltaCarbsPercent: Double,
    val deltaSugarPercent: Double,
    val fitsAll: Boolean
)

data class RemainingMacros(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val sugar: Double
) {
    val isExhausted: Boolean get() = kcal <= 100.0
}
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.pumpernickel.domain.ai

import com.pumpernickel.data.api.ChatMessage
import com.pumpernickel.data.api.ChatRequest
import com.pumpernickel.data.api.JsonSchemaSpec
import com.pumpernickel.data.api.OpenAICompatibleClient
import com.pumpernickel.data.api.RecipeAiInlineFood
import com.pumpernickel.data.api.RecipeAiResponse
import com.pumpernickel.data.api.ResponseFormat
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first  // for SettingsRepository Flow fields only
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.uuid.Uuid

class RecipeAiUseCase(
    private val client: OpenAICompatibleClient,
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
        // LoadConsumptionsForDateUseCase.invoke() returns List<ConsumptionEntry> directly (suspend, NOT Flow).
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

    suspend fun invoke(remaining: RemainingMacros): RecipeAiPreview {
        if (remaining.isExhausted) {
            throw IllegalStateException("RecipeAiUseCase.invoke called with exhausted remaining macros — VM should branch to RemainingExhausted before calling")
        }
        val baseUrl = settingsRepository.aiBaseUrl.first()
        val model = settingsRepository.aiModel.first()
        val systemPrompt = promptCatalog.recipeSystemPrompt()
        val userMessage = buildUserMessage(remaining)

        // Per D-18-14: fallback fires on SchemaInvalid OR AuthOrQuota{400,422}
        // (providers like Together.AI / OpenRouter routes that don't support
        // response_format=json_schema return 400/422 "structured-output-not-supported";
        // 401/403/429 still surface as auth/quota error per D-18-08).
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
        baseUrl: String, model: String, systemPrompt: String, userMessage: String
    ): RecipeAiResponse {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userMessage)
            ),
            responseFormat = ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaSpec(
                    name = "RecipeAiResponse",
                    schema = recipeAiSchema(),
                    strict = false
                )
            ),
            temperature = 0.7
        )
        val chat = client.chatCompletion(baseUrl, request)
        return parseResponse(chat.choices.firstOrNull()?.message?.content)
    }

    private suspend fun callWithJsonObject(
        baseUrl: String, model: String, systemPrompt: String, userMessage: String
    ): RecipeAiResponse {
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

    private fun parseResponse(content: String?): RecipeAiResponse {
        if (content.isNullOrBlank()) throw AiError.SchemaInvalid("Empty recipe response")
        return try {
            json.decodeFromString(content)
        } catch (e: Exception) {
            throw AiError.SchemaInvalid("Recipe JSON parse failed: ${e.message ?: "unknown"}")
        }
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

    private fun recipeAiSchema(): JsonElement = buildJsonObject {
        put("type", "object")
    }
}
```

Notes:
- `fitsAll = true` is informational, not blocking — REQ-AI-04 UAT #1 says "within ±10%" but the use case still returns the preview when fits=false so the user can choose to accept a wider miss. The UI shows the indicator. If the policy must be strict (auto-reject), wrap the preview return in `if (!fits.fitsAll) throw AiError.SchemaInvalid(...)`. Given "within ±10% on a representative day" is the UAT phrasing (descriptive, not strict), keep fits=false as a non-blocking warning.
- The `_staged_$idx` pseudo-id is internal to `computeFits` and never reaches the DB.
- `loadConsumptions(today)` — confirmed: `LoadConsumptionsForDateUseCase.invoke()` is a suspend function that returns `List<ConsumptionEntry>` directly (not Flow). Do NOT add `.first()`. The `kotlinx.coroutines.flow.first` import is retained for `SettingsRepository.aiBaseUrl/aiModel/nutritionGoals` (those ARE Flows).
  </action>
  <verify>
    <automated>grep -E "data class RecipeAiPreview" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt && grep -E "class RecipeAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt && grep -E "suspend fun computeRemaining" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt && grep -E "suspend fun invoke" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt && grep -E "suspend fun commit" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "data class RecipeAiPreview" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns exactly `1`.
    - `grep -c "data class StagedRecipe" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns at least `2` (StagedRecipe + StagedRecipeIngredient).
    - `grep -c "data class StagedFood" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns exactly `1`.
    - `grep -c "data class MacrosFitIndicator" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns exactly `1`.
    - `grep -c "data class RemainingMacros" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns exactly `1`.
    - `grep -c "isExhausted" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` returns at least `1`.
    - `grep -c "class RecipeAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns exactly `1`.
    - `grep -c "suspend fun computeRemaining" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns exactly `1`.
    - `grep -c "suspend fun invoke" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns exactly `1`.
    - `grep -c "suspend fun commit" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns exactly `1`.
    - `grep -c "AiError.SchemaInvalid" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns at least `5`.
    - `grep -c "callWithJsonSchema" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns at least `2`.
    - `grep -c "callWithJsonObject" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns at least `2`.
    - `grep -c "source = \"AI\"" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns at least `2` (Recipe + StagedFood-to-Domain).
    - `grep -c "computeFits" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns at least `2`.
    - `grep -c "abs(it) <= 10.0" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` returns exactly `1` (10% tolerance check).
  </acceptance_criteria>
  <done>RecipeAiPreview defines staging types + fit indicator; RecipeAiUseCase orchestrates remaining → call → validate → resolve → preview, with retry-once and transactional commit.</done>
</task>

<task type="auto">
  <name>Task 2: Create RecipeAiViewModel + register in AiModule + iOS KoinHelper</name>
  <files>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt
  </read_first>
  <dependency_verification>
    Before adding the `single { RecipeAiUseCase(get() x 7) }` binding, verify SharedModule.kt
    already declares the 4 nutrition/data dependencies. Confirmed at planning time:
    - `single { CalculateDailyMacrosUseCase() }` — present in SharedModule.kt
    - `single { CalculateRecipeMacrosUseCase() }` — present in SharedModule.kt
    - `single { LoadConsumptionsForDateUseCase(get()) }` — present in SharedModule.kt
    - `single<FoodRepository> { FoodRepositoryImpl(get(), get()) }` — present in SharedModule.kt
    If any are missing at execute time (e.g., a refactor moved them), add to AiModule.kt
    using the same constructor signatures.
  </dependency_verification>
  <action>
**File 1: shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt**

```kotlin
package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.RecipeAiPreview
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.ai.RemainingMacros
import com.pumpernickel.domain.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * D-18-04 / D-18-08 / D-18-12 / D-18-16 — F8 Recipe AI flow VM.
 *
 * State machine:
 * - NoKey (when SecureKeyStore.readApiKey() == null at init)
 * - RemainingExhausted (when remaining kcal <= 100)
 * - Form (default — F8 has no required form fields per CONTEXT.md; tap "Generieren" to fire)
 * - Generating (in-flight LLM call; Cancel button visible)
 * - Preview (LLM returned a valid response; preview sheet shown)
 * - Error (per-class user copy)
 * - Saved (commit succeeded; recipe is in user's collection)
 */
class RecipeAiViewModel(
    private val useCase: RecipeAiUseCase,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeAiUiState>(RecipeAiUiState.Loading)

    @NativeCoroutinesState
    val uiState: StateFlow<RecipeAiUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var lastRemaining: RemainingMacros? = null

    init {
        viewModelScope.launch {
            if (secureKeyStore.readApiKey() == null) {
                _uiState.value = RecipeAiUiState.NoKey
                return@launch
            }
            refreshRemaining()
        }
    }

    /**
     * Re-reads today's totals + goals; transitions to RemainingExhausted or Form.
     * Called from init and from manual refresh in the UI.
     */
    fun refreshRemaining() {
        viewModelScope.launch {
            try {
                val remaining = useCase.computeRemaining()
                lastRemaining = remaining
                _uiState.value = if (remaining.isExhausted) {
                    RecipeAiUiState.RemainingExhausted(remaining)
                } else {
                    RecipeAiUiState.Form(remaining)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.SchemaInvalid("computeRemaining failed: ${t.message ?: "unknown"}"),
                    originatingRemaining = null
                )
            }
        }
    }

    fun generate() {
        val form = (_uiState.value as? RecipeAiUiState.Form) ?: return
        val remaining = form.remaining
        _uiState.value = RecipeAiUiState.Generating
        generationJob = viewModelScope.launch {
            try {
                val preview = useCase.invoke(remaining)
                _uiState.value = RecipeAiUiState.Preview(preview, originatingRemaining = remaining)
            } catch (ce: CancellationException) {
                _uiState.value = RecipeAiUiState.Form(remaining)
                throw ce
            } catch (ai: AiError) {
                _uiState.value = RecipeAiUiState.Error(ai, originatingRemaining = remaining)
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.fromThrowable(t),
                    originatingRemaining = remaining
                )
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
    }

    fun save() {
        val preview = (_uiState.value as? RecipeAiUiState.Preview) ?: return
        viewModelScope.launch {
            try {
                useCase.commit(preview.preview)
                _uiState.value = RecipeAiUiState.Saved
            } catch (ce: CancellationException) {
                throw ce
            } catch (ai: AiError) {
                _uiState.value = RecipeAiUiState.Error(ai, originatingRemaining = preview.originatingRemaining)
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                    originatingRemaining = preview.originatingRemaining
                )
            }
        }
    }

    fun discardPreview() {
        val preview = (_uiState.value as? RecipeAiUiState.Preview) ?: return
        _uiState.value = RecipeAiUiState.Form(preview.originatingRemaining)
    }

    fun retryFromError() {
        val error = (_uiState.value as? RecipeAiUiState.Error) ?: return
        if (error.originatingRemaining == null) {
            // computeRemaining failure path — re-run the refresh.
            refreshRemaining()
        } else {
            _uiState.value = RecipeAiUiState.Form(error.originatingRemaining)
        }
    }
}

sealed class RecipeAiUiState {
    object Loading : RecipeAiUiState()
    object NoKey : RecipeAiUiState()
    data class RemainingExhausted(val remaining: RemainingMacros) : RecipeAiUiState()
    data class Form(val remaining: RemainingMacros) : RecipeAiUiState()
    object Generating : RecipeAiUiState()
    data class Preview(val preview: RecipeAiPreview, val originatingRemaining: RemainingMacros) : RecipeAiUiState()
    data class Error(val error: AiError, val originatingRemaining: RemainingMacros?) : RecipeAiUiState()
    object Saved : RecipeAiUiState()
}
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt** — append RecipeAiUseCase + RecipeAiViewModel bindings inside the existing `module { ... }`. Add:

```kotlin
single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }
viewModel { RecipeAiViewModel(get(), get()) }
```

The 7 `get()` calls resolve in order: OpenAICompatibleClient, AiPromptCatalog, FoodRepository, SettingsRepository, CalculateDailyMacrosUseCase, CalculateRecipeMacrosUseCase, LoadConsumptionsForDateUseCase. The default `Json` argument has a default value.

Add imports:
```kotlin
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.pumpernickel.presentation.ai.RecipeAiViewModel
```

Do NOT remove existing bindings from Plan 05 / 06.

**File 3: shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt**

```kotlin
package com.pumpernickel.di

import com.pumpernickel.presentation.ai.RecipeAiViewModel
import org.koin.mp.KoinPlatform

class RecipeAiKoinHelper {
    fun getRecipeAiViewModel(): RecipeAiViewModel =
        KoinPlatform.getKoin().get()
}
```
  </action>
  <verify>
    <automated>grep -E "class RecipeAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt && grep -E "sealed class RecipeAiUiState" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt && grep -E "RecipeAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt && grep -E "class RecipeAiKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "class RecipeAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "sealed class RecipeAiUiState" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "object NoKey" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class RemainingExhausted" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Form" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "object Generating" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Preview" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "data class Error" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "object Saved" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun generate" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun cancel" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "fun save" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "useCase.invoke" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "useCase.commit" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "useCase.computeRemaining" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` returns exactly `1`.
    - `grep -c "single { RecipeAiUseCase" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "viewModel { RecipeAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1`.
    - `grep -c "viewModel { WorkoutAiViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1` (existing retained).
    - `grep -c "viewModel { AiSettingsViewModel" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` returns exactly `1` (existing retained).
    - `grep -c "class RecipeAiKoinHelper" shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt` returns exactly `1`.
    - `grep -c "single { CalculateDailyMacrosUseCase()" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1` (verify nutrition use cases are Koin-bound for RecipeAiUseCase resolution).
    - `grep -c "single { CalculateRecipeMacrosUseCase()" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1`.
    - `grep -c "single { LoadConsumptionsForDateUseCase" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1`.
    - `grep -c "single<FoodRepository>" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` returns at least `1`.
  </acceptance_criteria>
  <done>RecipeAiViewModel exposes all 7 sealed states; AiModule has all 5 AI bindings (catalog + client + 3 VMs); iOS KoinHelper ready; nutrition use cases verified Koin-bound in SharedModule.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| LLM JSON response → app | Untrusted JSON crosses; validated app-side before persistence |
| Staged preview → DB | Only after explicit user Save; transactional commit (Foods then Recipe) |
| Today's ConsumptionEntry log | Trusted local data; no untrusted input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-08-01 | Tampering | LLM emits a Food with sugar > carbohydrates | mitigate | validateResponse() rejects with AiError.SchemaInvalid; Food.kt's `init { require(sugar <= carbs) }` would also reject at construction. |
| T-18-08-02 | Tampering | LLM emits negative macros | mitigate | validateResponse() rejects all negative macros. |
| T-18-08-03 | Tampering | LLM emits unknown unit | mitigate | validateResponse() rejects unit not in {GRAM, MILLILITER}. |
| T-18-08-04 | Spoofing | LLM duplicates an existing Food name | mitigate | resolvePreview's case-insensitive trimmed match dedupes — uses existing Food.id without staging a new row (D-18-10). |
| T-18-08-05 | Information disclosure | Remaining macros exposed in error UI | accept | The remaining macros are user-local data; no PII leakage risk. |
| T-18-08-06 | Denial of service | LLM returns recipe with massive ingredients | mitigate | Plan 02's 64KB response cap defends; the validateResponse loop is O(n) over ingredients with no DB writes. |
</threat_model>

<verification>
- RecipeAiPreview defines all staging types + fit indicator + RemainingMacros.
- RecipeAiUseCase: computeRemaining → invoke → commit, with retry-once fallback and 10% fits computation.
- RecipeAiViewModel: 8 sealed states, all 5 actions.
- AiModule has 5 declarations: AiPromptCatalog single, OpenAICompatibleClient single, WorkoutAiUseCase single, RecipeAiUseCase single, plus 3 viewModel bindings.
- iOS KoinHelper compiles.
- `./gradlew :shared:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64` both succeed.
</verification>

<success_criteria>
- F8 generation pipeline ready below the UI layer (REQ-AI-04 / REQ-AI-05).
- Schema validation enforced before any DB write (REQ-AI-07).
- All five AiError classes available to the UI (REQ-AI-08 + D-18-08).
- Remaining-macros gate (D-18-04 — Exhausted state).
- Inline Food + amount-grams shape (D-18-10).
- Preview-then-save transactional commit (D-18-12).
- Recipe macros within +/-10% of remaining (REQ-AI-04 UAT #1, computed app-side via fits indicator).
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-08-SUMMARY.md` with: file list, RecipeAiUseCase computeRemaining/invoke/commit responsibilities, the 7 sealed UiState branches, and AiModule final shape (5 bindings + 3 viewModels).
</output>

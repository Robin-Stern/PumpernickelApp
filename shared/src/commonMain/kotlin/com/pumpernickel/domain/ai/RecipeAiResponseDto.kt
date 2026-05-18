package com.pumpernickel.domain.ai

import kotlinx.serialization.Serializable

/**
 * Phase 20 Plan 07 (Smell 4) — pure-Kotlin domain DTO mirroring the JSON wire
 * shape returned by the LLM for the recipe generation flow.
 *
 * Moved out of `data/api/RecipeAiSchema.kt` (where the Ktor adapter lives)
 * so `RecipeAiUseCase` can parse the raw JSON string returned by the
 * `AiClient` port without importing `com.pumpernickel.data.api.*`. Field
 * shape is verbatim identical to the previous `data/api/RecipeAiSchema.kt`
 * structures.
 */
@Serializable
data class RecipeAiResponse(
    val name: String? = null,
    val ingredients: List<RecipeAiIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val refusal: String? = null
)

@Serializable
data class RecipeAiIngredient(
    val food: RecipeAiInlineFood,
    val amountGrams: Double
)

@Serializable
data class RecipeAiInlineFood(
    val name: String,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbohydrates: Double = 0.0,
    val sugar: Double = 0.0,
    val unit: String = "GRAM"  // "GRAM" | "MILLILITER" — validated in RecipeAiUseCase.validateResponse
)

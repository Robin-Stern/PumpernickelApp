package com.pumpernickel.data.api

import kotlinx.serialization.Serializable

/**
 * D-18-10 — recipe response models. Each ingredient pairs an inline Food
 * (per-100g macros) with the amountGrams used. The app matches food.name
 * against existing Foods (case-insensitive trimmed) and reuses on hit;
 * misses are persisted with source="AI" before the Recipe is written
 * (D-18-12 transactional commit on Save).
 *
 * Recipe macros are recomputed app-side via CalculateRecipeMacrosUseCase
 * (D-18-10). The LLM does NOT compute totals — the app does.
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
    val unit: String = "GRAM"  // "GRAM" | "MILLILITER" — validated app-side against FoodUnit
)

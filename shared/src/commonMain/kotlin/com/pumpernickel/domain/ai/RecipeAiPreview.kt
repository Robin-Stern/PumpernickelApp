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

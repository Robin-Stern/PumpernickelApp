package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.model.calculateMacros

class CalculateRecipeMacrosUseCase {
    operator fun invoke(recipe: Recipe, foods: List<Food>): RecipeMacros {
        val foodMap = foods.associateBy { it.id }
        val pairs = recipe.ingredients.mapNotNull { ingredient ->
            val food = foodMap[ingredient.foodId] ?: return@mapNotNull null
            food to (ingredient.amountGrams / 100.0)
        }
        return calculateMacros(pairs)
    }
}

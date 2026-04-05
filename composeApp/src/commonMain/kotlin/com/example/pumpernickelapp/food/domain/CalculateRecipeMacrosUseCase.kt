@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi

class CalculateRecipeMacrosUseCase {
    operator fun invoke(recipe: Food.Recipe, foods: List<Food>): RecipeMacros {
        val foodMap = foods.associateBy { it.id }
        val pairs = recipe.ingredients.mapNotNull { ingredient ->
            val food = foodMap[ingredient.foodId] ?: return@mapNotNull null
            food to (ingredient.amountGrams / 100.0)
        }
        return calculateMacros(pairs)
    }
}

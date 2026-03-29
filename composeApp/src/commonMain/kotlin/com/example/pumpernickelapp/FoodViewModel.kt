@file:OptIn(ExperimentalUuidApi::class)
package com.example.pumpernickelapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi

class FoodViewModel : ViewModel() {
    private val repository = FoodRepository()

    init {
        seedDemoDataIfEmpty(repository)
    }

    private val _foods = MutableStateFlow(repository.loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _recipes = MutableStateFlow(repository.loadRecipes())
    val recipes: StateFlow<List<Food.Recipe>> = _recipes.asStateFlow()

    fun addFood(food: Food) {
        repository.saveFood(food)
        _foods.value = repository.loadFoods()
    }

    fun searchFoods(query: String): List<Food> {
        if (query.isBlank()) return emptyList()
        return _foods.value.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    fun addRecipe(recipe: Food.Recipe) {
        repository.saveRecipe(recipe)
        _recipes.value = repository.loadRecipes()
    }

    /** Berechnet die Gesamtkalorien eines Rezepts (Nährwerte sind pro 100g). */

    fun calculateRecipeCalories(recipe: Food.Recipe): Double {
        val foodMap = _foods.value.associateBy { it.id }
        return recipe.ingredients.sumOf { ingredient ->
            val food = foodMap[ingredient.foodId] ?: return@sumOf 0.0
            food.calories * ingredient.amountGrams / 100.0
        }
    }
}

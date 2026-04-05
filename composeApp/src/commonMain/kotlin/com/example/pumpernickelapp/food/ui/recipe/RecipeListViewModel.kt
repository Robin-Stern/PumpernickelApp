@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi

data class RecipeMacros(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0
)

sealed interface RecipeListEvent {
    data class OnRecipeDeleted(val recipe: Food.Recipe) : RecipeListEvent
    data class OnRecipeFavoriteToggled(val recipe: Food.Recipe) : RecipeListEvent
}

class RecipeListViewModel(private val repository: FoodRepository) : ViewModel() {

    private val _foods = MutableStateFlow(repository.loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _recipes = MutableStateFlow(sortedRecipes(repository.loadRecipes()))
    val recipes: StateFlow<List<Food.Recipe>> = _recipes.asStateFlow()

    fun onEvent(event: RecipeListEvent) {
        when (event) {
            is RecipeListEvent.OnRecipeDeleted -> {
                repository.deleteRecipe(event.recipe.id)
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }
            is RecipeListEvent.OnRecipeFavoriteToggled -> {
                repository.updateRecipe(event.recipe.copy(isFavorite = !event.recipe.isFavorite))
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }
        }
    }

    fun refresh() {
        _foods.value = repository.loadFoods()
        _recipes.value = sortedRecipes(repository.loadRecipes())
    }

    fun calculateRecipeMacros(recipe: Food.Recipe): RecipeMacros {
        val foodMap = _foods.value.associateBy { it.id }
        return RecipeMacros(
            calories = recipe.ingredients.sumOf { (foodMap[it.foodId]?.calories ?: 0.0) * it.amountGrams / 100.0 },
            protein  = recipe.ingredients.sumOf { (foodMap[it.foodId]?.protein ?: 0.0) * it.amountGrams / 100.0 },
            fat      = recipe.ingredients.sumOf { (foodMap[it.foodId]?.fat ?: 0.0) * it.amountGrams / 100.0 },
            carbs    = recipe.ingredients.sumOf { (foodMap[it.foodId]?.carbohydrates ?: 0.0) * it.amountGrams / 100.0 },
            sugar    = recipe.ingredients.sumOf { (foodMap[it.foodId]?.sugar ?: 0.0) * it.amountGrams / 100.0 }
        )
    }

    private companion object {
        fun sortedRecipes(recipes: List<Food.Recipe>): List<Food.Recipe> =
            recipes.sortedByDescending { it.isFavorite }
    }
}

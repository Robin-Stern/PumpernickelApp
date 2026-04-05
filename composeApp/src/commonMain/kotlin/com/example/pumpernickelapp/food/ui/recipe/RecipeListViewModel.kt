@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.CalculateRecipeMacrosUseCase
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.RecipeMacros
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi

sealed interface RecipeListEvent {
    data class OnRecipeDeleted(val recipe: Food.Recipe) : RecipeListEvent
    data class OnRecipeFavoriteToggled(val recipe: Food.Recipe) : RecipeListEvent
}

class RecipeListViewModel(
    private val repository: FoodRepository,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase
) : ViewModel() {

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

    fun calculateMacros(recipe: Food.Recipe): RecipeMacros =
        calculateRecipeMacros(recipe, _foods.value)

    private companion object {
        fun sortedRecipes(recipes: List<Food.Recipe>): List<Food.Recipe> =
            recipes.sortedByDescending { it.isFavorite }
    }
}

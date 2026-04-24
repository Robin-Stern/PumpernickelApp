package com.pumpernickel.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecipeListEvent {
    data class OnRecipeDeleted(val recipe: Recipe) : RecipeListEvent
    data class OnRecipeFavoriteToggled(val recipe: Recipe) : RecipeListEvent
}

class RecipeListViewModel(
    private val repository: FoodRepository,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase
) : ViewModel() {

    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    @NativeCoroutinesState
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    @NativeCoroutinesState
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    init { refresh() }

    fun onEvent(event: RecipeListEvent) {
        when (event) {
            is RecipeListEvent.OnRecipeDeleted -> viewModelScope.launch {
                repository.deleteRecipe(event.recipe.id)
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }
            is RecipeListEvent.OnRecipeFavoriteToggled -> viewModelScope.launch {
                repository.updateRecipe(event.recipe.copy(isFavorite = !event.recipe.isFavorite))
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _foods.value = repository.loadFoods()
            _recipes.value = sortedRecipes(repository.loadRecipes())
        }
    }

    fun calculateMacros(recipe: Recipe): RecipeMacros =
        calculateRecipeMacros(recipe, _foods.value)

    private companion object {
        fun sortedRecipes(recipes: List<Recipe>): List<Recipe> =
            recipes.sortedByDescending { it.isFavorite }
    }
}

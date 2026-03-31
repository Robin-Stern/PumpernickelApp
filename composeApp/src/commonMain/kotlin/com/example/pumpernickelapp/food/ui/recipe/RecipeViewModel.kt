@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.data.FoodRepository
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.RecipeIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi

data class IngredientEntry(val food: Food, val amountGrams: String)

data class RecipeUiState(
    val recipeName: String = "",
    val searchQuery: String = "",
    val searchResults: List<Food> = emptyList(),
    val ingredients: List<IngredientEntry> = emptyList(),
    val totalCalories: Double = 0.0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed interface RecipeEvent {
    data class OnRecipeNameChanged(val value: String) : RecipeEvent
    data class OnSearchQueryChanged(val value: String) : RecipeEvent
    data class OnFoodSelected(val food: Food) : RecipeEvent
    data class OnIngredientAmountChanged(val index: Int, val value: String) : RecipeEvent
    data class OnIngredientRemoved(val index: Int) : RecipeEvent
    data object OnSaveClicked : RecipeEvent
    data object ClearMessages : RecipeEvent
}

class RecipeViewModel : ViewModel() {
    private val repository = FoodRepository()

    private val _foods = MutableStateFlow(repository.loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _recipes = MutableStateFlow(repository.loadRecipes())
    val recipes: StateFlow<List<Food.Recipe>> = _recipes.asStateFlow()

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    fun onEvent(event: RecipeEvent) {
        when (event) {
            is RecipeEvent.OnRecipeNameChanged -> _uiState.update { it.copy(recipeName = event.value) }
            is RecipeEvent.OnSearchQueryChanged -> {
                val freshFoods = repository.loadFoods()
                _foods.value = freshFoods
                val results = if (event.value.isBlank()) emptyList()
                    else freshFoods.filter { it.name.contains(event.value.trim(), ignoreCase = true) }
                _uiState.update { it.copy(searchQuery = event.value, searchResults = results) }
            }
            is RecipeEvent.OnFoodSelected -> _uiState.update { state ->
                if (state.ingredients.none { it.food.id == event.food.id }) {
                    val newIngredients = state.ingredients + IngredientEntry(event.food, "100")
                    state.copy(
                        searchQuery = "",
                        searchResults = emptyList(),
                        ingredients = newIngredients,
                        totalCalories = calcTotalCalories(newIngredients)
                    )
                } else {
                    state.copy(searchQuery = "", searchResults = emptyList())
                }
            }
            is RecipeEvent.OnIngredientAmountChanged -> _uiState.update { state ->
                val newIngredients = state.ingredients.toMutableList()
                    .also { it[event.index] = it[event.index].copy(amountGrams = event.value) }
                state.copy(ingredients = newIngredients, totalCalories = calcTotalCalories(newIngredients))
            }
            is RecipeEvent.OnIngredientRemoved -> _uiState.update { state ->
                val newIngredients = state.ingredients.toMutableList().also { it.removeAt(event.index) }
                state.copy(ingredients = newIngredients, totalCalories = calcTotalCalories(newIngredients))
            }
            RecipeEvent.OnSaveClicked -> saveRecipe()
            RecipeEvent.ClearMessages -> _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }

    fun calculateRecipeCalories(recipe: Food.Recipe): Double {
        val foodMap = _foods.value.associateBy { it.id }
        return recipe.ingredients.sumOf { ingredient ->
            val food = foodMap[ingredient.foodId] ?: return@sumOf 0.0
            food.calories * ingredient.amountGrams / 100.0
        }
    }

    private fun calcTotalCalories(ingredients: List<IngredientEntry>): Double =
        ingredients.sumOf { it.food.calories * (it.amountGrams.toDoubleOrNull() ?: 0.0) / 100.0 }

    private fun saveRecipe() {
        val state = _uiState.value
        val error = when {
            state.recipeName.isBlank() -> "Rezeptname darf nicht leer sein."
            state.ingredients.isEmpty() -> "Mindestens eine Zutat hinzufügen."
            state.ingredients.any { it.amountGrams.toDoubleOrNull()?.let { v -> v <= 0 } != false } ->
                "Alle Mengenangaben müssen eine Zahl > 0 sein."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error, successMessage = null) }
            return
        }
        val recipeIngredients = state.ingredients.map { entry ->
            RecipeIngredient(foodId = entry.food.id, amountGrams = entry.amountGrams.toDouble())
        }
        repository.saveRecipe(Food.Recipe(name = state.recipeName.trim(), ingredients = recipeIngredients))
        _recipes.value = repository.loadRecipes()
        _uiState.value = RecipeUiState(successMessage = "Rezept gespeichert!")
    }
}

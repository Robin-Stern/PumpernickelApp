@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.RecipeIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi

data class IngredientEntry(val food: Food, val amountGrams: String)

data class RecipeMacros(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0
)

data class RecipeCreationUiState(
    val recipeName: String = "",
    val searchQuery: String = "",
    val searchResults: List<Food> = emptyList(),
    val ingredients: List<IngredientEntry> = emptyList(),
    val totals: RecipeMacros = RecipeMacros(),
    val errorMessage: String? = null
)

sealed interface RecipeEvent {
    data class OnRecipeNameChanged(val value: String) : RecipeEvent
    data class OnSearchQueryChanged(val value: String) : RecipeEvent
    data class OnFoodSelected(val food: Food) : RecipeEvent
    data class OnIngredientAmountChanged(val index: Int, val value: String) : RecipeEvent
    data class OnIngredientRemoved(val index: Int) : RecipeEvent
    data class OnRecipeDeleted(val recipe: Food.Recipe) : RecipeEvent
    data class OnRecipeFavoriteToggled(val recipe: Food.Recipe) : RecipeEvent
    data object OnSaveClicked : RecipeEvent
    data object OnShowCreation : RecipeEvent
    data object OnNavigateToList : RecipeEvent
}

class RecipeViewModel(
    private val repository: FoodRepository
) : ViewModel() {

    private val _foods = MutableStateFlow(repository.loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _recipes = MutableStateFlow(sortedRecipes(repository.loadRecipes()))
    val recipes: StateFlow<List<Food.Recipe>> = _recipes.asStateFlow()

    private val _creationState = MutableStateFlow(RecipeCreationUiState(searchResults = recentFoods()))
    val creationState: StateFlow<RecipeCreationUiState> = _creationState.asStateFlow()

    private val _showCreation = MutableStateFlow(false)
    val showCreation: StateFlow<Boolean> = _showCreation.asStateFlow()

    private fun recentFoods() = _foods.value.takeLast(5).reversed()

    fun onEvent(event: RecipeEvent) {
        when (event) {
            is RecipeEvent.OnRecipeNameChanged ->
                _creationState.update { it.copy(recipeName = event.value) }

            is RecipeEvent.OnSearchQueryChanged -> {
                val freshFoods = repository.loadFoods()
                _foods.value = freshFoods
                val results = if (event.value.isBlank()) recentFoods()
                    else freshFoods.filter { it.name.contains(event.value.trim(), ignoreCase = true) }
                _creationState.update { it.copy(searchQuery = event.value, searchResults = results) }
            }

            is RecipeEvent.OnFoodSelected -> _creationState.update { state ->
                val newIngredients = if (state.ingredients.none { it.food.id == event.food.id }) {
                    state.ingredients + IngredientEntry(event.food, "100")
                } else {
                    state.ingredients
                }
                state.withIngredients(newIngredients)
                    .copy(searchResults = state.searchResults.filter { it.id != event.food.id })
            }

            is RecipeEvent.OnIngredientAmountChanged -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList()
                    .also { it[event.index] = it[event.index].copy(amountGrams = event.value) }
                state.withIngredients(newIngredients)
            }

            is RecipeEvent.OnIngredientRemoved -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList().also { it.removeAt(event.index) }
                state.withIngredients(newIngredients)
            }

            is RecipeEvent.OnRecipeDeleted -> {
                repository.deleteRecipe(event.recipe.id)
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }

            is RecipeEvent.OnRecipeFavoriteToggled -> {
                repository.updateRecipe(event.recipe.copy(isFavorite = !event.recipe.isFavorite))
                _recipes.value = sortedRecipes(repository.loadRecipes())
            }

            RecipeEvent.OnSaveClicked -> saveRecipe()

            RecipeEvent.OnShowCreation -> {
                _foods.value = repository.loadFoods()
                _creationState.value = RecipeCreationUiState(searchResults = recentFoods())
                _showCreation.value = true
            }

            RecipeEvent.OnNavigateToList -> _showCreation.value = false
        }
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

    private fun RecipeCreationUiState.withIngredients(newIngredients: List<IngredientEntry>) = copy(
        ingredients = newIngredients,
        totals = calcTotals(newIngredients)
    )

    private fun calcTotals(ingredients: List<IngredientEntry>): RecipeMacros {
        val factor = { entry: IngredientEntry -> (entry.amountGrams.toDoubleOrNull() ?: 0.0) / 100.0 }
        return RecipeMacros(
            calories = ingredients.sumOf { it.food.calories * factor(it) },
            protein  = ingredients.sumOf { it.food.protein * factor(it) },
            fat      = ingredients.sumOf { it.food.fat * factor(it) },
            carbs    = ingredients.sumOf { it.food.carbohydrates * factor(it) },
            sugar    = ingredients.sumOf { it.food.sugar * factor(it) }
        )
    }

    private fun saveRecipe() {
        val state = _creationState.value
        val error = when {
            state.recipeName.isBlank() -> "Rezeptname darf nicht leer sein."
            state.ingredients.isEmpty() -> "Mindestens eine Zutat hinzufügen."
            state.ingredients.any { it.amountGrams.toDoubleOrNull()?.let { v -> v <= 0 } != false } ->
                "Alle Mengenangaben müssen eine Zahl > 0 sein."
            else -> null
        }
        if (error != null) {
            _creationState.update { it.copy(errorMessage = error) }
            return
        }
        val recipeIngredients = state.ingredients.map { entry ->
            RecipeIngredient(foodId = entry.food.id, amountGrams = entry.amountGrams.toDouble())
        }
        repository.saveRecipe(Food.Recipe(name = state.recipeName.trim(), ingredients = recipeIngredients))
        _recipes.value = sortedRecipes(repository.loadRecipes())
        _showCreation.value = false
    }

    private companion object {
        fun sortedRecipes(recipes: List<Food.Recipe>): List<Food.Recipe> =
            recipes.sortedByDescending { it.isFavorite }
    }
}

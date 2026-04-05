@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.recipe

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.CalculateRecipeMacrosUseCase
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.RecipeMacros
import com.example.pumpernickelapp.food.domain.RecipeIngredient
import com.example.pumpernickelapp.food.domain.calculateMacros
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi

data class IngredientEntry(val food: Food, val amountGrams: String)

data class RecipeCreationUiState(
    val recipeName: String = "",
    val searchQuery: String = "",
    val searchResults: List<Food> = emptyList(),
    val ingredients: List<IngredientEntry> = emptyList(),
    val totals: RecipeMacros = RecipeMacros(),
    val errorMessage: String? = null
)

sealed interface RecipeCreationEvent {
    data class OnRecipeNameChanged(val value: String) : RecipeCreationEvent
    data class OnSearchQueryChanged(val value: String) : RecipeCreationEvent
    data class OnFoodSelected(val food: Food) : RecipeCreationEvent
    data class OnIngredientAmountChanged(val index: Int, val value: String) : RecipeCreationEvent
    data class OnIngredientRemoved(val index: Int) : RecipeCreationEvent
    data object OnSaveClicked : RecipeCreationEvent
}

class RecipeCreationViewModel(
    private val repository: FoodRepository,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase
) : ViewModel() {

    private val _foods = MutableStateFlow(repository.loadFoods())

    private val _creationState = MutableStateFlow(RecipeCreationUiState(searchResults = recentFoods()))
    val creationState: StateFlow<RecipeCreationUiState> = _creationState.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

    fun reset() {
        _foods.value = repository.loadFoods()
        _creationState.value = RecipeCreationUiState(searchResults = recentFoods())
    }

    fun onEvent(event: RecipeCreationEvent) {
        when (event) {
            is RecipeCreationEvent.OnRecipeNameChanged ->
                _creationState.update { it.copy(recipeName = event.value) }

            is RecipeCreationEvent.OnSearchQueryChanged -> {
                val freshFoods = repository.loadFoods()
                _foods.value = freshFoods
                val results = if (event.value.isBlank()) recentFoods()
                    else freshFoods.filter { it.name.contains(event.value.trim(), ignoreCase = true) }
                _creationState.update { it.copy(searchQuery = event.value, searchResults = results) }
            }

            is RecipeCreationEvent.OnFoodSelected -> _creationState.update { state ->
                val newIngredients = if (state.ingredients.none { it.food.id == event.food.id })
                    state.ingredients + IngredientEntry(event.food, "100")
                else
                    state.ingredients
                state.withIngredients(newIngredients)
                    .copy(searchResults = state.searchResults.filter { it.id != event.food.id })
            }

            is RecipeCreationEvent.OnIngredientAmountChanged -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList()
                    .also { it[event.index] = it[event.index].copy(amountGrams = event.value) }
                state.withIngredients(newIngredients)
            }

            is RecipeCreationEvent.OnIngredientRemoved -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList().also { it.removeAt(event.index) }
                state.withIngredients(newIngredients)
            }

            RecipeCreationEvent.OnSaveClicked -> saveRecipe()
        }
    }

    private fun recentFoods() = _foods.value.takeLast(5).reversed()

    private fun RecipeCreationUiState.withIngredients(newIngredients: List<IngredientEntry>) = copy(
        ingredients = newIngredients,
        totals = calcTotals(newIngredients)
    )

    private fun calcTotals(ingredients: List<IngredientEntry>): RecipeMacros {
        val pairs = ingredients.mapNotNull { entry ->
            val factor = entry.amountGrams.toDoubleOrNull() ?: return@mapNotNull null
            entry.food to (factor / 100.0)
        }
        return calculateMacros(pairs)
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
        _savedEvent.tryEmit(Unit)
    }
}

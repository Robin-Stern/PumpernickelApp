@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.entry

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.ValidateFoodInputUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi

data class FoodEntryUiState(
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val barcode: String = "",
    val isRecipe: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

sealed interface FoodEntryEvent {
    data class OnNameChanged(val value: String) : FoodEntryEvent
    data class OnCaloriesChanged(val value: String) : FoodEntryEvent
    data class OnProteinChanged(val value: String) : FoodEntryEvent
    data class OnFatChanged(val value: String) : FoodEntryEvent
    data class OnCarbsChanged(val value: String) : FoodEntryEvent
    data class OnSugarChanged(val value: String) : FoodEntryEvent
    data class OnBarcodeChanged(val value: String) : FoodEntryEvent
    data class OnIsRecipeChanged(val value: Boolean) : FoodEntryEvent
    data object OnSaveClicked : FoodEntryEvent
    data object ClearMessages : FoodEntryEvent
}

class FoodEntryViewModel(
    private val repository: FoodRepository,
    private val validateFood: ValidateFoodInputUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodEntryUiState())
    val uiState: StateFlow<FoodEntryUiState> = _uiState.asStateFlow()

    fun onEvent(event: FoodEntryEvent) {
        when (event) {
            is FoodEntryEvent.OnNameChanged -> _uiState.update { it.copy(name = event.value) }
            is FoodEntryEvent.OnCaloriesChanged -> _uiState.update { it.copy(calories = event.value) }
            is FoodEntryEvent.OnProteinChanged -> _uiState.update { it.copy(protein = event.value) }
            is FoodEntryEvent.OnFatChanged -> _uiState.update { it.copy(fat = event.value) }
            is FoodEntryEvent.OnCarbsChanged -> _uiState.update { it.copy(carbs = event.value) }
            is FoodEntryEvent.OnSugarChanged -> _uiState.update { it.copy(sugar = event.value) }
            is FoodEntryEvent.OnBarcodeChanged -> _uiState.update { it.copy(barcode = event.value) }
            is FoodEntryEvent.OnIsRecipeChanged -> _uiState.update { it.copy(isRecipe = event.value) }
            FoodEntryEvent.OnSaveClicked -> validateAndSave()
            FoodEntryEvent.ClearMessages -> _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }
    private fun updateError(message: String) {
        _uiState.update { it.copy(errorMessage = message, successMessage = null) }
    }

    private fun validateAndSave() {
        val state = _uiState.value
        when (val result = validateFood(state.name, state.calories, state.protein, state.fat, state.carbs, state.sugar)) {
            is ValidateFoodInputUseCase.Result.Error -> updateError(result.message)
            is ValidateFoodInputUseCase.Result.Valid -> {
                repository.saveFood(
                    Food(
                        name          = state.name.trim(),
                        calories      = result.calories,
                        protein       = result.protein,
                        fat           = result.fat,
                        carbohydrates = result.carbs,
                        sugar         = result.sugar,
                        isRecipe      = state.isRecipe,
                        barcode       = state.barcode.trim().ifBlank { null }
                    )
                )
                _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel gespeichert!")
            }
        }
    }
}

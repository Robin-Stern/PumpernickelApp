package com.example.pumpernickelapp.food.ui.entry

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.domain.AddFoodUseCase
import com.example.pumpernickelapp.food.domain.DeleteFoodUseCase
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.LoadFoodsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FoodEntryUiState(
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val barcode: String = "",
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
    data class OnFoodDeleted(val food: Food) : FoodEntryEvent
    data object OnSaveClicked : FoodEntryEvent
    data object ClearMessages : FoodEntryEvent
}

class FoodEntryViewModel(
    private val loadFoods: LoadFoodsUseCase,
    private val addFood: AddFoodUseCase,
    private val deleteFood: DeleteFoodUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodEntryUiState())
    val uiState: StateFlow<FoodEntryUiState> = _uiState.asStateFlow()

    private val _foods = MutableStateFlow(loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    fun onEvent(event: FoodEntryEvent) {
        when (event) {
            is FoodEntryEvent.OnNameChanged     -> _uiState.update { it.copy(name = event.value) }
            is FoodEntryEvent.OnCaloriesChanged -> _uiState.update { it.copy(calories = event.value) }
            is FoodEntryEvent.OnProteinChanged  -> _uiState.update { it.copy(protein = event.value) }
            is FoodEntryEvent.OnFatChanged      -> _uiState.update { it.copy(fat = event.value) }
            is FoodEntryEvent.OnCarbsChanged    -> _uiState.update { it.copy(carbs = event.value) }
            is FoodEntryEvent.OnSugarChanged    -> _uiState.update { it.copy(sugar = event.value) }
            is FoodEntryEvent.OnBarcodeChanged  -> _uiState.update { it.copy(barcode = event.value) }
            is FoodEntryEvent.OnFoodDeleted     -> {
                deleteFood(event.food)
                _foods.value = loadFoods()
            }
            FoodEntryEvent.OnSaveClicked  -> save()
            FoodEntryEvent.ClearMessages  -> _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }

    private fun save() {
        val s = _uiState.value
        when (val result = addFood(s.name, s.calories, s.protein, s.fat, s.carbs, s.sugar, s.barcode)) {
            is AddFoodUseCase.Result.Error   ->
                _uiState.update { it.copy(errorMessage = result.message, successMessage = null) }
            is AddFoodUseCase.Result.Success -> {
                _foods.value = loadFoods()
                _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel gespeichert!")
            }
        }
    }
}

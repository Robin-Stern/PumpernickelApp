@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pumpernickelapp.food.domain.AddFoodUseCase
import com.example.pumpernickelapp.food.domain.DeleteFoodUseCase
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodUnit
import com.example.pumpernickelapp.food.domain.LoadFoodsUseCase
import com.example.pumpernickelapp.food.domain.LookupBarcodeUseCase
import com.example.pumpernickelapp.food.domain.UpdateFoodUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class FoodEntryUiState(
    val name: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val barcode: String = "",
    val unit: FoodUnit = FoodUnit.GRAM,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val editingFoodId: Uuid? = null,
    val searchQuery: String = "",
    val isLookingUp: Boolean = false
)

sealed interface FoodEntryEvent {
    data class OnNameChanged(val value: String) : FoodEntryEvent
    data class OnCaloriesChanged(val value: String) : FoodEntryEvent
    data class OnProteinChanged(val value: String) : FoodEntryEvent
    data class OnFatChanged(val value: String) : FoodEntryEvent
    data class OnCarbsChanged(val value: String) : FoodEntryEvent
    data class OnSugarChanged(val value: String) : FoodEntryEvent
    data class OnUnitChanged(val unit: FoodUnit) : FoodEntryEvent
    data class OnFoodDeleted(val food: Food) : FoodEntryEvent
    data class OnFoodSelected(val food: Food) : FoodEntryEvent
    data class OnSearchQueryChanged(val value: String) : FoodEntryEvent
    data class OnBarcodeScanned(val barcode: String) : FoodEntryEvent
    data object OnCancelEdit : FoodEntryEvent
    data object OnSaveClicked : FoodEntryEvent
    data object ClearMessages : FoodEntryEvent
}

class FoodEntryViewModel(
    private val loadFoods: LoadFoodsUseCase,
    private val addFood: AddFoodUseCase,
    private val deleteFood: DeleteFoodUseCase,
    private val updateFood: UpdateFoodUseCase,
    private val lookupBarcode: LookupBarcodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodEntryUiState())
    val uiState: StateFlow<FoodEntryUiState> = _uiState.asStateFlow()

    private val _foods = MutableStateFlow(loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    val filteredFoods: StateFlow<List<Food>> = combine(_foods, _uiState) { foods, state ->
        if (state.searchQuery.isBlank()) foods
        else foods.filter { it.name.contains(state.searchQuery.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _foods.value)

    fun onEvent(event: FoodEntryEvent) {
        when (event) {
            is FoodEntryEvent.OnNameChanged     -> _uiState.update { it.copy(name = event.value) }
            is FoodEntryEvent.OnCaloriesChanged -> _uiState.update { it.copy(calories = event.value) }
            is FoodEntryEvent.OnProteinChanged  -> _uiState.update { it.copy(protein = event.value) }
            is FoodEntryEvent.OnFatChanged      -> _uiState.update { it.copy(fat = event.value) }
            is FoodEntryEvent.OnCarbsChanged    -> _uiState.update { it.copy(carbs = event.value) }
            is FoodEntryEvent.OnSugarChanged    -> _uiState.update { it.copy(sugar = event.value) }
            is FoodEntryEvent.OnUnitChanged     -> _uiState.update { it.copy(unit = event.unit) }
            is FoodEntryEvent.OnFoodDeleted     -> {
                deleteFood(event.food)
                _foods.value = loadFoods()
            }
            is FoodEntryEvent.OnFoodSelected    -> loadFoodForEdit(event.food)
            is FoodEntryEvent.OnSearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.value) }
            is FoodEntryEvent.OnBarcodeScanned  -> onBarcodeScanned(event.barcode)
            FoodEntryEvent.OnCancelEdit         -> _uiState.value = FoodEntryUiState()
            FoodEntryEvent.OnSaveClicked        -> save()
            FoodEntryEvent.ClearMessages        -> _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }

    private fun onBarcodeScanned(barcode: String) {
        _uiState.update { it.copy(barcode = barcode, isLookingUp = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            when (val result = lookupBarcode(barcode)) {
                is LookupBarcodeUseCase.Result.FoundLocally -> {
                    loadFoodForEdit(result.food)
                    _uiState.update { it.copy(isLookingUp = false, successMessage = "Lokales Lebensmittel geladen.") }
                    autoClearSuccess()
                }
                is LookupBarcodeUseCase.Result.FoundRemote -> {
                    _uiState.update {
                        it.copy(
                            name = result.name,
                            calories = formatNumber(result.calories),
                            protein = formatNumber(result.protein),
                            fat = formatNumber(result.fat),
                            carbs = formatNumber(result.carbs),
                            sugar = formatNumber(result.sugar),
                            isLookingUp = false,
                            successMessage = "Produkt gefunden!"
                        )
                    }
                    autoClearSuccess()
                }
                is LookupBarcodeUseCase.Result.NotFound -> {
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            errorMessage = "Produkt nicht gefunden. Du kannst es manuell eingeben."
                        )
                    }
                }
                is LookupBarcodeUseCase.Result.Error -> {
                    _uiState.update {
                        it.copy(isLookingUp = false, errorMessage = "Fehler bei der Produktsuche: ${result.message}")
                    }
                }
            }
        }
    }

    private fun autoClearSuccess() {
        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(successMessage = null) }
        }
    }

    private fun loadFoodForEdit(food: Food) {
        _uiState.value = FoodEntryUiState(
            editingFoodId = food.id,
            name          = food.name,
            calories      = formatNumber(food.calories),
            protein       = formatNumber(food.protein),
            fat           = formatNumber(food.fat),
            carbs         = formatNumber(food.carbohydrates),
            sugar         = formatNumber(food.sugar),
            barcode       = food.barcode ?: "",
            unit          = food.unit
        )
    }

    private fun formatNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun save() {
        val s = _uiState.value
        if (s.editingFoodId != null) {
            when (val result = updateFood(s.editingFoodId, s.name, s.calories, s.protein, s.fat, s.carbs, s.sugar, s.barcode, s.unit)) {
                is UpdateFoodUseCase.Result.Error   ->
                    _uiState.update { it.copy(errorMessage = result.message, successMessage = null) }
                is UpdateFoodUseCase.Result.Success -> {
                    _foods.value = loadFoods()
                    _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel aktualisiert!")
                    autoClearSuccess()
                }
            }
        } else {
            when (val result = addFood(s.name, s.calories, s.protein, s.fat, s.carbs, s.sugar, s.barcode, s.unit)) {
                is AddFoodUseCase.Result.Error   ->
                    _uiState.update { it.copy(errorMessage = result.message, successMessage = null) }
                is AddFoodUseCase.Result.Success -> {
                    _foods.value = loadFoods()
                    _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel gespeichert!")
                    autoClearSuccess()
                }
            }
        }
    }
}

package com.pumpernickel.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.nutrition.AddFoodUseCase
import com.pumpernickel.domain.nutrition.DeleteFoodUseCase
import com.pumpernickel.domain.nutrition.LoadFoodsUseCase
import com.pumpernickel.domain.nutrition.LogConsumptionUseCase
import com.pumpernickel.domain.nutrition.LookupBarcodeUseCase
import com.pumpernickel.domain.nutrition.SearchFoodsRemoteUseCase
import com.pumpernickel.domain.nutrition.UpdateFoodUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val editingFoodId: String? = null,
    val searchQuery: String = "",
    val isLookingUp: Boolean = false,
    val pendingLogFood: Food? = null,
    val remoteSearchResults: List<SearchFoodsRemoteUseCase.RemoteFoodResult> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val remoteSearchError: String? = null
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
    data class OnRemoteFoodSelected(val result: SearchFoodsRemoteUseCase.RemoteFoodResult) : FoodEntryEvent
    data class OnConfirmLogAmount(val food: Food, val amount: Double) : FoodEntryEvent
    data object OnDismissLogDialog : FoodEntryEvent
    data object OnCancelEdit : FoodEntryEvent
    data object OnSaveClicked : FoodEntryEvent
    data object ClearMessages : FoodEntryEvent
}

class FoodEntryViewModel(
    private val loadFoods: LoadFoodsUseCase,
    private val addFood: AddFoodUseCase,
    private val deleteFood: DeleteFoodUseCase,
    private val updateFood: UpdateFoodUseCase,
    private val lookupBarcode: LookupBarcodeUseCase,
    private val logConsumption: LogConsumptionUseCase,
    private val searchFoodsRemote: SearchFoodsRemoteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodEntryUiState())
    @NativeCoroutinesState
    val uiState: StateFlow<FoodEntryUiState> = _uiState.asStateFlow()

    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    @NativeCoroutinesState
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    @NativeCoroutinesState
    val filteredFoods: StateFlow<List<Food>> = combine(_foods, _uiState) { foods, state ->
        if (state.searchQuery.isBlank()) foods
        else foods.filter { it.name.contains(state.searchQuery.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _foods.value = loadFoods() }
        viewModelScope.launch {
            _uiState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(500)
                .collect { query ->
                    if (query.length >= 3) {
                        _uiState.update { it.copy(isSearchingRemote = true, remoteSearchError = null) }
                        when (val result = searchFoodsRemote(query)) {
                            is SearchFoodsRemoteUseCase.Result.Success ->
                                _uiState.update { it.copy(remoteSearchResults = result.foods, isSearchingRemote = false) }
                            is SearchFoodsRemoteUseCase.Result.Empty ->
                                _uiState.update { it.copy(remoteSearchResults = emptyList(), isSearchingRemote = false) }
                            is SearchFoodsRemoteUseCase.Result.Error ->
                                _uiState.update { it.copy(remoteSearchResults = emptyList(), isSearchingRemote = false, remoteSearchError = result.message) }
                        }
                    } else {
                        _uiState.update { it.copy(remoteSearchResults = emptyList(), isSearchingRemote = false, remoteSearchError = null) }
                    }
                }
        }
    }

    fun onEvent(event: FoodEntryEvent) {
        when (event) {
            is FoodEntryEvent.OnNameChanged     -> _uiState.update { it.copy(name = event.value) }
            is FoodEntryEvent.OnCaloriesChanged -> _uiState.update { it.copy(calories = event.value) }
            is FoodEntryEvent.OnProteinChanged  -> _uiState.update { it.copy(protein = event.value) }
            is FoodEntryEvent.OnFatChanged      -> _uiState.update { it.copy(fat = event.value) }
            is FoodEntryEvent.OnCarbsChanged    -> _uiState.update { it.copy(carbs = event.value) }
            is FoodEntryEvent.OnSugarChanged    -> _uiState.update { it.copy(sugar = event.value) }
            is FoodEntryEvent.OnUnitChanged     -> _uiState.update { it.copy(unit = event.unit) }
            is FoodEntryEvent.OnFoodDeleted     -> viewModelScope.launch {
                deleteFood(event.food)
                _foods.value = loadFoods()
            }
            is FoodEntryEvent.OnFoodSelected    -> loadFoodForEdit(event.food)
            is FoodEntryEvent.OnSearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.value, remoteSearchResults = emptyList()) }
            is FoodEntryEvent.OnRemoteFoodSelected  -> _uiState.update { it.copy(
                name = event.result.name,
                calories = formatNumber(event.result.calories),
                protein = formatNumber(event.result.protein),
                fat = formatNumber(event.result.fat),
                carbs = formatNumber(event.result.carbs),
                sugar = formatNumber(event.result.sugar),
                barcode = "",
                editingFoodId = null
            )}
            is FoodEntryEvent.OnBarcodeScanned  -> onBarcodeScanned(event.barcode)
            is FoodEntryEvent.OnConfirmLogAmount -> viewModelScope.launch {
                logConsumption(event.food, event.amount)
                _uiState.update { it.copy(pendingLogFood = null, successMessage = "Eintrag gespeichert!") }
                autoClearSuccess()
            }
            FoodEntryEvent.OnDismissLogDialog    -> _uiState.update { it.copy(pendingLogFood = null) }
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
                    val food = result.food
                    _uiState.value = FoodEntryUiState(
                        editingFoodId = food.id, name = food.name,
                        calories = formatNumber(food.calories), protein = formatNumber(food.protein),
                        fat = formatNumber(food.fat), carbs = formatNumber(food.carbohydrates),
                        sugar = formatNumber(food.sugar), barcode = food.barcode ?: "", unit = food.unit,
                        isLookingUp = false, successMessage = "Lokales Lebensmittel geladen."
                    )
                    autoClearSuccess()
                }
                is LookupBarcodeUseCase.Result.FoundRemote -> {
                    _uiState.update { it.copy(
                        name = result.name, calories = formatNumber(result.calories),
                        protein = formatNumber(result.protein), fat = formatNumber(result.fat),
                        carbs = formatNumber(result.carbs), sugar = formatNumber(result.sugar),
                        isLookingUp = false, successMessage = "Produkt gefunden!"
                    )}
                    autoClearSuccess()
                }
                is LookupBarcodeUseCase.Result.NotFound -> {
                    _uiState.update { it.copy(isLookingUp = false, errorMessage = "Produkt nicht gefunden. Du kannst es manuell eingeben.") }
                }
                is LookupBarcodeUseCase.Result.Error -> {
                    _uiState.update { it.copy(isLookingUp = false, errorMessage = "Fehler bei der Produktsuche: ${result.message}") }
                }
            }
        }
    }

    private fun autoClearSuccess() {
        viewModelScope.launch { delay(3000); _uiState.update { it.copy(successMessage = null) } }
    }

    private fun loadFoodForEdit(food: Food) {
        _uiState.value = FoodEntryUiState(
            editingFoodId = food.id, name = food.name,
            calories = formatNumber(food.calories), protein = formatNumber(food.protein),
            fat = formatNumber(food.fat), carbs = formatNumber(food.carbohydrates),
            sugar = formatNumber(food.sugar), barcode = food.barcode ?: "", unit = food.unit
        )
    }

    private fun formatNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            if (s.editingFoodId != null) {
                when (val result = updateFood(s.editingFoodId, s.name, s.calories, s.protein, s.fat, s.carbs, s.sugar, s.barcode, s.unit)) {
                    is UpdateFoodUseCase.Result.Error   -> _uiState.update { it.copy(errorMessage = result.message, successMessage = null) }
                    is UpdateFoodUseCase.Result.Success -> {
                        _foods.value = loadFoods()
                        _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel aktualisiert!")
                        autoClearSuccess()
                    }
                }
            } else {
                when (val result = addFood(s.name, s.calories, s.protein, s.fat, s.carbs, s.sugar, s.barcode, s.unit)) {
                    is AddFoodUseCase.Result.Error   -> _uiState.update { it.copy(errorMessage = result.message, successMessage = null) }
                    is AddFoodUseCase.Result.Success -> {
                        _foods.value = loadFoods()
                        _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel gespeichert!")
                        autoClearSuccess()
                    }
                }
            }
        }
    }
}

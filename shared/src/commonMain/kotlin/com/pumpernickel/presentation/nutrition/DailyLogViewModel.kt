package com.pumpernickel.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.DeleteConsumptionUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.pumpernickel.domain.nutrition.LoadFoodsUseCase
import com.pumpernickel.domain.nutrition.LogConsumptionUseCase
import com.pumpernickel.domain.nutrition.LookupBarcodeUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class DailyLogUiState(
    val selectedDate: LocalDate,
    val entries: List<ConsumptionEntry> = emptyList(),
    val foods: List<Food> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val totals: RecipeMacros = RecipeMacros(),
    val showAddPicker: Boolean = false,
    val pendingFood: Food? = null,
    val showAdHocDialog: Boolean = false,
    val isLookingUp: Boolean = false,
    val errorMessage: String? = null
)

class DailyLogViewModel(
    private val loadFoods: LoadFoodsUseCase,
    private val loadForDate: LoadConsumptionsForDateUseCase,
    private val logConsumption: LogConsumptionUseCase,
    private val deleteConsumption: DeleteConsumptionUseCase,
    private val calculateDaily: CalculateDailyMacrosUseCase,
    private val lookupBarcode: LookupBarcodeUseCase,
    private val repository: FoodRepository,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase
) : ViewModel() {

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val _uiState = MutableStateFlow(DailyLogUiState(selectedDate = today()))
    @NativeCoroutinesState
    val uiState: StateFlow<DailyLogUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val foods = repository.loadFoodsAndRecipes()
            val recipes = repository.loadRecipes()
            val entries = loadForDate(date)
            val totals = calculateDaily(entries)
            _uiState.update { it.copy(foods = foods, recipes = recipes, entries = entries, totals = totals) }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        refresh()
    }

    fun goPreviousDay() = selectDate(_uiState.value.selectedDate.minus(DatePeriod(days = 1)))
    fun goNextDay()     = selectDate(_uiState.value.selectedDate.plus(DatePeriod(days = 1)))
    fun goToday()       = selectDate(today())

    fun openAddPicker()                 = _uiState.update { it.copy(showAddPicker = true) }
    fun dismissAddPicker()              = _uiState.update { it.copy(showAddPicker = false, pendingFood = null) }
    fun selectFoodForEntry(food: Food)  = _uiState.update { it.copy(pendingFood = food, showAddPicker = false) }
    fun clearPendingFood()              = _uiState.update { it.copy(pendingFood = null) }
    fun openAdHocDialog()               = _uiState.update { it.copy(showAdHocDialog = true, showAddPicker = false) }
    fun dismissAdHocDialog()            = _uiState.update { it.copy(showAdHocDialog = false) }

    fun confirmLog(food: Food, amount: Double) {
        viewModelScope.launch {
            when (val r = logConsumption(food, amount)) {
                is LogConsumptionUseCase.Result.Error -> _uiState.update { it.copy(errorMessage = r.message) }
                is LogConsumptionUseCase.Result.Success -> {
                    _uiState.update { it.copy(pendingFood = null, errorMessage = null) }
                    refresh()
                }
            }
        }
    }

    fun confirmAdHocLog(
        name: String, caloriesPer100: Double, proteinPer100: Double,
        fatPer100: Double, carbsPer100: Double, sugarPer100: Double,
        unit: FoodUnit, amount: Double
    ) {
        viewModelScope.launch {
            when (val r = logConsumption.logAdHoc(
                name, caloriesPer100, proteinPer100, fatPer100, carbsPer100, sugarPer100, unit, amount
            )) {
                is LogConsumptionUseCase.Result.Error -> _uiState.update { it.copy(errorMessage = r.message) }
                is LogConsumptionUseCase.Result.Success -> {
                    _uiState.update { it.copy(showAdHocDialog = false, errorMessage = null) }
                    refresh()
                }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            deleteConsumption(id)
            refresh()
        }
    }

    fun selectRecipeForEntry(recipe: Recipe) {
        val foods = _uiState.value.foods
        val macros = calculateRecipeMacros(recipe, foods)
        val totalWeight = recipe.ingredients.sumOf { it.amountGrams }
        if (totalWeight <= 0) return
        val virtualFood = Food(
            name = recipe.name,
            calories = macros.calories / totalWeight * 100,
            protein = macros.protein / totalWeight * 100,
            fat = macros.fat / totalWeight * 100,
            carbohydrates = macros.carbs / totalWeight * 100,
            sugar = macros.sugar / totalWeight * 100,
            unit = FoodUnit.GRAM,
            isRecipe = true
        )
        _uiState.update { it.copy(pendingFood = virtualFood, showAddPicker = false) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun onBarcodeScanned(barcode: String) {
        _uiState.update { it.copy(showAddPicker = false, isLookingUp = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = lookupBarcode(barcode)) {
                is LookupBarcodeUseCase.Result.FoundLocally -> {
                    _uiState.update { it.copy(isLookingUp = false, pendingFood = result.food) }
                }
                is LookupBarcodeUseCase.Result.FoundRemote -> {
                    val newFood = Food(
                        name = result.name, calories = result.calories,
                        protein = result.protein, fat = result.fat,
                        carbohydrates = result.carbs, sugar = result.sugar,
                        barcode = barcode
                    )
                    repository.saveFood(newFood)
                    _uiState.update { it.copy(
                        isLookingUp = false, foods = repository.loadFoodsAndRecipes(), pendingFood = newFood
                    )}
                }
                is LookupBarcodeUseCase.Result.NotFound -> {
                    _uiState.update { it.copy(isLookingUp = false, errorMessage = "Produkt nicht gefunden.") }
                }
                is LookupBarcodeUseCase.Result.Error -> {
                    _uiState.update { it.copy(isLookingUp = false, errorMessage = "Fehler: ${result.message}") }
                }
            }
        }
    }
}

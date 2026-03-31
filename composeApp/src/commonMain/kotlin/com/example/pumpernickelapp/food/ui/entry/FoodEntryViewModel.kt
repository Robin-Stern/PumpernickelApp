@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.entry

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.data.FoodRepository
import com.example.pumpernickelapp.food.data.seedDemoDataIfEmpty
import com.example.pumpernickelapp.food.domain.Food
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

class FoodEntryViewModel : ViewModel() {
    private val repository = FoodRepository()

    init {
        seedDemoDataIfEmpty(repository)
    }

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

    private fun validateAndSave() {//Empfängt Werte von ViewModel. Ersetzt eingegebene , durch . Wirft passende Fehler.
        // Wenn keine Fehler, wird das Food Objekt erstellt und repository aufgerufen, sonst der UI State mit Fehler geupdatet
        val state = _uiState.value
        val caloriesVal = state.calories.replace(',', '.').toDoubleOrNull()
        val proteinVal = state.protein.replace(',', '.').toDoubleOrNull()
        val fatVal = state.fat.replace(',', '.').toDoubleOrNull()
        val carbsVal = state.carbs.replace(',', '.').toDoubleOrNull()
        val sugarVal = state.sugar.replace(',', '.').toDoubleOrNull()

        val error = when {
            state.name.isBlank() -> "Name darf nicht leer sein."
            caloriesVal == null || caloriesVal < 0 -> "Kalorien: gültige Zahl >= 0 eingeben."
            proteinVal == null || proteinVal < 0 -> "Protein: gültige Zahl >= 0 eingeben."
            fatVal == null || fatVal < 0 -> "Fett: gültige Zahl >= 0 eingeben."
            carbsVal == null || carbsVal < 0 -> "Kohlenhydrate: gültige Zahl >= 0 eingeben."
            sugarVal == null || sugarVal < 0 -> "Zucker: gültige Zahl >= 0 eingeben."
            sugarVal > carbsVal!! -> "Zucker darf nicht größer als Kohlenhydrate sein."
            else -> null
        }

        if (error != null) {
            _uiState.update { it.copy(errorMessage = error, successMessage = null) }
            return
        }

        repository.saveFood(
            Food(
                name = state.name.trim(),
                calories = caloriesVal!!,
                protein = proteinVal!!,
                fat = fatVal!!,
                carbohydrates = carbsVal!!,
                sugar = sugarVal!!,
                isRecipe = state.isRecipe,
                barcode = state.barcode.trim().ifBlank { null }
            )
        )
        _uiState.value = FoodEntryUiState(successMessage = "Lebensmittel gespeichert!")
    }
}

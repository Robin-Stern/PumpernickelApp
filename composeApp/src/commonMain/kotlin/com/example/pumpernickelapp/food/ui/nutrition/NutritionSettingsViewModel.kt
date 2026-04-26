package com.example.pumpernickelapp.food.ui.nutrition

import androidx.lifecycle.ViewModel
import com.example.pumpernickelapp.food.data.NutritionSettingsRepository
import com.example.pumpernickelapp.food.domain.ActivityLevel
import com.example.pumpernickelapp.food.domain.CalculateNutritionGoalsUseCase
import com.example.pumpernickelapp.food.domain.Gender
import com.example.pumpernickelapp.food.domain.GoalType
import com.example.pumpernickelapp.food.domain.NutritionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NutritionSettingsUiState(
    val gender: Gender = Gender.Male,
    val age: String = "25",
    val weightKg: String = "70",
    val heightCm: String = "175",
    val activityLevel: ActivityLevel = ActivityLevel.Moderate,
    val goalType: GoalType = GoalType.Maintain,
    val calculatedKcal: Int? = null,
    val saved: Boolean = false
)

sealed interface NutritionSettingsEvent {
    data class OnGenderChanged(val gender: Gender) : NutritionSettingsEvent
    data class OnAgeChanged(val value: String) : NutritionSettingsEvent
    data class OnWeightChanged(val value: String) : NutritionSettingsEvent
    data class OnHeightChanged(val value: String) : NutritionSettingsEvent
    data class OnActivityLevelChanged(val level: ActivityLevel) : NutritionSettingsEvent
    data class OnGoalTypeChanged(val goal: GoalType) : NutritionSettingsEvent
    data object OnSaveClicked : NutritionSettingsEvent
    data object OnSavedConsumed : NutritionSettingsEvent
}

class NutritionSettingsViewModel(
    private val repository: NutritionSettingsRepository,
    private val calculateGoals: CalculateNutritionGoalsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionSettingsUiState())
    val uiState: StateFlow<NutritionSettingsUiState> = _uiState.asStateFlow()

    init {
        val saved = repository.load()
        _uiState.value = NutritionSettingsUiState(
            gender = saved.gender,
            age = saved.age.toString(),
            weightKg = saved.weightKg.toString(),
            heightCm = saved.heightCm.toString(),
            activityLevel = saved.activityLevel,
            goalType = saved.goalType,
            calculatedKcal = calculateGoals(saved)
        )
    }

    fun onEvent(event: NutritionSettingsEvent) {
        val current = _uiState.value
        val next = when (event) {
            is NutritionSettingsEvent.OnGenderChanged -> current.copy(gender = event.gender)
            is NutritionSettingsEvent.OnAgeChanged -> current.copy(age = event.value)
            is NutritionSettingsEvent.OnWeightChanged -> current.copy(weightKg = event.value)
            is NutritionSettingsEvent.OnHeightChanged -> current.copy(heightCm = event.value)
            is NutritionSettingsEvent.OnActivityLevelChanged -> current.copy(activityLevel = event.level)
            is NutritionSettingsEvent.OnGoalTypeChanged -> current.copy(goalType = event.goal)
            NutritionSettingsEvent.OnSaveClicked -> {
                val settings = toSettings(current) ?: return
                repository.save(settings)
                current.copy(calculatedKcal = calculateGoals(settings), saved = true)
            }
            NutritionSettingsEvent.OnSavedConsumed -> current.copy(saved = false)
        }
        _uiState.value = if (event !is NutritionSettingsEvent.OnSaveClicked && event !is NutritionSettingsEvent.OnSavedConsumed) {
            next.copy(calculatedKcal = toSettings(next)?.let { calculateGoals(it) })
        } else next
    }

    private fun toSettings(state: NutritionSettingsUiState): NutritionSettings? {
        val age = state.age.toIntOrNull()?.takeIf { it in 1..120 } ?: return null
        val weight = state.weightKg.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val height = state.heightCm.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return NutritionSettings(
            gender = state.gender,
            age = age,
            weightKg = weight,
            heightCm = height,
            activityLevel = state.activityLevel,
            goalType = state.goalType
        )
    }
}

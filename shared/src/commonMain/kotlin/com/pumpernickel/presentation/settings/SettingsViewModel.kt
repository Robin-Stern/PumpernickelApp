package com.pumpernickel.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.BodyProfile
import com.pumpernickel.domain.model.Gender
import com.pumpernickel.domain.model.GoalType
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.domain.nutrition.CalculateTdeeUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BodyProfileUiState(
    val gender: Gender = Gender.Male,
    val age: String = "25",
    val weightKg: String = "70",
    val heightCm: String = "175",
    val activityLevel: ActivityLevel = ActivityLevel.Moderate,
    val goalType: GoalType = GoalType.Maintain,
    val calculatedKcal: Int? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val calculateTdee: CalculateTdeeUseCase
) : ViewModel() {

    @NativeCoroutinesState
    val hasSeenTutorial: StateFlow<Boolean> = settingsRepository
        .hasSeenTutorial
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setHasSeenTutorial(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasSeenTutorial(value)
        }
    }

    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    @NativeCoroutinesState
    val appTheme: StateFlow<String> = settingsRepository
        .appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    @NativeCoroutinesState
    val accentColor: StateFlow<String> = settingsRepository
        .accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "green")

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            settingsRepository.setWeightUnit(unit)
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(color)
        }
    }

    private val _bodyProfileUiState = MutableStateFlow(BodyProfileUiState())
    val bodyProfileUiState: StateFlow<BodyProfileUiState> = _bodyProfileUiState

    init {
        viewModelScope.launch {
            settingsRepository.bodyProfile.collect { profile ->
                val kcal = calculateTdee(profile).calorieGoal
                _bodyProfileUiState.value = BodyProfileUiState(
                    gender = profile.gender,
                    age = profile.age.toString(),
                    weightKg = profile.weightKg.toString(),
                    heightCm = profile.heightCm.toString(),
                    activityLevel = profile.activityLevel,
                    goalType = profile.goalType,
                    calculatedKcal = kcal
                )
            }
        }
    }

    fun updateGender(gender: Gender) = _bodyProfileUiState.update { recalc(it.copy(gender = gender)) }
    fun updateAge(age: String) = _bodyProfileUiState.update { recalc(it.copy(age = age)) }
    fun updateWeight(weight: String) = _bodyProfileUiState.update { recalc(it.copy(weightKg = weight)) }
    fun updateHeight(height: String) = _bodyProfileUiState.update { recalc(it.copy(heightCm = height)) }
    fun updateActivityLevel(level: ActivityLevel) = _bodyProfileUiState.update { recalc(it.copy(activityLevel = level)) }
    fun updateGoalType(goal: GoalType) = _bodyProfileUiState.update { recalc(it.copy(goalType = goal)) }

    fun saveBodyProfile() {
        val state = _bodyProfileUiState.value
        val profile = toBodyProfile(state) ?: return
        viewModelScope.launch {
            settingsRepository.setBodyProfile(profile)
            settingsRepository.setNutritionGoals(calculateTdee(profile))
        }
    }

    private fun recalc(state: BodyProfileUiState): BodyProfileUiState {
        val kcal = toBodyProfile(state)?.let { calculateTdee(it).calorieGoal }
        return state.copy(calculatedKcal = kcal)
    }

    private fun toBodyProfile(state: BodyProfileUiState): BodyProfile? {
        val age = state.age.toIntOrNull()?.takeIf { it in 1..120 } ?: return null
        val weight = state.weightKg.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val height = state.heightCm.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return BodyProfile(state.gender, age, weight, height, state.activityLevel, state.goalType)
    }
}

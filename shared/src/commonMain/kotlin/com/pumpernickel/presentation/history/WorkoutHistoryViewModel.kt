package com.pumpernickel.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.domain.model.CompletedWorkout
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.domain.model.WorkoutSummary
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutHistoryViewModel(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @NativeCoroutinesState
    val workoutSummaries: StateFlow<List<WorkoutSummary>> = workoutRepository
        .getWorkoutSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @NativeCoroutinesState
    val weightUnit: StateFlow<WeightUnit> = settingsRepository
        .weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightUnit.KG)

    private val _workoutDetail = MutableStateFlow<CompletedWorkout?>(null)
    @NativeCoroutinesState
    val workoutDetail: StateFlow<CompletedWorkout?> = _workoutDetail.asStateFlow()

    fun loadWorkoutDetail(workoutId: Long) {
        viewModelScope.launch {
            _workoutDetail.value = workoutRepository.getWorkoutDetail(workoutId)
        }
    }

    fun clearDetail() {
        _workoutDetail.value = null
    }
}

package com.pumpernickel.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExerciseCatalogViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    @NativeCoroutinesState
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    @NativeCoroutinesState
    val selectedMuscleGroup: StateFlow<MuscleGroup?> = _selectedMuscleGroup.asStateFlow()

    @NativeCoroutines
    val exercises: StateFlow<List<Exercise>> = combine(
        _searchQuery.debounce(300),
        _selectedMuscleGroup
    ) { query, group ->
        Pair(query, group)
    }.flatMapLatest { (query, group) ->
        repository.searchExercises(query, group)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onMuscleGroupSelected(group: MuscleGroup?) {
        _selectedMuscleGroup.value = group
    }
}

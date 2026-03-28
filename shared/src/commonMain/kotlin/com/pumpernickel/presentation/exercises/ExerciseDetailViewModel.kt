package com.pumpernickel.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.domain.model.Exercise
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _exerciseId = MutableStateFlow<String?>(null)

    @NativeCoroutines
    val exercise: StateFlow<Exercise?> = _exerciseId
        .filterNotNull()
        .flatMapLatest { id -> repository.getExerciseById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadExercise(id: String) {
        _exerciseId.value = id
    }
}

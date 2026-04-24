package com.pumpernickel.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class CreateExerciseViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    @NativeCoroutinesState
    val name: StateFlow<String> = _name.asStateFlow()

    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    @NativeCoroutinesState
    val selectedMuscleGroup: StateFlow<MuscleGroup?> = _selectedMuscleGroup.asStateFlow()

    private val _selectedEquipment = MutableStateFlow<String?>(null)
    @NativeCoroutinesState
    val selectedEquipment: StateFlow<String?> = _selectedEquipment.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    @NativeCoroutinesState
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _equipmentOptions = MutableStateFlow<List<String>>(emptyList())
    @NativeCoroutines
    val equipmentOptions: StateFlow<List<String>> = _equipmentOptions.asStateFlow()

    private val _categoryOptions = MutableStateFlow<List<String>>(emptyList())
    @NativeCoroutines
    val categoryOptions: StateFlow<List<String>> = _categoryOptions.asStateFlow()

    @NativeCoroutinesState
    val isFormValid: StateFlow<Boolean> = combine(
        _name, _selectedMuscleGroup, _selectedEquipment, _selectedCategory
    ) { name, muscle, equip, cat ->
        name.isNotBlank() && muscle != null && equip != null && cat != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _saveResult = MutableSharedFlow<SaveResult>()
    @NativeCoroutines
    val saveResult: SharedFlow<SaveResult> = _saveResult.asSharedFlow()

    init {
        viewModelScope.launch {
            _equipmentOptions.value = repository.getDistinctEquipment()
            _categoryOptions.value = repository.getDistinctCategories()
        }
    }

    fun onNameChanged(name: String) { _name.value = name }
    fun onMuscleGroupSelected(group: MuscleGroup?) { _selectedMuscleGroup.value = group }
    fun onEquipmentSelected(equipment: String) { _selectedEquipment.value = equipment }
    fun onCategorySelected(category: String) { _selectedCategory.value = category }

    fun createExercise() {
        viewModelScope.launch {
            try {
                val exercise = Exercise(
                    id = "custom_" + _name.value.lowercase().replace(" ", "_") + "_" + kotlin.time.Clock.System.now().epochSeconds,
                    name = _name.value.trim(),
                    force = null,
                    level = "beginner",
                    mechanic = null,
                    equipment = _selectedEquipment.value,
                    category = _selectedCategory.value!!,
                    instructions = emptyList(),
                    images = emptyList(),
                    isCustom = true,
                    primaryMuscles = listOfNotNull(_selectedMuscleGroup.value),
                    secondaryMuscles = emptyList()
                )
                repository.createExercise(exercise)
                _saveResult.emit(SaveResult.Success)
            } catch (e: Exception) {
                _saveResult.emit(SaveResult.Error(e.message ?: "Unknown error"))
            }
        }
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}

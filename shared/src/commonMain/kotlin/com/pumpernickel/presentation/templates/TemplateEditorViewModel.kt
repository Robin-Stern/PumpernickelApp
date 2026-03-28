package com.pumpernickel.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.TemplateExercise
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val repository: TemplateRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _templateId = MutableStateFlow<Long?>(null)

    private val _name = MutableStateFlow("")
    @NativeCoroutinesState
    val name: StateFlow<String> = _name.asStateFlow()

    private val _exercises = MutableStateFlow<List<TemplateExercise>>(emptyList())
    @NativeCoroutinesState
    val exercises: StateFlow<List<TemplateExercise>> = _exercises.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    @NativeCoroutinesState
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // SaveResult sealed class for communicating save outcomes to UI
    sealed class SaveResult {
        data class Success(val templateId: Long) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    @NativeCoroutinesState
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    // isFormValid: name must be non-blank and <= 100 characters
    @NativeCoroutinesState
    val isFormValid: StateFlow<Boolean> = _name
        .combine(_exercises) { name, _ ->
            name.isNotBlank() && name.trim().length <= 100
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // True when editing an existing template, false when creating new
    val isEditMode: Boolean get() = _templateId.value != null

    // Load an existing template for editing.
    // TemplateRepository.getTemplateById() already resolves exercise names and
    // primaryMuscles via ExerciseRepository at the repository layer (Plan 01).
    // The exercises collected here will have real names, not placeholder strings.
    fun loadTemplate(id: Long) {
        _templateId.value = id
        viewModelScope.launch {
            repository.getTemplateById(id).collect { template ->
                if (template != null) {
                    _name.value = template.name
                    _exercises.value = template.exercises
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _name.value = name
    }

    // Add an exercise with D-08 defaults: 3 sets, 10 reps, 0 weight, 90s rest
    fun addExercise(exerciseId: String, exerciseName: String, primaryMuscles: List<MuscleGroup>) {
        val currentList = _exercises.value
        val newOrder = currentList.size
        val tempId = -(kotlin.time.Clock.System.now().toEpochMilliseconds())
        val newExercise = TemplateExercise(
            id = tempId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            primaryMuscles = primaryMuscles,
            targetSets = 3,
            targetReps = 10,
            targetWeightKgX10 = 0,
            restPeriodSec = 90,
            exerciseOrder = newOrder
        )

        if (_templateId.value != null) {
            // Edit mode: persist immediately
            viewModelScope.launch {
                val insertedId = repository.addExercise(
                    templateId = _templateId.value!!,
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    primaryMuscles = primaryMuscles,
                    order = newOrder
                )
                // Update local list with real ID
                _exercises.value = currentList + newExercise.copy(id = insertedId)
            }
        } else {
            // Create mode: in-memory only until save()
            _exercises.value = currentList + newExercise
        }
    }

    fun removeExercise(templateExerciseId: Long) {
        _exercises.value = _exercises.value
            .filter { it.id != templateExerciseId }
            .mapIndexed { index, ex -> ex.copy(exerciseOrder = index) }

        if (_templateId.value != null && templateExerciseId > 0) {
            // Edit mode: persist immediately
            viewModelScope.launch {
                repository.removeExercise(templateExerciseId)
            }
        }
    }

    fun updateExerciseTargets(id: Long, sets: Int, reps: Int, weightKgX10: Int, restSec: Int) {
        _exercises.value = _exercises.value.map { ex ->
            if (ex.id == id) ex.copy(
                targetSets = sets,
                targetReps = reps,
                targetWeightKgX10 = weightKgX10,
                restPeriodSec = restSec
            ) else ex
        }

        if (_templateId.value != null && id > 0) {
            // Edit mode: persist immediately
            viewModelScope.launch {
                repository.updateExerciseTargets(id, sets, reps, weightKgX10, restSec)
            }
        }
    }

    // Reorder: move exercise from fromIndex to toIndex (D-12)
    fun moveExercise(from: Int, to: Int) {
        val list = _exercises.value.toMutableList()
        if (from < 0 || from >= list.size || to < 0 || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        // Normalize order indices to be contiguous 0, 1, 2, ...
        _exercises.value = list.mapIndexed { index, ex ->
            ex.copy(exerciseOrder = index)
        }

        if (_templateId.value != null) {
            // Edit mode: persist reorder
            viewModelScope.launch {
                val orderedIds = _exercises.value.map { it.id }
                repository.reorderExercises(orderedIds)
            }
        }
    }

    fun save() {
        val currentName = _name.value.trim()
        if (currentName.isBlank() || currentName.length > 100) return

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val templateId = _templateId.value
                if (templateId != null) {
                    // Edit mode: update name (exercises already persisted incrementally)
                    repository.updateTemplateName(templateId, currentName)
                    _saveResult.value = SaveResult.Success(templateId)
                } else {
                    // Create mode: create template + insert all exercises
                    val newId = repository.createTemplate(currentName)
                    for ((index, exercise) in _exercises.value.withIndex()) {
                        val insertedId = repository.addExercise(
                            templateId = newId,
                            exerciseId = exercise.exerciseId,
                            exerciseName = exercise.exerciseName,
                            primaryMuscles = exercise.primaryMuscles,
                            order = index
                        )
                        // Update targets if user changed from D-08 defaults
                        if (exercise.targetSets != 3 || exercise.targetReps != 10 ||
                            exercise.targetWeightKgX10 != 0 || exercise.restPeriodSec != 90) {
                            repository.updateExerciseTargets(
                                insertedId, exercise.targetSets, exercise.targetReps,
                                exercise.targetWeightKgX10, exercise.restPeriodSec
                            )
                        }
                    }
                    _saveResult.value = SaveResult.Success(newId)
                }
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Failed to save template")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}

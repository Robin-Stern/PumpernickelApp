package com.pumpernickel.domain.repository

import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.TemplateExercise
import com.pumpernickel.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>
    fun getTemplateById(id: Long): Flow<WorkoutTemplate?>
    fun getTemplateExercises(templateId: Long): Flow<List<TemplateExercise>>
    suspend fun createTemplate(name: String, source: String? = null): Long
    suspend fun updateTemplateName(id: Long, name: String)
    suspend fun deleteTemplate(id: Long)
    suspend fun addExercise(
        templateId: Long,
        exerciseId: String,
        exerciseName: String,
        primaryMuscles: List<MuscleGroup>,
        order: Int
    ): Long
    suspend fun removeExercise(templateExerciseId: Long)
    suspend fun updateExerciseTargets(id: Long, sets: Int, reps: Int, restSec: Int)
    suspend fun updatePerSetReps(id: Long, perSetReps: List<Int>?)
    suspend fun reorderExercises(exerciseIdsInOrder: List<Long>)
}

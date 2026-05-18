package com.pumpernickel.domain.repository

import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getExercises(): Flow<List<Exercise>>
    fun searchExercises(query: String, muscleGroup: MuscleGroup?): Flow<List<Exercise>>
    fun getExerciseById(id: String): Flow<Exercise?>
    suspend fun createExercise(exercise: Exercise)
    suspend fun getDistinctEquipment(): List<String>
    suspend fun getDistinctCategories(): List<String>
}

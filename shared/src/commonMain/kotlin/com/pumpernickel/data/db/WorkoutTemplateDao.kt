package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<WorkoutTemplateEntity?>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY exerciseOrder ASC")
    fun getTemplateExercises(templateId: Long): Flow<List<TemplateExerciseEntity>>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertTemplateExercise(exercise: TemplateExerciseEntity): Long

    @Query("UPDATE workout_templates SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTemplateName(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE workout_templates SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchTemplate(id: Long, updatedAt: Long)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM template_exercises WHERE id = :id")
    suspend fun deleteTemplateExercise(id: Long)

    @Query("UPDATE template_exercises SET targetSets = :sets, targetReps = :reps, targetWeightKgX10 = :weight, restPeriodSec = :rest WHERE id = :id")
    suspend fun updateExerciseTargets(id: Long, sets: Int, reps: Int, weight: Int, rest: Int)

    @Query("UPDATE template_exercises SET exerciseOrder = :order WHERE id = :id")
    suspend fun updateExerciseOrder(id: Long, order: Int)
}

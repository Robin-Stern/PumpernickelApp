package com.pumpernickel.data.repository

import com.pumpernickel.data.db.TemplateExerciseEntity
import com.pumpernickel.data.db.WorkoutTemplateDao
import com.pumpernickel.data.db.WorkoutTemplateEntity
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.TemplateExercise
import com.pumpernickel.domain.model.WorkoutTemplate
import com.pumpernickel.domain.model.toDomain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>
    fun getTemplateById(id: Long): Flow<WorkoutTemplate?>
    fun getTemplateExercises(templateId: Long): Flow<List<TemplateExercise>>
    suspend fun createTemplate(name: String): Long
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

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateRepositoryImpl(
    private val templateDao: WorkoutTemplateDao,
    private val exerciseRepository: ExerciseRepository
) : TemplateRepository {

    override fun getAllTemplates(): Flow<List<WorkoutTemplate>> {
        return templateDao.getAllTemplates().flatMapLatest { templates ->
            if (templates.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(templates.map { template ->
                    getTemplateExercises(template.id).map { exercises ->
                        template.toDomain(exercises)
                    }
                }) { it.toList() }
            }
        }
    }

    override fun getTemplateById(id: Long): Flow<WorkoutTemplate?> {
        return templateDao.getTemplateById(id)
            .flatMapLatest { template ->
                if (template == null) {
                    flowOf(null)
                } else {
                    getTemplateExercises(id).map { exercises ->
                        template.toDomain(exercises)
                    }
                }
            }
    }

    override fun getTemplateExercises(templateId: Long): Flow<List<TemplateExercise>> {
        return templateDao.getTemplateExercises(templateId).map { entities ->
            entities.map { entity ->
                // Resolve exercise name and primaryMuscles from ExerciseRepository
                val exercise = exerciseRepository.getExerciseById(entity.exerciseId).first()
                entity.toDomain(
                    exerciseName = exercise?.name ?: entity.exerciseId,
                    primaryMuscles = exercise?.primaryMuscles ?: emptyList()
                )
            }
        }
    }

    override suspend fun createTemplate(name: String): Long {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return templateDao.insertTemplate(
            WorkoutTemplateEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun updateTemplateName(id: Long, name: String) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        templateDao.updateTemplateName(id, name.trim(), now)
    }

    override suspend fun deleteTemplate(id: Long) {
        templateDao.deleteTemplate(id)
    }

    override suspend fun addExercise(
        templateId: Long,
        exerciseId: String,
        exerciseName: String,
        primaryMuscles: List<MuscleGroup>,
        order: Int
    ): Long {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        templateDao.touchTemplate(templateId, now)
        return templateDao.insertTemplateExercise(
            TemplateExerciseEntity(
                templateId = templateId,
                exerciseId = exerciseId,
                targetSets = 3,        // D-08 default
                targetReps = 10,       // D-08 default
                targetWeightKgX10 = 0, // D-08 default
                restPeriodSec = 90,    // D-08 default
                exerciseOrder = order
            )
        )
    }

    override suspend fun removeExercise(templateExerciseId: Long) {
        templateDao.deleteTemplateExercise(templateExerciseId)
    }

    override suspend fun updateExerciseTargets(
        id: Long, sets: Int, reps: Int, restSec: Int
    ) {
        templateDao.updateExerciseTargets(id, sets, reps, restSec)
    }

    override suspend fun updatePerSetReps(
        id: Long, perSetReps: List<Int>?
    ) {
        templateDao.updatePerSetReps(
            id,
            perSetReps?.joinToString(",")
        )
    }

    override suspend fun reorderExercises(exerciseIdsInOrder: List<Long>) {
        // Batch reorder: call individual DAO updates (no @Transaction on DAO).
        // This is safe because exerciseOrder is cosmetic -- a partial failure
        // would leave some orders out of sync but would self-correct on the
        // next reorder. If atomicity becomes critical, use
        // database.useWriterConnection { immediateTransaction { } } instead.
        exerciseIdsInOrder.forEachIndexed { index, id ->
            templateDao.updateExerciseOrder(id, index)
        }
    }
}

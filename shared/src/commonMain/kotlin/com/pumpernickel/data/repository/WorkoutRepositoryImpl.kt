package com.pumpernickel.data.repository

import com.pumpernickel.data.db.ActiveSessionEntity
import com.pumpernickel.data.db.ActiveSessionSetEntity
import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.CompletedWorkoutEntity
import com.pumpernickel.data.db.CompletedWorkoutExerciseEntity
import com.pumpernickel.data.db.CompletedWorkoutSetEntity
import com.pumpernickel.data.db.ExerciseSetRirDto
import com.pumpernickel.data.db.WorkoutSessionDao
import com.pumpernickel.domain.gamification.CompletedExerciseRecord
import com.pumpernickel.domain.gamification.CompletedSetRecord
import com.pumpernickel.domain.gamification.CompletedWorkoutRecord
import com.pumpernickel.domain.model.CompletedExercise
import com.pumpernickel.domain.model.CompletedSet
import com.pumpernickel.domain.model.CompletedWorkout
import com.pumpernickel.domain.model.WorkoutSummary
import com.pumpernickel.domain.repository.ActiveSessionData
import com.pumpernickel.domain.repository.ActiveSessionSetData
import com.pumpernickel.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WorkoutRepositoryImpl(
    private val workoutSessionDao: WorkoutSessionDao,
    private val completedWorkoutDao: CompletedWorkoutDao
) : WorkoutRepository {

    override suspend fun hasActiveSession(): Boolean {
        return workoutSessionDao.getActiveSession() != null
    }

    override suspend fun createActiveSession(
        templateId: Long,
        templateName: String,
        startTimeMillis: Long
    ) {
        workoutSessionDao.upsertSession(
            ActiveSessionEntity(
                id = 1,
                templateId = templateId,
                templateName = templateName,
                currentExerciseIndex = 0,
                currentSetIndex = 0,
                startTimeMillis = startTimeMillis,
                lastUpdatedMillis = startTimeMillis
            )
        )
    }

    override suspend fun getActiveSession(): ActiveSessionData? {
        val session = workoutSessionDao.getActiveSession() ?: return null
        val sets = workoutSessionDao.getSessionSets()
        return ActiveSessionData(
            templateId = session.templateId,
            templateName = session.templateName,
            currentExerciseIndex = session.currentExerciseIndex,
            currentSetIndex = session.currentSetIndex,
            startTimeMillis = session.startTimeMillis,
            completedSets = sets.map { entity ->
                ActiveSessionSetData(
                    exerciseIndex = entity.exerciseIndex,
                    setIndex = entity.setIndex,
                    actualReps = entity.actualReps,
                    actualWeightKgX10 = entity.actualWeightKgX10,
                    completedAtMillis = entity.completedAtMillis,
                    rir = entity.rir
                )
            },
            exerciseOrder = session.exerciseOrder
        )
    }

    override suspend fun saveCompletedSet(
        exerciseIndex: Int,
        setIndex: Int,
        actualReps: Int,
        actualWeightKgX10: Int,
        completedAtMillis: Long,
        rir: Int
    ) {
        workoutSessionDao.insertSet(
            ActiveSessionSetEntity(
                sessionId = 1,
                exerciseIndex = exerciseIndex,
                setIndex = setIndex,
                actualReps = actualReps,
                actualWeightKgX10 = actualWeightKgX10,
                completedAtMillis = completedAtMillis,
                rir = rir
            )
        )
        workoutSessionDao.updateCursor(
            exerciseIndex = exerciseIndex,
            setIndex = setIndex,
            updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        )
    }

    override suspend fun updateSetValues(
        exerciseIndex: Int,
        setIndex: Int,
        reps: Int,
        weightKgX10: Int,
        rir: Int
    ) {
        workoutSessionDao.updateSet(exerciseIndex, setIndex, reps, weightKgX10, rir)
    }

    override suspend fun updateCursor(exerciseIndex: Int, setIndex: Int) {
        workoutSessionDao.updateCursor(
            exerciseIndex = exerciseIndex,
            setIndex = setIndex,
            updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        )
    }

    override suspend fun updateExerciseOrder(order: String) {
        workoutSessionDao.updateExerciseOrder(
            order = order,
            updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        )
    }

    override suspend fun clearActiveSession() {
        workoutSessionDao.clearActiveSession()
    }

    override suspend fun saveCompletedWorkout(workout: CompletedWorkout): Long {
        val workoutId = completedWorkoutDao.insertWorkout(
            CompletedWorkoutEntity(
                templateId = workout.templateId,
                name = workout.name,
                startTimeMillis = workout.startTimeMillis,
                endTimeMillis = workout.endTimeMillis,
                durationMillis = workout.durationMillis,
                abandoned = false
            )
        )
        for (exercise in workout.exercises) {
            val exerciseId = completedWorkoutDao.insertExercise(
                CompletedWorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    exerciseOrder = exercise.exerciseOrder
                )
            )
            val setEntities = exercise.sets.map { set ->
                CompletedWorkoutSetEntity(
                    workoutExerciseId = exerciseId,
                    setIndex = set.setIndex,
                    actualReps = set.actualReps,
                    actualWeightKgX10 = set.actualWeightKgX10,
                    rir = set.rir
                )
            }
            if (setEntities.isNotEmpty()) {
                completedWorkoutDao.insertSets(setEntities)
            }
        }
        return workoutId
    }

    override suspend fun saveAbandonedWorkout(workout: CompletedWorkout): Long {
        val workoutId = completedWorkoutDao.insertWorkout(
            CompletedWorkoutEntity(
                templateId = workout.templateId,
                name = workout.name,
                startTimeMillis = workout.startTimeMillis,
                endTimeMillis = workout.endTimeMillis,
                durationMillis = workout.durationMillis,
                abandoned = true
            )
        )
        for (exercise in workout.exercises) {
            val exerciseId = completedWorkoutDao.insertExercise(
                CompletedWorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    exerciseOrder = exercise.exerciseOrder
                )
            )
            val setEntities = exercise.sets.map { set ->
                CompletedWorkoutSetEntity(
                    workoutExerciseId = exerciseId,
                    setIndex = set.setIndex,
                    actualReps = set.actualReps,
                    actualWeightKgX10 = set.actualWeightKgX10,
                    rir = set.rir
                )
            }
            if (setEntities.isNotEmpty()) {
                completedWorkoutDao.insertSets(setEntities)
            }
        }
        return workoutId
    }

    override fun getWorkoutSummaries(): Flow<List<WorkoutSummary>> {
        return completedWorkoutDao.getWorkoutSummaries().map { dtos ->
            dtos.map { dto ->
                WorkoutSummary(
                    id = dto.id,
                    templateId = dto.templateId,
                    name = dto.name,
                    startTimeMillis = dto.startTimeMillis,
                    durationMillis = dto.durationMillis,
                    exerciseCount = dto.exerciseCount,
                    totalVolumeKgX10 = dto.totalVolume
                )
            }
        }
    }

    override suspend fun getWorkoutDetail(workoutId: Long): CompletedWorkout? {
        val workoutEntity = completedWorkoutDao.getWorkoutById(workoutId) ?: return null
        val exerciseEntities = completedWorkoutDao.getExercisesForWorkout(workoutId)
        val exercises = exerciseEntities.map { exerciseEntity ->
            val setEntities = completedWorkoutDao.getSetsForExercise(exerciseEntity.id)
            CompletedExercise(
                exerciseId = exerciseEntity.exerciseId,
                exerciseName = exerciseEntity.exerciseName,
                exerciseOrder = exerciseEntity.exerciseOrder,
                sets = setEntities.map { setEntity ->
                    CompletedSet(
                        setIndex = setEntity.setIndex,
                        actualReps = setEntity.actualReps,
                        actualWeightKgX10 = setEntity.actualWeightKgX10,
                        rir = setEntity.rir
                    )
                }
            )
        }
        return CompletedWorkout(
            id = workoutEntity.id,
            templateId = workoutEntity.templateId,
            name = workoutEntity.name,
            startTimeMillis = workoutEntity.startTimeMillis,
            endTimeMillis = workoutEntity.endTimeMillis,
            durationMillis = workoutEntity.durationMillis,
            exercises = exercises
        )
    }

    override suspend fun getPreviousPerformance(templateId: Long): CompletedWorkout? {
        val lastWorkout = completedWorkoutDao.getLastWorkoutForTemplate(templateId) ?: return null
        return getWorkoutDetail(lastWorkout.id)
    }

    override suspend fun getPersonalBests(exerciseIds: List<String>): Map<String, Int> {
        return completedWorkoutDao.getPersonalBests(exerciseIds)
            .associate { it.exerciseId to it.maxWeightKgX10 }
    }

    override suspend fun getExerciseSetRirSince(sinceMillis: Long): List<ExerciseSetRirDto> {
        return completedWorkoutDao.getExerciseSetRirSince(sinceMillis)
    }

    // ----- Plan 20-08 (Smell 3): engine-facing domain-record queries -----

    override suspend fun getAllCompletedWorkoutRecords(): List<CompletedWorkoutRecord> {
        return completedWorkoutDao.getAllWorkouts().first()
            .map { CompletedWorkoutRecord(id = it.id, startTimeMillis = it.startTimeMillis) }
    }

    override suspend fun getExercisesForCompletedWorkout(workoutId: Long): List<CompletedExerciseRecord> {
        return completedWorkoutDao.getExercisesForWorkout(workoutId).map { entity ->
            CompletedExerciseRecord(
                id = entity.id,
                workoutId = entity.workoutId,
                exerciseId = entity.exerciseId
            )
        }
    }

    override suspend fun getSetsForCompletedExercise(workoutExerciseId: Long): List<CompletedSetRecord> {
        return completedWorkoutDao.getSetsForExercise(workoutExerciseId).map { entity ->
            CompletedSetRecord(
                workoutExerciseId = entity.workoutExerciseId,
                actualReps = entity.actualReps,
                actualWeightKgX10 = entity.actualWeightKgX10
            )
        }
    }
}

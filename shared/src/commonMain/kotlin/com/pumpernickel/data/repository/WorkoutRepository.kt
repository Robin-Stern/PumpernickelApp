package com.pumpernickel.data.repository

import com.pumpernickel.data.db.ActiveSessionEntity
import com.pumpernickel.data.db.ActiveSessionSetEntity
import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.CompletedWorkoutEntity
import com.pumpernickel.data.db.CompletedWorkoutExerciseEntity
import com.pumpernickel.data.db.CompletedWorkoutSetEntity
import com.pumpernickel.data.db.WorkoutSessionDao
import com.pumpernickel.domain.model.CompletedWorkout

interface WorkoutRepository {
    // Active session (crash recovery - WORK-09)
    suspend fun hasActiveSession(): Boolean
    suspend fun createActiveSession(templateId: Long, templateName: String, startTimeMillis: Long)
    suspend fun getActiveSession(): ActiveSessionData?
    suspend fun saveCompletedSet(exerciseIndex: Int, setIndex: Int, actualReps: Int, actualWeightKgX10: Int, completedAtMillis: Long)
    suspend fun updateSetValues(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int)
    suspend fun updateCursor(exerciseIndex: Int, setIndex: Int)
    suspend fun clearActiveSession()

    // Completed workouts (WORK-07)
    suspend fun saveCompletedWorkout(workout: CompletedWorkout)
}

// Domain-level representation of active session data (no Room entity leakage)
data class ActiveSessionData(
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val completedSets: List<ActiveSessionSetData>
)

data class ActiveSessionSetData(
    val exerciseIndex: Int,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    val completedAtMillis: Long
)

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
                    completedAtMillis = entity.completedAtMillis
                )
            }
        )
    }

    override suspend fun saveCompletedSet(
        exerciseIndex: Int,
        setIndex: Int,
        actualReps: Int,
        actualWeightKgX10: Int,
        completedAtMillis: Long
    ) {
        workoutSessionDao.insertSet(
            ActiveSessionSetEntity(
                sessionId = 1,
                exerciseIndex = exerciseIndex,
                setIndex = setIndex,
                actualReps = actualReps,
                actualWeightKgX10 = actualWeightKgX10,
                completedAtMillis = completedAtMillis
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
        weightKgX10: Int
    ) {
        workoutSessionDao.updateSet(exerciseIndex, setIndex, reps, weightKgX10)
    }

    override suspend fun updateCursor(exerciseIndex: Int, setIndex: Int) {
        workoutSessionDao.updateCursor(
            exerciseIndex = exerciseIndex,
            setIndex = setIndex,
            updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
        )
    }

    override suspend fun clearActiveSession() {
        workoutSessionDao.clearActiveSession()
    }

    override suspend fun saveCompletedWorkout(workout: CompletedWorkout) {
        val workoutId = completedWorkoutDao.insertWorkout(
            CompletedWorkoutEntity(
                templateId = workout.templateId,
                name = workout.name,
                startTimeMillis = workout.startTimeMillis,
                endTimeMillis = workout.endTimeMillis,
                durationMillis = workout.durationMillis
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
                    actualWeightKgX10 = set.actualWeightKgX10
                )
            }
            if (setEntities.isNotEmpty()) {
                completedWorkoutDao.insertSets(setEntities)
            }
        }
    }
}

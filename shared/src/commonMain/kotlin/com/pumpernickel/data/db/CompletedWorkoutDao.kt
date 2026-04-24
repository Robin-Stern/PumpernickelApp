package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedWorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: CompletedWorkoutEntity): Long

    @Insert
    suspend fun insertExercise(exercise: CompletedWorkoutExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<CompletedWorkoutSetEntity>)

    @Query("SELECT * FROM completed_workouts ORDER BY startTimeMillis DESC")
    fun getAllWorkouts(): Flow<List<CompletedWorkoutEntity>>

    @Query("""
        SELECT w.id, w.templateId, w.name, w.startTimeMillis, w.durationMillis,
               COUNT(DISTINCT e.id) AS exerciseCount,
               COALESCE(SUM(CAST(s.actualReps AS INTEGER) * CAST(s.actualWeightKgX10 AS INTEGER)), 0) AS totalVolume
        FROM completed_workouts w
        LEFT JOIN completed_workout_exercises e ON e.workoutId = w.id
        LEFT JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
        GROUP BY w.id
        ORDER BY w.startTimeMillis DESC
    """)
    fun getWorkoutSummaries(): Flow<List<WorkoutSummaryDto>>

    @Query("SELECT * FROM completed_workout_exercises WHERE workoutId = :workoutId ORDER BY exerciseOrder ASC")
    suspend fun getExercisesForWorkout(workoutId: Long): List<CompletedWorkoutExerciseEntity>

    @Query("SELECT * FROM completed_workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setIndex ASC")
    suspend fun getSetsForExercise(workoutExerciseId: Long): List<CompletedWorkoutSetEntity>

    @Query("SELECT * FROM completed_workouts WHERE templateId = :templateId ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getLastWorkoutForTemplate(templateId: Long): CompletedWorkoutEntity?

    @Query("SELECT * FROM completed_workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): CompletedWorkoutEntity?

    @Query("""
        SELECT e.exerciseId,
               MAX(CAST(s.actualWeightKgX10 AS INTEGER)) AS maxWeightKgX10
        FROM completed_workout_exercises e
        INNER JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
        WHERE e.exerciseId IN (:exerciseIds)
        AND s.actualReps > 0
        GROUP BY e.exerciseId
    """)
    suspend fun getPersonalBests(exerciseIds: List<String>): List<ExercisePbDto>

    @Query("""
        SELECT cwe.exerciseId, cws.rir
        FROM completed_workout_exercises cwe
        INNER JOIN completed_workouts cw ON cw.id = cwe.workoutId
        INNER JOIN completed_workout_sets cws ON cws.workoutExerciseId = cwe.id
        WHERE cw.startTimeMillis >= :sinceMillis
    """)
    suspend fun getExerciseSetRirSince(sinceMillis: Long): List<ExerciseSetRirDto>
}

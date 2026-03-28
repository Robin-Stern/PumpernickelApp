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
}

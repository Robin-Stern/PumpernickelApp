package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%'
        AND (:muscleGroup IS NULL OR primaryMuscles LIKE '%' || :muscleGroup || '%')
        ORDER BY name ASC
        """
    )
    fun searchExercises(query: String, muscleGroup: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun getExerciseById(id: String): Flow<ExerciseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCount(): Int

    @Query("SELECT DISTINCT equipment FROM exercises WHERE equipment IS NOT NULL ORDER BY equipment ASC")
    suspend fun getDistinctEquipment(): List<String>

    @Query("SELECT DISTINCT category FROM exercises ORDER BY category ASC")
    suspend fun getDistinctCategories(): List<String>
}

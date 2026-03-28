package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM active_sessions WHERE id = 1")
    suspend fun getActiveSession(): ActiveSessionEntity?

    @Query("SELECT * FROM active_sessions WHERE id = 1")
    fun observeActiveSession(): Flow<ActiveSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ActiveSessionEntity)

    @Insert
    suspend fun insertSet(set: ActiveSessionSetEntity): Long

    @Query("SELECT * FROM active_session_sets WHERE sessionId = 1 ORDER BY exerciseIndex ASC, setIndex ASC")
    suspend fun getSessionSets(): List<ActiveSessionSetEntity>

    @Query("UPDATE active_session_sets SET actualReps = :reps, actualWeightKgX10 = :weightKgX10 WHERE exerciseIndex = :exerciseIndex AND setIndex = :setIndex AND sessionId = 1")
    suspend fun updateSet(exerciseIndex: Int, setIndex: Int, reps: Int, weightKgX10: Int)

    @Query("UPDATE active_sessions SET currentExerciseIndex = :exerciseIndex, currentSetIndex = :setIndex, lastUpdatedMillis = :updatedAt WHERE id = 1")
    suspend fun updateCursor(exerciseIndex: Int, setIndex: Int, updatedAt: Long)

    @Query("DELETE FROM active_sessions WHERE id = 1")
    suspend fun clearActiveSession()
}

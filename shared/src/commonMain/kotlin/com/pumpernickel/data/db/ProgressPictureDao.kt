package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the `progress_pictures` table introduced in DB v9 (Phase 17).
 *
 * Read methods come in two flavours:
 *  - `Flow<...>` for live UI observation (gallery grid, viewer carousel,
 *    in-prompt photo counter).
 *  - `suspend ...` for one-shot reads (repository-level batch deletes that
 *    need the file list before issuing the row delete).
 *
 * Aggregation queries (`observeGalleryTiles`) JOIN through `completed_workouts`
 * + `completed_workout_exercises` + `completed_workout_sets` to compute the
 * tile overlay's volume metric in the same shape used by
 * [CompletedWorkoutDao.getWorkoutSummaries]. Column names (`actualReps`,
 * `actualWeightKgX10`, `workoutExerciseId`) match
 * [CompletedWorkoutSetEntity] verbatim — keep them in sync if the entity
 * column names ever change.
 */
@Dao
interface ProgressPictureDao {

    @Insert
    suspend fun insertPicture(picture: ProgressPictureEntity)

    @Insert
    suspend fun insertPictures(pictures: List<ProgressPictureEntity>)

    /**
     * All photos for a single workout, ordered by sortOrder ascending (D-17-11
     * — viewer pages chronologically). Used by the photo viewer carousel.
     */
    @Query("SELECT * FROM progress_pictures WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    fun getPicturesForWorkout(workoutId: Long): Flow<List<ProgressPictureEntity>>

    /**
     * Suspend variant for one-shot reads (e.g. when the repository deletes
     * all files for a cascade-deleted workout).
     */
    @Query("SELECT * FROM progress_pictures WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    suspend fun getPicturesForWorkoutOnce(workoutId: Long): List<ProgressPictureEntity>

    /**
     * Live count for the prompt card's "Add another?" affordance (D-17-02).
     */
    @Query("SELECT COUNT(*) FROM progress_pictures WHERE workoutId = :workoutId")
    fun observePhotoCount(workoutId: Long): Flow<Int>

    /**
     * Gallery tile DTO: one row per workout that has >= 1 photo (D-17-11),
     * cover photo = the most recently captured photo for that workout
     * (MAX(capturedAtMillis)), volume = sum(actualReps * actualWeightKgX10),
     * photoCount = total photos for that workout.
     *
     * Note: actualWeightKgX10 is stored as kg*10 (project convention); the
     * caller divides by 10.0 to get real kg before formatting (D-17-12).
     */
    @Query("""
        SELECT
            w.id AS workoutId,
            w.name AS workoutName,
            w.startTimeMillis AS startTimeMillis,
            COALESCE(SUM(CAST(s.actualReps AS INTEGER) * CAST(s.actualWeightKgX10 AS INTEGER)), 0) AS volumeKgX10,
            (SELECT p.relativePath FROM progress_pictures p
             WHERE p.workoutId = w.id
             ORDER BY p.capturedAtMillis DESC LIMIT 1) AS coverRelativePath,
            (SELECT COUNT(*) FROM progress_pictures p WHERE p.workoutId = w.id) AS photoCount
        FROM completed_workouts w
        LEFT JOIN completed_workout_exercises e ON e.workoutId = w.id
        LEFT JOIN completed_workout_sets s ON s.workoutExerciseId = e.id
        WHERE EXISTS (SELECT 1 FROM progress_pictures p WHERE p.workoutId = w.id)
        GROUP BY w.id
        ORDER BY w.startTimeMillis DESC
    """)
    fun observeGalleryTiles(): Flow<List<ProgressGalleryTileDto>>

    @Query("DELETE FROM progress_pictures WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM progress_pictures WHERE workoutId = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: Long)
}

/**
 * Projection used by [ProgressPictureDao.observeGalleryTiles]. The repository
 * augments these with PR count (from xp_ledger) and goal-day flag (from
 * NutritionGoalDayPolicy) before exposing a domain ProgressGalleryTile.
 *
 * volumeKgX10 is in kg*10 units (project convention). Divide by 10.0 for real kg.
 */
data class ProgressGalleryTileDto(
    val workoutId: Long,
    val workoutName: String,
    val startTimeMillis: Long,
    val volumeKgX10: Long,
    val coverRelativePath: String,
    val photoCount: Int
)

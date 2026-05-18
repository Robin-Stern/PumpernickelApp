package com.pumpernickel.domain.repository

import com.pumpernickel.domain.progresspic.ProgressGalleryTile
import com.pumpernickel.domain.progresspic.ProgressPicture
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer port for progress pictures. Mediates between the photo bytes
 * on disk (PhotoVault) and the metadata rows in Room (ProgressPictureDao).
 * Owns the disk <-> DB consistency on delete (D-17-09 / T-DELETE-ORPHAN):
 * every delete operation removes both the row and the file in one
 * repository call.
 *
 * The repository is intentionally independent of the gamification + nutrition
 * sub-systems: `observeGalleryTiles()` emits placeholders for `prCount` and
 * `isGoalDay`; the ProgressGalleryViewModel (plan 17-06) owns the enrichment
 * by `combine`-ing this Flow with per-workout queries against
 * GamificationDao + NutritionGoalDayPolicy. This keeps the Koin graph simple
 * and the repository easy to test.
 */
interface ProgressPictureRepository {

    /** Live gallery tiles: one per workout that has at least one photo. */
    fun observeGalleryTiles(): Flow<List<ProgressGalleryTile>>

    /** Live list of all photos for one workout, ordered by sortOrder ASC. */
    fun observePicturesForWorkout(workoutId: Long): Flow<List<ProgressPicture>>

    /** Live count for the prompt-card "Add another?" affordance (D-17-02). */
    fun observePhotoCount(workoutId: Long): Flow<Int>

    /**
     * Saves bytes to disk via PhotoVault, then inserts the row. The caller
     * generates the UUID; the repository keeps disk and DB consistent.
     * @return the inserted ProgressPicture domain model.
     */
    suspend fun savePicture(
        id: String,
        workoutId: Long,
        bytes: ByteArray,
        capturedAtMillis: Long,
        sortOrder: Int
    ): ProgressPicture

    /** Deletes one photo (file + row). Used from the viewer overflow. */
    suspend fun deletePicture(id: String, relativePath: String)

    /**
     * Deletes every photo + row for a workout. Called when a workout is
     * deleted so files don't orphan (D-17-09).
     *
     * Best-effort: row delete is also handled by the FK CASCADE in 17-01,
     * but Room's CASCADE does not run application code, so the *files*
     * still need this call. Practice: call this BEFORE the workout row is
     * deleted so the relativePaths can be looked up.
     */
    suspend fun deleteForWorkout(workoutId: Long)
}

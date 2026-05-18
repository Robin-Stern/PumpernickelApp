package com.pumpernickel.data.repository

import com.pumpernickel.data.db.ProgressPictureDao
import com.pumpernickel.data.db.ProgressPictureEntity
import com.pumpernickel.infrastructure.progresspic.PhotoVault
import com.pumpernickel.domain.progresspic.ProgressGalleryTile
import com.pumpernickel.domain.progresspic.ProgressPicture
import com.pumpernickel.domain.repository.ProgressPictureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressPictureRepositoryImpl(
    private val dao: ProgressPictureDao,
    private val vault: PhotoVault
) : ProgressPictureRepository {

    override fun observeGalleryTiles(): Flow<List<ProgressGalleryTile>> =
        dao.observeGalleryTiles().map { dtos ->
            dtos.map { dto ->
                ProgressGalleryTile(
                    workoutId = dto.workoutId,
                    workoutName = dto.workoutName,
                    startTimeMillis = dto.startTimeMillis,
                    volumeKg = dto.volumeKgX10 / 10L,
                    coverRelativePath = dto.coverRelativePath,
                    photoCount = dto.photoCount,
                    prCount = 0,                 // placeholder; VM enriches via GamificationDao
                    isGoalDay = false             // placeholder; VM enriches via NutritionGoalDayPolicy
                )
            }
        }
    // NOTE: PR count + goal-day enrichment is delegated to the VM layer
    // in plan 17-06 (ProgressGalleryViewModel). The VM combines this Flow
    // with the per-workout PR / goal-day Flows. The repository stays
    // independent of the gamification + nutrition sub-systems and the
    // Koin graph stays simple (Option A — required, not optional).

    override fun observePicturesForWorkout(workoutId: Long): Flow<List<ProgressPicture>> =
        dao.getPicturesForWorkout(workoutId).map { rows -> rows.map { it.toDomain() } }

    override fun observePhotoCount(workoutId: Long): Flow<Int> =
        dao.observePhotoCount(workoutId)

    override suspend fun savePicture(
        id: String,
        workoutId: Long,
        bytes: ByteArray,
        capturedAtMillis: Long,
        sortOrder: Int
    ): ProgressPicture {
        val relativePath = vault.write(id, bytes)
        val entity = ProgressPictureEntity(
            id = id,
            workoutId = workoutId,
            relativePath = relativePath,
            capturedAtMillis = capturedAtMillis,
            sortOrder = sortOrder
        )
        dao.insertPicture(entity)
        return entity.toDomain()
    }

    override suspend fun deletePicture(id: String, relativePath: String) {
        // Row first (so a stale row with missing file is the worse failure mode
        // than an orphaned file with no row — T-DELETE-ORPHAN ordering).
        dao.deleteById(id)
        vault.delete(relativePath)
    }

    override suspend fun deleteForWorkout(workoutId: Long) {
        // Read paths BEFORE deleting rows — once rows are gone, we can't look
        // up the on-disk filenames. T-DELETE-ORPHAN mitigation.
        val rows = dao.getPicturesForWorkoutOnce(workoutId)
        val paths = rows.map { it.relativePath }
        dao.deleteByWorkoutId(workoutId)
        vault.deleteAll(paths)
    }
}

// ----- Mappers -----

private fun ProgressPictureEntity.toDomain(): ProgressPicture =
    ProgressPicture(
        id = id,
        workoutId = workoutId,
        relativePath = relativePath,
        capturedAtMillis = capturedAtMillis,
        sortOrder = sortOrder
    )

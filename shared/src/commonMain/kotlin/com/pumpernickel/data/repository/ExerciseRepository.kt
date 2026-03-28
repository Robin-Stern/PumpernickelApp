package com.pumpernickel.data.repository

import com.pumpernickel.data.db.DatabaseSeeder
import com.pumpernickel.data.db.ExerciseDao
import com.pumpernickel.data.db.ExerciseEntity
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ExerciseRepository {
    fun getExercises(): Flow<List<Exercise>>
    fun searchExercises(query: String, muscleGroup: MuscleGroup?): Flow<List<Exercise>>
    fun getExerciseById(id: String): Flow<Exercise?>
    suspend fun createExercise(exercise: Exercise)
    suspend fun getDistinctEquipment(): List<String>
    suspend fun getDistinctCategories(): List<String>
}

class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    private val seeder: DatabaseSeeder
) : ExerciseRepository {

    private val seedMutex = Mutex()
    private var seeded = false

    private suspend fun ensureSeeded() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            val count = dao.getExerciseCount()
            if (count == 0) {
                seeder.seedExercises(dao)
            }
            seeded = true
        }
    }

    override fun getExercises(): Flow<List<Exercise>> {
        return dao.getAllExercises()
            .onStart { ensureSeeded() }
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun searchExercises(query: String, muscleGroup: MuscleGroup?): Flow<List<Exercise>> {
        return dao.searchExercises(query, muscleGroup?.dbName)
            .onStart { ensureSeeded() }
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getExerciseById(id: String): Flow<Exercise?> {
        return dao.getExerciseById(id)
            .onStart { ensureSeeded() }
            .map { it?.toDomain() }
    }

    override suspend fun createExercise(exercise: Exercise) {
        val json = Json { ignoreUnknownKeys = true }
        val entity = ExerciseEntity(
            id = exercise.id,
            name = exercise.name,
            force = exercise.force,
            level = exercise.level,
            mechanic = exercise.mechanic,
            equipment = exercise.equipment,
            category = exercise.category,
            instructions = json.encodeToString(exercise.instructions),
            images = json.encodeToString(exercise.images),
            isCustom = true,
            primaryMuscles = exercise.primaryMuscles.joinToString(",") { it.dbName },
            secondaryMuscles = exercise.secondaryMuscles.joinToString(",") { it.dbName }
        )
        dao.insert(entity)
    }

    override suspend fun getDistinctEquipment(): List<String> {
        ensureSeeded()
        return dao.getDistinctEquipment()
    }

    override suspend fun getDistinctCategories(): List<String> {
        ensureSeeded()
        return dao.getDistinctCategories()
    }
}

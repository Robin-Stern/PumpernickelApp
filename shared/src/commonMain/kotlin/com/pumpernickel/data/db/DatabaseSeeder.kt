package com.pumpernickel.data.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExerciseJson(
    val name: String,
    val force: String? = null,
    val level: String,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val category: String,
    val images: List<String>,
    val id: String
)

class DatabaseSeeder(
    private val readResourceFile: () -> String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun seedExercises(dao: ExerciseDao) {
        val jsonString = readResourceFile()
        val exercises = json.decodeFromString<List<ExerciseJson>>(jsonString)

        val entities = exercises.map { exercise ->
            ExerciseEntity(
                id = exercise.id,
                name = exercise.name,
                force = exercise.force,
                level = exercise.level,
                mechanic = exercise.mechanic,
                equipment = exercise.equipment,
                category = exercise.category,
                instructions = json.encodeToString(exercise.instructions),
                images = json.encodeToString(exercise.images),
                isCustom = false,
                primaryMuscles = exercise.primaryMuscles.joinToString(","),
                secondaryMuscles = exercise.secondaryMuscles.joinToString(",")
            )
        }

        dao.insertAll(entities)
    }
}

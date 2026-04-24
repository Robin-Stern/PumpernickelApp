package com.pumpernickel.domain.model

import com.pumpernickel.data.db.ExerciseEntity
import kotlinx.serialization.json.Json

data class Exercise(
    val id: String,
    val name: String,
    val force: String?,
    val level: String,
    val mechanic: String?,
    val equipment: String?,
    val category: String,
    val instructions: List<String>,
    val images: List<String>,
    val isCustom: Boolean,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>
)

private val json = Json { ignoreUnknownKeys = true }

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    force = force,
    level = level,
    mechanic = mechanic,
    equipment = equipment,
    category = category,
    instructions = try {
        json.decodeFromString<List<String>>(instructions)
    } catch (_: Exception) {
        emptyList()
    },
    images = try {
        json.decodeFromString<List<String>>(images)
    } catch (_: Exception) {
        emptyList()
    },
    isCustom = isCustom,
    primaryMuscles = primaryMuscles
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { MuscleGroup.fromDbName(it) },
    secondaryMuscles = secondaryMuscles
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { MuscleGroup.fromDbName(it) }
)

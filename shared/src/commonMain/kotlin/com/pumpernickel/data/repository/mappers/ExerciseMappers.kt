package com.pumpernickel.data.repository.mappers

import com.pumpernickel.data.db.ExerciseEntity
import com.pumpernickel.domain.model.Exercise
import com.pumpernickel.domain.model.MuscleGroup
import kotlinx.serialization.json.Json

/**
 * Entity-to-Domain mapper for the Exercise aggregate.
 *
 * Lives in `data/repository/mappers/` so `domain/model/Exercise.kt` stays free of
 * Room-entity imports (Phase 20, Smell 5 — D-20-01 dependency rule, D-20-08 mapper layout).
 *
 * JSON-parsing and CSV-splitting helpers stay co-located here because they exist
 * solely to translate the SQLite storage representation back to domain types.
 */

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
        .mapNotNull { MuscleGroup.fromDbName(it) },
    source = source
)

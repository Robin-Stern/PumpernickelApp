package com.pumpernickel.data.repository.mappers

import com.pumpernickel.data.db.TemplateExerciseEntity
import com.pumpernickel.data.db.WorkoutTemplateEntity
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.TemplateExercise
import com.pumpernickel.domain.model.WorkoutTemplate

/**
 * Entity-to-Domain mappers for the WorkoutTemplate aggregate.
 *
 * Lives in `data/repository/mappers/` so `domain/model/WorkoutTemplate.kt` stays free of
 * Room-entity imports (Phase 20, Smell 5 — D-20-01 dependency rule, D-20-08 mapper layout).
 *
 * The `perSetReps` CSV-split helper is inlined here for the same reason — it exists solely
 * to translate the SQLite storage representation back to a `List<Int>?` on the domain type.
 */

fun WorkoutTemplateEntity.toDomain(
    exercises: List<TemplateExercise> = emptyList()
) = WorkoutTemplate(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    exercises = exercises,
    source = source  // pass through from entity
)

fun TemplateExerciseEntity.toDomain(
    exerciseName: String,
    primaryMuscles: List<MuscleGroup>
) = TemplateExercise(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    primaryMuscles = primaryMuscles,
    targetSets = targetSets,
    targetReps = targetReps,
    restPeriodSec = restPeriodSec,
    exerciseOrder = exerciseOrder,
    perSetReps = perSetReps?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }
)

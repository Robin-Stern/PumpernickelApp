package com.pumpernickel.domain.model

data class SessionExercise(
    val exerciseId: String,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKgX10: Int,
    val restPeriodSec: Int,
    val sets: List<SessionSet>
)

data class SessionSet(
    val setIndex: Int,
    val targetReps: Int,
    val targetWeightKgX10: Int,
    val actualReps: Int?,      // null = not yet completed
    val actualWeightKgX10: Int?, // null = not yet completed
    val isCompleted: Boolean
)

data class SetPreFill(
    val reps: Int,
    val weightKgX10: Int
)

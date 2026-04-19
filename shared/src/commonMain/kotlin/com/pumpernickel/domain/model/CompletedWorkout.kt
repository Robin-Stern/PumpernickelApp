package com.pumpernickel.domain.model

data class CompletedWorkout(
    val id: Long,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val exercises: List<CompletedExercise>
)

data class CompletedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val sets: List<CompletedSet>
)

data class CompletedSet(
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    val rir: Int = 2
)

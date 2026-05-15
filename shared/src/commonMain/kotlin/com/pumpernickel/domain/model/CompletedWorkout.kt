package com.pumpernickel.domain.model

data class CompletedWorkout(
    val id: Long,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val exercises: List<CompletedExercise>,
    /**
     * D-19-14 — true when this workout was auto-saved due to a geofence-exit
     * timeout rather than a normal review-and-save flow. Defaults to false
     * for backward compatibility with existing callers and historical rows.
     */
    val abandoned: Boolean = false
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

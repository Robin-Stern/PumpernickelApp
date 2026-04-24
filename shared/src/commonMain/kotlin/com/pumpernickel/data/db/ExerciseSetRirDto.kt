package com.pumpernickel.data.db

/**
 * One row per completed set, carrying the exercise ID and the RIR value.
 * Used by OverviewViewModel to compute weighted muscle-load scores.
 */
data class ExerciseSetRirDto(
    val exerciseId: String,
    val rir: Int
)

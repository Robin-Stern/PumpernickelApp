package com.pumpernickel.domain.model

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exercises: List<TemplateExercise> = emptyList(),
    val source: String? = null  // "USER" | "AI" — null treated as USER (D-18-11)
)

data class TemplateExercise(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscles: List<MuscleGroup>,
    val targetSets: Int,
    val targetReps: Int,
    val restPeriodSec: Int,
    val exerciseOrder: Int,
    val perSetReps: List<Int>? = null
)

fun formatWeightKg(kgX10: Int): String {
    val whole = kgX10 / 10
    val decimal = kgX10 % 10
    return if (decimal == 0) "$whole kg" else "$whole.$decimal kg"
}

fun parseWeightKgX10(input: String): Int? {
    val value = input.toDoubleOrNull() ?: return null
    if (value < 0) return null
    return (value * 10).toInt()
}

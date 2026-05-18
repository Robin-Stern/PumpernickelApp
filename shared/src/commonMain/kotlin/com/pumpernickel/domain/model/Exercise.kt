package com.pumpernickel.domain.model

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
    val secondaryMuscles: List<MuscleGroup>,
    val source: String? = null
)

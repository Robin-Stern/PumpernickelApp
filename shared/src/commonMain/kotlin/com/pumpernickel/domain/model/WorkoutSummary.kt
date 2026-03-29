package com.pumpernickel.domain.model

data class WorkoutSummary(
    val id: Long,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val durationMillis: Long,
    val exerciseCount: Int,
    val totalVolumeKgX10: Long
)

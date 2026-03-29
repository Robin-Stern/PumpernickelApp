package com.pumpernickel.data.db

data class WorkoutSummaryDto(
    val id: Long,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val durationMillis: Long,
    val exerciseCount: Int,
    val totalVolume: Long
)

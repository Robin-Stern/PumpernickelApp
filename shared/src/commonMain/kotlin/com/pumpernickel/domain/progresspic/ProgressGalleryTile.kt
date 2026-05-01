package com.pumpernickel.domain.progresspic

data class ProgressGalleryTile(
    val workoutId: Long,
    val workoutName: String,
    val startTimeMillis: Long,
    val volumeKg: Long,        // already divided by 10 from the kg*10 storage
    val coverRelativePath: String,
    val photoCount: Int,
    val prCount: Int,           // joined from xp_ledger
    val isGoalDay: Boolean      // joined from NutritionGoalDayPolicy
)

package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_workouts")
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long
)

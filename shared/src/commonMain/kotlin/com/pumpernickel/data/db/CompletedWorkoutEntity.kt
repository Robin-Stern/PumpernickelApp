package com.pumpernickel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_workouts")
data class CompletedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    /**
     * D-19-14 — true when the workout was auto-saved after a geofence-exit
     * grace-period timeout. False for normal completion through review.
     * Existing rows (pre-v11) default to false via Room AutoMigration.
     */
    @ColumnInfo(defaultValue = "0")
    val abandoned: Boolean = false
)

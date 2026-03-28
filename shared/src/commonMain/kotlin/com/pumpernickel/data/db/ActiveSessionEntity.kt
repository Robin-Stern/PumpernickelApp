package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,  // Singleton: at most one active session
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val lastUpdatedMillis: Long
)

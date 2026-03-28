package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "active_session_sets",
    foreignKeys = [ForeignKey(
        entity = ActiveSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ActiveSessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 1,
    val exerciseIndex: Int,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    val completedAtMillis: Long
)

package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,     // References exercises.id (String PK)
    val targetSets: Int,        // Default: 3 (D-08)
    val targetReps: Int,        // Default: 10 (D-08)
    val targetWeightKgX10: Int, // kg * 10, default: 0 (D-06, D-08)
    val restPeriodSec: Int,     // seconds, default: 90 (D-07, D-08)
    val exerciseOrder: Int,     // 0-based ordering (D-10)
    val perSetReps: String? = null,           // CSV per-set reps e.g. "10,8,6", null = uniform
    val perSetWeightKgX10: String? = null     // CSV per-set weight e.g. "800,700,600", null = uniform
)

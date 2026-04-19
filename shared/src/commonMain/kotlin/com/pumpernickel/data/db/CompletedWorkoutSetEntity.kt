package com.pumpernickel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completed_workout_sets",
    foreignKeys = [ForeignKey(
        entity = CompletedWorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutExerciseId")]
)
data class CompletedWorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    val actualReps: Int,
    val actualWeightKgX10: Int,
    @ColumnInfo(defaultValue = "2") val rir: Int = 2
)

package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completed_workout_exercises",
    foreignKeys = [ForeignKey(
        entity = CompletedWorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class CompletedWorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int
)

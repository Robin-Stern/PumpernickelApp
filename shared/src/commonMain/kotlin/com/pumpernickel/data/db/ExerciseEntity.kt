package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val force: String?,
    val level: String,
    val mechanic: String?,
    val equipment: String?,
    val category: String,
    val instructions: String,    // JSON string: ["step1","step2"]
    val images: String,          // JSON string: ["img1.jpg","img2.jpg"]
    val isCustom: Boolean = false,
    val primaryMuscles: String,  // Comma-separated: "abdominals"
    val secondaryMuscles: String // Comma-separated: "calves,hamstrings" or ""
)

package com.pumpernickel.domain.progresspic

data class ProgressPicture(
    val id: String,
    val workoutId: Long,
    val relativePath: String,
    val capturedAtMillis: Long,
    val sortOrder: Int
)

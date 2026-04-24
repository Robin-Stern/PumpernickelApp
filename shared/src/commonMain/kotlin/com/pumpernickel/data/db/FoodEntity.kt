package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double,
    val unit: String, // "GRAM" or "MILLILITER"
    val isRecipe: Boolean = false,
    val barcode: String? = null
)

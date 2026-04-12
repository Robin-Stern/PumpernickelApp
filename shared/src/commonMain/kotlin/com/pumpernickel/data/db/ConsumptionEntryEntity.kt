package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consumption_entries")
data class ConsumptionEntryEntity(
    @PrimaryKey val id: String,
    val foodId: String?,
    val name: String,
    val caloriesPer100: Double,
    val proteinPer100: Double,
    val fatPer100: Double,
    val carbsPer100: Double,
    val sugarPer100: Double,
    val unit: String, // "GRAM" or "MILLILITER"
    val amount: Double,
    val timestampMillis: Long
)

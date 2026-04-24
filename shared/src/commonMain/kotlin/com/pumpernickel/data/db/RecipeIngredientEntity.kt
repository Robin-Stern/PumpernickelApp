package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId")]
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    val foodId: String,
    val amountGrams: Double
)

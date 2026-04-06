@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import com.example.pumpernickelapp.core.UuidSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A single logged consumption at a point in time.
 *
 * The entry carries a snapshot of the food's per-100 macros so that ad-hoc entries
 * (not backed by a saved [Food]) are fully standalone, and so that editing/deleting
 * a library Food does not retroactively change history.
 *
 * [amount] is in the unit stored in [unit] (g or ml).
 */
@Serializable
data class ConsumptionEntry(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = Uuid.random(),
    @Serializable(with = UuidSerializer::class)
    val foodId: Uuid? = null,
    val name: String,
    val caloriesPer100: Double,
    val proteinPer100: Double,
    val fatPer100: Double,
    val carbsPer100: Double,
    val sugarPer100: Double,
    val unit: FoodUnit,
    val amount: Double,
    val timestamp: Instant
) {
    init {
        require(amount > 0) { "Menge muss größer als 0 sein" }
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
        require(caloriesPer100 >= 0) { "Kalorien dürfen nicht negativ sein" }
    }

    companion object {
        fun fromFood(food: Food, amount: Double, timestamp: Instant): ConsumptionEntry =
            ConsumptionEntry(
                foodId         = food.id,
                name           = food.name,
                caloriesPer100 = food.calories,
                proteinPer100  = food.protein,
                fatPer100      = food.fat,
                carbsPer100    = food.carbohydrates,
                sugarPer100    = food.sugar,
                unit           = food.unit,
                amount         = amount,
                timestamp      = timestamp
            )
    }
}

fun ConsumptionEntry.macros(): RecipeMacros {
    val factor = amount / 100.0
    return RecipeMacros(
        calories = caloriesPer100 * factor,
        protein  = proteinPer100 * factor,
        fat      = fatPer100 * factor,
        carbs    = carbsPer100 * factor,
        sugar    = sugarPer100 * factor
    )
}

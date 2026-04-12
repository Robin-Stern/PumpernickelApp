@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.pumpernickel.domain.model

@kotlinx.serialization.Serializable
data class ConsumptionEntry(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val foodId: String? = null,
    val name: String,
    val caloriesPer100: Double,
    val proteinPer100: Double,
    val fatPer100: Double,
    val carbsPer100: Double,
    val sugarPer100: Double,
    val unit: FoodUnit,
    val amount: Double,
    val timestampMillis: Long
) {
    init {
        require(amount > 0) { "Menge muss größer als 0 sein" }
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
        require(caloriesPer100 >= 0) { "Kalorien dürfen nicht negativ sein" }
    }

    companion object {
        fun fromFood(food: Food, amount: Double, timestampMillis: Long): ConsumptionEntry =
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
                timestampMillis = timestampMillis
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

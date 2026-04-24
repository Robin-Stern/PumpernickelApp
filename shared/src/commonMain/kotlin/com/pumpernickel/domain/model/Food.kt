@file:OptIn(ExperimentalUuidApi::class)

package com.pumpernickel.domain.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Food(
    val id: String = Uuid.random().toString(),
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double,
    val unit: FoodUnit = FoodUnit.GRAM,
    val isRecipe: Boolean = false,
    val barcode: String? = null
) {
    init {
        require(calories >= 0) { "Kalorien dürfen nicht negativ sein" }
        require(protein >= 0) { "Protein darf nicht negativ sein" }
        require(fat >= 0) { "Fett darf nicht negativ sein" }
        require(carbohydrates >= 0) { "Kohlenhydrate dürfen nicht negativ sein" }
        require(sugar >= 0) { "Zucker darf nicht negativ sein" }
        require(sugar <= carbohydrates) { "Zucker ($sugar) kann nicht größer sein als Kohlenhydrate ($carbohydrates)" }
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
    }
}

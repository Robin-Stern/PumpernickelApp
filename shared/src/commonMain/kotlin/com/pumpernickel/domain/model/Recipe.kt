@file:OptIn(ExperimentalUuidApi::class)

package com.pumpernickel.domain.model

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Recipe(
    val id: String = Uuid.random().toString(),
    val name: String,
    val ingredients: List<RecipeIngredient>,
    val isFavorite: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
        require(ingredients.isNotEmpty()) { "Zutatenliste darf nicht leer sein" }
    }
}

@Serializable
data class RecipeIngredient(
    val foodId: String,
    val amountGrams: Double
) {
    init {
        require(amountGrams >= 0) { "Menge darf nicht negativ sein" }
    }
}

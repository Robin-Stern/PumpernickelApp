@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi


// Optin für neue Features (Uuid für gleiche UUID auf Android und iOS) notwendig. Kotlin Updates können hier breaking Changes einführen!
@Serializable
data class Food constructor(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = Uuid.random(), // Nutze Uuid (Kotlin-Native) statt UUID (Java)
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double,
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

    @Serializable
    data class Recipe constructor(
        @Serializable(with = UuidSerializer::class)
        val id: Uuid = Uuid.random(),
        val name: String,
        val ingredients: List<RecipeIngredient>
    ) {
        init {
            require(name.isNotBlank()) { "Name darf nicht leer sein" }
            require(value = ingredients.isNotEmpty()) { "Zutatenliste darf nicht leer sein" }
        }
    }
}

@Serializable
data class RecipeIngredient constructor(
    @Serializable(with = UuidSerializer::class)
    val foodId: Uuid = Uuid.random(),
    val amountGrams: Double
) {
    init {
        require(amountGrams >= 0) { "Menge darf nicht negativ sein" }
    }
}
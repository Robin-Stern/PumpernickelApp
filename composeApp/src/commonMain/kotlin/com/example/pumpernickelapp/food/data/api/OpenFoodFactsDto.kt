package com.example.pumpernickelapp.food.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenFoodFactsResponse(
    val status: Int = 0,
    val product: ProductDto? = null
)

@Serializable
data class ProductDto(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: NutrimentsDto? = null
)

@Serializable
data class NutrimentsDto(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null
)

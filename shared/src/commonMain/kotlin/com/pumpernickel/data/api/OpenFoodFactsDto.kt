package com.pumpernickel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenFoodFactsResponse(
    val status: Int = 0,
    val product: ProductDto? = null
)

@Serializable
data class OpenFoodFactsSearchResponse(
    val count: Int = 0,
    val products: List<SearchProductDto> = emptyList()
)

@Serializable
internal data class OpenFoodFactsSearchV2Response(
    val hits: List<SearchProductDto> = emptyList(),
    val count: Int = 0
)

@Serializable
data class ProductDto(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: NutrimentsDto? = null
)

@Serializable
data class SearchProductDto(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: NutrimentsDto? = null,
    val brands: List<String>? = null,
    @SerialName("nutrition_grade_fr") val nutritionGradeFr: String? = null,
    // D-21-04 step 1 — generic_name + categories_tags so the adapter brand-match re-ranker
    // can score against more fields than just product_name+brands. Optional + default null
    // so historical fixtures and any OFF response shape variation still deserializes.
    @SerialName("generic_name") val genericName: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null
)

@Serializable
data class NutrimentsDto(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null
)

@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import com.example.pumpernickelapp.food.data.api.OpenFoodFactsApi
import kotlin.uuid.ExperimentalUuidApi

class LookupBarcodeUseCase(
    private val api: OpenFoodFactsApi,
    private val loadFoods: LoadFoodsUseCase
) {

    sealed interface Result {
        data class FoundLocally(val food: Food) : Result
        data class FoundRemote(
            val name: String,
            val calories: Double,
            val protein: Double,
            val fat: Double,
            val carbs: Double,
            val sugar: Double
        ) : Result
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(barcode: String): Result {
        val localMatch = loadFoods().firstOrNull { it.barcode == barcode }
        if (localMatch != null) return Result.FoundLocally(localMatch)

        return try {
            val response = api.lookupBarcode(barcode)
            val product = response.product
            val nutriments = product?.nutriments
            if (response.status == 1 && product?.productName != null && nutriments != null) {
                val carbs = nutriments.carbohydrates100g ?: 0.0
                val sugar = nutriments.sugars100g ?: 0.0
                Result.FoundRemote(
                    name = product.productName,
                    calories = nutriments.energyKcal100g ?: 0.0,
                    protein = nutriments.proteins100g ?: 0.0,
                    fat = nutriments.fat100g ?: 0.0,
                    carbs = carbs,
                    sugar = minOf(sugar, carbs)
                )
            } else {
                Result.NotFound
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unbekannter Fehler")
        }
    }
}

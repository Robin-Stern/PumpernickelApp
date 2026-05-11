package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.api.OpenFoodFactsApi

class SearchFoodsRemoteUseCase(private val api: OpenFoodFactsApi) {

    data class RemoteFoodResult(
        val name: String,
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
        val sugar: Double
    )

    sealed interface Result {
        data class Success(val foods: List<RemoteFoodResult>) : Result
        data object Empty : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(query: String): Result {
        return try {
            val response = api.searchByName(query)
            val results = response.products.mapNotNull { product ->
                val name = product.productName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val nutriments = product.nutriments ?: return@mapNotNull null
                val carbs = nutriments.carbohydrates100g ?: 0.0
                val sugar = nutriments.sugars100g ?: 0.0
                RemoteFoodResult(
                    name = name,
                    calories = nutriments.energyKcal100g ?: 0.0,
                    protein = nutriments.proteins100g ?: 0.0,
                    fat = nutriments.fat100g ?: 0.0,
                    carbs = carbs,
                    sugar = minOf(sugar, carbs)
                )
            }
            if (results.isEmpty()) Result.Empty else Result.Success(results)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unbekannter Fehler")
        }
    }
}

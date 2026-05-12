package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.api.OpenFoodFactsApi

class SearchFoodsRemoteUseCase(private val api: OpenFoodFactsApi) {

    data class RemoteFoodResult(
        val name: String,
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
        val sugar: Double,
        val brand: String? = null
    )

    sealed interface Result {
        data class Success(val foods: List<RemoteFoodResult>) : Result
        data object Empty : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(query: String): Result {
        println("[SearchUC] invoke query='$query'")
        return try {
            val response = api.searchByName(query)
            println("[SearchUC] api returned count=${response.count} products=${response.products.size}")
            val results = response.products.mapNotNull { product ->
                val name = product.productName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val nutriments = product.nutriments ?: return@mapNotNull null
                val carbs = nutriments.carbohydrates100g ?: 0.0
                val sugar = nutriments.sugars100g ?: 0.0
                val brand = product.brands?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
                RemoteFoodResult(
                    name = name,
                    calories = nutriments.energyKcal100g ?: 0.0,
                    protein = nutriments.proteins100g ?: 0.0,
                    fat = nutriments.fat100g ?: 0.0,
                    carbs = carbs,
                    sugar = minOf(sugar, carbs),
                    brand = brand
                )
            }
            println("[SearchUC] mapped results=${results.size}")
            if (results.isEmpty()) Result.Empty else Result.Success(results)
        } catch (e: Exception) {
            println("[SearchUC] caught ${e::class.simpleName}: ${e.message}")
            val msg = e.message
            if (msg != null && msg.contains("OpenFoodFacts ist gerade nicht erreichbar")) {
                Result.Error("OpenFoodFacts ist gerade nicht erreichbar. Versuch es später nochmal.")
            } else {
                Result.Error(msg ?: "Unbekannter Fehler")
            }
        }
    }
}

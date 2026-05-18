package com.pumpernickel.infrastructure.nutrition

import com.pumpernickel.data.api.OpenFoodFactsApi
import com.pumpernickel.domain.nutrition.RemoteBarcodeProduct
import com.pumpernickel.domain.nutrition.RemoteFoodResult
import com.pumpernickel.domain.nutrition.RemoteFoodSearchClient

/**
 * Phase 20 Plan 07 (Smell 4) — concrete adapter implementing the
 * `RemoteFoodSearchClient` domain port by delegating to the Ktor-based
 * `OpenFoodFactsApi` in `data/api/`.
 *
 * Responsibility split:
 *  - **Port surface** (`searchByQuery` / `lookupBarcode`) deals only with
 *    `domain/nutrition/` types — `RemoteFoodResult`, `RemoteBarcodeProduct`.
 *  - **Adapter (this class)** translates OpenFoodFacts wire DTOs
 *    (`SearchProductDto`, `NutrimentsDto`, `OpenFoodFactsResponse`) into
 *    those domain types and preserves the existing filter semantics:
 *    products without `productName` or `nutriments` are dropped from search
 *    hits; `sugar` is clamped to `carbohydrates`; `nutriScore` is uppercased
 *    and rejected when outside A–E; `brand` is taken as the first entry of
 *    the OFF v2 `brands` array; barcode lookups return `null` when `status != 1`
 *    or the product name is missing.
 *
 * Error contract: transport exceptions surface verbatim so
 * `SearchFoodsRemoteUseCase` can match on the substring
 * "OpenFoodFacts ist gerade nicht erreichbar" (thrown by
 * `OpenFoodFactsApi.searchByName` when the response body looks like HTML).
 */
class OpenFoodFactsAdapter(
    private val api: OpenFoodFactsApi
) : RemoteFoodSearchClient {

    override suspend fun searchByQuery(query: String, pageSize: Int): List<RemoteFoodResult> {
        val response = api.searchByName(query, pageSize)
        return response.products.mapNotNull { product ->
            val name = product.productName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nutriments = product.nutriments ?: return@mapNotNull null
            val carbs = nutriments.carbohydrates100g ?: 0.0
            val sugar = nutriments.sugars100g ?: 0.0
            val brand = product.brands?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            RemoteFoodResult(
                name = name,
                calories = nutriments.energyKcal100g ?: 0.0,
                protein = nutriments.proteins100g ?: 0.0,
                fat = nutriments.fat100g ?: 0.0,
                carbs = carbs,
                sugar = minOf(sugar, carbs),
                brand = brand,
                nutriScore = product.nutritionGradeFr
                    ?.uppercase()
                    ?.takeIf { it in setOf("A", "B", "C", "D", "E") }
            )
        }
    }

    override suspend fun lookupBarcode(barcode: String): RemoteBarcodeProduct? {
        val response = api.lookupBarcode(barcode)
        val product = response.product
        val productName = product?.productName
        if (response.status != 1 || productName.isNullOrBlank()) return null

        val nutriments = product.nutriments
        return RemoteBarcodeProduct(
            name = productName,
            calories = nutriments?.energyKcal100g ?: 0.0,
            protein = nutriments?.proteins100g ?: 0.0,
            fat = nutriments?.fat100g ?: 0.0,
            carbohydrates = nutriments?.carbohydrates100g ?: 0.0,
            sugar = nutriments?.sugars100g ?: 0.0
        )
    }
}

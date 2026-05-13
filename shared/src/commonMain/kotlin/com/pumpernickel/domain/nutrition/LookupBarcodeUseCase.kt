package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.api.OpenFoodFactsApi
import com.pumpernickel.domain.model.Food

class LookupBarcodeUseCase(
    private val api: OpenFoodFactsApi,
    private val loadFoods: LoadFoodsUseCase
) {
    sealed interface Result {
        data class FoundLocally(val food: Food) : Result
        data class FoundRemote(
            val name: String, val calories: Double, val protein: Double,
            val fat: Double, val carbs: Double, val sugar: Double,
            /** True when macros came from the canonical fallback table because OFF had none. */
            val fromFallback: Boolean = false
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
            val productName = product?.productName

            if (response.status != 1 || productName == null) return Result.NotFound

            // OFF macros (any may be null/0).
            val cal = nutriments?.energyKcal100g ?: 0.0
            val prot = nutriments?.proteins100g ?: 0.0
            val fat = nutriments?.fat100g ?: 0.0
            val carbs = nutriments?.carbohydrates100g ?: 0.0
            val sugar = nutriments?.sugars100g ?: 0.0

            // Fall back to canonical values when OFF has none. Common case for
            // raw single-ingredient products (Honig, Ahornsirup, Olivenöl etc.)
            // where producers don't fill in nutriments because "everyone knows".
            val allZero = cal <= 0.0 && prot <= 0.0 && fat <= 0.0 && carbs <= 0.0
            val fallback = if (allZero) fallbackFor(productName) else null

            Result.FoundRemote(
                name = productName,
                calories = fallback?.calories ?: cal,
                protein = fallback?.protein ?: prot,
                fat = fallback?.fat ?: fat,
                carbs = fallback?.carbs ?: carbs,
                sugar = (fallback?.sugar ?: sugar).coerceAtMost(fallback?.carbs ?: carbs),
                fromFallback = fallback != null
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    private data class Fallback(
        val keywords: List<String>,
        val calories: Double, val protein: Double, val fat: Double,
        val carbs: Double, val sugar: Double
    )

    /** First substring (case-insensitive) match wins. Order matters — put more specific items first. */
    private fun fallbackFor(productName: String): Fallback? {
        val lower = productName.lowercase()
        return FALLBACKS.firstOrNull { fb -> fb.keywords.any { lower.contains(it) } }
    }

    private companion object {
        // Per-100g values from standard nutrition databases (USDA / BLS).
        // Triggered only when OFF returns the product with all-zero macros.
        private val FALLBACKS = listOf(
            // Sweeteners / syrups
            Fallback(listOf("honig", "honey"), 304.0, 0.3, 0.0, 82.0, 82.0),
            Fallback(listOf("ahornsirup", "maple syrup"), 260.0, 0.0, 0.2, 67.0, 60.0),
            Fallback(listOf("agavendicksaft", "agave"), 310.0, 0.1, 0.4, 76.0, 68.0),
            // Fats / oils
            Fallback(listOf("olivenöl", "olive oil"), 884.0, 0.0, 100.0, 0.0, 0.0),
            Fallback(listOf("rapsöl", "rapeseed", "canola"), 884.0, 0.0, 100.0, 0.0, 0.0),
            Fallback(listOf("kokosöl", "coconut oil"), 892.0, 0.0, 100.0, 0.0, 0.0),
            Fallback(listOf("butter"), 717.0, 0.9, 81.0, 0.1, 0.1),
            Fallback(listOf("margarine"), 717.0, 0.2, 80.0, 0.7, 0.7),
            // Sugars / flours
            Fallback(listOf("rohrzucker", "brown sugar"), 380.0, 0.0, 0.0, 98.0, 97.0),
            Fallback(listOf("zucker", "sugar"), 387.0, 0.0, 0.0, 100.0, 100.0),
            Fallback(listOf("vollkornmehl", "whole wheat flour"), 340.0, 13.0, 2.5, 72.0, 0.4),
            Fallback(listOf("mehl", "flour"), 364.0, 10.0, 1.0, 76.0, 0.3),
            // Grains / cereals (raw)
            Fallback(listOf("haferflocken", "rolled oats", "oats"), 379.0, 13.0, 7.0, 68.0, 1.0),
            Fallback(listOf("reis", "rice"), 365.0, 7.0, 0.7, 80.0, 0.1),
            Fallback(listOf("nudeln", "pasta", "spaghetti"), 371.0, 13.0, 1.5, 75.0, 2.7),
            // Nuts / seeds
            Fallback(listOf("walnuss", "walnut"), 654.0, 15.0, 65.0, 14.0, 2.6),
            Fallback(listOf("mandel", "almond"), 579.0, 21.0, 50.0, 22.0, 4.4),
            Fallback(listOf("erdnussbutter", "peanut butter"), 588.0, 25.0, 50.0, 20.0, 9.0),
            Fallback(listOf("erdnuss", "peanut"), 567.0, 26.0, 49.0, 16.0, 4.7),
            Fallback(listOf("haselnuss", "hazelnut"), 628.0, 15.0, 61.0, 17.0, 4.3),
            // Dairy
            Fallback(listOf("vollmilch"), 64.0, 3.4, 3.6, 4.7, 4.7),
            Fallback(listOf("milch", "milk"), 47.0, 3.4, 1.5, 4.8, 4.8),
            Fallback(listOf("magerquark", "magertopfen"), 67.0, 12.0, 0.3, 4.0, 4.0),
            Fallback(listOf("quark"), 110.0, 12.0, 5.0, 4.0, 4.0),
            Fallback(listOf("griechischer joghurt", "greek yogurt"), 97.0, 9.0, 5.0, 4.0, 4.0),
            Fallback(listOf("joghurt", "yogurt"), 61.0, 3.5, 3.3, 4.7, 4.7)
        )
    }
}

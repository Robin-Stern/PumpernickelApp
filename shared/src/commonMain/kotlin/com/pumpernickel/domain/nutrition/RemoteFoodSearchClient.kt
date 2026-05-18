package com.pumpernickel.domain.nutrition

/**
 * Phase 20 Plan 07 (Smell 4) — domain port for remote food lookup.
 * Implemented by `infrastructure/nutrition/OpenFoodFactsAdapter` over the
 * Ktor-based `data/api/OpenFoodFactsApi`.
 *
 * Keeping the port in `domain/nutrition/` (not `infrastructure/`) follows
 * CONTEXT.md §Specifics: an AI-style network port belongs to
 * `infrastructure/` because it is generic transport, but the food-search
 * surface is shaped by domain needs (barcode resolution + result fields
 * fed into `FoodEntryViewModel`). Use-cases consume this directly so the
 * port is a domain contract.
 *
 * Pure-Kotlin only — no Ktor, no kotlinx-serialization, no `com.pumpernickel.data.*`
 * imports on the surface. Field mapping from the OpenFoodFacts wire DTOs into
 * these domain types is the adapter's job.
 */
interface RemoteFoodSearchClient {

    /**
     * Free-text search over the OpenFoodFacts product catalog. Returns a
     * list of canonical `RemoteFoodResult` rows (already filtered to
     * usable entries — products without a name or without nutriments are
     * dropped by the adapter, see `OpenFoodFactsAdapter.searchByQuery`).
     *
     * Throws on transport failures so the use-case can surface its
     * "OpenFoodFacts ist gerade nicht erreichbar" message. Adapter MUST
     * preserve the existing exception message contract (substring match
     * `"OpenFoodFacts ist gerade nicht erreichbar"`) to keep
     * `SearchFoodsRemoteUseCase` error branching unchanged.
     */
    suspend fun searchByQuery(query: String, pageSize: Int = 20): List<RemoteFoodResult>

    /**
     * Barcode (EAN/UPC) lookup. Returns `null` when OpenFoodFacts has no
     * matching product (status != 1 or product_name missing). All-zero
     * macros are signalled by [RemoteBarcodeProduct] with zeroed fields —
     * the fallback-table augmentation lives in `LookupBarcodeUseCase`.
     */
    suspend fun lookupBarcode(barcode: String): RemoteBarcodeProduct?
}

/**
 * Domain row for a search hit. Previously lived inside
 * `SearchFoodsRemoteUseCase` as a nested class — promoted to top-level so
 * the port type signature carries no use-case-internal nesting.
 *
 * Field set is unchanged from the previous nested
 * `SearchFoodsRemoteUseCase.RemoteFoodResult` so call-sites in
 * `FoodEntryViewModel`, `RecipeCreationViewModel`, the Android
 * `NutritionFoodEntryScreen` and iOS `NutritionFoodEntryView` keep
 * working via a type alias (see [SearchFoodsRemoteUseCase]).
 */
data class RemoteFoodResult(
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val sugar: Double,
    val brand: String? = null,
    val nutriScore: String? = null
)

/**
 * Domain row for a barcode hit. Pure macros + product name; the OpenFoodFacts
 * `status` field is folded into the `null` return from `lookupBarcode` (the
 * use-case never sees the raw status integer).
 */
data class RemoteBarcodeProduct(
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sugar: Double
)

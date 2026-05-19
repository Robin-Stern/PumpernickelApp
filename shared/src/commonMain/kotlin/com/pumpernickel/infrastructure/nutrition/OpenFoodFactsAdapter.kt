package com.pumpernickel.infrastructure.nutrition

import com.pumpernickel.data.api.OpenFoodFactsApi
import com.pumpernickel.data.api.SearchProductDto
import com.pumpernickel.domain.nutrition.RemoteBarcodeProduct
import com.pumpernickel.domain.nutrition.RemoteFoodResult
import com.pumpernickel.domain.nutrition.RemoteFoodSearchClient

/**
 * D-21-04 step 3 — local brand-match re-ranking.
 *
 * Compute a relevance score per product for the supplied (already-tokenised)
 * query. Score formula:
 *   - +2 per query word that equals any brand-token (case-insensitive,
 *     whole-token equality after splitting brands on comma/whitespace —
 *     "Alpro Soja" → ["alpro", "soja"]; substring matches like
 *     "alpro" ⊂ "alproletariat" do NOT count)
 *   - +1 per query word found (case-insensitive substring) in product_name
 *   - +1 per query word found (case-insensitive substring) in generic_name
 *
 * `internal` so commonTest can exercise the scorer directly without spinning
 * up an `OpenFoodFactsApi` + Ktor `HttpClient`.
 */
internal fun scoreResult(
    productName: String?,
    genericName: String?,
    brands: List<String>?,
    queryTokens: List<String>
): Int {
    if (queryTokens.isEmpty()) return 0
    val nameLower = productName.orEmpty().lowercase()
    val genericLower = genericName.orEmpty().lowercase()
    // Brand entries on OFF can themselves contain multiple sub-tokens
    // (e.g. "gut & günstig" → ["gut", "&", "günstig"]). Split each entry on
    // comma and whitespace, lowercase, and drop blanks.
    val brandTokens = brands.orEmpty()
        .flatMap { it.split(',', ' ', '\t') }
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
    var score = 0
    for (q in queryTokens) {
        if (brandTokens.contains(q)) score += 2
        if (nameLower.contains(q)) score += 1
        if (genericLower.contains(q)) score += 1
    }
    return score
}

/**
 * D-21-04 step 3 — apply [scoreResult] to a server-returned product list and
 * return a stable, descending-score-sorted copy. Query tokens are lowercased,
 * split on whitespace, and filtered to length >= 2 (so noise tokens like
 * "a", "1" don't trigger spurious matches).
 *
 * Stable sort: ties preserve OFF's server order (which is already
 * relevance-ranked since D-21-04 step 2 removed `sort_by=unique_scans_n`).
 *
 * `internal` for the same testability reason as [scoreResult].
 */
internal fun rankProducts(products: List<SearchProductDto>, query: String): List<SearchProductDto> {
    if (products.isEmpty()) return products
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.isEmpty()) return products
    return products
        .mapIndexed { idx, dto ->
            Triple(idx, dto, scoreResult(dto.productName, dto.genericName, dto.brands, tokens))
        }
        .sortedWith(
            compareByDescending<Triple<Int, SearchProductDto, Int>> { it.third }
                .thenBy { it.first }
        )
        .map { it.second }
}

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
        // D-21-04 step 3 — re-rank the server hits by brand-match score BEFORE mapping
        // to RemoteFoodResult so the brand boost survives the domain projection.
        val ranked = rankProducts(response.products, query)
        return ranked.mapNotNull { product ->
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
        // D-21-03 trace — raw OFF response, BEFORE the null-coalescing fold.
        println(
            "[B2] adapter raw barcode=$barcode status=${response.status} name=$productName " +
                "kcal=${product?.nutriments?.energyKcal100g} " +
                "protein=${product?.nutriments?.proteins100g} " +
                "carbs=${product?.nutriments?.carbohydrates100g} " +
                "fat=${product?.nutriments?.fat100g}"
        )
        if (response.status != 1 || productName.isNullOrBlank()) {
            println("[B2] adapter rejecting barcode=$barcode status=${response.status} name='$productName'")
            return null
        }

        val nutriments = product.nutriments
        val mapped = RemoteBarcodeProduct(
            name = productName,
            calories = nutriments?.energyKcal100g ?: 0.0,
            protein = nutriments?.proteins100g ?: 0.0,
            fat = nutriments?.fat100g ?: 0.0,
            carbohydrates = nutriments?.carbohydrates100g ?: 0.0,
            sugar = nutriments?.sugars100g ?: 0.0
        )
        // D-21-03 trace — domain projection after null-coalescing.
        println(
            "[B2] adapter mapped barcode=$barcode name=${mapped.name} " +
                "kcal=${mapped.calories} protein=${mapped.protein} " +
                "carbs=${mapped.carbohydrates} fat=${mapped.fat} sugar=${mapped.sugar}"
        )
        return mapped
    }
}

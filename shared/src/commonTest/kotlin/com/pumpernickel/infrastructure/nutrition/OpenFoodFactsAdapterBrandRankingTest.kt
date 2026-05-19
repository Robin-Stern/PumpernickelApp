package com.pumpernickel.infrastructure.nutrition

import com.pumpernickel.data.api.NutrimentsDto
import com.pumpernickel.data.api.SearchProductDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 21 Plan 03 — B3: Brand-match re-ranking unit tests.
 *
 * Validates D-21-04 step 3 ("brand match → +2, name match → +1"):
 *   - `scoreResult` computes a deterministic score per product against tokenised query
 *   - `rankProducts` sorts a hit list in descending score order, stable for ties
 *
 * The three CONTEXT.md spec queries are encoded as separate cases so a regression
 * in the scorer can be traced to the exact failing query.
 */
class OpenFoodFactsAdapterBrandRankingTest {

    // ---------- scoreResult ----------

    @Test
    fun scoreResult_brandMatchScoresTwo() {
        val score = scoreResult(
            productName = "Some Random Cereal",
            genericName = null,
            brands = listOf("Alpro"),
            queryTokens = listOf("alpro")
        )
        // brand-token "alpro" matches query word "alpro" → +2 (name does NOT contain "alpro")
        assertEquals(2, score)
    }

    @Test
    fun scoreResult_nameMatchScoresOne() {
        val score = scoreResult(
            productName = "Chips Ungarisch Paprika",
            genericName = null,
            brands = listOf("Funny Frisch"),
            queryTokens = listOf("chips")
        )
        // name contains "chips" → +1; brands do NOT contain "chips"
        assertEquals(1, score)
    }

    @Test
    fun scoreResult_genericNameMatchScoresOne() {
        val score = scoreResult(
            productName = "Naturjoghurt 3,8%",
            genericName = "Joghurt aus pasteurisierter Milch",
            brands = listOf("Andechser"),
            queryTokens = listOf("joghurt")
        )
        // name does NOT contain "joghurt" (it contains "Naturjoghurt" — substring match yes!)
        // Actually "naturjoghurt".contains("joghurt") == true → +1 from name as well
        // + generic_name contains "joghurt" → +1 → total 2
        // Brand "Andechser" does NOT contain "joghurt" → +0
        assertEquals(2, score)
    }

    @Test
    fun scoreResult_brandTokenIsExactMatchNotSubstring() {
        // "alproletariat" should NOT match query "alpro" via brand path
        val score = scoreResult(
            productName = "Random",
            genericName = null,
            brands = listOf("alproletariat"),
            queryTokens = listOf("alpro")
        )
        // brand-token comparison is whole-token-equality (after lowercase/split on comma+space),
        // so "alproletariat" != "alpro" → no brand match. Name "random" doesn't contain "alpro".
        assertEquals(0, score)
    }

    @Test
    fun scoreResult_multiTokenBrandsAreSplit() {
        // Brand entry "gut & günstig" should produce tokens ["gut", "&", "günstig"]
        // (after splitting on comma/whitespace) — both "gut" and "günstig" should match
        val score = scoreResult(
            productName = "Sprühsahne",
            genericName = null,
            brands = listOf("gut & günstig"),
            queryTokens = listOf("walnüsse", "gut", "günstig")
        )
        // brand "gut" matches +2, brand "günstig" matches +2, "walnüsse" doesn't match → 4
        assertEquals(4, score)
    }

    @Test
    fun scoreResult_emptyQueryReturnsZero() {
        val score = scoreResult(
            productName = "Anything",
            genericName = null,
            brands = listOf("Whatever"),
            queryTokens = emptyList()
        )
        assertEquals(0, score)
    }

    @Test
    fun scoreResult_nullProductNameAndBrandsHandledGracefully() {
        val score = scoreResult(
            productName = null,
            genericName = null,
            brands = null,
            queryTokens = listOf("anything")
        )
        assertEquals(0, score)
    }

    // ---------- rankProducts ----------

    @Test
    fun rankProducts_chipsUngarischPromotesFunnyFrisch() {
        val products = listOf(
            SearchProductDto(
                productName = "Tue Gut Form",
                brands = listOf("Tue Gut"),
                nutriments = NutrimentsDto(energyKcal100g = 100.0)
            ),
            SearchProductDto(
                productName = "Chips Ungarisch",
                brands = listOf("Funny Frisch"),
                nutriments = NutrimentsDto(energyKcal100g = 500.0)
            )
        )
        val ranked = rankProducts(products, "Chips ungarisch")
        assertEquals("Chips Ungarisch", ranked[0].productName)
        assertEquals("Tue Gut Form", ranked[1].productName)
    }

    @Test
    fun rankProducts_walnusseGutGunstigPromotesWalnusse() {
        val products = listOf(
            SearchProductDto(
                productName = "Sprühsahne",
                brands = listOf("gut & günstig"),
                nutriments = NutrimentsDto(energyKcal100g = 200.0)
            ),
            SearchProductDto(
                productName = "Walnüsse",
                brands = listOf("gut & günstig"),
                nutriments = NutrimentsDto(energyKcal100g = 700.0)
            )
        )
        val ranked = rankProducts(products, "Walnüsse gut und günstig")
        // Walnüsse score: name match "walnüsse" (+1) + brand match "gut" (+2) + brand match "günstig" (+2) = 5
        // Sprühsahne score: brand match "gut" (+2) + brand match "günstig" (+2) = 4
        // ("und" is filtered by length>=2 check? "und" has length 3 → kept, but no match anywhere)
        assertEquals("Walnüsse", ranked[0].productName)
        assertEquals("Sprühsahne", ranked[1].productName)
    }

    @Test
    fun rankProducts_singleResultIsReturnedUnchanged() {
        val products = listOf(
            SearchProductDto(
                productName = "Joghurt natur",
                brands = listOf("Alpro"),
                nutriments = NutrimentsDto(energyKcal100g = 60.0)
            )
        )
        val ranked = rankProducts(products, "Joghurt")
        assertEquals(1, ranked.size)
        assertEquals("Joghurt natur", ranked[0].productName)
    }

    @Test
    fun rankProducts_stableSortPreservesServerOrderOnTies() {
        val products = listOf(
            SearchProductDto(productName = "Apfel A", brands = listOf("BrandA"), nutriments = NutrimentsDto()),
            SearchProductDto(productName = "Apfel B", brands = listOf("BrandB"), nutriments = NutrimentsDto()),
            SearchProductDto(productName = "Apfel C", brands = listOf("BrandC"), nutriments = NutrimentsDto())
        )
        // Query token "apfel" matches all three name fields → score 1 each → tie
        val ranked = rankProducts(products, "Apfel")
        assertEquals("Apfel A", ranked[0].productName)
        assertEquals("Apfel B", ranked[1].productName)
        assertEquals("Apfel C", ranked[2].productName)
    }

    @Test
    fun rankProducts_shortQueryTokensFilteredOut() {
        // Tokens of length < 2 should be skipped (e.g. "a", "1") to avoid noise matches
        val products = listOf(
            SearchProductDto(productName = "Banane", brands = listOf("Chiquita"), nutriments = NutrimentsDto()),
            SearchProductDto(productName = "Apfel", brands = listOf("a"), nutriments = NutrimentsDto())
        )
        // Query "a banane" → token "a" filtered → only "banane" remains → Banane scores +1, Apfel scores 0
        val ranked = rankProducts(products, "a banane")
        assertEquals("Banane", ranked[0].productName)
    }

    @Test
    fun rankProducts_emptyListReturnsEmpty() {
        val ranked = rankProducts(emptyList(), "anything")
        assertTrue(ranked.isEmpty())
    }
}

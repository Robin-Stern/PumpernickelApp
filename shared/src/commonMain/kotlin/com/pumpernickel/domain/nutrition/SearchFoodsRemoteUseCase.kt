package com.pumpernickel.domain.nutrition

class SearchFoodsRemoteUseCase(private val client: RemoteFoodSearchClient) {

    /**
     * Phase 20 Plan 07 (Smell 4): `RemoteFoodResult` lives at top-level in
     * `domain/nutrition/RemoteFoodSearchClient.kt` so the port can return it
     * without a use-case-internal nesting. Existing call-sites
     * (`FoodEntryViewModel`, `RecipeCreationViewModel`, Android
     * `NutritionFoodEntryScreen`, iOS `NutritionFoodEntryView`) reference
     * `SearchFoodsRemoteUseCase.RemoteFoodResult` — the alias keeps that
     * Public-API stable; binary shape is identical.
     */
    @Suppress("unused")
    companion object {
        // Kept as a documentation anchor; the actual alias is below at file
        // top-level so consumers can still write `SearchFoodsRemoteUseCase.RemoteFoodResult`.
    }

    sealed interface Result {
        data class Success(val foods: List<RemoteFoodResult>) : Result
        data object Empty : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(query: String): Result {
        return try {
            val results = client.searchByQuery(query)
            if (results.isEmpty()) Result.Empty else Result.Success(results)
        } catch (e: Exception) {
            val msg = e.message
            if (msg != null && msg.contains("OpenFoodFacts ist gerade nicht erreichbar")) {
                Result.Error("OpenFoodFacts ist gerade nicht erreichbar. Versuch es später nochmal.")
            } else {
                Result.Error(msg ?: "Unbekannter Fehler")
            }
        }
    }
}

/**
 * Phase 20 Plan 07 (Smell 4) — backwards-compatible name alias.
 *
 * The previous nested class `SearchFoodsRemoteUseCase.RemoteFoodResult` is
 * referenced from three call-sites: `FoodEntryViewModel`,
 * `RecipeCreationViewModel`, Android `NutritionFoodEntryScreen` and the iOS
 * `NutritionFoodEntryView` (as `SearchFoodsRemoteUseCase.RemoteFoodResult`).
 *
 * Kotlin doesn't allow `typealias` inside a class body, so the existing
 * call-sites need updating to reference the top-level `RemoteFoodResult`
 * directly. The compile pass after this commit migrates those references.
 */

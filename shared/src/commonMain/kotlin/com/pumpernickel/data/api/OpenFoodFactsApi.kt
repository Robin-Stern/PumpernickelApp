package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class OpenFoodFactsApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun lookupBarcode(barcode: String): OpenFoodFactsResponse {
        val responseText = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json") {
            header("User-Agent", "PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)")
        }.bodyAsText()
        return json.decodeFromString(responseText)
    }

    suspend fun searchByName(query: String, pageSize: Int = 20): OpenFoodFactsSearchResponse {
        println("[OFF] searchByName query='$query' pageSize=$pageSize")
        val responseText = client.get("https://search.openfoodfacts.org/search") {
            header("User-Agent", "PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)")
            parameter("q", query)
            // D-21-04 step 1 — request generic_name + categories_tags so the server can match
            // the query in more fields (and so the adapter's re-ranker can read them).
            parameter("fields", "product_name,generic_name,brands,categories_tags,nutriments,nutrition_grade_fr")
            parameter("page_size", pageSize.toString())
            // D-21-04 — sort_by: default server relevance (parameter omitted) — empirically beats
            // the previous scan-count strategy AND `popularity_key` for German queries like
            // "Chips ungarisch", "Walnüsse gut und günstig", "Joghurt" where scan-count drowns
            // out relevance (it surfaced "Tue Gut Form" before "Funny Frisch Chips Ungarisch").
            // Adapter-side brand-match re-ranking (scoreResult) handles fine-tuning.
        }.bodyAsText()
        println("[OFF] response received bytes=${responseText.length} preview='${responseText.take(120)}'")
        if (responseText.trimStart().startsWith("<")) {
            println("[OFF] HTML detected — throwing IllegalStateException")
            throw IllegalStateException("OpenFoodFacts ist gerade nicht erreichbar.")
        }
        println("[OFF] decoding JSON…")
        val v2 = json.decodeFromString<OpenFoodFactsSearchV2Response>(responseText)
        return OpenFoodFactsSearchResponse(count = v2.count, products = v2.hits)
    }
}

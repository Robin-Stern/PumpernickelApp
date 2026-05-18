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
            parameter("fields", "product_name,brands,nutriments,nutrition_grade_fr")
            parameter("page_size", pageSize.toString())
            parameter("sort_by", "unique_scans_n")
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

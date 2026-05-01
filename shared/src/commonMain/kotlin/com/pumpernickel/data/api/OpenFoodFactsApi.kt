package com.pumpernickel.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class OpenFoodFactsApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupBarcode(barcode: String): OpenFoodFactsResponse {
        val responseText = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json") {
            header("User-Agent", "PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)")
        }.bodyAsText()
        return json.decodeFromString(responseText)
    }

    suspend fun searchByName(query: String, pageSize: Int = 20): OpenFoodFactsSearchResponse {
        val responseText = client.get("https://world.openfoodfacts.org/cgi/search.pl") {
            header("User-Agent", "PumpernickelApp/1.0 (Android/iOS; contact@pumpernickel.app)")
            parameter("search_terms", query)
            parameter("search_simple", "1")
            parameter("action", "process")
            parameter("json", "1")
            parameter("fields", "product_name,nutriments")
            parameter("page_size", pageSize.toString())
        }.bodyAsText()
        return json.decodeFromString(responseText)
    }
}

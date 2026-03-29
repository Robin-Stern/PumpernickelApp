package com.example.pumpernickelapp

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FoodRepository {
    private val settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }

    fun saveFood(food: Food) {
        val current = loadFoods().toMutableList()
        current.add(food)
        settings.putString(KEY_FOODS, json.encodeToString(current))
    }

    fun loadFoods(): List<Food> {
        val raw = settings.getStringOrNull(KEY_FOODS) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    private companion object {
        const val KEY_FOODS = "foods"
    }
}

package com.example.pumpernickelapp

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
// Datensicherungsklasse für die Food Liste, Lokale Speicherung.
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

    fun saveRecipe(recipe: Food.Recipe) {
        val current = loadRecipes().toMutableList()
        current.add(recipe)
        settings.putString(KEY_RECIPES, json.encodeToString(current))
    }

    fun loadRecipes(): List<Food.Recipe> {
        val raw = settings.getStringOrNull(KEY_RECIPES) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    private companion object {
        const val KEY_FOODS = "foods"
        const val KEY_RECIPES = "recipes"
    }
}

package com.example.pumpernickelapp.food.data

import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

class FoodRepositoryImpl : FoodRepository {
    private val settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveFood(food: Food) {
        val current = loadFoods().toMutableList()
        current.add(food)
        settings.putString(KEY_FOODS, json.encodeToString(current))
    }

    override fun loadFoods(): List<Food> {
        val raw = settings.getStringOrNull(KEY_FOODS) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    override fun saveRecipe(recipe: Food.Recipe) {
        val current = loadRecipes().toMutableList()
        current.add(recipe)
        settings.putString(KEY_RECIPES, json.encodeToString(current))
    }

    override fun loadRecipes(): List<Food.Recipe> {
        val raw = settings.getStringOrNull(KEY_RECIPES) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    private companion object {
        const val KEY_FOODS = "foods"
        const val KEY_RECIPES = "recipes"
    }
}

@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.data

import com.example.pumpernickelapp.food.domain.ConsumptionEntry
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FoodRepositoryImpl : FoodRepository {
    private val settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveFood(food: Food) {
        val updated = loadFoods() + food
        settings.putString(KEY_FOODS, json.encodeToString(updated))
    }

    override fun loadFoods(): List<Food> {
        val raw = settings.getStringOrNull(KEY_FOODS) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    override fun saveRecipe(recipe: Food.Recipe) {
        val updated = loadRecipes() + recipe
        settings.putString(KEY_RECIPES, json.encodeToString(updated))
    }

    override fun loadRecipes(): List<Food.Recipe> {
        val raw = settings.getStringOrNull(KEY_RECIPES) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    override fun deleteFood(id: Uuid) {
        val updated = loadFoods().filter { it.id != id }
        settings.putString(KEY_FOODS, json.encodeToString(updated))
    }

    override fun updateFood(food: Food) {
        val updated = loadFoods().map { if (it.id == food.id) food else it }
        settings.putString(KEY_FOODS, json.encodeToString(updated))
    }

    override fun deleteRecipe(id: Uuid) {
        val updated = loadRecipes().filter { it.id != id }
        settings.putString(KEY_RECIPES, json.encodeToString(updated))
    }

    override fun updateRecipe(recipe: Food.Recipe) {
        val updated = loadRecipes().map { if (it.id == recipe.id) recipe else it }
        settings.putString(KEY_RECIPES, json.encodeToString(updated))
    }

    override fun saveConsumption(entry: ConsumptionEntry) {
        val updated = loadConsumptions() + entry
        settings.putString(KEY_CONSUMPTIONS, json.encodeToString(updated))
    }

    override fun loadConsumptions(): List<ConsumptionEntry> {
        val raw = settings.getStringOrNull(KEY_CONSUMPTIONS) ?: return emptyList()
        return json.decodeFromString(raw)
    }

    override fun deleteConsumption(id: Uuid) {
        val updated = loadConsumptions().filter { it.id != id }
        settings.putString(KEY_CONSUMPTIONS, json.encodeToString(updated))
    }

    private companion object {
        const val KEY_FOODS = "foods"
        const val KEY_RECIPES = "recipes"
        const val KEY_CONSUMPTIONS = "consumptions"
    }
}

package com.pumpernickel.data.repository

import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient

interface FoodRepository {
    suspend fun saveFood(food: Food)
    suspend fun loadFoods(): List<Food>
    suspend fun deleteFood(id: String)
    suspend fun updateFood(food: Food)

    suspend fun saveRecipe(recipe: Recipe)
    suspend fun loadRecipes(): List<Recipe>
    suspend fun deleteRecipe(id: String)
    suspend fun updateRecipe(recipe: Recipe)

    suspend fun saveConsumption(entry: ConsumptionEntry)
    suspend fun loadConsumptions(): List<ConsumptionEntry>
    suspend fun deleteConsumption(id: String)
}

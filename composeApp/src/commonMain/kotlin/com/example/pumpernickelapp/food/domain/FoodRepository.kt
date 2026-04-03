@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface FoodRepository {
    fun saveFood(food: Food)
    fun loadFoods(): List<Food>
    fun saveRecipe(recipe: Food.Recipe)
    fun loadRecipes(): List<Food.Recipe>
    fun deleteFood(id: Uuid)
    fun deleteRecipe(id: Uuid)
    fun updateRecipe(recipe: Food.Recipe)
}

package com.example.pumpernickelapp.food.domain

interface FoodRepository {
    fun saveFood(food: Food)
    fun loadFoods(): List<Food>
    fun saveRecipe(recipe: Food.Recipe)
    fun loadRecipes(): List<Food.Recipe>
}

package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface NutritionDao {
    // Foods
    @Query("SELECT * FROM foods ORDER BY name ASC")
    suspend fun getAllFoods(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity)

    @Update
    suspend fun updateFood(food: FoodEntity)

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: String)

    // Recipes
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, name ASC")
    suspend fun getAllRecipes(): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: String)

    // Recipe Ingredients
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipe(recipeId: String): List<RecipeIngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: String)

    // Consumption Entries
    @Query("SELECT * FROM consumption_entries ORDER BY timestampMillis DESC")
    suspend fun getAllConsumptions(): List<ConsumptionEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumption(entry: ConsumptionEntryEntity)

    @Query("DELETE FROM consumption_entries WHERE id = :id")
    suspend fun deleteConsumption(id: String)
}

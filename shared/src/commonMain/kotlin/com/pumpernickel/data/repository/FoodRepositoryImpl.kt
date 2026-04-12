package com.pumpernickel.data.repository

import com.pumpernickel.data.db.ConsumptionEntryEntity
import com.pumpernickel.data.db.FoodEntity
import com.pumpernickel.data.db.NutritionDao
import com.pumpernickel.data.db.NutritionDataSeeder
import com.pumpernickel.data.db.RecipeEntity
import com.pumpernickel.data.db.RecipeIngredientEntity
import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FoodRepositoryImpl(
    private val dao: NutritionDao,
    private val seeder: NutritionDataSeeder
) : FoodRepository {

    private val seedMutex = Mutex()
    private var seeded = false

    private suspend fun ensureSeeded() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            seeder.seedIfEmpty()
            seeded = true
        }
    }

    // -- Foods --

    override suspend fun saveFood(food: Food) {
        dao.insertFood(food.toEntity())
    }

    override suspend fun loadFoods(): List<Food> {
        ensureSeeded()
        return dao.getAllFoods().map { it.toDomain() }
    }

    override suspend fun deleteFood(id: String) {
        dao.deleteFood(id)
    }

    override suspend fun updateFood(food: Food) {
        dao.updateFood(food.toEntity())
    }

    // -- Recipes --

    override suspend fun saveRecipe(recipe: Recipe) {
        dao.insertRecipe(RecipeEntity(id = recipe.id, name = recipe.name, isFavorite = recipe.isFavorite))
        dao.insertIngredients(recipe.ingredients.map {
            RecipeIngredientEntity(recipeId = recipe.id, foodId = it.foodId, amountGrams = it.amountGrams)
        })
    }

    override suspend fun loadRecipes(): List<Recipe> {
        val entities = dao.getAllRecipes()
        return entities.map { entity ->
            val ingredients = dao.getIngredientsForRecipe(entity.id)
            Recipe(
                id = entity.id,
                name = entity.name,
                isFavorite = entity.isFavorite,
                ingredients = ingredients.map { RecipeIngredient(foodId = it.foodId, amountGrams = it.amountGrams) }
            )
        }
    }

    override suspend fun deleteRecipe(id: String) {
        dao.deleteIngredientsForRecipe(id)
        dao.deleteRecipe(id)
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        dao.updateRecipe(RecipeEntity(id = recipe.id, name = recipe.name, isFavorite = recipe.isFavorite))
        dao.deleteIngredientsForRecipe(recipe.id)
        dao.insertIngredients(recipe.ingredients.map {
            RecipeIngredientEntity(recipeId = recipe.id, foodId = it.foodId, amountGrams = it.amountGrams)
        })
    }

    // -- Consumption --

    override suspend fun saveConsumption(entry: ConsumptionEntry) {
        dao.insertConsumption(entry.toEntity())
    }

    override suspend fun loadConsumptions(): List<ConsumptionEntry> =
        dao.getAllConsumptions().map { it.toDomain() }

    override suspend fun deleteConsumption(id: String) {
        dao.deleteConsumption(id)
    }

    // -- Mappers --

    private fun Food.toEntity() = FoodEntity(
        id = id, name = name, calories = calories, protein = protein,
        fat = fat, carbohydrates = carbohydrates, sugar = sugar,
        unit = unit.name, isRecipe = isRecipe, barcode = barcode
    )

    private fun FoodEntity.toDomain() = Food(
        id = id, name = name, calories = calories, protein = protein,
        fat = fat, carbohydrates = carbohydrates, sugar = sugar,
        unit = FoodUnit.valueOf(unit), isRecipe = isRecipe, barcode = barcode
    )

    private fun ConsumptionEntry.toEntity() = ConsumptionEntryEntity(
        id = id, foodId = foodId, name = name,
        caloriesPer100 = caloriesPer100, proteinPer100 = proteinPer100,
        fatPer100 = fatPer100, carbsPer100 = carbsPer100, sugarPer100 = sugarPer100,
        unit = unit.name, amount = amount, timestampMillis = timestampMillis
    )

    private fun ConsumptionEntryEntity.toDomain() = ConsumptionEntry(
        id = id, foodId = foodId, name = name,
        caloriesPer100 = caloriesPer100, proteinPer100 = proteinPer100,
        fatPer100 = fatPer100, carbsPer100 = carbsPer100, sugarPer100 = sugarPer100,
        unit = FoodUnit.valueOf(unit), amount = amount, timestampMillis = timestampMillis
    )
}

package com.pumpernickel.data.db

class NutritionDataSeeder(private val dao: NutritionDao) {
    suspend fun seedIfEmpty() {
        if (dao.getAllFoods().isNotEmpty()) return

        val foods = listOf(
            FoodEntity(id = "00000000-0000-0000-0000-000000000001", name = "Ei (gekocht)", calories = 155.0, protein = 13.0, fat = 11.0, carbohydrates = 1.1, sugar = 1.1, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000002", name = "Haferflocken", calories = 370.0, protein = 13.0, fat = 7.0, carbohydrates = 59.0, sugar = 1.1, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000003", name = "Vollmilch", calories = 64.0, protein = 3.3, fat = 3.5, carbohydrates = 4.8, sugar = 4.8, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000004", name = "Banane", calories = 89.0, protein = 1.1, fat = 0.3, carbohydrates = 23.0, sugar = 12.0, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000005", name = "Hähnchenbrustfilet", calories = 110.0, protein = 23.0, fat = 1.8, carbohydrates = 0.0, sugar = 0.0, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000006", name = "Reis (gekocht)", calories = 130.0, protein = 2.7, fat = 0.3, carbohydrates = 28.0, sugar = 0.0, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000007", name = "Brokkoli", calories = 34.0, protein = 2.8, fat = 0.4, carbohydrates = 7.0, sugar = 1.7, unit = "GRAM", isRecipe = false, barcode = null),
            FoodEntity(id = "00000000-0000-0000-0000-000000000008", name = "Honig", calories = 304.0, protein = 0.3, fat = 0.0, carbohydrates = 82.0, sugar = 82.0, unit = "GRAM", isRecipe = false, barcode = null),
        )
        foods.forEach { dao.insertFood(it) }

        dao.insertRecipe(RecipeEntity(id = "10000000-0000-0000-0000-000000000001", name = "Porridge mit Banane", isFavorite = false))
        dao.insertIngredients(listOf(
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000001", foodId = "00000000-0000-0000-0000-000000000002", amountGrams = 80.0),
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000001", foodId = "00000000-0000-0000-0000-000000000003", amountGrams = 200.0),
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000001", foodId = "00000000-0000-0000-0000-000000000004", amountGrams = 100.0),
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000001", foodId = "00000000-0000-0000-0000-000000000008", amountGrams = 10.0),
        ))

        dao.insertRecipe(RecipeEntity(id = "10000000-0000-0000-0000-000000000002", name = "Fitness-Teller", isFavorite = false))
        dao.insertIngredients(listOf(
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000002", foodId = "00000000-0000-0000-0000-000000000005", amountGrams = 150.0),
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000002", foodId = "00000000-0000-0000-0000-000000000006", amountGrams = 200.0),
            RecipeIngredientEntity(recipeId = "10000000-0000-0000-0000-000000000002", foodId = "00000000-0000-0000-0000-000000000007", amountGrams = 150.0),
        ))
    }
}

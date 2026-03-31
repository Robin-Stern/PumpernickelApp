@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.data

import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.RecipeIngredient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun seedDemoDataIfEmpty(repository: FoodRepository) {
    if (repository.loadFoods().isNotEmpty()) return

    val ei = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        name = "Ei (gekocht)",
        calories = 155.0, protein = 13.0, fat = 11.0,
        carbohydrates = 1.1, sugar = 1.1
    )
    val haferflocken = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        name = "Haferflocken",
        calories = 370.0, protein = 13.0, fat = 7.0,
        carbohydrates = 59.0, sugar = 1.1
    )
    val milch = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
        name = "Vollmilch",
        calories = 64.0, protein = 3.3, fat = 3.5,
        carbohydrates = 4.8, sugar = 4.8
    )
    val banane = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000004"),
        name = "Banane",
        calories = 89.0, protein = 1.1, fat = 0.3,
        carbohydrates = 23.0, sugar = 12.0
    )
    val haehnchenBrust = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000005"),
        name = "Hähnchenbrustfilet",
        calories = 110.0, protein = 23.0, fat = 1.8,
        carbohydrates = 0.0, sugar = 0.0
    )
    val reis = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000006"),
        name = "Reis (gekocht)",
        calories = 130.0, protein = 2.7, fat = 0.3,
        carbohydrates = 28.0, sugar = 0.0
    )
    val brokkoli = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000007"),
        name = "Brokkoli",
        calories = 34.0, protein = 2.8, fat = 0.4,
        carbohydrates = 7.0, sugar = 1.7
    )
    val honig = Food(
        id = Uuid.parse("00000000-0000-0000-0000-000000000008"),
        name = "Honig",
        calories = 304.0, protein = 0.3, fat = 0.0,
        carbohydrates = 82.0, sugar = 82.0
    )

    listOf(ei, haferflocken, milch, banane, haehnchenBrust, reis, brokkoli, honig)
        .forEach { repository.saveFood(it) }

    val porridge = Food.Recipe(
        id = Uuid.parse("10000000-0000-0000-0000-000000000001"),
        name = "Porridge mit Banane",
        ingredients = listOf(
            RecipeIngredient(foodId = haferflocken.id, amountGrams = 80.0),
            RecipeIngredient(foodId = milch.id, amountGrams = 200.0),
            RecipeIngredient(foodId = banane.id, amountGrams = 100.0),
            RecipeIngredient(foodId = honig.id, amountGrams = 10.0)
        )
    )
    val fitnessTeller = Food.Recipe(
        id = Uuid.parse("10000000-0000-0000-0000-000000000002"),
        name = "Fitness-Teller",
        ingredients = listOf(
            RecipeIngredient(foodId = haehnchenBrust.id, amountGrams = 150.0),
            RecipeIngredient(foodId = reis.id, amountGrams = 200.0),
            RecipeIngredient(foodId = brokkoli.id, amountGrams = 150.0)
        )
    )

    listOf(porridge, fitnessTeller).forEach { repository.saveRecipe(it) }
}

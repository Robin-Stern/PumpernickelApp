package com.pumpernickel.domain.model

data class RecipeMacros(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0
)

fun calculateMacros(ingredients: List<Pair<Food, Double>>): RecipeMacros = RecipeMacros(
    calories = ingredients.sumOf { (food, factor) -> food.calories * factor },
    protein  = ingredients.sumOf { (food, factor) -> food.protein * factor },
    fat      = ingredients.sumOf { (food, factor) -> food.fat * factor },
    carbs    = ingredients.sumOf { (food, factor) -> food.carbohydrates * factor },
    sugar    = ingredients.sumOf { (food, factor) -> food.sugar * factor }
)

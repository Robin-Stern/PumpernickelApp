package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi

class AddFoodUseCase(
    private val repository: FoodRepository,
    private val validate: ValidateFoodInputUseCase
) {
    sealed interface Result {
        data class Error(val message: String) : Result
        data object Success : Result
    }

    @OptIn(ExperimentalUuidApi::class)
    operator fun invoke(
        name: String,
        calories: String,
        protein: String,
        fat: String,
        carbs: String,
        sugar: String,
        barcode: String
    ): Result {
        return when (val validation = validate(name, calories, protein, fat, carbs, sugar)) {
            is ValidateFoodInputUseCase.Result.Error -> Result.Error(validation.message)
            is ValidateFoodInputUseCase.Result.Valid -> {
                repository.saveFood(
                    Food(
                        name          = name.trim(),
                        calories      = validation.calories,
                        protein       = validation.protein,
                        fat           = validation.fat,
                        carbohydrates = validation.carbs,
                        sugar         = validation.sugar,
                        barcode       = barcode.trim().ifBlank { null }
                    )
                )
                Result.Success
            }
        }
    }
}

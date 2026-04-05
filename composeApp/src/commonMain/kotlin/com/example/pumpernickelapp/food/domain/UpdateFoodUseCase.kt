@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class UpdateFoodUseCase(
    private val repository: FoodRepository,
    private val validate: ValidateFoodInputUseCase
) {
    sealed interface Result {
        data class Error(val message: String) : Result
        data object Success : Result
    }

    operator fun invoke(
        id: Uuid,
        name: String,
        calories: String,
        protein: String,
        fat: String,
        carbs: String,
        sugar: String,
        barcode: String,
        unit: FoodUnit = FoodUnit.GRAM
    ): Result {
        return when (val validation = validate(name, calories, protein, fat, carbs, sugar)) {
            is ValidateFoodInputUseCase.Result.Error -> Result.Error(validation.message)
            is ValidateFoodInputUseCase.Result.Valid -> {
                repository.updateFood(
                    Food(
                        id            = id,
                        name          = name.trim(),
                        calories      = validation.calories,
                        protein       = validation.protein,
                        fat           = validation.fat,
                        carbohydrates = validation.carbs,
                        sugar         = validation.sugar,
                        unit          = unit,
                        barcode       = barcode.trim().ifBlank { null }
                    )
                )
                Result.Success
            }
        }
    }
}

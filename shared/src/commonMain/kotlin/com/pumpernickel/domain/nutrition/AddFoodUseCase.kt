package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit

class AddFoodUseCase(
    private val repository: FoodRepository,
    private val validate: ValidateFoodInputUseCase
) {
    sealed interface Result {
        data class Error(val message: String) : Result
        data object Success : Result
    }

    suspend operator fun invoke(
        name: String, calories: String, protein: String,
        fat: String, carbs: String, sugar: String,
        barcode: String, unit: FoodUnit = FoodUnit.GRAM
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
                        unit          = unit,
                        barcode       = barcode.trim().ifBlank { null }
                    )
                )
                Result.Success
            }
        }
    }
}

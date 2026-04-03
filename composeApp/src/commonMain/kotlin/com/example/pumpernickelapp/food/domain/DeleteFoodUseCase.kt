@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi

class DeleteFoodUseCase(private val repository: FoodRepository) {
    operator fun invoke(food: Food) = repository.deleteFood(food.id)
}

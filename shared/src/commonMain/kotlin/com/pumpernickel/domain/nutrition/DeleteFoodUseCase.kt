package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.Food

class DeleteFoodUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(food: Food) = repository.deleteFood(food.id)
}

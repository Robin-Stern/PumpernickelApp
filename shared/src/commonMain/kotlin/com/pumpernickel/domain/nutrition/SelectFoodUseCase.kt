package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.repository.FoodRepository
import com.pumpernickel.domain.model.Food

class SelectFoodUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(food: Food): Food {
        if (food.source == "openfoodfacts") {
            repository.saveFood(food)
        }
        return food
    }
}

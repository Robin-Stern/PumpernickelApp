package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.repository.FoodRepository
import com.pumpernickel.domain.model.Food

class LoadFoodsUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(): List<Food> = repository.loadFoods()
}

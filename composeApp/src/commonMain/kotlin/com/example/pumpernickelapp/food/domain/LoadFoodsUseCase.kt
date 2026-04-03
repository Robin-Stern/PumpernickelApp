package com.example.pumpernickelapp.food.domain

class LoadFoodsUseCase(private val repository: FoodRepository) {
    operator fun invoke(): List<Food> = repository.loadFoods()
}

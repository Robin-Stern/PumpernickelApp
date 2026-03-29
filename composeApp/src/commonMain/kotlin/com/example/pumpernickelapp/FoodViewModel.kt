package com.example.pumpernickelapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FoodViewModel : ViewModel() {
    private val repository = FoodRepository()

    private val _foods = MutableStateFlow(repository.loadFoods())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    fun addFood(food: Food) {
        repository.saveFood(food)
        _foods.value = repository.loadFoods()
    }
}

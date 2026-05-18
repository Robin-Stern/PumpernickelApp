package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.repository.FoodRepository

class DeleteConsumptionUseCase(
    private val repository: FoodRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteConsumption(id)
    }
}

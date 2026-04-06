@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DeleteConsumptionUseCase(
    private val repository: FoodRepository
) {
    operator fun invoke(id: Uuid) {
        repository.deleteConsumption(id)
    }
}

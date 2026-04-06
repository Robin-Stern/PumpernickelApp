package com.example.pumpernickelapp.food.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class LoadConsumptionsForDateUseCase(
    private val repository: FoodRepository
) {
    operator fun invoke(
        date: LocalDate,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): List<ConsumptionEntry> =
        repository.loadConsumptions()
            .filter { it.timestamp.toLocalDateTime(zone).date == date }
            .sortedBy { it.timestamp }
}

package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.ConsumptionEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

class LoadConsumptionsForDateUseCase(
    private val repository: FoodRepository
) {
    suspend operator fun invoke(
        date: LocalDate,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): List<ConsumptionEntry> =
        repository.loadConsumptions()
            .filter {
                Instant.fromEpochMilliseconds(it.timestampMillis)
                    .toLocalDateTime(zone).date == date
            }
            .sortedBy { it.timestampMillis }
}

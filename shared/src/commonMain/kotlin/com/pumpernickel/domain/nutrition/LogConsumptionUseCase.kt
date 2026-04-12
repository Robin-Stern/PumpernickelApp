package com.pumpernickel.domain.nutrition

import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import kotlin.time.Clock

class LogConsumptionUseCase(
    private val repository: FoodRepository
) {
    sealed interface Result {
        data class Success(val entry: ConsumptionEntry) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        food: Food, amount: Double,
        timestampMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): Result = persist { ConsumptionEntry.fromFood(food, amount, timestampMillis) }

    suspend fun logAdHoc(
        name: String, caloriesPer100: Double, proteinPer100: Double,
        fatPer100: Double, carbsPer100: Double, sugarPer100: Double,
        unit: FoodUnit, amount: Double,
        timestampMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): Result = persist {
        ConsumptionEntry(
            name = name.trim(), caloriesPer100 = caloriesPer100,
            proteinPer100 = proteinPer100, fatPer100 = fatPer100,
            carbsPer100 = carbsPer100, sugarPer100 = sugarPer100,
            unit = unit, amount = amount, timestampMillis = timestampMillis
        )
    }

    private suspend inline fun persist(build: () -> ConsumptionEntry): Result =
        try {
            val entry = build()
            repository.saveConsumption(entry)
            Result.Success(entry)
        } catch (e: IllegalArgumentException) {
            Result.Error(e.message ?: "Ungültige Eingabe")
        }
}

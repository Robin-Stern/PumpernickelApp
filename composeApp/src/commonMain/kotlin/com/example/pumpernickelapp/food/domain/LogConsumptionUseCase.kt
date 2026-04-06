@file:OptIn(ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.domain

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class LogConsumptionUseCase(
    private val repository: FoodRepository
) {
    sealed interface Result {
        data class Success(val entry: ConsumptionEntry) : Result
        data class Error(val message: String) : Result
    }

    /** Log a consumption backed by a saved Food. */
    operator fun invoke(
        food: Food,
        amount: Double,
        timestamp: Instant = Clock.System.now()
    ): Result = persist { ConsumptionEntry.fromFood(food, amount, timestamp) }

    /** Log an ad-hoc consumption (not backed by a saved Food). */
    fun logAdHoc(
        name: String,
        caloriesPer100: Double,
        proteinPer100: Double,
        fatPer100: Double,
        carbsPer100: Double,
        sugarPer100: Double,
        unit: FoodUnit,
        amount: Double,
        timestamp: Instant = Clock.System.now()
    ): Result = persist {
        ConsumptionEntry(
            name           = name.trim(),
            caloriesPer100 = caloriesPer100,
            proteinPer100  = proteinPer100,
            fatPer100      = fatPer100,
            carbsPer100    = carbsPer100,
            sugarPer100    = sugarPer100,
            unit           = unit,
            amount         = amount,
            timestamp      = timestamp
        )
    }

    private inline fun persist(build: () -> ConsumptionEntry): Result =
        try {
            val entry = build()
            repository.saveConsumption(entry)
            Result.Success(entry)
        } catch (e: IllegalArgumentException) {
            Result.Error(e.message ?: "Ungültige Eingabe")
        }
}

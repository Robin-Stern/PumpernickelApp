package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlin.math.roundToInt

/**
 * Pure TDEE / suggested-macros calculator.
 * Mifflin–St Jeor BMR (D-16-04) × standard activity multipliers (D-16-05).
 * Cut/Maintain/Bulk deltas per D-16-06; macro split per D-16-07.
 * No DB, no coroutines, no Koin.
 */
object TdeeCalculator {

    // D-16-05 — standard 5 activity tiers, multipliers in fixed order.
    private val ACTIVITY_MULTIPLIER: Map<ActivityLevel, Double> = mapOf(
        ActivityLevel.SEDENTARY         to 1.2,
        ActivityLevel.LIGHTLY_ACTIVE    to 1.375,
        ActivityLevel.MODERATELY_ACTIVE to 1.55,
        ActivityLevel.VERY_ACTIVE       to 1.725,
        ActivityLevel.EXTRA_ACTIVE      to 1.9
    )

    // D-16-04 — Mifflin–St Jeor.
    fun bmr(stats: UserPhysicalStats): Double {
        val sexConstant = when (stats.sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
        }
        return 10.0 * stats.weightKg + 6.25 * stats.heightCm - 5.0 * stats.age + sexConstant
    }

    fun tdee(stats: UserPhysicalStats): Double {
        val multiplier = ACTIVITY_MULTIPLIER.getValue(stats.activityLevel)
        return bmr(stats) * multiplier
    }

    // D-16-06 + D-16-07 — three suggestions derived from TDEE.
    fun suggestions(stats: UserPhysicalStats): TdeeSuggestions {
        val tdeeKcal = tdee(stats)
        return TdeeSuggestions(
            cut      = buildSplit(stats, tdeeKcal - 500.0, proteinPerKg = 2.2),
            maintain = buildSplit(stats, tdeeKcal,         proteinPerKg = 2.0),
            bulk     = buildSplit(stats, tdeeKcal + 300.0, proteinPerKg = 1.8)
        )
    }

    private fun buildSplit(
        stats: UserPhysicalStats,
        kcalDouble: Double,
        proteinPerKg: Double
    ): MacroSplit {
        val kcal = kcalDouble.roundToInt()
        val proteinRaw = stats.weightKg * proteinPerKg
        val fatRaw     = kcal * 0.25 / 9.0
        val proteinG   = roundToStep(proteinRaw)
        val fatG       = roundToStep(fatRaw)
        // Carbs = remainder of kcal after protein (4 kcal/g) and fat (9 kcal/g).
        val carbsRaw = (kcal - proteinG * 4 - fatG * 9) / 4.0
        val carbsG   = roundToStep(carbsRaw).coerceAtLeast(0)
        return MacroSplit(
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            sugarG = 50  // D-16-07 — sugar is not derived; default 50 g.
        )
    }

    // UI-SPEC "Gram rounding for suggestion cards":
    // round to nearest 5 g if >= 20 g, else nearest 1 g (avoid jumping to 0).
    private fun roundToStep(value: Double): Int {
        val rounded = value.roundToInt()
        return if (rounded >= 20) ((rounded + 2) / 5) * 5 else rounded
    }
}

data class TdeeSuggestions(
    val cut: MacroSplit,
    val maintain: MacroSplit,
    val bulk: MacroSplit
)

data class MacroSplit(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val sugarG: Int = 50
)

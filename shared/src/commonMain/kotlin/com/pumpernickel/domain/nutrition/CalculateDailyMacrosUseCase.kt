package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.model.macros

class CalculateDailyMacrosUseCase {
    operator fun invoke(entries: List<ConsumptionEntry>): RecipeMacros {
        var calories = 0.0; var protein = 0.0; var fat = 0.0; var carbs = 0.0; var sugar = 0.0
        for (e in entries) {
            val m = e.macros()
            calories += m.calories; protein += m.protein
            fat += m.fat; carbs += m.carbs; sugar += m.sugar
        }
        return RecipeMacros(calories, protein, fat, carbs, sugar)
    }
}

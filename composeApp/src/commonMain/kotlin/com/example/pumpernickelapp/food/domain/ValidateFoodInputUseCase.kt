package com.example.pumpernickelapp.food.domain

class ValidateFoodInputUseCase {

    sealed interface Result {
        data class Error(val message: String) : Result
        data class Valid(
            val calories: Double,
            val protein: Double,
            val fat: Double,
            val carbs: Double,
            val sugar: Double
        ) : Result
    }

    operator fun invoke(
        name: String,
        calories: String,
        protein: String,
        fat: String,
        carbs: String,
        sugar: String
    ): Result {
        val caloriesVal = calories.replace(',', '.').toDoubleOrNull()
        val proteinVal  = protein.replace(',', '.').toDoubleOrNull()
        val fatVal      = fat.replace(',', '.').toDoubleOrNull()
        val carbsVal    = carbs.replace(',', '.').toDoubleOrNull()
        val sugarVal    = sugar.replace(',', '.').toDoubleOrNull()

        return when {
            name.isBlank()                         -> Result.Error("Name darf nicht leer sein.")
            caloriesVal == null || caloriesVal < 0 -> Result.Error("Kalorien: gültige Zahl >= 0 eingeben.")
            proteinVal  == null || proteinVal  < 0 -> Result.Error("Protein: gültige Zahl >= 0 eingeben.")
            fatVal      == null || fatVal      < 0 -> Result.Error("Fett: gültige Zahl >= 0 eingeben.")
            carbsVal    == null || carbsVal    < 0 -> Result.Error("Kohlenhydrate: gültige Zahl >= 0 eingeben.")
            sugarVal    == null || sugarVal    < 0 -> Result.Error("Zucker: gültige Zahl >= 0 eingeben.")
            sugarVal!!  > carbsVal!!               -> Result.Error("Zucker darf nicht größer als Kohlenhydrate sein.")
            else -> Result.Valid(caloriesVal, proteinVal, fatVal, carbsVal, sugarVal)
        }
    }
}

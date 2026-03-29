package com.example.pumpernickelapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun FoodEntryScreen(viewModel: FoodViewModel, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbohydrates by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var isRecipe by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        errorMessage = null
        successMessage = null

        val caloriesVal = calories.replace(',', '.').toDoubleOrNull()
        val proteinVal = protein.replace(',', '.').toDoubleOrNull()
        val fatVal = fat.replace(',', '.').toDoubleOrNull()
        val carbsVal = carbohydrates.replace(',', '.').toDoubleOrNull()
        val sugarVal = sugar.replace(',', '.').toDoubleOrNull()

        when {
            name.isBlank() -> errorMessage = "Name darf nicht leer sein."
            caloriesVal == null || caloriesVal < 0 -> errorMessage = "Kalorien: gültige Zahl >= 0 eingeben."
            proteinVal == null || proteinVal < 0 -> errorMessage = "Protein: gültige Zahl >= 0 eingeben."
            fatVal == null || fatVal < 0 -> errorMessage = "Fett: gültige Zahl >= 0 eingeben."
            carbsVal == null || carbsVal < 0 -> errorMessage = "Kohlenhydrate: gültige Zahl >= 0 eingeben."
            sugarVal == null || sugarVal < 0 -> errorMessage = "Zucker: gültige Zahl >= 0 eingeben."
            sugarVal > carbsVal!! -> errorMessage = "Zucker darf nicht größer als Kohlenhydrate sein."
            else -> {
                viewModel.addFood(
                    Food(
                        name = name.trim(),
                        calories = caloriesVal!!,
                        protein = proteinVal!!,
                        fat = fatVal!!,
                        carbohydrates = carbsVal,
                        sugar = sugarVal,
                        isRecipe = isRecipe,
                        barcode = barcode.trim().ifBlank { null }
                    )
                )
                name = ""
                calories = ""
                protein = ""
                fat = ""
                carbohydrates = ""
                sugar = ""
                isRecipe = false
                barcode = ""
                successMessage = "Lebensmittel gespeichert!"
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Lebensmittel erfassen") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = calories,
                onValueChange = { calories = it },
                label = { Text("Kalorien (kcal) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = protein,
                onValueChange = { protein = it },
                label = { Text("Protein (g) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = fat,
                onValueChange = { fat = it },
                label = { Text("Fett (g) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = carbohydrates,
                onValueChange = { carbohydrates = it },
                label = { Text("Kohlenhydrate (g) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = sugar,
                onValueChange = { sugar = it },
                label = { Text("davon Zucker (g) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            successMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { validateAndSave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.example.pumpernickelapp.food.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.example.pumpernickelapp.food.domain.ConsumptionEntry
import com.example.pumpernickelapp.food.domain.Food
import com.example.pumpernickelapp.food.domain.FoodUnit
import com.example.pumpernickelapp.food.domain.macros
import com.example.pumpernickelapp.food.ui.components.MacroRow
import com.example.pumpernickelapp.food.ui.entry.BarcodeScannerButton
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import pumpernickelapp.composeapp.generated.resources.Res
import pumpernickelapp.composeapp.generated.resources.action_ad_hoc_entry
import pumpernickelapp.composeapp.generated.resources.action_add_entry
import pumpernickelapp.composeapp.generated.resources.action_cancel
import pumpernickelapp.composeapp.generated.resources.action_log_entry
import pumpernickelapp.composeapp.generated.resources.label_amount
import pumpernickelapp.composeapp.generated.resources.label_calories
import pumpernickelapp.composeapp.generated.resources.label_carbs
import pumpernickelapp.composeapp.generated.resources.label_daily_total
import pumpernickelapp.composeapp.generated.resources.label_fat
import pumpernickelapp.composeapp.generated.resources.label_name
import pumpernickelapp.composeapp.generated.resources.label_per_100_hint
import pumpernickelapp.composeapp.generated.resources.label_protein
import pumpernickelapp.composeapp.generated.resources.label_sugar
import pumpernickelapp.composeapp.generated.resources.msg_no_entries_today
import pumpernickelapp.composeapp.generated.resources.title_daily_log
import pumpernickelapp.composeapp.generated.resources.unit_gram
import pumpernickelapp.composeapp.generated.resources.unit_ml
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun DailyLogScreen(
    viewModel: DailyLogViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.title_daily_log)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddPicker() }) { Text("+") }
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date navigator
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.goPreviousDay() }) { Text("◀") }
                TextButton(onClick = { viewModel.goToday() }) {
                    Text(
                        state.selectedDate.toString(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { viewModel.goNextDay() }) { Text("▶") }
            }

            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.label_daily_total),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${state.totals.calories.roundToInt()} kcal",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MacroRow(
                        protein = state.totals.protein,
                        fat     = state.totals.fat,
                        carbs   = state.totals.carbs,
                        sugar   = state.totals.sugar
                    )
                }
            }

            // Entries
            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.msg_no_entries_today))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        EntrySwipeCard(
                            entry = entry,
                            onDelete = { viewModel.delete(entry.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Food picker dialog
    if (state.showAddPicker) {
        FoodPickerDialog(
            foods = state.foods,
            onPick = { viewModel.selectFoodForEntry(it) },
            onAdHoc = { viewModel.openAdHocDialog() },
            onBarcode = { viewModel.onBarcodeScanned(it) },
            onDismiss = { viewModel.dismissAddPicker() }
        )
    }

    // Amount dialog
    state.pendingFood?.let { food ->
        AmountDialog(
            food = food,
            onConfirm = { amount -> viewModel.confirmLog(food, amount) },
            onDismiss = { viewModel.clearPendingFood() }
        )
    }

    // Ad-hoc entry dialog
    if (state.showAdHocDialog) {
        AdHocEntryDialog(
            onConfirm = { name, cal, p, f, c, s, unit, amount ->
                viewModel.confirmAdHocLog(name, cal, p, f, c, s, unit, amount)
            },
            onDismiss = { viewModel.dismissAdHocDialog() }
        )
    }

    // Error snackbar-ish
    state.errorMessage?.let { msg ->
        LaunchedEffect(msg) { /* simple; auto-clear on next state change */ }
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Snackbar(action = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }) { Text(msg) }
        }
    }
}

@Composable
private fun EntrySwipeCard(entry: ConsumptionEntry, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) onDelete()
    }
    val m = entry.macros()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) { Text("🗑") }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        entry.name,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTime(entry),
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${entry.amount.roundToInt()} ${entry.unit.label}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${m.calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                MacroRow(protein = m.protein, fat = m.fat, carbs = m.carbs, sugar = m.sugar)
            }
        }
    }
}

private fun formatTime(entry: ConsumptionEntry): String {
    val ldt = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val h = ldt.hour.toString().padStart(2, '0')
    val m = ldt.minute.toString().padStart(2, '0')
    return "$h:$m"
}

@Composable
private fun FoodPickerDialog(
    foods: List<Food>,
    onPick: (Food) -> Unit,
    onAdHoc: () -> Unit,
    onBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(foods, query) {
        if (query.isBlank()) foods
        else foods.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(stringResource(Res.string.action_add_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarcodeScannerButton(
                    onBarcodeScanned = onBarcode,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onAdHoc, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_ad_hoc_entry))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.id }) { food ->
                        TextButton(
                            onClick = { onPick(food) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                food.name,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AdHocEntryDialog(
    onConfirm: (name: String, cal: Double, p: Double, f: Double, c: Double, s: Double, unit: FoodUnit, amount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(FoodUnit.GRAM) }
    var amount by remember { mutableStateOf("100") }
    var error by remember { mutableStateOf<String?>(null) }

    fun parse(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val cal = parse(calories)
                val p = parse(protein) ?: 0.0
                val f = parse(fat) ?: 0.0
                val c = parse(carbs) ?: 0.0
                val s = parse(sugar) ?: 0.0
                val a = parse(amount)
                when {
                    name.isBlank() -> error = "Name fehlt"
                    cal == null || cal < 0 -> error = "Kalorien ungültig"
                    a == null || a <= 0 -> error = "Menge ungültig"
                    s > c -> error = "Zucker kann nicht größer als Kohlenhydrate sein"
                    else -> onConfirm(name, cal, p, f, c, s, unit, a)
                }
            }) { Text(stringResource(Res.string.action_log_entry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(stringResource(Res.string.action_ad_hoc_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.label_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(Res.string.label_per_100_hint),
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = calories, onValueChange = { calories = it },
                    label = { Text(stringResource(Res.string.label_calories)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = protein, onValueChange = { protein = it },
                        label = { Text(stringResource(Res.string.label_protein)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat, onValueChange = { fat = it },
                        label = { Text(stringResource(Res.string.label_fat)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = carbs, onValueChange = { carbs = it },
                        label = { Text(stringResource(Res.string.label_carbs)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sugar, onValueChange = { sugar = it },
                        label = { Text(stringResource(Res.string.label_sugar)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FoodUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = unit == u,
                            onClick = { unit = u },
                            shape = SegmentedButtonDefaults.itemShape(i, FoodUnit.entries.size),
                            label = { Text(stringResource(if (u == FoodUnit.GRAM) Res.string.unit_gram else Res.string.unit_ml)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text(stringResource(Res.string.label_amount) + " (${unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}

@Composable
private fun AmountDialog(
    food: Food,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("100") }
    val unitLabel = stringResource(
        if (food.unit == FoodUnit.MILLILITER) Res.string.unit_ml else Res.string.unit_gram
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm)
            }) { Text(stringResource(Res.string.action_log_entry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(unitLabel)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(Res.string.label_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    "${food.calories.roundToInt()} kcal / 100 ${food.unit.label}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}


package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.navigation.NutritionFoodEntryRoute
import com.pumpernickel.android.ui.navigation.NutritionRecipeListRoute
import com.pumpernickel.domain.model.ConsumptionEntry
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.model.macros
import com.pumpernickel.android.ui.components.BodyProfileBottomSheet
import com.pumpernickel.presentation.nutrition.DailyLogViewModel
import com.pumpernickel.presentation.settings.SettingsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDailyLogScreen(
    navController: NavController,
    viewModel: DailyLogViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBodyProfile by remember { mutableStateOf(false) }

    if (showBodyProfile) {
        BodyProfileBottomSheet(
            viewModel = settingsViewModel,
            onDismiss = { showBodyProfile = false }
        )
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_daily_log)) },
                actions = {
                    IconButton(onClick = { showBodyProfile = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // Date navigator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.goPreviousDay() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
                }
                TextButton(onClick = { viewModel.goToday() }) {
                    Text(state.selectedDate.toString(), fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { viewModel.goNextDay() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
                }
            }

            // Summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.label_daily_total), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.totals.calories.roundToInt()} / ${state.goals.calorieGoal} kcal",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MacroRow(
                        protein = state.totals.protein,
                        fat = state.totals.fat,
                        carbs = state.totals.carbs,
                        sugar = state.totals.sugar
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MacroGoalChip("P", state.totals.protein, state.goals.proteinGoal.toDouble(), Modifier.weight(1f))
                        MacroGoalChip("F", state.totals.fat, state.goals.fatGoal.toDouble(), Modifier.weight(1f))
                        MacroGoalChip("KH", state.totals.carbs, state.goals.carbGoal.toDouble(), Modifier.weight(1f))
                    }
                }
            }

            // Entries
            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.msg_no_entries_today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        EntrySwipeCard(entry = entry, onDelete = { viewModel.delete(entry.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // Bottom action bar (matches iOS pattern: primary + secondary inline buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { viewModel.openAddPicker() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.action_add_entry_short), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { navController.navigate(NutritionRecipeListRoute) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.tab_recipes), fontWeight = FontWeight.SemiBold)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySwipeCard(entry: ConsumptionEntry, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    val m = entry.macros()
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        text = formatTime(entry),
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
    val ldt = Instant.fromEpochMilliseconds(entry.timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
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
        if (query.isBlank()) foods else foods.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.action_add_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarcodeScannerButton(onBarcodeScanned = onBarcode, modifier = Modifier.fillMaxWidth())
                Button(onClick = onAdHoc, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_ad_hoc_entry))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(filtered, key = { it.id }) { food ->
                        Text(
                            text = food.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(food) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
                val cal = parse(calories); val p = parse(protein) ?: 0.0; val f = parse(fat) ?: 0.0
                val c = parse(carbs) ?: 0.0; val s = parse(sugar) ?: 0.0; val a = parse(amount)
                when {
                    name.isBlank() -> error = "Name fehlt"
                    cal == null || cal < 0 -> error = "Kalorien ung\u00FCltig"
                    a == null || a <= 0 -> error = "Menge ung\u00FCltig"
                    s > c -> error = "Zucker kann nicht gr\u00F6\u00DFer als Kohlenhydrate sein"
                    else -> onConfirm(name, cal, p, f, c, s, unit, a)
                }
            }) { Text(stringResource(R.string.action_log_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.action_ad_hoc_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.label_per_100_hint), style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text(stringResource(R.string.label_calories)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text(stringResource(R.string.label_protein)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text(stringResource(R.string.label_fat)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text(stringResource(R.string.label_carbs)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sugar, onValueChange = { sugar = it }, label = { Text(stringResource(R.string.label_sugar)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FoodUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(selected = unit == u, onClick = { unit = u }, shape = SegmentedButtonDefaults.itemShape(i, FoodUnit.entries.size), label = { Text(stringResource(if (u == FoodUnit.GRAM) R.string.unit_gram else R.string.unit_ml)) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.label_amount) + " (${unit.label})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
}

@Composable
private fun AmountDialog(food: Food, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm) }) {
                Text(stringResource(R.string.action_log_entry))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.label_amount) + " (${food.unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    "pro 100 ${food.unit.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MacroRow(
                    protein = food.protein,
                    fat = food.fat,
                    carbs = food.carbohydrates,
                    sugar = food.sugar
                )
                Text(
                    "${food.calories.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun MacroGoalChip(label: String, current: Double, goal: Double, modifier: Modifier = Modifier) {
    val color = if (goal > 0 && current > goal) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = "$label ${current.roundToInt()}/${goal.roundToInt()}g",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

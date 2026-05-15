package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.components.*
import com.pumpernickel.domain.model.*
import com.pumpernickel.domain.nutrition.MacroSplit
import com.pumpernickel.domain.nutrition.TdeeCalculator
import com.pumpernickel.presentation.overview.OverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

private enum class SuggestionType { CUT, MAINTAIN, BULK }

private val activityLabels: Map<ActivityLevel, String> = mapOf(
    ActivityLevel.SEDENTARY to "Bürojob / kaum Bewegung",
    ActivityLevel.LIGHTLY_ACTIVE to "Leicht aktiv (1–3×/Woche)",
    ActivityLevel.MODERATELY_ACTIVE to "Mäßig aktiv (3–5×/Woche)",
    ActivityLevel.VERY_ACTIVE to "Sehr aktiv (6–7×/Woche)",
    ActivityLevel.EXTRA_ACTIVE to "Extrem aktiv / körperlicher Beruf"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionGoalsEditorScreen(
    navController: NavHostController,
    viewModel: OverviewViewModel = koinViewModel()
) {
    val storedStats by viewModel.userPhysicalStats.collectAsState()
    val storedGoals by viewModel.nutritionGoals.collectAsState()

    var weightText by remember { mutableStateOf("80") }
    var heightText by remember { mutableStateOf("180") }
    var ageText by remember { mutableStateOf("30") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATELY_ACTIVE) }
    var statsExpanded by remember { mutableStateOf(true) }

    var kcalValue by remember { mutableStateOf(0) }
    var proteinValue by remember { mutableStateOf(0) }
    var carbsValue by remember { mutableStateOf(0) }
    var fatValue by remember { mutableStateOf(0) }
    var sugarValue by remember { mutableStateOf(0) }

    var selectedSuggestion by remember { mutableStateOf<SuggestionType?>(null) }
    var statsInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(storedStats) {
        if (!statsInitialized && storedStats != null) {
            weightText = "%.0f".format(storedStats!!.weightKg)
            heightText = storedStats!!.heightCm.toString()
            ageText = storedStats!!.age.toString()
            sex = storedStats!!.sex
            activity = storedStats!!.activityLevel
            statsExpanded = false
            statsInitialized = true
        }
    }

    LaunchedEffect(storedGoals) {
        kcalValue = storedGoals.calorieGoal
        proteinValue = storedGoals.proteinGoal
        carbsValue = storedGoals.carbGoal
        fatValue = storedGoals.fatGoal
        sugarValue = storedGoals.sugarGoal
    }

    val currentStatsForCalc by remember {
        derivedStateOf {
            val w = weightText.toDoubleOrNull() ?: 80.0
            val h = heightText.toIntOrNull() ?: 180
            val a = ageText.toIntOrNull() ?: 30
            UserPhysicalStats(w, h, a, sex, activity)
        }
    }
    val suggestions by remember { derivedStateOf { TdeeCalculator.suggestions(currentStatsForCalc) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ernährungsziele", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                StatsSection(
                    expanded = statsExpanded,
                    onToggle = { statsExpanded = !statsExpanded },
                    weightText = weightText,
                    onWeightChange = { weightText = it; selectedSuggestion = null },
                    heightText = heightText,
                    onHeightChange = { heightText = it; selectedSuggestion = null },
                    ageText = ageText,
                    onAgeChange = { ageText = it; selectedSuggestion = null },
                    sex = sex,
                    onSexChange = { sex = it; selectedSuggestion = null },
                    activity = activity,
                    onActivityChange = { activity = it; selectedSuggestion = null }
                )
            }

            item {
                SuggestionRow(
                    suggestions = suggestions,
                    selected = selectedSuggestion,
                    onSelect = { type ->
                        selectedSuggestion = type
                        val split: MacroSplit = when (type) {
                            SuggestionType.CUT -> suggestions.cut
                            SuggestionType.MAINTAIN -> suggestions.maintain
                            SuggestionType.BULK -> suggestions.bulk
                        }
                        kcalValue = split.kcal
                        proteinValue = split.proteinG
                        carbsValue = split.carbsG
                        fatValue = split.fatG
                        sugarValue = split.sugarG
                    }
                )
            }

            item {
                PickerSection(
                    kcalValue = kcalValue,
                    onKcalChange = { kcalValue = it; selectedSuggestion = null },
                    proteinValue = proteinValue,
                    onProteinChange = { proteinValue = it; selectedSuggestion = null },
                    carbsValue = carbsValue,
                    onCarbsChange = { carbsValue = it; selectedSuggestion = null },
                    fatValue = fatValue,
                    onFatChange = { fatValue = it; selectedSuggestion = null },
                    sugarValue = sugarValue,
                    onSugarChange = { sugarValue = it; selectedSuggestion = null }
                )
            }

            item {
                Button(
                    onClick = {
                        val stats = UserPhysicalStats(
                            weightKg = weightText.toDoubleOrNull() ?: 80.0,
                            heightCm = heightText.toIntOrNull() ?: 180,
                            age = ageText.toIntOrNull() ?: 30,
                            sex = sex,
                            activityLevel = activity
                        )
                        viewModel.updateUserPhysicalStats(stats)
                        viewModel.updateNutritionGoals(
                            NutritionGoals(
                                calorieGoal = kcalValue,
                                proteinGoal = proteinValue,
                                fatGoal = fatValue,
                                carbGoal = carbsValue,
                                sugarGoal = sugarValue
                            )
                        )
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Ziele speichern")
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSection(
    expanded: Boolean, onToggle: () -> Unit,
    weightText: String, onWeightChange: (String) -> Unit,
    heightText: String, onHeightChange: (String) -> Unit,
    ageText: String, onAgeChange: (String) -> Unit,
    sex: Sex, onSexChange: (Sex) -> Unit,
    activity: ActivityLevel, onActivityChange: (ActivityLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Meine Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggle) {
                    Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(weightText, onWeightChange, label = { Text("Gewicht (kg)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(heightText, onHeightChange, label = { Text("Körpergröße (cm)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(ageText, onAgeChange, label = { Text("Alter") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(onClick = { onSexChange(Sex.MALE) }, selected = sex == Sex.MALE, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Männlich") }
                        SegmentedButton(onClick = { onSexChange(Sex.FEMALE) }, selected = sex == Sex.FEMALE, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Weiblich") }
                    }
                    ActivityDropdown(activity, onActivityChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDropdown(selected: ActivityLevel, onSelected: (ActivityLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(activityLabels[selected] ?: selected.name, {}, readOnly = true, label = { Text("Aktivitätslevel") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ActivityLevel.entries.forEach { level ->
                DropdownMenuItem({ Text(activityLabels[level] ?: level.name) }, { onSelected(level); expanded = false })
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestions: com.pumpernickel.domain.nutrition.TdeeSuggestions, selected: SuggestionType?, onSelect: (SuggestionType) -> Unit) {
    Column {
        Text("Vorschlag berechnen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionCard("Defizit", "−500 kcal", suggestions.cut, selected == SuggestionType.CUT, { onSelect(SuggestionType.CUT) }, Modifier.weight(1f))
            SuggestionCard("Erhalt", "TDEE", suggestions.maintain, selected == SuggestionType.MAINTAIN, { onSelect(SuggestionType.MAINTAIN) }, Modifier.weight(1f))
            SuggestionCard("Aufbau", "+300 kcal", suggestions.bulk, selected == SuggestionType.BULK, { onSelect(SuggestionType.BULK) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SuggestionCard(title: String, subtitle: String, split: MacroSplit, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Card(modifier.clickable(onClick = onClick).then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("${split.kcal} kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = CalorieRingColor)
            Spacer(Modifier.height(4.dp))
            MacroDotRow("P ${split.proteinG}g", ProteinRingColor)
            MacroDotRow("K ${split.carbsG}g", CarbRingColor)
            MacroDotRow("F ${split.fatG}g", FatRingColor)
        }
    }
}

@Composable
private fun MacroDotRow(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickerSection(
    kcalValue: Int, onKcalChange: (Int) -> Unit,
    proteinValue: Int, onProteinChange: (Int) -> Unit,
    carbsValue: Int, onCarbsChange: (Int) -> Unit,
    fatValue: Int, onFatChange: (Int) -> Unit,
    sugarValue: Int, onSugarChange: (Int) -> Unit
) {
    Column {
        Text("Zielwerte anpassen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Large Calorie Picker
        GoalPickerRow(
            label = "Kalorien (kcal)",
            items = (800..6000 step 50).toList(),
            value = kcalValue,
            onValueChange = onKcalChange,
            displayTransform = { "$it" }
        )

        Spacer(Modifier.height(16.dp))

        // Compact Macro Pickers in a Row (Matching iOS Design)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompactGoalPicker(
                label = "Protein",
                items = (20..400 step 5).toList(),
                value = proteinValue,
                onValueChange = onProteinChange,
                modifier = Modifier.weight(1f),
                visibleItemCount = 3
            )
            CompactGoalPicker(
                label = "Kohlenh.",
                items = (20..700 step 5).toList(),
                value = carbsValue,
                onValueChange = onCarbsChange,
                modifier = Modifier.weight(1f),
                visibleItemCount = 3
            )
            CompactGoalPicker(
                label = "Fett",
                items = (10..250 step 5).toList(),
                value = fatValue,
                onValueChange = onFatChange,
                modifier = Modifier.weight(1f),
                visibleItemCount = 3
            )
            CompactGoalPicker(
                label = "Zucker",
                items = (0..200 step 5).toList(),
                value = sugarValue,
                onValueChange = onSugarChange,
                modifier = Modifier.weight(1f),
                visibleItemCount = 3
            )
        }

        Spacer(Modifier.height(16.dp))
        MacroCalorieBanner(kcalValue, proteinValue, carbsValue, fatValue)
    }
}

@Composable
private fun CompactGoalPicker(
    label: String,
    items: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        DrumPicker(
            items = items,
            selectedItem = value,
            onItemSelected = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = "",
            displayTransform = { "$it" },
            visibleItemCount = visibleItemCount
        )
    }
}

@Composable
private fun MacroCalorieBanner(kcalGoal: Int, proteinGrams: Int, carbsGrams: Int, fatGrams: Int) {
    val macroKcal = proteinGrams * 4 + carbsGrams * 4 + fatGrams * 9
    val deviation = macroKcal - kcalGoal
    val isOff = kotlin.math.abs(deviation) > (kcalGoal * 0.10).toInt().coerceAtLeast(50)
    val containerColor = if (isOff) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Makro-Kalorien", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("$macroKcal kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(if (isOff) "Weicht um ${deviation} kcal vom Ziel ab." else "Passt zum Kalorienziel.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GoalPickerRow(label: String, items: List<Int>, value: Int, onValueChange: (Int) -> Unit, displayTransform: (Int) -> String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            DrumPicker(items, value, onValueChange, Modifier.fillMaxWidth(), "", displayTransform)
        }
    }
}

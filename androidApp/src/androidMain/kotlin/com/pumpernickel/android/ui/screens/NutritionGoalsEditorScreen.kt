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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.components.DrumPicker
import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import com.pumpernickel.domain.nutrition.MacroSplit
import com.pumpernickel.domain.nutrition.TdeeCalculator
import com.pumpernickel.presentation.overview.OverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

private enum class SuggestionType { CUT, MAINTAIN, BULK }

// Activity tier labels (D-16-05)
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

    // Stats inputs — placeholder defaults; seeded once from the first non-null
    // userPhysicalStats emission via LaunchedEffect below (Gap 2 / WR-03 fix).
    var weightText by remember { mutableStateOf("80") }
    var heightText by remember { mutableStateOf("180") }
    var ageText by remember { mutableStateOf("30") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATELY_ACTIVE) }
    var statsExpanded by remember { mutableStateOf(true) }

    // Picker state — defaults from NutritionGoals defaults; seeded once from
    // first storedGoals emission via LaunchedEffect below.
    var kcalValue by remember { mutableStateOf(2500) }
    var proteinValue by remember { mutableStateOf(150) }
    var carbsValue by remember { mutableStateOf(300) }
    var fatValue by remember { mutableStateOf(80) }
    var sugarValue by remember { mutableStateOf(50) }

    var selectedSuggestion by remember { mutableStateOf<SuggestionType?>(null) }

    // One-shot initialization guards (WR-03 / Gap 2). Survives configuration
    // changes because rememberSaveable persists across recomposition + rotation.
    var statsInitialized by rememberSaveable { mutableStateOf(false) }
    var goalsInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(storedStats) {
        if (!statsInitialized && storedStats != null) {
            weightText = "%.0f".format(storedStats!!.weightKg)
            heightText = storedStats!!.heightCm.toString()
            ageText = storedStats!!.age.toString()
            sex = storedStats!!.sex
            activity = storedStats!!.activityLevel
            statsExpanded = false  // collapse when stats already stored (D-16-09)
            statsInitialized = true
        }
    }

    LaunchedEffect(storedGoals) {
        if (!goalsInitialized) {
            kcalValue = storedGoals.calorieGoal
            proteinValue = storedGoals.proteinGoal
            carbsValue = storedGoals.carbGoal
            fatValue = storedGoals.fatGoal
            sugarValue = storedGoals.sugarGoal
            goalsInitialized = true
        }
    }

    // Live-computed suggestions based on current stats inputs
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

            // Section 1 — Stats (collapsible)
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

            // Section 2 — Suggestion cards
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

            // Section 3 — DrumPickers
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

            // Save button
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Ziele speichern")
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Stats Section (collapsible) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    weightText: String,
    onWeightChange: (String) -> Unit,
    heightText: String,
    onHeightChange: (String) -> Unit,
    ageText: String,
    onAgeChange: (String) -> Unit,
    sex: Sex,
    onSexChange: (Sex) -> Unit,
    activity: ActivityLevel,
    onActivityChange: (ActivityLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meine Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Einklappen" else "Ausklappen"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = onWeightChange,
                        label = { Text("Gewicht (kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = heightText,
                        onValueChange = onHeightChange,
                        label = { Text("Körpergröße (cm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = ageText,
                        onValueChange = onAgeChange,
                        label = { Text("Alter") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Sex selector — SingleChoiceSegmentedButtonRow (exactly one selected)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { onSexChange(Sex.MALE) },
                            selected = sex == Sex.MALE
                        ) {
                            Text("Männlich")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { onSexChange(Sex.FEMALE) },
                            selected = sex == Sex.FEMALE
                        ) {
                            Text("Weiblich")
                        }
                    }

                    // Activity level dropdown
                    ActivityDropdown(
                        selected = activity,
                        onSelected = onActivityChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDropdown(
    selected: ActivityLevel,
    onSelected: (ActivityLevel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = activityLabels[selected] ?: selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Aktivitätslevel") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ActivityLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(activityLabels[level] ?: level.name) },
                    onClick = {
                        onSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Suggestion Row ──

@Composable
private fun SuggestionRow(
    suggestions: com.pumpernickel.domain.nutrition.TdeeSuggestions,
    selected: SuggestionType?,
    onSelect: (SuggestionType) -> Unit
) {
    Column {
        Text(
            text = "Vorschlag berechnen",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionCard(
                title = "Defizit",
                subtitle = "−500 kcal",
                split = suggestions.cut,
                isSelected = selected == SuggestionType.CUT,
                onClick = { onSelect(SuggestionType.CUT) },
                modifier = Modifier.weight(1f)
            )
            SuggestionCard(
                title = "Erhalt",
                subtitle = "TDEE",
                split = suggestions.maintain,
                isSelected = selected == SuggestionType.MAINTAIN,
                onClick = { onSelect(SuggestionType.MAINTAIN) },
                modifier = Modifier.weight(1f)
            )
            SuggestionCard(
                title = "Aufbau",
                subtitle = "+300 kcal",
                split = suggestions.bulk,
                isSelected = selected == SuggestionType.BULK,
                onClick = { onSelect(SuggestionType.BULK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Suggestion Card ──

private val SuggestionCalorieColor = Color(0xFFFF6B6B)
private val SuggestionProteinColor = Color(0xFF4FC3F7)
private val SuggestionCarbColor = Color(0xFFFFD54F)
private val SuggestionFatColor = Color(0xFFFF8A65)
private val SuggestionSugarColor = Color(0xFFBA68C8)

@Composable
private fun SuggestionCard(
    title: String,
    subtitle: String,
    split: MacroSplit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderModifier = if (isSelected) {
        modifier
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    } else {
        modifier.clip(RoundedCornerShape(12.dp))
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = borderModifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${split.kcal} kcal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SuggestionCalorieColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            MacroDotRow("P ${split.proteinG}g", SuggestionProteinColor)
            MacroDotRow("K ${split.carbsG}g", SuggestionCarbColor)
            MacroDotRow("F ${split.fatG}g", SuggestionFatColor)
            MacroDotRow("Z ${split.sugarG}g", SuggestionSugarColor)
        }
    }
}

@Composable
private fun MacroDotRow(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Picker Section ──

@Composable
private fun PickerSection(
    kcalValue: Int,
    onKcalChange: (Int) -> Unit,
    proteinValue: Int,
    onProteinChange: (Int) -> Unit,
    carbsValue: Int,
    onCarbsChange: (Int) -> Unit,
    fatValue: Int,
    onFatChange: (Int) -> Unit,
    sugarValue: Int,
    onSugarChange: (Int) -> Unit
) {
    Column {
        Text(
            text = "Zielwerte anpassen",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DrumPicker(
                items = (800..6000 step 50).toList(),
                selectedItem = kcalValue,
                onItemSelected = onKcalChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = "Kalorien",
                displayTransform = { "$it kcal" }
            )
            DrumPicker(
                items = (20..400 step 5).toList(),
                selectedItem = proteinValue,
                onItemSelected = onProteinChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = "Protein",
                displayTransform = { "$it g" }
            )
            DrumPicker(
                items = (20..700 step 5).toList(),
                selectedItem = carbsValue,
                onItemSelected = onCarbsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = "Kohlenhydrate",
                displayTransform = { "$it g" }
            )
            DrumPicker(
                items = (10..250 step 5).toList(),
                selectedItem = fatValue,
                onItemSelected = onFatChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = "Fett",
                displayTransform = { "$it g" }
            )
            DrumPicker(
                items = (0..200 step 5).toList(),
                selectedItem = sugarValue,
                onItemSelected = onSugarChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = "Zucker",
                displayTransform = { "$it g" }
            )
        }
    }
}

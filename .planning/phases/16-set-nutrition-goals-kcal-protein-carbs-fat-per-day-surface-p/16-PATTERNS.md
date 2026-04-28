# Phase 16: Set Nutrition Goals — Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 10
**Analogs found:** 10 / 10

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `shared/.../domain/model/UserPhysicalStats.kt` | model | — | `shared/.../domain/model/NutritionGoals.kt` | exact |
| `shared/.../domain/nutrition/TdeeCalculator.kt` | utility (pure functions) | transform | `shared/.../domain/gamification/XpFormula.kt` | exact |
| `shared/.../data/repository/SettingsRepository.kt` (modify) | repository | CRUD / DataStore | itself — add alongside existing `nutritionGoals` and `retroactiveApplied` blocks | exact |
| `shared/.../presentation/overview/OverviewViewModel.kt` (modify) | viewmodel | request-response | itself — add alongside existing `nutritionGoals` and `rankState` StateFlows | exact |
| `androidApp/.../screens/NutritionGoalsEditorScreen.kt` | screen/component | request-response | `androidApp/.../screens/TemplateEditorScreen.kt` | role-match |
| `androidApp/.../screens/OverviewScreen.kt` (modify) | screen/component | request-response | itself — add `NutritionGoalsBanner` composable + edit button to `NutritionRingsCard` | exact |
| `androidApp/.../navigation/Routes.kt` (modify) | config/route | — | itself — add `NutritionGoalsEditorRoute` beside `RanksAndAchievementsRoute` | exact |
| `androidApp/.../navigation/MainScreen.kt` (modify) | config/nav-host | — | itself — add `composable<NutritionGoalsEditorRoute>` in the Overview NavHost block | exact |
| `iosApp/.../Views/Overview/NutritionGoalsEditorView.swift` | view | request-response | `iosApp/.../Views/Templates/TemplateEditorView.swift` | role-match |
| `iosApp/.../Views/Overview/OverviewView.swift` (modify) | view | request-response | itself — add `nutritionGoalsBannerView` + edit button inside `nutritionRingsSection` | exact |
| `shared/.../commonTest/.../TdeeCalculatorTest.kt` | test | — | `shared/.../commonTest/.../XpFormulaTest.kt` | exact |

---

## Pattern Assignments

---

### `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` (model)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt`

**Full analog (lines 1–9):**
```kotlin
package com.pumpernickel.domain.model

data class NutritionGoals(
    val calorieGoal: Int = 2500,
    val proteinGoal: Int = 150,
    val fatGoal: Int = 80,
    val carbGoal: Int = 300,
    val sugarGoal: Int = 50
)
```

**Pattern to copy:** Same package, same structure — plain `data class` in `domain/model`, no imports needed.
`UserPhysicalStats` adds two enums (`Sex`, `ActivityLevel`) declared in the same file, then a data class referencing them:

```kotlin
package com.pumpernickel.domain.model

enum class Sex { MALE, FEMALE }

enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
    EXTRA_ACTIVE
}

data class UserPhysicalStats(
    val weightKg: Double,
    val heightCm: Int,
    val age: Int,
    val sex: Sex,
    val activityLevel: ActivityLevel
)
```

No defaults on `UserPhysicalStats` — `SettingsRepository` returns `Flow<UserPhysicalStats?>` (null = never set).

---

### `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/TdeeCalculator.kt` (utility, transform)

**Analog:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/XpFormula.kt`

**Imports + object structure pattern (lines 1–9 of XpFormula.kt):**
```kotlin
package com.pumpernickel.domain.gamification

import kotlin.math.floor

/**
 * Pure XP calculation functions. No side effects, no DB, no Koin.
 */
object XpFormula {
    // ...
    fun workoutXp(sets: List<WorkoutSetInput>): Int { ... }
}
```

**Pattern to copy:** `object` (singleton, no constructor), pure functions only, no Room / no Koin / no coroutines, KDoc comment at class level explaining purity. Place in `domain/nutrition/` package to mirror the existing `NutritionGoalDayPolicy` neighbor.

**NutritionGoalDayPolicy analog for constants** (lines 28–30 of NutritionGoalDayPolicy.kt):
```kotlin
object NutritionGoalDayPolicy {
    private const val TOLERANCE: Double = 0.10  // +-10% — D-04 strict
    ...
}
```

**TdeeCalculator shape to produce** (derived from decisions D-16-04 through D-16-07):
```kotlin
package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlin.math.roundToInt

object TdeeCalculator {

    // Activity multipliers — D-16-05
    private val ACTIVITY_MULTIPLIER = mapOf(
        ActivityLevel.SEDENTARY         to 1.2,
        ActivityLevel.LIGHTLY_ACTIVE    to 1.375,
        ActivityLevel.MODERATELY_ACTIVE to 1.55,
        ActivityLevel.VERY_ACTIVE       to 1.725,
        ActivityLevel.EXTRA_ACTIVE      to 1.9
    )

    fun bmr(stats: UserPhysicalStats): Double { ... }   // Mifflin-St Jeor D-16-04
    fun tdee(stats: UserPhysicalStats): Double { ... }  // bmr * multiplier D-16-05
    fun suggestions(stats: UserPhysicalStats): TdeeSuggestions { ... } // D-16-06 / D-16-07
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
    val sugarG: Int = 50   // D-16-07: sugar not derived, always default
)
```

---

### `shared/.../data/repository/SettingsRepository.kt` (modify — add new keys)

**Analog:** itself. Copy the `nutritionGoals` multi-key `combine` pattern and the `retroactiveApplied` single-boolean pattern.

**New DataStore keys to add** (copy style of lines 17–25):
```kotlin
// Existing keys (do not change):
private val calorieGoalKey = stringPreferencesKey("calorie_goal")
// ...
private val retroactiveAppliedKey = booleanPreferencesKey("gamification_retroactive_applied")

// Add:
private val userWeightKgKey   = stringPreferencesKey("user_weight_kg")
private val userHeightCmKey   = stringPreferencesKey("user_height_cm")
private val userAgeKey        = stringPreferencesKey("user_age")
private val userSexKey        = stringPreferencesKey("user_sex")
private val userActivityKey   = stringPreferencesKey("user_activity_level")
private val bannerDismissedKey = booleanPreferencesKey("nutrition_goals_banner_dismissed")
```

**Multi-key combine Flow pattern** (lines 60–68 — use for `userPhysicalStats`):
```kotlin
val nutritionGoals: Flow<NutritionGoals> = combine(
    dataStore.data.map { it[calorieGoalKey]?.toIntOrNull() ?: 2500 },
    dataStore.data.map { it[proteinGoalKey]?.toIntOrNull() ?: 150 },
    // ...
) { cal, pro, fat, carb, sugar ->
    NutritionGoals(cal, pro, fat, carb, sugar)
}
```

`userPhysicalStats` returns `Flow<UserPhysicalStats?>` — use `combine` of the five keys; if any key is missing return `null`.

**Boolean sentinel pattern** (lines 85–93 — use for `nutritionGoalsBannerDismissed`):
```kotlin
val retroactiveApplied: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[retroactiveAppliedKey] ?: false
}
suspend fun setRetroactiveApplied(applied: Boolean) {
    dataStore.edit { preferences ->
        preferences[retroactiveAppliedKey] = applied
    }
}
```

**Setter pattern for multi-key writes** (lines 70–78):
```kotlin
suspend fun setNutritionGoals(goals: NutritionGoals) {
    dataStore.edit { prefs ->
        prefs[calorieGoalKey] = goals.calorieGoal.toString()
        prefs[proteinGoalKey] = goals.proteinGoal.toString()
        // ...
    }
}
```

**Enum storage:** enums (`Sex`, `ActivityLevel`) are stored as their `.name` string, recovered via `enumValueOf<Sex>(it)` in the Flow map (same as `WeightUnit` pattern at lines 27–32 which maps the string back to the enum).

---

### `shared/.../presentation/overview/OverviewViewModel.kt` (modify — add StateFlows + methods)

**Analog:** itself. Copy the `rankState` StateFlow pattern and the `updateNutritionGoals` suspend-launch pattern.

**`@NativeCoroutinesState` + `stateIn` pattern** (lines 98–109):
```kotlin
@NativeCoroutinesState
val nutritionGoals: StateFlow<NutritionGoals> = settingsRepository
    .nutritionGoals
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionGoals())

@NativeCoroutinesState
val rankState: StateFlow<RankState> = gamificationRepository
    .rankState
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RankState.Unranked)
```

**New StateFlows to add** — copy this exact shape:
```kotlin
@NativeCoroutinesState
val userPhysicalStats: StateFlow<UserPhysicalStats?> = settingsRepository
    .userPhysicalStats
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

@NativeCoroutinesState
val nutritionGoalsBannerVisible: StateFlow<Boolean> = settingsRepository
    .nutritionGoalsBannerDismissed
    .map { !it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
```

**`updateNutritionGoals` setter pattern** (lines 178–183):
```kotlin
fun updateNutritionGoals(goals: NutritionGoals) {
    viewModelScope.launch {
        settingsRepository.setNutritionGoals(goals)
        _uiState.update { it.copy(nutritionGoals = goals) }
    }
}
```

**New methods to add** — copy this pattern:
```kotlin
fun updateUserPhysicalStats(stats: UserPhysicalStats) {
    viewModelScope.launch {
        settingsRepository.setUserPhysicalStats(stats)
    }
}

fun dismissBanner() {
    viewModelScope.launch {
        settingsRepository.setBannerDismissed(true)
    }
}
```

`dismissBanner()` is also called from `updateNutritionGoals` after a successful save (D-16-14).

---

### `androidApp/.../screens/NutritionGoalsEditorScreen.kt` (new screen)

**Analog:** `androidApp/.../screens/TemplateEditorScreen.kt`

**Import block pattern** (lines 1–53 of TemplateEditorScreen.kt):
```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.presentation.overview.OverviewViewModel
import org.koin.compose.viewmodel.koinViewModel
```

**Scaffold + TopAppBar + LazyColumn shell** (lines 88–118):
```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
    ) { ... }
}
```

**collectAsState pattern for ViewModel observation** (lines 65–71):
```kotlin
val uiState by viewModel.uiState.collectAsState()
val isSaving by viewModel.isSaving.collectAsState()
val saveResult by viewModel.saveResult.collectAsState()
```

**DrumPicker usage pattern** — from `DrumPicker.kt` convenience wrappers:
```kotlin
DrumPicker(
    items = (800..6000 step 50).toList(),   // kcal range
    selectedItem = kcalValue,
    onItemSelected = { kcalValue = it },
    modifier = Modifier.height(200.dp),      // fixed height inside LazyColumn
    label = "Kalorien",
    displayTransform = { it.toString() }
)
```

**Card section container** — from `NutritionRingsCard` in OverviewScreen.kt (lines 323–329):
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
) {
    Column(modifier = Modifier.padding(16.dp)) { ... }
}
```

**AnimatedVisibility collapse** — for stats section expand/collapse (see UI-SPEC):
```kotlin
var isStatsExpanded by remember { mutableStateOf(userPhysicalStats == null) }
AnimatedVisibility(visible = isStatsExpanded) {
    Column { /* weight, height, age, sex, activity fields */ }
}
```

**OutlinedTextField with numeric keyboard** (line 122 of TemplateEditorScreen.kt):
```kotlin
OutlinedTextField(
    value = weightText,
    onValueChange = { weightText = it },
    label = { Text("Gewicht (kg)") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = Modifier.fillMaxWidth(),
    singleLine = true
)
```

**Save button — full-width accent filled** (copy from existing TextButton pattern but as `Button`):
```kotlin
Button(
    onClick = { /* save */ },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
) {
    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
    else Text("Ziele speichern")
}
```

---

### `androidApp/.../screens/OverviewScreen.kt` (modify — add banner + edit button)

**Analog:** itself. Insertions go in two places:

**1. Banner insert point** — between `OverviewRankStrip` and `MuscleActivityCard` (lines 115–122):
```kotlin
// Add ABOVE NutritionRingsCard:
val bannerVisible by viewModel.nutritionGoalsBannerVisible.collectAsState()

AnimatedVisibility(
    visible = bannerVisible,
    exit = slideOutVertically() + fadeOut(animationSpec = tween(300))
) {
    NutritionGoalsBanner(
        onTap = { navController.navigate(NutritionGoalsEditorRoute) },
        onDismiss = { viewModel.dismissBanner() }
    )
}
```

**2. Edit button in NutritionRingsCard header** — replace the plain `Text` title at line 335 with a `Row`:
```kotlin
// Replace:
Text(text = "Ernährung · Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

// With:
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Ernährung · Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = { navController.navigate(NutritionGoalsEditorRoute) }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.Edit, contentDescription = "Ziele bearbeiten", modifier = Modifier.size(18.dp))
    }
}
```

**Banner composable shape** — mirrors `MuscleActivityCard` card structure (lines 140–147) but smaller:
```kotlin
@Composable
private fun NutritionGoalsBanner(onTap: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TrackChanges, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Persönliche Ziele setzen", style = MaterialTheme.typography.bodyMedium)
                Text("Berechne deinen Tagesbedarf...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Banner ausblenden", modifier = Modifier.size(16.dp))
            }
        }
    }
}
```

---

### `androidApp/.../navigation/Routes.kt` (modify — add route)

**Analog:** itself (lines 29–33 of Routes.kt).

**Pattern to copy:**
```kotlin
// Existing Overview tab routes:
@Serializable data object OverviewRootRoute
@Serializable data object RanksAndAchievementsRoute

// Add:
@Serializable data object NutritionGoalsEditorRoute
```

No parameters needed — the editor always opens from the Overview tab and reads the current goals from the ViewModel.

---

### `androidApp/.../navigation/MainScreen.kt` (modify — register composable)

**Analog:** itself. The Overview NavHost block (lines 163–181).

**Pattern to copy** (lines 172–179):
```kotlin
composable<RanksAndAchievementsRoute> {
    RankLadderScreen(navController = overviewNavController)
}
```

**Add after the `RanksAndAchievementsRoute` block:**
```kotlin
composable<NutritionGoalsEditorRoute> {
    NutritionGoalsEditorScreen(navController = overviewNavController)
}
```

Also add import at the top:
```kotlin
import com.pumpernickel.android.ui.screens.NutritionGoalsEditorScreen
import com.pumpernickel.android.ui.navigation.NutritionGoalsEditorRoute
```

---

### `iosApp/.../Views/Overview/NutritionGoalsEditorView.swift` (new view)

**Analog:** `iosApp/.../Views/Templates/TemplateEditorView.swift`

**Import + struct shell pattern** (lines 1–23 of TemplateEditorView.swift):
```swift
import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionGoalsEditorView: View {
    private let viewModel = KoinHelper.shared.getOverviewViewModel()

    @Environment(\.dismiss) private var dismiss

    // Local state for all editable fields
    @State private var weightText: String = "80"
    @State private var heightText: String = "180"
    @State private var ageText: String = "30"
    @State private var sex: SharedSex = .male
    @State private var activityLevel: SharedActivityLevel = .moderatelyActive

    @State private var kcalValue: Int = 2500
    // ... etc.

    @State private var isSaving: Bool = false
    @State private var selectedSuggestion: SuggestionType? = nil   // cut / maintain / bulk

    var body: some View { ... }
}
```

**Sheet dismiss pattern** (from TemplateEditorView, lines 10–12):
```swift
@Environment(\.dismiss) private var dismiss
// Called on "Abbrechen" button and after successful save
```

**Form + Section pattern** (from SettingsView.swift lines 13–28):
```swift
NavigationStack {
    Form {
        Section("Meine Stats") {
            // DisclosureGroup wraps fields for collapse
            DisclosureGroup("Meine Stats", isExpanded: $statsExpanded) {
                TextField("80", text: $weightText)
                    .keyboardType(.numberPad)
                // ...
                Picker("Aktivität", selection: $activityLevel) {
                    Text("Bürojob / kaum Bewegung").tag(SharedActivityLevel.sedentary)
                    // ...
                }
            }
        }

        Section("Vorschlag berechnen") {
            HStack(spacing: 8) {
                SuggestionCardView(...).frame(maxWidth: .infinity)
                SuggestionCardView(...).frame(maxWidth: .infinity)
                SuggestionCardView(...).frame(maxWidth: .infinity)
            }
        }

        Section("Zielwerte anpassen") {
            Picker("Kalorien", selection: $kcalValue) {
                ForEach(stride(from: 800, through: 6000, by: 50).map { $0 }, id: \.self) { v in
                    Text("\(v) kcal").tag(v)
                }
            }
            .pickerStyle(.wheel)
            // ... repeat for each macro
        }
    }
    .navigationTitle("Ernährungsziele")
    .navigationBarTitleDisplayMode(.inline)
    .toolbar {
        ToolbarItem(placement: .navigationBarLeading) {
            Button("Abbrechen") { dismiss() }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Button("Ziele speichern") { saveGoals() }
                .fontWeight(.semibold)
        }
    }
}
```

**Async observation pattern** (from OverviewView.swift lines 51–70):
```swift
.task {
    await observePhysicalStats()
}

private func observePhysicalStats() async {
    do {
        for try await stats in asyncSequence(for: viewModel.userPhysicalStatsFlow) {
            if let s = stats {
                weightText = String(format: "%.0f", s.weightKg)
                heightText = "\(s.heightCm)"
                ageText    = "\(s.age)"
                sex           = s.sex
                activityLevel = s.activityLevel
                statsExpanded = false   // collapse when stats already stored
            }
        }
    } catch {
        print("Stats observation error: \(error)")
    }
}
```

---

### `iosApp/.../Views/Overview/OverviewView.swift` (modify — add banner + edit button)

**Analog:** itself. Two insertion points:

**1. New `@State` properties** — add alongside existing `@State` vars (lines 8–15):
```swift
@State private var bannerVisible: Bool = true
@State private var showEditor: Bool = false
```

**2. Banner above `nutritionRingsSection`** — insert in the `VStack` (before `nutritionRingsSection`, line 27):
```swift
if bannerVisible {
    NutritionGoalsBannerView(
        onTap: { showEditor = true },
        onDismiss: {
            withAnimation(.easeOut(duration: 0.3)) { bannerVisible = false }
            viewModel.dismissBanner()
        }
    )
    .transition(.move(edge: .top).combined(with: .opacity))
}
```

**3. Edit button in `nutritionRingsSection`** — replace plain `Text("Ernährung · Heute")` (line 135):
```swift
// Replace:
Text("Ernährung · Heute").font(.headline)

// With:
HStack {
    Text("Ernährung · Heute").font(.headline)
    Spacer()
    Button(action: { showEditor = true }) {
        Image(systemName: "pencil")
            .foregroundColor(.appAccent)
    }
    .accessibilityLabel("Ziele bearbeiten")
}
```

**4. Sheet presentation** — add modifier to ScrollView or VStack (copy from NutritionDailyLogView pattern):
```swift
.sheet(isPresented: $showEditor) {
    NutritionGoalsEditorView()
        .presentationDragIndicator(.visible)
}
```

**5. Observe `bannerVisible` from ViewModel** — add task alongside existing `observeUiState`:
```swift
group.addTask { await observeBannerVisible() }

private func observeBannerVisible() async {
    do {
        for try await visible in asyncSequence(for: viewModel.nutritionGoalsBannerVisibleFlow) {
            self.bannerVisible = visible
        }
    } catch {
        print("Banner observation error: \(error)")
    }
}
```

---

### `shared/.../commonTest/.../TdeeCalculatorTest.kt` (test)

**Analog:** `shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/XpFormulaTest.kt`

**Test file structure pattern** (full XpFormulaTest.kt):
```kotlin
package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals

class XpFormulaTest {
    @Test fun emptySetsYieldZeroXp() { ... }
    @Test fun singleSetTenRepsOneHundredKgYieldsTenXp() { ... }
}
```

**Test file to produce:**
```kotlin
package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TdeeCalculatorTest {

    private fun maleStats(weightKg: Double = 80.0, heightCm: Int = 180, age: Int = 30) =
        UserPhysicalStats(weightKg, heightCm, age, Sex.MALE, ActivityLevel.MODERATELY_ACTIVE)

    @Test fun bmrMaleKnownValue() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
        assertEquals(1780.0, TdeeCalculator.bmr(maleStats()), absoluteTolerance = 0.5)
    }

    @Test fun tdeeIsMultipliedCorrectly() { ... }

    @Test fun cutSuggestionIsTdeeMinus500() { ... }
    @Test fun maintainSuggestionEqualsTdee() { ... }
    @Test fun bulkSuggestionIsTdeePlus300() { ... }
    @Test fun proteinCutIs2Point2PerKg() { ... }
    @Test fun sugarIsAlways50() { ... }
    @Test fun carbsAreRemainder() { ... }
}
```

Also copy the `NutritionGoalDayPolicyTest` helper-function pattern for building test inputs (lines 18–55).

---

## Shared Patterns

### `@NativeCoroutinesState` StateFlow exposure (iOS interop)
**Source:** `shared/.../presentation/overview/OverviewViewModel.kt` lines 95–109
**Apply to:** Every new `StateFlow` on `OverviewViewModel`
```kotlin
@NativeCoroutinesState
val myFlow: StateFlow<T> = repository.myFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultValue)
```
The `@NativeCoroutinesState` annotation is required for iOS `asyncSequence(for: viewModel.myFlowFlow)` binding (note: the generated iOS property name appends `Flow`).

### DataStore key declaration style
**Source:** `shared/.../data/repository/SettingsRepository.kt` lines 17–25
**Apply to:** All new DataStore keys in `SettingsRepository`
```kotlin
private val myKey = stringPreferencesKey("my_key")         // String / numeric stored as String
private val myBoolKey = booleanPreferencesKey("my_bool")   // Boolean stored natively
```

### Card container style
**Source:** `androidApp/.../screens/OverviewScreen.kt` lines 323–329
**Apply to:** All section cards in `NutritionGoalsEditorScreen`
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
)
```

### iOS section background style
**Source:** `iosApp/.../Views/Overview/OverviewView.swift` lines 185–186
**Apply to:** All section containers in `NutritionGoalsEditorView` and `NutritionGoalsBannerView`
```swift
.background(Color(.secondarySystemGroupedBackground))
.cornerRadius(16)
```

### iOS async observation boilerplate
**Source:** `iosApp/.../Views/Overview/OverviewView.swift` lines 51–81
**Apply to:** Every new StateFlow observed in `OverviewView` and `NutritionGoalsEditorView`
```swift
for try await value in asyncSequence(for: viewModel.myFlowFlow) {
    self.localState = value
}
```

### Snackbar error handling (Android)
**Source:** `androidApp/.../screens/TemplateEditorScreen.kt` lines 72–86
**Apply to:** `NutritionGoalsEditorScreen` save error path
```kotlin
LaunchedEffect(saveResult) {
    when (val result = saveResult) {
        is SaveResult.Error -> { snackbarHostState.showSnackbar(result.message); viewModel.clearSaveResult() }
        is SaveResult.Success -> { navController.popBackStack() }
        null -> {}
    }
}
```

---

## No Analog Found

All files have close analogs in the codebase. No entries in this section.

---

## Metadata

**Analog search scope:** `shared/src/commonMain/`, `shared/src/commonTest/`, `androidApp/src/androidMain/`, `iosApp/iosApp/Views/`
**Files scanned:** ~15 source files read directly
**Pattern extraction date:** 2026-04-28

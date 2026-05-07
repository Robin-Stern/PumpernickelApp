---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 09
type: execute
wave: 7
depends_on: [07, 08]
files_modified:
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
autonomous: false
requirements:
  - REQ-AI-04
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "An AI affordance (sparkle icon) lives in the TopAppBar of NutritionDailyLogScreen and navigates to AiMealGenRoute"
    - "AiMealGenScreen renders all 8 RecipeAiUiState branches: Loading, NoKey, RemainingExhausted, Form, Generating, Preview, Error, Saved with German UI copy"
    - "AiMealGenRoute is registered in MainScreen Nutrition-tab NavHost"
    - "AiPreviewSheet's Recipe branch renders ingredient list + step list + fit indicator (kcal/protein/fat/carb/sugar deltas)"
    - "RemainingExhausted shows the empty state explaining why no recipe was generated (REQ-AI-04 UAT #3)"
    - "All five AiError classes have distinct German copy + correct action button (D-18-08)"
  artifacts:
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt"
      provides: "Compose screen for F8 form + states"
      contains: "fun AiMealGenScreen"
      min_lines: 120
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt"
      provides: "AiPreviewContent.Recipe branch + RecipePreviewBody composable"
      contains: "is AiPreviewContent.Recipe"
  key_links:
    - from: "NutritionDailyLogScreen TopAppBar"
      to: "AiMealGenRoute"
      via: "IconButton onClick navigate"
      pattern: "AiMealGenRoute"
    - from: "AiMealGenScreen.NoKey branch"
      to: "AiSettingsRoute"
      via: "Open AI Settings button"
      pattern: "navigate\\(AiSettingsRoute\\)"
    - from: "AiPreviewSheet.RecipePreviewBody"
      to: "MacrosFitIndicator"
      via: "render delta percentages"
      pattern: "deltaKcalPercent|deltaProteinPercent"
---

<objective>
Ship the F8 Meal AI Android UI: entry button, screen with all sealed states, and the Recipe branch of AiPreviewSheet.

UI surfaces:
1. **NutritionDailyLogScreen** — gain a sparkles TopAppBar action navigating to `AiMealGenRoute`.
2. **AiMealGenScreen** — render all 8 RecipeAiUiState branches:
   - `Loading` → centred CircularProgressIndicator (state during init while computeRemaining runs).
   - `NoKey` → empty-state card linking to AiSettingsRoute (D-18-07).
   - `RemainingExhausted` → empty-state explaining "Du hast deine Tagesziele bereits erreicht" with the remaining macros displayed (REQ-AI-04 UAT #3).
   - `Form(remaining)` → display the remaining macros + a "Restliche Makros füllen" button → calls `viewModel.generate()`. (Per CONTEXT.md "Claude's discretion whether F8 needs a form at all" — we keep it minimal: a panel of remaining-macros readouts + the generate button.)
   - `Generating` → static skeleton (5 ingredient placeholder rows) + Cancel button.
   - `Preview` → push `AiPreviewSheet(content = AiPreviewContent.Recipe(preview), ...)`.
   - `Error` → per-class copy + per-class action button (same five-class branch as Plan 07).
   - `Saved` → `LaunchedEffect(Unit) { navController.popBackStack() }`.

3. **AiPreviewSheet** — extend the sealed `AiPreviewContent` (created in Plan 07) with `Recipe(preview: RecipeAiPreview)`. Add a `RecipePreviewBody` composable rendering: name, ingredient list with "amountGrams" (e.g. "150 g Hafer"), steps (numbered list), and the fit indicator (5 macro deltas with green/yellow color coding).

4. **MainScreen** — register `composable<AiMealGenRoute> { AiMealGenScreen(nutritionNavController) }` in the Nutrition-tab NavHost.

Includes a human-verify checkpoint at the end.

Purpose: REQ-AI-04 (recipe within +/-10% of remaining), REQ-AI-08 (per-class errors). Closes D-18-01 (Nutrition-tab AI button), D-18-04 (CTA + remaining-macros gate), D-18-07 (no-key empty state), D-18-08 (per-class error copy), D-18-10 (recipe + ingredient pair preview), D-18-12 (preview-then-save), D-18-16 (skeleton + Cancel).
Output: 4 modified Android files. iOS surfaces in Plan 10.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt

<interfaces>
RecipeAiUiState (Plan 08) sealed branches:
- Loading
- NoKey
- RemainingExhausted(remaining: RemainingMacros)
- Form(remaining: RemainingMacros)
- Generating
- Preview(preview: RecipeAiPreview, originatingRemaining: RemainingMacros)
- Error(error: AiError, originatingRemaining: RemainingMacros?)
- Saved

RecipeAiViewModel actions:
- generate() / cancel() / save() / discardPreview() / retryFromError() / refreshRemaining()

AiPreviewSheet from Plan 07 (current shape):
- sealed AiPreviewContent { data class Workout(val preview: WorkoutAiPreview) : AiPreviewContent() }
- fun AiPreviewSheet(content: AiPreviewContent, onSaveAll: () -> Unit, onDiscard: () -> Unit)
- private fun WorkoutPreviewBody(preview: WorkoutAiPreview)

This plan extends AiPreviewSheet with:
- AiPreviewContent.Recipe(val preview: RecipeAiPreview) sealed branch
- private fun RecipePreviewBody(preview: RecipeAiPreview) composable
- when-branch dispatch to RecipePreviewBody in the main AiPreviewSheet body

NutritionDailyLogScreen TopAppBar — verify by reading the file. Existing add affordance is already present (the user creates Recipes / Foods from this screen). Add an `IconButton(onClick = { nutritionNavController.navigate(AiMealGenRoute) }) { Icon(Icons.Filled.AutoAwesome, "KI-Mahlzeit generieren") }` adjacent.

The error-class copy table from Plan 07 (verbatim — apply identically here):
- Timeout → "Generierung hat zu lange gedauert" + "Wiederholen"
- Network → "Keine Internetverbindung" + "Wiederholen"
- AuthOrQuota → "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht" + "AI-Einstellungen öffnen"
- Provider → "Der KI-Anbieter hat ein Problem" + "Wiederholen"
- SchemaInvalid → "Die KI-Antwort war nicht verwertbar" + "Wiederholen"
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Extend AiPreviewSheet with Recipe branch</name>
  <files>androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt</files>
  <read_first>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt
  </read_first>
  <action>
Modify `AiPreviewSheet.kt` (created in Plan 07) to extend the sealed AiPreviewContent and dispatch to the new RecipePreviewBody.

**Diff 1**: Locate the sealed class declaration:
```kotlin
sealed class AiPreviewContent {
    data class Workout(val preview: WorkoutAiPreview) : AiPreviewContent()
    // Plan 09 will add: data class Recipe(...)
}
```
Replace the comment line with:
```kotlin
data class Recipe(val preview: com.pumpernickel.domain.ai.RecipeAiPreview) : AiPreviewContent()
```
(Use the fully-qualified import name to avoid name shadowing with `kotlinx.coroutines.flow.combine`'s `Recipe` — or simply add `import com.pumpernickel.domain.ai.RecipeAiPreview` at top.)

**Diff 2**: Locate the `when (content) { is AiPreviewContent.Workout -> ... }` block and add the Recipe branch:
```kotlin
when (content) {
    is AiPreviewContent.Workout -> WorkoutPreviewBody(content.preview)
    is AiPreviewContent.Recipe -> RecipePreviewBody(content.preview)
}
```

**Diff 3**: Add a new private composable `RecipePreviewBody`:

```kotlin
@Composable
private fun RecipePreviewBody(preview: com.pumpernickel.domain.ai.RecipeAiPreview) {
    val recipe = preview.recipe
    val fits = preview.fitsIndicator

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(recipe.name, style = MaterialTheme.typography.titleMedium)

        if (preview.inlineNewFoods.isNotEmpty()) {
            Text(
                text = "${preview.inlineNewFoods.size} neue Lebensmittel werden mit angelegt",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Zutaten", style = MaterialTheme.typography.titleSmall)
        recipe.ingredients.forEach { ing ->
            Text(
                text = "• ${ing.amountGrams.toInt()} g — ${ing.foodName}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (recipe.steps.isNotEmpty()) {
            Text("Zubereitung", style = MaterialTheme.typography.titleSmall)
            recipe.steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Makro-Übereinstimmung", style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (fits.fitsAll)
                "Alle Makros liegen innerhalb von 10%."
            else
                "Außerhalb der 10%-Toleranz auf mindestens einem Makro.",
            style = MaterialTheme.typography.bodySmall,
            color = if (fits.fitsAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
        )
        FitDeltaRow("kcal", fits.deltaKcalPercent)
        FitDeltaRow("Protein", fits.deltaProteinPercent)
        FitDeltaRow("Fett", fits.deltaFatPercent)
        FitDeltaRow("KH", fits.deltaCarbsPercent)
        FitDeltaRow("Zucker", fits.deltaSugarPercent)
    }
}

@Composable
private fun FitDeltaRow(label: String, deltaPercent: Double) {
    val display = if (!deltaPercent.isFinite()) "—" else "${if (deltaPercent >= 0) "+" else ""}${deltaPercent.toInt()}%"
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(display, style = MaterialTheme.typography.bodySmall)
    }
}
```

Add the imports:
```kotlin
import androidx.compose.foundation.layout.Row
import com.pumpernickel.domain.ai.RecipeAiPreview
```

Do NOT remove the existing Workout branch or `WorkoutPreviewBody` composable. Do NOT change the AiPreviewSheet outer composable signature — `(content, onSaveAll, onDiscard)` stays as-is so Plan 07's call site continues to work.
  </action>
  <verify>
    <automated>grep -E "is AiPreviewContent.Recipe" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt && grep -E "fun RecipePreviewBody" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt && grep -E "data class Recipe" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "data class Recipe" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `1`.
    - `grep -c "is AiPreviewContent.Recipe" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "fun RecipePreviewBody" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "data class Workout" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1` (Plan 07 retained).
    - `grep -c "fun WorkoutPreviewBody" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1` (Plan 07 retained).
    - `grep -c "deltaKcalPercent" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `1`.
    - `grep -c "Zutaten" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `1`.
    - `grep -c "Makro-Übereinstimmung\\|Makro" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `1`.
  </acceptance_criteria>
  <done>AiPreviewSheet has both Workout and Recipe branches; RecipePreviewBody renders ingredients + steps + fit indicator.</done>
</task>

<task type="auto">
  <name>Task 2: Create AiMealGenScreen + add TopAppBar action on NutritionDailyLogScreen + register MainScreen route</name>
  <files>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
  </files>
  <read_first>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt
  </read_first>
  <action>
**File 1: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt**

```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.AiSettingsRoute
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.RemainingMacros
import com.pumpernickel.presentation.ai.RecipeAiUiState
import com.pumpernickel.presentation.ai.RecipeAiViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMealGenScreen(
    navController: NavHostController,
    viewModel: RecipeAiViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KI-Mahlzeit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is RecipeAiUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is RecipeAiUiState.NoKey -> NoKeyEmpty(onOpenSettings = { navController.navigate(AiSettingsRoute) })
                is RecipeAiUiState.RemainingExhausted -> ExhaustedEmpty(state.remaining)
                is RecipeAiUiState.Form -> FormBody(
                    remaining = state.remaining,
                    onGenerate = viewModel::generate
                )
                is RecipeAiUiState.Generating -> GeneratingBody(onCancel = viewModel::cancel)
                is RecipeAiUiState.Preview -> {
                    AiPreviewSheet(
                        content = AiPreviewContent.Recipe(state.preview),
                        onSaveAll = viewModel::save,
                        onDiscard = viewModel::discardPreview
                    )
                }
                is RecipeAiUiState.Error -> ErrorBody(
                    error = state.error,
                    onRetry = viewModel::retryFromError,
                    onOpenSettings = { navController.navigate(AiSettingsRoute) }
                )
                is RecipeAiUiState.Saved -> {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }
}

@Composable
private fun NoKeyEmpty(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Du hast noch keinen API-Schlüssel.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Öffne die KI-Einstellungen und füge einen Schlüssel hinzu, um Mahlzeiten generieren zu lassen.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) { Text("KI-Einstellungen öffnen") }
    }
}

@Composable
private fun ExhaustedEmpty(remaining: RemainingMacros) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Du hast deine Tagesziele bereits erreicht.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Verbleibend: ${remaining.kcal.toInt()} kcal — keine sinnvolle Mahlzeit zu generieren.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FormBody(remaining: RemainingMacros, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Restliche Tagesziele", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kalorien: ${remaining.kcal.toInt()} kcal")
                Text("Protein: ${remaining.protein.toInt()} g")
                Text("Fett: ${remaining.fat.toInt()} g")
                Text("Kohlenhydrate: ${remaining.carbs.toInt()} g")
                Text("Zucker: ${remaining.sugar.toInt()} g")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restliche Makros füllen") }
    }
}

@Composable
private fun GeneratingBody(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Static skeleton (D-18-16) — 5 placeholder ingredient rows.
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) { Card(modifier = Modifier.fillMaxSize()) {} }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abbrechen") }
    }
}

@Composable
private fun ErrorBody(
    error: AiError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val (message, action, isAuthError) = when (error) {
        is AiError.Timeout -> Triple("Generierung hat zu lange gedauert.", "Wiederholen", false)
        is AiError.Network -> Triple("Keine Internetverbindung.", "Wiederholen", false)
        is AiError.AuthOrQuota -> Triple(
            "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht.",
            "AI-Einstellungen öffnen", true
        )
        is AiError.Provider -> Triple("Der KI-Anbieter hat ein Problem.", "Wiederholen", false)
        is AiError.SchemaInvalid -> Triple("Die KI-Antwort war nicht verwertbar.", "Wiederholen", false)
        is AiError.Cancelled -> Triple("Abgebrochen.", "Zurück", false)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (isAuthError) onOpenSettings else onRetry) { Text(action) }
    }
}
```

**File 2: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt** — add a sparkles TopAppBar action.

Locate the existing TopAppBar block. In its `actions = { ... }` lambda (or add `actions = { ... }` if missing), append:

```kotlin
IconButton(onClick = { navController.navigate(AiMealGenRoute) }) {
    Icon(Icons.Filled.AutoAwesome, contentDescription = "KI-Mahlzeit generieren")
}
```

Add imports:
```kotlin
import androidx.compose.material.icons.filled.AutoAwesome
import com.pumpernickel.android.ui.navigation.AiMealGenRoute
```

**File 3: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt** — register AiMealGenRoute in the Nutrition-tab NavHost.

Locate the Nutrition-tab `NavHost { ... }` block (currently has NutritionDailyLogRoute, NutritionFoodEntryRoute, NutritionRecipeListRoute, NutritionRecipeCreationRoute). Add:

```kotlin
composable<AiMealGenRoute> {
    AiMealGenScreen(navController = nutritionNavController)
}
```

Add the import:
```kotlin
import com.pumpernickel.android.ui.screens.AiMealGenScreen
```

Do NOT touch the Workout-tab NavHost block (Plan 07's AiWorkoutGenRoute registration must remain).
  </action>
  <verify>
    <automated>grep -E "fun AiMealGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt && grep -E "RecipeAiUiState" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt && grep -E "AiMealGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt && grep -E "composable<AiMealGenRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "fun AiMealGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "RecipeAiUiState.Loading" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.NoKey" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.RemainingExhausted" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.Form" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.Generating" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.Preview" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.Error" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "RecipeAiUiState.Saved" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns at least `1`.
    - `grep -c "AiPreviewContent.Recipe" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "Du hast deine Tagesziele bereits erreicht" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1` (REQ-AI-04 UAT #3).
    - `grep -c "Restliche Makros füllen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.AuthOrQuota" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.Network" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.Timeout" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.Provider" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.SchemaInvalid" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt` returns exactly `1`.
    - `grep -c "AiMealGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt` returns at least `1`.
    - `grep -c "AutoAwesome" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionDailyLogScreen.kt` returns at least `1`.
    - `grep -c "composable<AiMealGenRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns exactly `1`.
    - `grep -c "AiMealGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns at least `2` (import + use).
    - `grep -c "composable<AiWorkoutGenRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns exactly `1` (Plan 07 retained).
  </acceptance_criteria>
  <done>AiMealGenScreen handles all 8 sealed states; sparkles entry on NutritionDailyLogScreen; route registered in MainScreen; AiPreviewSheet's Recipe branch wired.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3 — Human verify: F8 Meal AI flow end-to-end</name>
  <what-built>
The Android Meal AI flow is reachable from the Nutrition tab's daily log (sparkles icon). The user with no API key sees the no-key empty state. With a valid key, the screen displays remaining macros; tapping "Restliche Makros füllen" generates a recipe whose macros are within +/-10% of remaining. Save persists the Recipe (with new Foods if needed) into the existing collection.
  </what-built>
  <how-to-verify>
1. Build and install Android.
2. Configure a valid API key in AI Settings. Log a few foods today (so remaining > 100 kcal but < goal). Open Nutrition tab > sparkles icon.
3. Confirm the Form shows the 5 remaining macros (kcal, protein, fat, carbs, sugar). Confirm "Restliche Makros füllen" button is enabled.
4. Tap the button. Confirm static skeleton (5 placeholder rows) appears with Cancel.
5. Within ~10s the preview sheet opens with: recipe name, ingredients (each with grams), preparation steps, and the macro fit indicator. Confirm `+%`/`-%` deltas display for all 5 macros.
6. Tap "Alle speichern". Confirm the screen pops back to the Nutrition daily log.
7. Open Nutrition > Recipes (NutritionRecipeList). Confirm the new recipe appears with the AI-emitted name. Tap into it — confirm ingredients reflect what the preview showed.
8. **RemainingExhausted path**: Log enough food to push remaining kcal below 100. Tap the sparkles. Confirm the empty state "Du hast deine Tagesziele bereits erreicht" displays — no LLM call is made.
9. **No-key path**: Clear the key in AI Settings. Tap sparkles. Confirm "Du hast noch keinen API-Schlüssel" empty state with "AI-Einstellungen öffnen" button.
10. **Auth error path**: Save a bogus API key. Tap sparkles, then the generate button. Confirm "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht" + "AI-Einstellungen öffnen".
11. **Cancel path**: Start generation, tap Cancel. Confirm screen returns to Form (no error toast).
12. Verify the saved Recipe row has `source = "AI"`: `adb shell run-as com.pumpernickel sqlite3 /data/data/com.pumpernickel/databases/<db> 'SELECT id, name, source FROM recipes ORDER BY rowid DESC LIMIT 5;'`
13. Verify any new Food rows the AI emitted have `source = "AI"`: `... 'SELECT id, name, source FROM foods WHERE source = "AI" ORDER BY rowid DESC LIMIT 10;'`
  </how-to-verify>
  <resume-signal>Type "approved" or describe issues (e.g., "fit indicator missing", "saved recipe doesn't appear in Recipes list", "RemainingExhausted not triggered when kcal < 100").</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| LLM-emitted Food entries → Foods table | New Foods are persisted with source="AI" only after explicit Save |
| Today's ConsumptionEntry log → user message body | Trusted local data |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-09-01 | Tampering | RemainingExhausted bypass | mitigate | The state-machine ensures `Generating` is reachable only from `Form`, and `Form` is only reached when `remaining.isExhausted == false`. The only way to enter `Generating` is via `viewModel.generate()` which checks the active state. |
| T-18-09-02 | Information disclosure | API key leakage in error UI | mitigate | AuthOrQuota copy is generic ("ungültig oder Kontingent aufgebraucht") — never echoes the key. |
| T-18-09-03 | Repudiation | User unaware foods were created | mitigate | RecipePreviewBody shows "${count} neue Lebensmittel werden mit angelegt" before save (D-18-10 transparency). |
</threat_model>

<verification>
- AiPreviewSheet has both Workout and Recipe branches; backwards compatible with Plan 07's call site.
- AiMealGenScreen handles all 8 sealed UiState branches.
- All 5 AiError classes have distinct German copy + correct action.
- Sparkles entry on NutritionDailyLogScreen; AiMealGenRoute registered in Nutrition-tab NavHost.
- Human-verify passes all 13 steps.
</verification>

<success_criteria>
- F8 entry-to-save flow is end-to-end functional on Android (REQ-AI-04).
- Recipe is reusable in the existing Recipes list (REQ-AI-04 UAT #2).
- RemainingExhausted gate prevents wasted LLM calls (REQ-AI-04 UAT #3).
- Per-class error UX (REQ-AI-08).
- Recipe macros within +/-10% of remaining surfaces in fits indicator.
- AI-authored Foods + Recipe carry `source = "AI"` for future filtering.
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-09-SUMMARY.md` with: file list, AiPreviewSheet final shape (sealed + 2 bodies), the 8 sealed states verified, error-state copy verification, and human-verify outcome.
</output>

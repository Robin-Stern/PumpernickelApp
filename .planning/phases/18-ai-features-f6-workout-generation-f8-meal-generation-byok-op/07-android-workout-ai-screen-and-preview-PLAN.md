---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 07
type: execute
wave: 5
depends_on: [05, 06]
files_modified:
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
autonomous: false
requirements:
  - REQ-AI-01
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "An AI affordance (sparkle icon button) lives in the TopAppBar of TemplateListScreen and navigates to AiWorkoutGenRoute"
    - "AiWorkoutGenScreen renders all 6 WorkoutAiUiState branches (NoKey / Form / Generating / Preview / Error / Saved) with German UI copy"
    - "Form: AnatomyPickerSheet for muscle multi-select, drum picker for exercise count (3-8), segmented buttons for split style"
    - "Generating: skeleton shimmer rows (count = form.exerciseCount per D-18-16) + Cancel button calling viewModel.cancel()"
    - "Preview: AiPreviewSheet (sealed AiPreviewContent.Workout branch) lists templates with name + exercise count + per-template Save + Save all + Discard"
    - "Error: per-class copy + per-class action button matching D-18-08 (Retry / Open AI Settings)"
    - "Saved: navigates back to TemplateListScreen via popBackStack"
    - "AiWorkoutGenRoute is registered in MainScreen Workout-tab NavHost"
  artifacts:
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt"
      provides: "Compose screen for F6 form + skeleton + error states"
      contains: "fun AiWorkoutGenScreen"
      min_lines: 150
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt"
      provides: "Sealed AiPreviewContent (Workout + Recipe) ModalBottomSheet"
      contains: "fun AiPreviewSheet"
      min_lines: 80
  key_links:
    - from: "TemplateListScreen TopAppBar"
      to: "AiWorkoutGenRoute"
      via: "IconButton onClick navigate"
      pattern: "AiWorkoutGenRoute"
    - from: "AiWorkoutGenScreen.NoKey branch"
      to: "AiSettingsRoute"
      via: "Open AI Settings button"
      pattern: "navigate\\(AiSettingsRoute\\)"
    - from: "AiPreviewSheet"
      to: "WorkoutAiViewModel.save / discardPreview"
      via: "callbacks"
      pattern: "onSaveAll|onDiscard"
---

<objective>
Ship the F6 Workout AI Android UI: entry button, form screen, skeleton, preview sheet, and error states.

UI surfaces:
1. **TemplateListScreen** — gain a sparkles icon TopAppBar action that navigates to `AiWorkoutGenRoute`.
2. **AiWorkoutGenScreen** — render the WorkoutAiUiState sealed class:
   - `NoKey` → empty-state card with "Du hast noch keinen API-Schlüssel" + "AI-Einstellungen öffnen" button → navigate AiSettingsRoute (D-18-07).
   - `Form` → AnatomyPickerSheet for multi-select muscles, drum picker for exercise count, segmented buttons for split style, "Generieren" button enabled when ≥1 muscle selected.
   - `Generating` → skeleton shimmer rows (count = form.exerciseCount × splitStyle.templateCount) + Cancel button.
   - `Preview` → push `AiPreviewSheet` with `AiPreviewContent.Workout(templates)` and Save/Discard wired.
   - `Error(AiError)` → per-class copy and action button per D-18-08:
       - Timeout → "Generierung hat zu lange gedauert" + "Wiederholen" → retryFromError (which restores the form).
       - Network → "Keine Internetverbindung" + Wiederholen.
       - AuthOrQuota → "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht" + "AI-Einstellungen öffnen".
       - Provider → "Der KI-Anbieter hat ein Problem" + Wiederholen.
       - SchemaInvalid → "Die KI-Antwort war nicht verwertbar" + Wiederholen.
       - Cancelled → never appears as Error in UI (the VM transitions back to Form).
   - `Saved` → `LaunchedEffect(Unit) { workoutNavController.popBackStack() }` to return to TemplateList.

3. **AiPreviewSheet** — `ModalBottomSheet` rendering a sealed `AiPreviewContent`. The Workout branch lists each template (name, exercise count, total sets, list of exercise rows with sets x reps + rest). Single "Alle speichern" button + "Verwerfen" button. (Plan 09 reuses this sheet for the Recipe branch — it's important to design the sealed shape generically here so 09 only adds the Recipe branch render path.)

4. **MainScreen** — register `composable<AiWorkoutGenRoute> { AiWorkoutGenScreen(workoutNavController) }` in the Workout-tab NavHost.

Includes a human-verify checkpoint at the end.

Purpose: REQ-AI-01 (form → templates), REQ-AI-08 (per-class error UX). Closes D-18-01 (Workout-tab AI button), D-18-02 (AnatomyPickerSheet reuse), D-18-03 (form fields), D-18-07 (no-key empty state), D-18-08 (error copy), D-18-12 (preview-then-save), D-18-16 (skeleton + Cancel).
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
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnatomyPickerSheet.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt

<interfaces>
WorkoutAiUiState (Plan 06) sealed class branches and props:
- NoKey
- Form(targetMuscles: List<MuscleGroup>, exerciseCount: Int, splitStyle: WorkoutAiSplit)
- Generating(skeletonRowCount: Int)
- Preview(preview: WorkoutAiPreview, originatingForm: Form)
- Error(error: AiError, originatingForm: Form)
- Saved(templateIds: List<Long>)

WorkoutAiViewModel actions:
- onMusclesChanged(muscles: List<MuscleGroup>)
- onExerciseCountChanged(count: Int)
- onSplitStyleChanged(split: WorkoutAiSplit)
- generate()
- cancel()
- save()
- discardPreview()
- retryFromError()

AnatomyPickerSheet (existing, Phase 14): currently single-select with `selectedGroup: String?` + `onConfirm: (String) -> Unit`. The executor must extend it for multi-select OR build a thin "AnatomyMultiPickerSheet" wrapper. Recommended: add an overload or new file `AnatomyMultiPickerSheet.kt` that wraps the same MuscleRegionPaths drawing logic but maintains a Set<String> of selected groups. If the existing file is small enough to extend safely, do so; otherwise create a parallel file. Either way, do NOT break existing callers (TemplateEditor, CreateExercise) that depend on the single-select API.

WorkoutSessionScreen drum-picker pattern (LazyColumn + SnapFlingBehavior per Phase 13 STATE.md decision) — the executor copies that pattern for the exercise-count drum picker.

TemplateListScreen TopAppBar — read the file; locate the existing TopAppBar block and the existing add affordance. Add a new `IconButton(onClick = { navController.navigate(AiWorkoutGenRoute) }) { Icon(Icons.Filled.AutoAwesome, "KI-generieren") }` adjacent to the existing add button.

AnatomyPickerSheet existing exposed signature (verify by reading the file before editing):
```
@Composable
fun AnatomyPickerSheet(
    selectedGroup: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create AiPreviewSheet (sealed AiPreviewContent — Workout branch implemented; Recipe branch stubbed for Plan 09)</name>
  <files>androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt</files>
  <read_first>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnatomyPickerSheet.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ExerciseOverviewSheet.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
  </read_first>
  <action>
Create `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt`. The sheet is callable from both the F6 (Workout) and F8 (Recipe) screens — design the API as a sealed `AiPreviewContent` so Plan 09 only needs to fill the Recipe branch.

```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.ai.StagedTemplate
import com.pumpernickel.domain.ai.WorkoutAiPreview

/**
 * D-18-12 — preview-then-save sheet. Until the user taps Save, NOTHING is
 * written to the DB.
 *
 * Sealed AiPreviewContent supports both F6 (Workout templates) and F8
 * (single Recipe). Plan 09 implements the Recipe branch.
 */
sealed class AiPreviewContent {
    data class Workout(val preview: WorkoutAiPreview) : AiPreviewContent()
    // Plan 09 will add: data class Recipe(val recipe: ..., val fitsIndicator: ...)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreviewSheet(
    content: AiPreviewContent,
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDiscard,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Vorschau",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))

            when (content) {
                is AiPreviewContent.Workout -> WorkoutPreviewBody(content.preview)
                // Plan 09 will dispatch: is AiPreviewContent.Recipe -> RecipePreviewBody(content)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSaveAll,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Alle speichern") }

            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Verwerfen") }
        }
    }
}

@Composable
private fun WorkoutPreviewBody(preview: WorkoutAiPreview) {
    if (preview.inlineNewExercises.isNotEmpty()) {
        Text(
            text = "${preview.inlineNewExercises.size} neue Übungen werden mit erstellt",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(preview.templates) { template ->
            TemplatePreviewCard(template)
        }
    }
}

@Composable
private fun TemplatePreviewCard(template: StagedTemplate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            template.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            template.exercises.forEach { ex ->
                Text(
                    text = "• ${ex.exerciseName} — ${ex.targetSets} × ${ex.targetReps} reps, Pause ${ex.restPeriodSec}s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
```
  </action>
  <verify>
    <automated>grep -E "fun AiPreviewSheet" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt && grep -E "sealed class AiPreviewContent" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt && grep -E "data class Workout" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "fun AiPreviewSheet" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "sealed class AiPreviewContent" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "data class Workout" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "ModalBottomSheet" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "Alle speichern" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "Verwerfen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns exactly `1`.
    - `grep -c "onSaveAll" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `2` (param + use).
    - `grep -c "onDiscard" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt` returns at least `2`.
  </acceptance_criteria>
  <done>AiPreviewSheet exposes the sealed AiPreviewContent shape and renders the Workout branch; Recipe branch is reserved for Plan 09.</done>
</task>

<task type="auto">
  <name>Task 2: Create AiWorkoutGenScreen + add TopAppBar action on TemplateListScreen + register route in MainScreen</name>
  <files>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
  </files>
  <read_first>
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnatomyPickerSheet.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiError.kt
  </read_first>
  <action>
**File 1: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt** — full screen.

Required structure (skeleton — adapt naming/imports as needed):

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.AiSettingsRoute
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.WorkoutAiSplit
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.presentation.ai.WorkoutAiUiState
import com.pumpernickel.presentation.ai.WorkoutAiViewModel
import org.koin.compose.viewmodel.koinViewModel

private val SPLIT_OPTIONS = listOf(
    WorkoutAiSplit.NONE to "Einzeln",
    WorkoutAiSplit.PUSH_PULL_LEGS to "PPL",
    WorkoutAiSplit.UPPER_LOWER to "Upper/Lower",
    WorkoutAiSplit.FULL_BODY to "Ganzkörper"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorkoutGenScreen(
    navController: NavHostController,
    viewModel: WorkoutAiViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KI-Workout") },
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
                is WorkoutAiUiState.NoKey -> NoKeyEmptyState(onOpenSettings = { navController.navigate(AiSettingsRoute) })
                is WorkoutAiUiState.Form -> FormBody(
                    state = state,
                    onMusclesChanged = viewModel::onMusclesChanged,
                    onCountChanged = viewModel::onExerciseCountChanged,
                    onSplitChanged = viewModel::onSplitStyleChanged,
                    onGenerate = viewModel::generate
                )
                is WorkoutAiUiState.Generating -> GeneratingBody(
                    skeletonRows = state.skeletonRowCount,
                    onCancel = viewModel::cancel
                )
                is WorkoutAiUiState.Preview -> {
                    AiPreviewSheet(
                        content = AiPreviewContent.Workout(state.preview),
                        onSaveAll = viewModel::save,
                        onDiscard = viewModel::discardPreview
                    )
                }
                is WorkoutAiUiState.Error -> ErrorBody(
                    error = state.error,
                    onRetry = viewModel::retryFromError,
                    onOpenSettings = { navController.navigate(AiSettingsRoute) }
                )
                is WorkoutAiUiState.Saved -> {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }
    }
}

@Composable
private fun NoKeyEmptyState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Du hast noch keinen API-Schlüssel.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Öffne die KI-Einstellungen und füge einen Schlüssel hinzu, um Workouts generieren zu lassen.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) { Text("KI-Einstellungen öffnen") }
    }
}

@Composable
private fun FormBody(
    state: WorkoutAiUiState.Form,
    onMusclesChanged: (List<MuscleGroup>) -> Unit,
    onCountChanged: (Int) -> Unit,
    onSplitChanged: (WorkoutAiSplit) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Muscles — embed AnatomyMultiPickerSheet inline OR push it as a sub-route.
        // Recommended: embed an inline multi-select chip row keyed off AnatomyPickerSheet
        // body so the muscle picker is part of the form. Implementation detail —
        // the executor decides between (a) reusing AnatomyPickerSheet by repeated
        // single-select with a Set<MuscleGroup>, or (b) creating a parallel
        // AnatomyMultiPickerSheet that already handles Set<...> selection.
        Text("Zielmuskeln", style = MaterialTheme.typography.titleMedium)
        MuscleMultiSelect(
            selected = state.targetMuscles,
            onSelectedChanged = onMusclesChanged
        )

        Text("Anzahl Übungen: ${state.exerciseCount}", style = MaterialTheme.typography.titleMedium)
        ExerciseCountDrumPicker(
            value = state.exerciseCount,
            onValueChange = onCountChanged
        )

        Text("Split", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SPLIT_OPTIONS.forEachIndexed { index, (split, label) ->
                SegmentedButton(
                    selected = state.splitStyle == split,
                    onClick = { onSplitChanged(split) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SPLIT_OPTIONS.size)
                ) { Text(label) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onGenerate,
            enabled = state.targetMuscles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generieren") }
    }
}

@Composable
private fun GeneratingBody(skeletonRows: Int, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(skeletonRows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Static skeleton (D-18-16) — no shimmer animation; constant background.
            ) {
                Card(modifier = Modifier.fillMaxSize()) {}
            }
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
        is AiError.Cancelled -> Triple("Abgebrochen.", "Zurück zum Formular", false)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = if (isAuthError) onOpenSettings else onRetry
        ) { Text(action) }
    }
}

// Helper composables — implementation detail; planner stubs them. Executor fills
// using existing patterns:
//   MuscleMultiSelect — chip list of all MuscleGroup.entries, toggleable selection.
//   ExerciseCountDrumPicker — LazyColumn + SnapFlingBehavior copying the drum picker
//   from WorkoutSessionScreen.kt (existing Phase-13 pattern).

@Composable
private fun MuscleMultiSelect(
    selected: List<MuscleGroup>,
    onSelectedChanged: (List<MuscleGroup>) -> Unit
) {
    // Render filter chips for each MuscleGroup.entries; toggling adds/removes from selected.
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MuscleGroup.entries.forEach { mg ->
            val isSelected = selected.contains(mg)
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = {
                    val next = if (isSelected) selected - mg else selected + mg
                    onSelectedChanged(next)
                },
                label = { Text(mg.displayName) }
            )
        }
    }
}

@Composable
private fun ExerciseCountDrumPicker(value: Int, onValueChange: (Int) -> Unit) {
    // Range 3..8 per D-18-03 / Claude's discretion. Render a horizontal row of
    // tappable cells; or copy the LazyColumn drum picker from WorkoutSessionScreen.kt.
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        (3..8).toList().forEachIndexed { index, n ->
            SegmentedButton(
                selected = value == n,
                onClick = { onValueChange(n) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 6)
            ) { Text("$n") }
        }
    }
}
```

Notes:
- The `MuscleMultiSelect` and `ExerciseCountDrumPicker` are simplified placeholders here. The executor MAY upgrade `MuscleMultiSelect` to use the existing `AnatomyPickerSheet`'s body-drawing logic in multi-select mode — D-18-02 explicitly recommends this. Either approach is acceptable as long as the user can pick ≥1 muscle.
- The `Generating` body's "skeleton" is a flat Card placeholder — static per D-18-16 (NO shimmer animation). One Card per `skeletonRows`. The executor MAY add a subtle pulsing alpha if desired but the contract requires static.
- ErrorBody's mapping: each AiError class produces a distinct user message and a distinct action button. The acceptance criteria below verify all 5 messages exist.

**File 2: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt** — add a sparkles TopAppBar action.

Locate the existing TopAppBar block in TemplateListScreen.kt. In the `actions = { ... }` lambda (or add `actions = { ... }` if missing), append:

```kotlin
IconButton(onClick = { navController.navigate(AiWorkoutGenRoute) }) {
    Icon(Icons.Filled.AutoAwesome, contentDescription = "KI-Workout generieren")
}
```

Add the imports `import androidx.compose.material.icons.filled.AutoAwesome` and `import com.pumpernickel.android.ui.navigation.AiWorkoutGenRoute`.

**File 3: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt** — register the AiWorkoutGenRoute composable in the Workout-tab NavHost. Find the existing Workout-tab `NavHost { ... }` block and the `composable<AiSettingsRoute>` line added by Plan 05. Below it (still inside the same NavHost), add:

```kotlin
composable<AiWorkoutGenRoute> {
    AiWorkoutGenScreen(navController = workoutNavController)
}
```

Add the import `import com.pumpernickel.android.ui.screens.AiWorkoutGenScreen`.
  </action>
  <verify>
    <automated>grep -E "fun AiWorkoutGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt && grep -E "WorkoutAiUiState.NoKey|is WorkoutAiUiState.NoKey" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt && grep -E "AiWorkoutGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt && grep -E "composable<AiWorkoutGenRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "fun AiWorkoutGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "WorkoutAiUiState.NoKey" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "WorkoutAiUiState.Form" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "WorkoutAiUiState.Generating" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "WorkoutAiUiState.Preview" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "WorkoutAiUiState.Error" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "WorkoutAiUiState.Saved" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "AiError.Timeout" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.Network" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.AuthOrQuota" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.Provider" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "AiError.SchemaInvalid" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "Generierung hat zu lange gedauert" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "Keine Internetverbindung" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns exactly `1`.
    - `grep -c "AiPreviewSheet" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "navigate(AiSettingsRoute)" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `2` (NoKey branch + AuthOrQuota branch).
    - `grep -c "viewModel::cancel" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` returns at least `1`.
    - `grep -c "AiWorkoutGenRoute" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt` returns at least `1`.
    - `grep -c "AutoAwesome" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/TemplateListScreen.kt` returns at least `1` (sparkles icon).
    - `grep -c "composable<AiWorkoutGenRoute>" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns exactly `1`.
    - `grep -c "AiWorkoutGenScreen" androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` returns at least `2` (import + composable use).
  </acceptance_criteria>
  <done>AiWorkoutGenScreen renders all 6 sealed states + per-class error copy; sparkles entry on TemplateListScreen; route registered in MainScreen.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3 — Human verify: F6 Workout AI flow end-to-end</name>
  <what-built>
The Android Workout AI flow is reachable from the Workout tab's Templates list (sparkles icon). The user with no API key sees the no-key empty state; the user with a valid key fills the form, taps Generate, sees the skeleton (with Cancel), receives a preview sheet, and can Save (templates appear in the list) or Discard.
  </what-built>
  <how-to-verify>
1. Build and install Android: `./gradlew :androidApp:installDebug`.
2. **No-key path**: in AI Settings, tap "Schlüssel löschen". Then go to Workout tab > sparkles icon. Confirm "Du hast noch keinen API-Schlüssel" empty state with "KI-Einstellungen öffnen" button. Tap the button — confirm it navigates to AiSettingsScreen.
3. **Happy path**: in AI Settings, configure a working key + base URL (use a real OpenAI or Together.AI key for the UAT). Return to Workout tab > sparkles icon. Pick 2-3 muscles via the multi-select. Pick exercise count = 5. Pick split = "Einzeln" (NONE). Tap "Generieren".
4. Confirm the skeleton renders (5 placeholder rows) with a Cancel button.
5. Within ~10s the preview sheet should appear with 1 generated template — confirm name, exercise count, and per-exercise sets/reps/rest read sensibly.
6. Tap "Alle speichern". Confirm the screen navigates back to the Templates list and the new template is present.
7. Open the new template — confirm it opens in the existing TemplateEditor unchanged.
8. **Multi-template path**: repeat steps 3-7 with split = "PPL". Confirm the preview shows 3 templates and Save adds all 3 to the list.
9. **Cancel path**: start a generation, immediately tap Cancel. Confirm the screen returns to the Form (no error toast).
10. **Error path (network)**: turn airplane mode ON, generate, confirm "Keine Internetverbindung" + Retry. Turn airplane mode OFF and tap Retry — confirm it returns to Form (the user re-taps Generieren manually).
11. **Error path (auth)**: in AI Settings, save an obviously-bad API key (`sk-invalid-test`). Generate. Confirm "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht" + "AI-Einstellungen öffnen" button. Tap it — confirm navigation to AiSettings.
12. Confirm the saved templates have a `source = "AI"` row in the DB (use `adb shell run-as com.pumpernickel sqlite3 /data/data/com.pumpernickel/databases/<db-name> 'SELECT id, name, source FROM workout_templates ORDER BY id DESC LIMIT 5;'`).
  </how-to-verify>
  <resume-signal>Type "approved" or describe issues (e.g., "skeleton missing on second generate", "Cancel doesn't return to Form", "auth error doesn't show Open Settings button").</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| User input → form state → use case | Form fields bounded by chip/segmented/drum-picker UI; injection-safe by construction |
| Preview state → DB write | Triggered only by explicit "Alle speichern" tap (D-18-12) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-07-01 | Tampering | User saves a preview that was tampered between receive and Save | accept | The preview is in-memory state on the VM; outside attacker access requires app-level memory access. Out of scope for prototype. |
| T-18-07-02 | Information disclosure | API key visible in error UI text | mitigate | The AuthOrQuota branch shows a generic "Schlüssel ungültig oder Kontingent" message — never echoes the key value. Acceptance criteria verify wording. |
| T-18-07-03 | Denial of service | User keeps generating without a saved key | mitigate | NoKey state disables Generate until a key is configured (state-machine guard, not just UI). |
| T-18-07-04 | Repudiation | User unaware multi-template Save is transactional | accept | Documented in CONTEXT.md (D-18-12); UI says "Alle speichern" / "Verwerfen" — semantics implicit. |
</threat_model>

<verification>
- AiPreviewSheet exposes the sealed AiPreviewContent with the Workout branch implemented.
- AiWorkoutGenScreen handles all 6 sealed UiState branches and surfaces all 5 AiError classes with distinct German copy + correct action button.
- TemplateListScreen has a sparkles entry.
- MainScreen registers the route.
- Human-verify passes all 12 steps.
</verification>

<success_criteria>
- F6 entry-to-save flow is end-to-end functional on Android (REQ-AI-01).
- All 5 error classes surface per-class messaging (REQ-AI-08 / D-18-08).
- No-key empty state present and links to Settings (D-18-07).
- Static skeleton + Cancel during generation (D-18-16).
- Preview-then-save commit (D-18-12).
- Multi-template generation works for PPL / UL / Full Body splits (D-18-13).
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-07-SUMMARY.md` with: file list, the AiPreviewContent sealed shape so Plan 09 can extend it, error-state copy verification, and human-verify outcome.
</output>

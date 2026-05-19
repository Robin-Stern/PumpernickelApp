# Phase 23: AI UX und iOS-Parität — Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 6 (5 modifiziert, 1 neu)
**Analogs found:** 6 / 6

---

## File Classification

| Neue/Modifizierte Datei | Rolle | Data Flow | Nächster Analog | Match-Qualität |
|-------------------------|-------|-----------|-----------------|----------------|
| `shared/.../domain/ai/WorkoutAiPreview.kt` | model | transform | — (direkte Erweiterung) | exact (eigene Datei) |
| `shared/.../presentation/ai/WorkoutAiViewModel.kt` | viewmodel | request-response | `WorkoutAiViewModel.kt` selbst (Erweiterung) | exact |
| `shared/.../domain/ai/WorkoutAiUseCase.kt` | service | request-response | `WorkoutAiUseCase.kt` selbst (Erweiterung) | exact |
| `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` | component | request-response | `AiWorkoutGenScreen.kt` selbst – `exerciseCount`-Stepper | exact |
| `androidApp/.../ui/screens/AiGenerationMiniBar.kt` | component | event-driven | `OverviewScreen.kt` – `AnimatedVisibility`; `ProgressGalleryScreen.kt` – `koinInject()` | role-match |
| `androidApp/.../ui/navigation/MainScreen.kt` | component | event-driven | `MainScreen.kt` selbst – `Scaffold.bottomBar`-Slot | exact |

---

## Pattern Assignments

### `shared/.../domain/ai/WorkoutAiPreview.kt` (model, transform)

**Analog:** `WorkoutAiPreview.kt` — `WorkoutAiForm` data class (Zeile 46–50)

**Aktuelles data-class-Pattern** (Zeilen 46–50):
```kotlin
// Source: shared/.../domain/ai/WorkoutAiPreview.kt, Zeilen 46–50
data class WorkoutAiForm(
    val targetMuscles: List<MuscleGroup>,
    val exerciseCount: Int,                  // 3..8 typical
    val splitStyle: WorkoutAiSplit
)
```

**Ziel-Änderung (D-23-01) — neues Feld mit Default:**
```kotlin
data class WorkoutAiForm(
    val targetMuscles: List<MuscleGroup>,
    val exerciseCount: Int,
    val splitStyle: WorkoutAiSplit,
    val setsPerExercise: Int = 3            // NEU: Range 1–6, Default 3
)
```

**Wichtig:** Default-Wert `= 3` setzen, damit alle bestehenden Callsites (`copy()`-Aufrufe, `WorkoutAiUiState.Form`-Erstellung) ohne Änderung kompilieren. Nur die expliziten Konstruktor-Aufrufe in `WorkoutAiViewModel.generate()` und `defaultForm` müssen `setsPerExercise = 3` ergänzen.

---

### `shared/.../presentation/ai/WorkoutAiViewModel.kt` (viewmodel, request-response)

**Analog:** `WorkoutAiViewModel.kt` — `onExerciseCountChanged()` + `defaultForm` (Zeilen 97–101, 183–185)

**Bestehender Handler als Kopier-Muster** (Zeilen 183–185):
```kotlin
// Source: shared/.../presentation/ai/WorkoutAiViewModel.kt, Zeilen 183–185
fun onExerciseCountChanged(count: Int) {
    val current = _uiState.value as? WorkoutAiUiState.Form ?: return
    _uiState.value = current.copy(exerciseCount = count.coerceIn(1, 12))
}
```

**Neuer Handler — 1:1-Kopie mit angepasstem Feld + Range (D-23-01):**
```kotlin
fun onSetsPerExerciseChanged(count: Int) {
    val current = _uiState.value as? WorkoutAiUiState.Form ?: return
    _uiState.value = current.copy(setsPerExercise = count.coerceIn(1, 6))
}
```

**Bestehender `defaultForm`** (Zeilen 97–101):
```kotlin
// Source: shared/.../presentation/ai/WorkoutAiViewModel.kt, Zeilen 97–101
private val defaultForm = WorkoutAiUiState.Form(
    targetMuscles = emptyList(),
    exerciseCount = 5,
    splitStyle = WorkoutAiSplit.NONE
)
```

**`defaultForm` nach Änderung:**
```kotlin
private val defaultForm = WorkoutAiUiState.Form(
    targetMuscles = emptyList(),
    exerciseCount = 5,
    splitStyle = WorkoutAiSplit.NONE,
    setsPerExercise = 3                     // NEU
)
```

**`WorkoutAiUiState.Form` — Feld ergänzen** (Zeilen 292–296):
```kotlin
// Source: shared/.../presentation/ai/WorkoutAiViewModel.kt, Zeilen 292–296
data class Form(
    val targetMuscles: List<MuscleGroup>,
    val exerciseCount: Int,
    val splitStyle: WorkoutAiSplit
) : WorkoutAiUiState()
// → setsPerExercise: Int = 3 ergänzen
```

**`generate()`-Aufruf — `WorkoutAiForm`-Konstruktor** (Zeilen 197–202):
```kotlin
// Source: shared/.../presentation/ai/WorkoutAiViewModel.kt, Zeilen 197–202
val started = generationManager.startWorkoutGeneration(
    WorkoutAiForm(
        targetMuscles = form.targetMuscles,
        exerciseCount = form.exerciseCount,
        splitStyle = form.splitStyle
        // setsPerExercise = form.setsPerExercise  ← NEU hinzufügen
    )
)
```

---

### `shared/.../domain/ai/WorkoutAiUseCase.kt` (service, request-response)

**Analog:** `WorkoutAiUseCase.kt` — `buildUserMessage()` (Zeilen 156–174)

**Bestehende Interpolation als Kopier-Muster** (Zeilen 165–173):
```kotlin
// Source: shared/.../domain/ai/WorkoutAiUseCase.kt, Zeilen 165–173
return """
    targetMuscles: $targets
    exerciseCount: ${form.exerciseCount}
    splitStyle: ${form.splitStyle.name}
    templatesExpected: ${form.splitStyle.templateCount}

    existingExercises:
    $available
""".trimIndent()
```

**Ziel-Änderung (D-23-02) — `setsPerExercise`-Zeile einfügen:**
```kotlin
return """
    targetMuscles: $targets
    exerciseCount: ${form.exerciseCount}
    setsPerExercise: ${form.setsPerExercise}
    splitStyle: ${form.splitStyle.name}
    templatesExpected: ${form.splitStyle.templateCount}

    existingExercises:
    $available
""".trimIndent()
```

**Position:** Nach `exerciseCount`, vor `splitStyle`. Keine weiteren Änderungen an `buildUserMessage()`.

---

### `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` (component, request-response)

**Analog:** `AiWorkoutGenScreen.kt` — `WorkoutFormBody` `exerciseCount`-Stepper (Zeilen 189–213)

**Bestehender Stepper als 1:1-Kopier-Muster** (Zeilen 189–213):
```kotlin
// Source: androidApp/.../ui/screens/AiWorkoutGenScreen.kt, Zeilen 189–213
SectionLabel("Anzahl Übungen")
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    IconButton(
        onClick = { onExerciseCountChanged(state.exerciseCount - 1) },
        enabled = state.exerciseCount > 1
    ) {
        Icon(Icons.Default.Remove, contentDescription = "Weniger")
    }
    Text(
        text = "${state.exerciseCount} Übungen",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
        fontWeight = FontWeight.SemiBold
    )
    IconButton(
        onClick = { onExerciseCountChanged(state.exerciseCount + 1) },
        enabled = state.exerciseCount < 12
    ) {
        Icon(Icons.Default.Add, contentDescription = "Mehr")
    }
}
```

**Neuer Sets-Stepper — direkte Kopie mit angepassten Werten (D-23-03):**
```kotlin
// Position: nach exerciseCount-Stepper (Zeile 213), vor SectionLabel("Aufteilung") (Zeile 215)
SectionLabel("Anzahl Sätze")
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    IconButton(
        onClick = { onSetsPerExerciseChanged(state.setsPerExercise - 1) },
        enabled = state.setsPerExercise > 1
    ) {
        Icon(Icons.Default.Remove, contentDescription = "Weniger Sätze")
    }
    Text(
        text = "${state.setsPerExercise} Sätze",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
        fontWeight = FontWeight.SemiBold
    )
    IconButton(
        onClick = { onSetsPerExerciseChanged(state.setsPerExercise + 1) },
        enabled = state.setsPerExercise < 6
    ) {
        Icon(Icons.Default.Add, contentDescription = "Mehr Sätze")
    }
}
```

**`WorkoutFormBody`-Signatur erweitern:**
```kotlin
// Source: AiWorkoutGenScreen.kt, Zeilen 156–162
@Composable
private fun WorkoutFormBody(
    state: WorkoutAiUiState.Form,
    onMusclesChanged: (List<MuscleGroup>) -> Unit,
    onExerciseCountChanged: (Int) -> Unit,
    onSplitStyleChanged: (WorkoutAiSplit) -> Unit,
    onGenerate: () -> Unit
    // + onSetsPerExerciseChanged: (Int) -> Unit  ← NEU
)
```

**Aufruf-Site (Zeilen 105–112):**
```kotlin
is WorkoutAiUiState.Form -> WorkoutFormBody(
    state = state,
    onMusclesChanged = viewModel::onMusclesChanged,
    onExerciseCountChanged = viewModel::onExerciseCountChanged,
    onSplitStyleChanged = viewModel::onSplitStyleChanged,
    onGenerate = viewModel::generate
    // + onSetsPerExerciseChanged = viewModel::onSetsPerExerciseChanged  ← NEU
)
```

**Hinweis zu read-only Preview-State (Zeilen 121–130):** Beim `Preview`-State wird `WorkoutFormBody` ohne Callbacks gerendert — dort `onSetsPerExerciseChanged = {}` übergeben.

---

### `androidApp/.../ui/screens/AiGenerationMiniBar.kt` (NEU — component, event-driven)

**Analog 1:** `OverviewScreen.kt` — `AnimatedVisibility` mit `slideOutVertically + fadeOut` (Zeile 154–162)

**Imports-Pattern aus OverviewScreen** (Zeilen 3–7):
```kotlin
// Source: androidApp/.../ui/screens/OverviewScreen.kt, Zeilen 3–7
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
```

**AnimatedVisibility-Muster** (Zeilen 154–162):
```kotlin
// Source: androidApp/.../ui/screens/OverviewScreen.kt, Zeilen 154–162
AnimatedVisibility(
    visible = bannerVisible,
    exit = slideOutVertically() + fadeOut(animationSpec = tween(300))
)
```

**Analog 2:** `ProgressGalleryScreen.kt` — `koinInject()` für Koin-Single (Zeile 149)

**koinInject-Pattern** (Zeile 149):
```kotlin
// Source: androidApp/.../ui/screens/ProgressGalleryScreen.kt, Zeile 149
val photoVault: PhotoVault = koinInject()
```

**Vollständiges Composable-Gerüst (neue Datei):**
```kotlin
package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.ai.AiGenerationManager
import com.pumpernickel.domain.ai.AiGenerationState
import com.pumpernickel.domain.ai.AiType
import org.koin.compose.koinInject

@Composable
fun AiGenerationMiniBar(
    onTap: (AiType) -> Unit,
    generationManager: AiGenerationManager = koinInject()   // Single, kein ViewModel
) {
    val genState by generationManager.state.collectAsState()

    val visible = genState is AiGenerationState.Generating || genState is AiGenerationState.Success
    val activeType: AiType? = when (val s = genState) {
        is AiGenerationState.Generating -> s.type
        is AiGenerationState.Success    -> s.type
        else -> null
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(animationSpec = tween(300)),
        exit  = slideOutVertically { it } + fadeOut(animationSpec = tween(300))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = activeType != null) { activeType?.let(onTap) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            when (val s = genState) {
                is AiGenerationState.Generating -> MiniBarGenerating(s.type)
                is AiGenerationState.Success    -> MiniBarSuccess(s.type)
                else -> {}
            }
        }
    }
}

@Composable
private fun MiniBarGenerating(type: AiType) {
    val label = if (type == AiType.WORKOUT) "KI generiert Workout…" else "KI generiert Mahlzeit…"
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "KI-Generierung läuft" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingIndicatorDots()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniBarSuccess(type: AiType) {
    val label    = if (type == AiType.WORKOUT) "Workout bereit — tippen zum Ansehen"
                   else "Mahlzeit bereit — tippen zum Ansehen"
    val a11yDesc = if (type == AiType.WORKOUT) "Workout bereit, tippen zum Ansehen"
                   else "Mahlzeit bereit, tippen zum Ansehen"
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = a11yDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TypingIndicatorDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue  = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.3f at 0
                    1.0f at (200 + index * 200)
                    0.3f at (600 + index * 200)
                    0.3f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_$index"
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        alphas.forEach { alphaState ->
            val alphaValue by alphaState
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaValue),
                        shape = CircleShape
                    )
            )
        }
    }
}
```

---

### `androidApp/.../ui/navigation/MainScreen.kt` (component, event-driven)

**Analog:** `MainScreen.kt` selbst — aktueller `Scaffold.bottomBar`-Slot (Zeilen 77–95)

**Bestehende `Scaffold.bottomBar`-Struktur** (Zeilen 77–95):
```kotlin
// Source: androidApp/.../ui/navigation/MainScreen.kt, Zeilen 77–95
Scaffold(
    bottomBar = {
        NavigationBar {
            TopLevelTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.labelRes)
                        )
                    },
                    label = { Text(stringResource(tab.labelRes)) }
                )
            }
        }
    }
) { innerPadding -> ... }
```

**Ziel-Änderung (D-23-04/05) — Column-Wrapper in `bottomBar`:**
```kotlin
Scaffold(
    bottomBar = {
        Column {                                   // NEU: Column statt direkter NavigationBar
            AiGenerationMiniBar(
                onTap = { aiType ->
                    when (aiType) {
                        AiType.WORKOUT -> {
                            selectedTab = 0                                    // Tab zuerst!
                            workoutNavController.navigate(AiWorkoutGenRoute) {
                                launchSingleTop = true
                            }
                        }
                        AiType.RECIPE -> {
                            selectedTab = 2                                    // Tab zuerst!
                            nutritionNavController.navigate(AiMealGenRoute) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
            NavigationBar {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    }
) { innerPadding -> ... }
```

**Notwendiger neuer Import:**
```kotlin
import com.pumpernickel.domain.ai.AiType
import com.pumpernickel.android.ui.screens.AiGenerationMiniBar
```

**Kritisch: `selectedTab` VOR `navController.navigate()` setzen** — sonst rendert der falsche NavHost. Aktuell: `when (selectedTab) { 0 -> NavHost(workoutNavController...) ... }` (Zeilen 103–263).

---

## Shared Patterns

### koinInject() für Koin-Singles (nicht ViewModels)
**Source:** `androidApp/.../ui/screens/ProgressGalleryScreen.kt`, Zeile 149
**Gilt für:** `AiGenerationMiniBar.kt` — `AiGenerationManager` ist Koin `single` (AiModule.kt Zeile 115: `single { AiGenerationManager(...) }`), kein `viewModel`.
```kotlin
import org.koin.compose.koinInject
// In Composable-Funktion:
val generationManager: AiGenerationManager = koinInject()
```

### StateFlow in Composable beobachten
**Source:** `androidApp/.../ui/screens/AiWorkoutGenScreen.kt`, Zeilen 73–74
**Gilt für:** `AiGenerationMiniBar.kt`
```kotlin
// Source: AiWorkoutGenScreen.kt, Zeilen 73–74
val uiState by viewModel.uiState.collectAsState()
val streamingText by viewModel.streamingText.collectAsState()
// Analog:
val genState by generationManager.state.collectAsState()
```

### AnimatedVisibility mit enter + exit
**Source:** `androidApp/.../ui/screens/OverviewScreen.kt`, Zeilen 154–162
**Gilt für:** `AiGenerationMiniBar.kt`
```kotlin
// Source: OverviewScreen.kt, Zeilen 154–162
AnimatedVisibility(
    visible = bannerVisible,
    exit = slideOutVertically() + fadeOut(animationSpec = tween(300))
)
// Für Mini-Bar: zusätzlich enter = slideInVertically { it } + fadeIn(tween(300))
```

### SectionLabel-Pattern
**Source:** `androidApp/.../ui/screens/AiWorkoutGenScreen.kt`, Zeilen 531–538
**Gilt für:** Sets-Stepper in `AiWorkoutGenScreen.kt` — bereits privater Composable, direkt wiederverwendbar
```kotlin
// Source: AiWorkoutGenScreen.kt, Zeilen 531–538
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

---

## System-Prompt-Änderung

### `shared/src/commonMain/resources/workout-system-prompt.md` (minimal, MODIFY)

**Analog:** `WorkoutAiUseCase.kt:buildUserMessage()` — Interpolations-Muster (Zeile 167)

**Pitfall vermeiden:** Aktuell steht in `workout-system-prompt.md` Zeile 46: `targetSets ∈ 3..4` — diese Hard-Rule überschreibt den `setsPerExercise`-Parameter wenn User Werte außerhalb 3–4 wählt. Ändern auf:
```
targetSets: use setsPerExercise from user message exactly
```
Kein Platzhalter im System-Prompt selbst — der Wert kommt als User-Message (D-23-02). Nur die Hard-Rule-Zeile anpassen.

---

## No Analog Found

Keine Dateien ohne Analog — alle Änderungen haben direkte Muster im Codebase.

---

## Metadata

**Analog-Suchbereich:** `androidApp/src/androidMain/`, `shared/src/commonMain/`
**Gescannte Dateien:** 9 Quelldateien direkt gelesen
**Pattern-Extraktion:** 2026-05-19

### Kritische Reihenfolge-Abhängigkeiten

1. `WorkoutAiPreview.kt` (Feld `setsPerExercise` + Default) muss zuerst — alle anderen Shared-Änderungen hängen daran
2. `WorkoutAiUiState.Form` (in `WorkoutAiViewModel.kt`) — direkt danach, damit `copy()`-Aufrufe kompilieren
3. `onSetsPerExerciseChanged()` + `defaultForm`-Update — in derselben VM-Datei
4. `WorkoutAiUseCase.buildUserMessage()` — unabhängig von UI, kann parallel
5. `AiWorkoutGenScreen.kt` — setzt kompiliertes ViewModel voraus
6. `AiGenerationMiniBar.kt` (NEU) — vollständig unabhängig, kann parallel zu Schritten 1–5
7. `MainScreen.kt` — setzt `AiGenerationMiniBar.kt` voraus

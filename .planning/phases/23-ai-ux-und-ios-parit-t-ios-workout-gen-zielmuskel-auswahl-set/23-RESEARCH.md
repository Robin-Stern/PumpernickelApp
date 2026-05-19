# Phase 23: AI UX und iOS-Parität — Research

**Researched:** 2026-05-19
**Domain:** Compose Multiplatform UI / KMP shared state — Stepper-Widget + persister animierter Overlay
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-23-01:** `WorkoutAiForm` bekommt `setsPerExercise: Int`, Range 1–6, Default 3. Feld wird in `WorkoutAiUiState.Form` + `copy()`-Aufrufen im ViewModel mitgeführt.
- **D-23-02:** Platzhalter `setsPerExercise: ${form.setsPerExercise}` in `buildUserMessage()` in `WorkoutAiUseCase` — analog zu `exerciseCount`. Kein Prompt-Rewrite.
- **D-23-03:** Android: `+/-` IconButtons wie bei `exerciseCount`. iOS: SwiftUI `Stepper`-Widget.
- **D-23-04:** Mini-Bar beobachtet `AiGenerationManager.state: StateFlow<AiGenerationState>`. Erscheint bei `Generating` oder `Success`, verschwindet bei `Idle`/`Error`.
- **D-23-05:** Position direkt über `BottomNavigation` / `TabView`. Höhe 44dp/pt. Persistent, kein Floating-Overlay.
- **D-23-06:** Generating-Zustand: animierter 3-Punkte-Typing-Indicator + Label `"KI generiert Workout…"` / `"KI generiert Mahlzeit…"` (je `AiType`).
- **D-23-07:** Success-Zustand: Checkmark-Icon + Label `"Workout bereit — tippen zum Ansehen"` / `"Mahlzeit bereit — tippen zum Ansehen"`.
- **D-23-08:** Tap → Navigation zum aktiven Gen-Screen (Workout-Tab → `AiWorkoutGenRoute`, Nutrition-Tab → `AiMealGenRoute`).
- **D-23-09:** Hintergrund-Generation bleibt erhalten. `AiGenerationManager` läuft im `SupervisorJob`-Scope unabhängig. Lokale Notification + Mini-Bar koexistieren.
- **D-23-10:** Error-Zustand: Mini-Bar verschwindet (= Idle-Verhalten). Kein Fehler-Banner in der Bar.
- **D-23-11:** iOS hat bereits alle anderen Form-Felder. Einzige echte Lücke: neuer Sets-Stepper.
- **D-23-12:** iOS-Implementierung wird in `23-IOS-HANDOFF.md` dokumentiert — User implementiert iOS selbst. Agent-Scope: Android + shared KMP.

### Claude's Discretion

- Interne Platzierung der Mini-Bar in MainScreen.kt (Scaffold.bottomBar Column-Ansatz vs. äußeres Box-Layout)
- Konkrete `InfiniteTransition`-Parameter für Dots-Animation (Frequenz, Phasenlänge, Easing)
- Ob `AiGenerationManager` direkt via `koinInject()` in MainScreen konsumiert wird oder durch einen dedizierten Mini-Bar-ViewModel

### Deferred Ideas (OUT OF SCOPE)

- Reps-Stepper
- Sets/Reps-Presets (Hypertrophie/Kraft/Endurance)
- Streaming-Text in der Mini-Bar
- Fehler-Banner in der Mini-Bar
- Prompt-Rewrite oder Qualitätsoptimierung
- Per-Task-Defaults / Modell-Picker im Gen-Screen
- iOS SwiftUI-Details (separater IOS-HANDOFF-Abschnitt)
</user_constraints>

---

## Summary

Phase 23 besteht aus zwei entkoppelten Android+KMP-Arbeiten: (1) einem neuen `setsPerExercise`-Stepper, der auf drei Schichten wirkt (shared data class, ViewModel-Handler, Android-UI), und (2) einer App-Root-Composable (`AiGenerationMiniBar`), die `AiGenerationManager.state` beobachtet und einen animierten Typing-Indicator-Banner direkt über der `NavigationBar` rendert.

Beide Teilaufgaben sind gut abgegrenzt und bauen direkt auf bestehenden Mustern auf. Der Sets-Stepper ist ein 1:1-Clone des `exerciseCount`-Steppers in `WorkoutFormBody` mit angepassten Strings und Range 1–6 statt 1–12. Die Mini-Bar folgt dem `UnlockModalHost`-Muster aus Phase 15: eine globale Koin-`single`-abhängige Composable wird am Composition-Root in `MainScreen.kt` registriert und beobachtet einen app-weiten StateFlow.

Die technische Hauptfrage der Mini-Bar ist die Integration in das `Scaffold`-Layout von `MainScreen.kt`, das aktuell `bottomBar = { NavigationBar {...} }` verwendet. Der sicherste Ansatz: `Scaffold.bottomBar` zu einer `Column { AiGenerationMiniBarContent(); NavigationBar {...} }` umbauen — kein äußeres Layout-Brechen, keine IntrinsicSize-Probleme, `NavigationBar`-Insets bleiben intakt. `AnimatedVisibility(slideInVertically + fadeIn)` sitzt dann innerhalb des `AiGenerationMiniBarContent`-Composables.

**Primäre Empfehlung:** Sets-Stepper als Wave 1, Mini-Bar als Wave 2. Beide haben null Dateiüberschneidung nach Wave 1. iOS-HANDOFF als Wave 3.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `setsPerExercise`-Datenfeld | Shared KMP (`WorkoutAiForm`, `WorkoutAiUiState.Form`) | — | Plattformübergreifende Wahrheitsquelle; beide Plattformen lesen denselben State |
| Sets-Stepper UI (Android) | Android (`WorkoutFormBody` in `AiWorkoutGenScreen.kt`) | — | Platform-spezifisches Composable; direkte Kopie des `exerciseCount`-Patterns |
| Sets-Stepper UI (iOS) | iOS (`AIWorkoutGenView.swift`) | — | User implementiert SwiftUI `Stepper` selbst |
| Prompt-Interpolation | Shared KMP (`WorkoutAiUseCase.buildUserMessage()`) | — | LLM-Aufruf ist shared; `setsPerExercise`-Zeile direkt im `userMessage`-String |
| ViewModel-Handler | Shared KMP (`WorkoutAiViewModel.onSetsPerExerciseChanged()`) | — | State-Machine-Methode analog zu `onExerciseCountChanged()` |
| Mini-Bar State-Beobachtung | Shared KMP (`AiGenerationManager.state`) | — | Singleton-Scope überlebt Navigation; keine neue Infrastruktur nötig |
| Mini-Bar UI (Android) | Android (`MainScreen.kt` → neues Composable `AiGenerationMiniBar`) | — | Direkt über `NavigationBar` im `Scaffold.bottomBar`-Slot |
| Mini-Bar Tap-Navigation (Android) | Android (Lambda in `MainScreen.kt` → `selectedTab` + optional `navController.navigate`) | — | Tab-Switch + Screen-Navigate sind Android-seitig wegen separater `NavHostController`-Instanzen |
| Mini-Bar UI (iOS) | iOS (`ContentView.swift` oder äquivalenter Root) | — | User implementiert; Android-Muster dient als Referenz |
| Dot-Animation | Android (`rememberInfiniteTransition` in `TypingIndicatorDots`) | — | Rein UI-seitige Animation; kein shared State nötig |

---

## Standard Stack

### Core (ausschließlich bestehende Dependencies — keine neue Installation)

| Library | Version | Purpose | Warum Standard |
|---------|---------|---------|----------------|
| Compose Multiplatform | 1.10.3 | Shared UI | Bereits im Projekt; `AnimatedVisibility`, `InfiniteTransition`, `Row`, `Icon` alle built-in [VERIFIED: CLAUDE.md] |
| Material Icons Extended | (via BOM) | `Icons.Default.Add`, `Remove`, `Check` | Bereits im Projekt; `Check`/`CheckCircle` für Success-State [VERIFIED: AiWorkoutGenScreen.kt imports] |
| Koin Compose | 4.2.0 | `koinInject()` in MainScreen | Bereits im Projekt; `AiGenerationManager` ist Koin `single` [VERIFIED: AiModule.kt:103] |
| KMP-NativeCoroutines | (existing) | `@NativeCoroutinesState` für iOS-Export | Bereits auf WorkoutAiViewModel; neuer `onSetsPerExerciseChanged` braucht kein weiteres Annotation [VERIFIED: WorkoutAiViewModel.kt] |

**Keine neuen Gradle-Dependencies für Phase 23.** [VERIFIED: CLAUDE.md Stack + Codebase-Analyse]

---

## Architecture Patterns

### System Architecture Diagram

```
WorkoutAiForm (shared, data class)
  └─ setsPerExercise: Int [NEU D-23-01]
       │
       ├─► WorkoutAiUiState.Form (shared, sealed)
       │     └─ onSetsPerExerciseChanged() in WorkoutAiViewModel [NEU D-23-01]
       │
       ├─► buildUserMessage() in WorkoutAiUseCase [NEU D-23-02]
       │     └─ "setsPerExercise: ${form.setsPerExercise}" → LLM
       │
       └─► WorkoutFormBody Android UI [NEU D-23-03]
             └─ Sets-Stepper Row (Copy von exerciseCount-Stepper)


AiGenerationManager.state: StateFlow<AiGenerationState>  [EXISTING singleton]
       │
       ├─ Idle / Error  ──────► MiniBar HIDDEN
       │
       ├─ Generating(type, _) ─► MiniBar VISIBLE: TypingIndicatorDots + Label
       │
       └─ Success(type, _, _) ─► MiniBar VISIBLE: Checkmark + "bereit"-Label
                                        │
                               Tap ─────► selectedTab wechseln (Android)
                                          + navController.navigate(AiWorkoutGenRoute / AiMealGenRoute)


MainScreen.kt (Android)
  Scaffold(
    bottomBar = {
      Column {
        AiGenerationMiniBar()   [NEU — observiert AiGenerationManager via koinInject()]
        NavigationBar()
      }
    }
  )
```

### Recommended Project Structure

Nur **neue** Dateien:

```
androidApp/src/androidMain/.../android/ui/screens/
└── AiGenerationMiniBar.kt    # TypingIndicatorDots + MiniBar-Composable

shared/src/commonMain/.../domain/ai/
└── WorkoutAiPreview.kt       # WorkoutAiForm: setsPerExercise ergänzt [MODIFY]

shared/src/commonMain/.../presentation/ai/
└── WorkoutAiViewModel.kt     # onSetsPerExerciseChanged + defaultForm-Update [MODIFY]

shared/src/commonMain/resources/
└── workout-system-prompt.md  # Optional: setsPerExercise in Hard-rules-Sektion [MODIFY, minimal]

androidApp/src/androidMain/.../android/ui/screens/
└── AiWorkoutGenScreen.kt     # WorkoutFormBody: Sets-Stepper Row hinzufügen [MODIFY]

androidApp/src/androidMain/.../android/ui/navigation/
└── MainScreen.kt             # bottomBar zu Column umbauen, AiGenerationMiniBar einbinden [MODIFY]

.planning/phases/23-…/
└── 23-IOS-HANDOFF.md         # SwiftUI-Spec für User [NEW]
```

### Pattern 1: Sets-Stepper (direktes Kopier-Muster)

**Was:** Stepper-Row mit `+/-` `IconButton` und zentralem `Text`, identisch zu `exerciseCount`-Pattern.
**Wann verwenden:** Immer wenn ein Integer-Wert in einem definierten Bereich schrittweise änderbar sein soll.

```kotlin
// Source: AiWorkoutGenScreen.kt, Zeile 190–213 — exerciseCount-Muster, adaptiert für setsPerExercise
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
// [VERIFIED: Codebase — AiWorkoutGenScreen.kt]
```

**Position in der Form:** Nach dem `exerciseCount`-Stepper, vor dem `Aufteilung`-Dropdown. [VERIFIED: 23-UI-SPEC.md]

### Pattern 2: TypingIndicatorDots (InfiniteTransition)

**Was:** Drei animierte Kreise mit gestaffeltem Alpha-Pulse via `rememberInfiniteTransition()`.
**Wann verwenden:** Immer wenn ein "in-progress"-Signal ohne Fortschrittsbalken nötig ist.

```kotlin
// Source: [ASSUMED auf Basis von Compose API — bestätigt durch UI-SPEC D-23-06]
@Composable
fun TypingIndicatorDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
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
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        alphas.forEach { alpha ->
            val alphaValue by alpha
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
// Alpha: 0.3f→1.0f→0.3f, 600ms/Dot, 200ms Stagger — VERIFIED: 23-UI-SPEC.md
```

**Alternative (einfacher, weniger Kontrolle):** `animateFloatAsState` in einem `LaunchedEffect`-Loop. Nachteil: kein Infinite-Repeat ohne manuellen State-Reset. `rememberInfiniteTransition` ist die kanonische Compose-Lösung. [VERIFIED: bestehende `animateFloatAsState`-Nutzung in OverviewScreen.kt beweist verfügbare API]

### Pattern 3: Mini-Bar Integration in MainScreen.kt

**Was:** `Scaffold.bottomBar` zu `Column { MiniBar(); NavigationBar() }` umbauen.
**Warum Column statt Box:** `Scaffold` berechnet `innerPadding.calculateBottomPadding()` aus der Höhe des `bottomBar`-Slots. Ein `Column` gibt die kombinierte Höhe (44dp Mini-Bar + NavigationBar) korrekt als Bottom-Inset weiter, sodass der Scaffold-Content nicht hinter der Bar verschwindet. Bei `Box + Alignment.BottomCenter` müsste manuell extra Padding addiert werden — fehlerträchtig.

```kotlin
// Source: MainScreen.kt (aktuell), angepasst für Mini-Bar
// [VERIFIED: MainScreen.kt gelesen — aktueller Scaffold-Aufbau]
Scaffold(
    bottomBar = {
        Column {
            AiGenerationMiniBar(
                onTap = { aiType ->
                    when (aiType) {
                        AiType.WORKOUT -> {
                            selectedTab = 0
                            workoutNavController.navigate(AiWorkoutGenRoute)
                        }
                        AiType.RECIPE -> {
                            selectedTab = 2
                            nutritionNavController.navigate(AiMealGenRoute)
                        }
                    }
                }
            )
            NavigationBar {
                TopLevelTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    }
) { innerPadding -> ... }
```

### Pattern 4: Mini-Bar Composable mit AnimatedVisibility

```kotlin
// Source: OverviewScreen.kt (slideOutVertically + fadeOut Muster) — VERIFIED
// AnimatedVisibility-Spec laut 23-UI-SPEC.md
@Composable
fun AiGenerationMiniBar(
    onTap: (AiType) -> Unit,
    generationManager: AiGenerationManager = koinInject()
) {
    val genState by generationManager.state.collectAsState()

    val visible = genState is AiGenerationState.Generating || genState is AiGenerationState.Success
    val activeType = when (val s = genState) {
        is AiGenerationState.Generating -> s.type
        is AiGenerationState.Success -> s.type
        else -> null
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically { it } + fadeOut(animationSpec = tween(300))
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
```

**Wichtig:** `koinInject()` statt `koinViewModel()` — `AiGenerationManager` ist ein Koin `single`, kein ViewModel. [VERIFIED: AiModule.kt:103 — `single { AiGenerationManager(...) }`]

### Anti-Patterns vermeiden

- **Anti-Pattern: Floatings Box über dem Scaffold-Content** — Würde den Content nicht nach oben shiften, wenn die Bar erscheint. Beim Scrollen könnte Content hinter der Bar verschwinden.
- **Anti-Pattern: Eigener CoroutineScope für die Mini-Bar** — Nicht nötig. `collectAsState()` in Compose hat automatisch den Composition-Lifecycle.
- **Anti-Pattern: AiGenerationManager als ViewModel registrieren** — Er ist ein application-scoped `single` in Koin, kein ViewModel. `koinInject()` ist korrekt.
- **Anti-Pattern: `selectedTab`-State nicht auf Tab-0/2 syncen vor `navigate()`** — Wenn Mini-Bar tap ohne Tab-Wechsel navigiert, rendert die falsche NavHost-Instanz den Screen. Erst `selectedTab` setzen, dann `navController.navigate()` aufrufen.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dot-Animation | Eigener Canvas-Painter mit `drawCircle` | `rememberInfiniteTransition` + `Box(background = CircleShape)` | Compose Animations API ist deklarativ; kein manueller Animation-Ticker |
| Visibility-Animation | `if (visible)` mit `LaunchedEffect`-Delay | `AnimatedVisibility(slideInVertically + fadeIn)` | Bereits im Projekt (OverviewScreen.kt), korrekte Compose-Animation-Lifecycle-Behandlung |
| App-weiter State für AI-Status | Neuer StateFlow / SharedFlow | `AiGenerationManager.state` (existing singleton) | Manager ist bereits application-scoped und thread-safe; kein Duplikat nötig |

---

## Runtime State Inventory

Nicht anwendbar — Phase 23 ist ein Feature-Add (keine Rename/Refactor/Migration). Keine bestehenden Datensätze werden umbenannt oder verschoben.

---

## Common Pitfalls

### Pitfall 1: Scaffold-Insets korrumpieren

**Was schiefläuft:** Mini-Bar in `Box(Alignment.BottomCenter)` über dem `Scaffold` platziert → Scaffold kennt die extra Höhe nicht → Content unter der Bar abgeschnitten.
**Warum passiert es:** `Scaffold.innerPadding.calculateBottomPadding()` reflektiert nur `bottomBar`-Slot-Höhe.
**Wie vermeiden:** Mini-Bar in `Column` innerhalb `Scaffold.bottomBar`, nicht außerhalb des Scaffolds. [VERIFIED: MainScreen.kt-Struktur gelesen]
**Frühwarnung:** Letzter Listeneintrag auf Screen ist nicht erreichbar.

### Pitfall 2: `selectedTab` nicht vor navigate() setzen

**Was schiefläuft:** `nutritionNavController.navigate(AiMealGenRoute)` ohne `selectedTab = 2` → Nutrition-NavHost wird nicht gerendert, Workout-NavHost ist aktiv → Navigation hat keinen sichtbaren Effekt.
**Warum passiert es:** `MainScreen.kt` rendert nur den aktiven Tab per `when (selectedTab)`.
**Wie vermeiden:** In `onTap`-Lambda: `selectedTab` zuerst setzen, dann `navController.navigate()`. [VERIFIED: MainScreen.kt — `when (selectedTab) { 0 -> ...; 1 -> ...; 2 -> ... }`]

### Pitfall 3: Compile-Error-getriebener `copy()`-Scan nach `WorkoutAiForm`-Änderung

**Was schiefläuft:** `WorkoutAiForm` hat kein Default-Argument für `setsPerExercise` → alle Aufrufer brechen beim Kompilieren.
**Warum passiert es:** Kotlin data class ohne Default im Konstruktor → alle `copy()`- und direkten Konstruktor-Aufrufe müssen das Feld explizit führen.
**Wie vermeiden:** `setsPerExercise: Int = 3` als Default deklarieren → bestehende Callsites bleiben lauffähig; ViewModel-Form-Default explizit auf 3 setzen. [VERIFIED: WorkoutAiViewModel.kt — `defaultForm = WorkoutAiUiState.Form(...)` muss ebenfalls `setsPerExercise = 3` bekommen]
**Frühwarnung:** Compiler listet alle fehlenden Argumente — kein manueller Grep nötig.

### Pitfall 4: `AiGenerationMiniBar` beobachtet falschen StateFlow

**Was schiefläuft:** Mini-Bar beobachtet `WorkoutAiViewModel.uiState` (workout-only) statt `AiGenerationManager.state` (global, beide AI-Typen).
**Warum passiert es:** `WorkoutAiViewModel.uiState` ist ein ViewModel-Scope — `Generating` wird nur dann korrekt gesetzt, wenn `AiType.WORKOUT`. Recipe-Generierung wäre unsichtbar.
**Wie vermeiden:** Mini-Bar holt `AiGenerationManager` direkt via `koinInject()`, nicht via `koinViewModel()`. [VERIFIED: AiModule.kt — Manager ist `single`, nicht `factory` / `viewModel`]

### Pitfall 5: `workout-system-prompt.md` Hard-Rules überschreiben `setsPerExercise`

**Was schiefläuft:** Aktuell steht in `workout-system-prompt.md`: `targetSets ∈ 3..4`. Wenn `setsPerExercise = 1` oder `setsPerExercise = 6`, ignoriert das LLM den User-Parameter, weil Hard-Rule strenger ist.
**Warum passiert es:** System-Prompt-Constraint wird höher priorisiert als User-Message-Parameter.
**Wie vermeiden:** `workout-system-prompt.md` Hard-Rules-Zeile auf `targetSets: use setsPerExercise from user message` ändern (statt `targetSets ∈ 3..4`). Dies ist eine minimale Ein-Zeilen-Änderung. [VERIFIED: workout-system-prompt.md Zeile 46 — "`targetSets ∈ 3..4`"]
**Frühwarnung:** LLM antwortet mit `targetSets: 3` obwohl User `setsPerExercise: 1` gesetzt hat.

### Pitfall 6: Doppelte Navigation bei Mini-Bar-Tap auf aktivem Gen-Screen

**Was schiefläuft:** User ist bereits auf `AiWorkoutGenRoute` → Mini-Bar-Tap triggert erneutes `navController.navigate(AiWorkoutGenRoute)` → zweifach gestapelt auf dem Back-Stack.
**Warum passiert es:** `navigate()` ohne `launchSingleTop = true` oder Back-Stack-Check.
**Wie vermeiden:** `navController.navigate(AiWorkoutGenRoute) { launchSingleTop = true }` oder prüfen ob Route schon an oberster Stelle des Back-Stacks liegt. [ASSUMED — NavOptions API-Pattern; Compose Navigation 2.9.2]

---

## Code Examples

### Existing: `buildUserMessage` in `WorkoutAiUseCase` (Muster für `setsPerExercise`-Einfügung)

```kotlin
// Source: WorkoutAiUseCase.kt, Zeile 165–173 [VERIFIED]
return """
    targetMuscles: $targets
    exerciseCount: ${form.exerciseCount}
    splitStyle: ${form.splitStyle.name}
    templatesExpected: ${form.splitStyle.templateCount}
    // +++ NEU D-23-02 +++
    setsPerExercise: ${form.setsPerExercise}

    existingExercises:
    $available
""".trimIndent()
```

### Existing: `AnimatedVisibility` mit slide (aus OverviewScreen.kt)

```kotlin
// Source: OverviewScreen.kt [VERIFIED]
AnimatedVisibility(
    visible = showBanner,
    exit = slideOutVertically() + fadeOut(animationSpec = tween(300))
)
// Für Mini-Bar analog mit slideInVertically { it } als enter
```

### Existing: `collectAsState()` Pattern für StateFlow

```kotlin
// Source: AiWorkoutGenScreen.kt, Zeile 73–74 [VERIFIED]
val uiState by viewModel.uiState.collectAsState()
val streamingText by viewModel.streamingText.collectAsState()
// Analog in AiGenerationMiniBar:
val genState by generationManager.state.collectAsState()
```

---

## State of the Art

| Alter Ansatz | Aktueller Ansatz | Geändert | Auswirkung |
|--------------|-----------------|----------|------------|
| `AnimatedVisibility` ohne `enter` | `AnimatedVisibility(enter = ..., exit = ...)` | Compose 1.0+ | Bidirektionale Animation erfordert beide Parameter |
| `InfiniteTransition.animateFloat` mit `tween` | `keyframes { }` für gestaffelte Stagger-Animationen | Compose 1.3+ | Keyframes ermöglichen exaktes Timing-Offset pro Dot |
| `NavigationBar` als einziger `bottomBar`-Inhalt | `Column { CustomBanner; NavigationBar }` | Etabliertes Pattern | Saubere Inset-Propagation ohne manuelle Padding-Berechnung |

---

## Assumptions Log

| # | Claim | Section | Risiko bei Fehler |
|---|-------|---------|-------------------|
| A1 | `navigate(AiWorkoutGenRoute) { launchSingleTop = true }` verhindert doppelten Back-Stack-Eintrag in Navigation Compose 2.9.2 | Common Pitfalls #6 | Doppelter Entry — zurück-navigieren braucht zwei Taps. Niedrig-riskant, leicht zu beheben beim Testen. |
| A2 | `keyframes { durationMillis = 1200 }` mit 200ms-Stagger produziert visuell akzeptable WhatsApp-ähnliche Dot-Animation ohne Performance-Probleme | Code Examples / TypingIndicatorDots | Animation zu schnell/langsam — kosmetische Anpassung, kein Funktionsbruch |
| A3 | `Surface(tonalElevation = 0.dp)` mit `colorScheme.surfaceVariant` als Container liefert korrekten Hintergrund ohne unerwünschten Tonal-Lift | Architecture Patterns #4 | Bar erscheint heller/dunkler als erwartet — ein `dp`-Wert ändern |

---

## Open Questions

1. **Soll `navigate()` mit `launchSingleTop = true` aufgerufen werden?**
   - Was wir wissen: `NavHostController.navigate()` ohne Options pushed immer eine neue Instanz.
   - Was unklar: Ob für `AiWorkoutGenRoute` / `AiMealGenRoute` ein Singleton-Verhalten erwünscht ist.
   - Empfehlung: `launchSingleTop = true` standardmäßig setzen — sicherer Default.

2. **Soll `workout-system-prompt.md` Hard-Rule `targetSets ∈ 3..4` entfernt oder ersetzt werden?**
   - Was wir wissen: Ohne Änderung überschreibt das LLM `setsPerExercise`-Parameter bei Werten außerhalb 3–4.
   - Was unklar: Ob User explizit will, dass LLM den Wert trotzdem respektiert (ja, laut D-23-02).
   - Empfehlung: Zeile zu `targetSets: use setsPerExercise from user message exactly` umschreiben.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 23 ist ein reines Code/UI-Feature-Add ohne externe Tooling-Abhängigkeiten. Alle benötigten Libraries (Compose, Koin, Material 3) sind bereits installiert und build-verified.

---

## Validation Architecture

`workflow.nyquist_validation` ist `false` in `.planning/config.json` [VERIFIED: config.json]. Dieser Abschnitt wird übersprungen.

---

## Security Domain

`security_enforcement` nicht explizit in `.planning/config.json` gesetzt. Phase 23 ändert keine Authentifizierungs-, Speicher-, Netzwerk- oder Kryptographie-Schichten. Einzig relevante ASVS-Kategorie wäre V5 (Input Validation) für `setsPerExercise`.

| ASVS-Kategorie | Betroffen | Kontrolle |
|----------------|----------|-----------|
| V5 Input Validation | Ja — `setsPerExercise` Range 1–6 | `count.coerceIn(1, 6)` in `onSetsPerExerciseChanged()` (analog zu `exerciseCount` mit `coerceIn(1, 12)` [VERIFIED: WorkoutAiViewModel.kt:181]) |
| V2 Authentication | Nein | — |
| V3 Session Management | Nein | — |
| V4 Access Control | Nein | — |
| V6 Cryptography | Nein | — |

---

## Sources

### Primary (HIGH confidence)
- `AiGenerationManager.kt` — vollständige StateFlow-API + State-Sealed-Klassen gelesen [VERIFIED: codebase]
- `WorkoutAiViewModel.kt` — `onExerciseCountChanged()` Muster + `defaultForm` + `coerceIn(1, 12)` [VERIFIED: codebase]
- `WorkoutAiPreview.kt` — `WorkoutAiForm` data class ohne `setsPerExercise` [VERIFIED: codebase]
- `WorkoutAiUseCase.kt` — `buildUserMessage()` mit `exerciseCount`-Interpolation [VERIFIED: codebase]
- `AiWorkoutGenScreen.kt` — `WorkoutFormBody` exerciseCount-Stepper-Pattern, Zeile 189–213 [VERIFIED: codebase]
- `MainScreen.kt` — Scaffold-Struktur + selectedTab-Logik + NavHostController-Setup [VERIFIED: codebase]
- `AiModule.kt:103` — `AiGenerationManager` als Koin `single` registriert [VERIFIED: codebase]
- `workout-system-prompt.md:46` — `targetSets ∈ 3..4` Hard-Rule [VERIFIED: codebase]
- `OverviewScreen.kt` — `AnimatedVisibility(exit = slideOutVertically() + fadeOut(...))` Pattern [VERIFIED: codebase]
- `23-CONTEXT.md` — alle Entscheidungen D-23-01 … D-23-12 [VERIFIED: artifact]
- `23-UI-SPEC.md` — Spacing, Typography, Color-Tokens, Component-Inventory [VERIFIED: artifact]
- `.planning/config.json` — `nyquist_validation: false` [VERIFIED: artifact]

### Secondary (MEDIUM confidence)
- CLAUDE.md Stack-Tabelle — bestätigt alle verwendeten Library-Versionen [VERIFIED: project instructions]

### Tertiary (LOW confidence)
- `launchSingleTop = true` als Schutz vor doppeltem Back-Stack-Entry [ASSUMED — API-Verhalten aus Training]

---

## Metadata

**Confidence Breakdown:**
- Standard Stack: HIGH — alle Libraries bereits installiert und im Einsatz
- Architecture Patterns: HIGH — direkte Codebase-Analyse; Muster werden 1:1 kopiert
- Pitfalls: HIGH (Pitfall 1–5) / MEDIUM (Pitfall 6) — aus Codebase-Analyse abgeleitet; Pitfall 6 ASSUMED

**Research date:** 2026-05-19
**Valid until:** 2026-06-19 (stabile KMP-Version; keine External-API-Abhängigkeiten)

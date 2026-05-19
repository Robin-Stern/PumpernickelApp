---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
verified: 2026-05-19T10:41:05Z
status: human_needed
score: 14/15 must-haves verified
overrides_applied: 0
human_verification:
  - test: "iOS Sets-Stepper und AiGenerationMiniBar SwiftUI-Implementation durch User"
    expected: "User implementiert laut 23-IOS-HANDOFF.md: Stepper(in: 1...6) in AIWorkoutGenView zwischen Anzahl Übungen und Aufteilung, AiGenerationMiniBar View ueber der TabView mit korrekten States (Generating/Success/Idle-Error-hidden), korrekten deutschen Copy-Strings, und Tab-Switch-Navigation bei Tap"
    why_human: "iOS-Implementation ist bewusst User-Aufgabe (D-23-12, MEMORY-Konvention). Agent-Scope endet bei Shared KMP + Android. Kein iosApp-Code wurde veraendert — Verifikation erfordert manuelle Implementierung und visuellen Test auf iOS-Simulator/Geraet durch den User."
---

# Phase 23: AI UX und iOS-Paritaet Verifikationsbericht

**Phase-Ziel:** Zwei entkoppelte AI-UX-Verbesserungen auf Android + iOS-Paritaet-Spec: (1) Neuer `setsPerExercise`-Stepper (Range 1-6, Default 3) in der Workout-AI-Form, propagiert via shared `WorkoutAiForm` -> `buildUserMessage()` -> LLM; System-Prompt-Hard-Rule `targetSets ∈ 3..4` wird ersetzt durch `use setsPerExercise from user message exactly`. (2) Globale Background-Mini-Bar 44dp direkt ueber der `NavigationBar` / `TabView`: observiert `AiGenerationManager.state`, zeigt WhatsApp-Style Typing-Indicator mit Label `KI generiert Workout…` / `KI generiert Mahlzeit…` waehrend Generation, wechselt zu Checkmark + `Workout bereit — tippen zum Ansehen` / `Mahlzeit bereit — tippen zum Ansehen` bei Success, ist nicht sichtbar bei Idle/Error. Tap navigiert via Tab-Switch + `launchSingleTop` zum aktiven Gen-Screen. Agent-Scope: Shared KMP + Android-UI. iOS-Implementation per `23-IOS-HANDOFF.md` (D-23-12) durch User.

**Verifiziert:** 2026-05-19T10:41:05Z
**Status:** human_needed
**Re-Verifikation:** Nein — initiale Verifikation

---

## Ziel-Erreichung

### Beobachtbare Wahrheiten

| # | Wahrheit | Status | Evidenz |
|---|----------|--------|---------|
| 1 | `WorkoutAiForm` data class enthaelt Feld `setsPerExercise: Int` mit Default 3 (D-23-01) | VERIFIED | `WorkoutAiPreview.kt` Z50: `val setsPerExercise: Int = 3 // D-23-01: Range 1–6, Default 3` |
| 2 | `WorkoutAiUiState.Form` sealed-subclass enthaelt `setsPerExercise: Int = 3` und wird durch alle `copy()`-Pfade des ViewModels getragen | VERIFIED | `WorkoutAiViewModel.kt` Z304: `val setsPerExercise: Int = 3` in `data class Form`; Z153 + Z167: beide originatingForm-Rekonstruktions-Pfade uebergeben `form.setsPerExercise` |
| 3 | `WorkoutAiViewModel.onSetsPerExerciseChanged(count: Int)` existiert, klemmt via `coerceIn(1, 6)` und aktualisiert `_uiState` | VERIFIED | `WorkoutAiViewModel.kt` Z189-191: `fun onSetsPerExerciseChanged(count: Int)` + `_uiState.value = current.copy(setsPerExercise = count.coerceIn(1, 6))` |
| 4 | `WorkoutAiViewModel.defaultForm` setzt `setsPerExercise = 3` explizit | VERIFIED | `WorkoutAiViewModel.kt` Z97-102: `private val defaultForm = WorkoutAiUiState.Form(..., setsPerExercise = 3)` |
| 5 | `WorkoutAiViewModel.generate()` propagiert `form.setsPerExercise` in den `WorkoutAiForm(...)`-Konstruktor-Aufruf | VERIFIED | `WorkoutAiViewModel.kt` Z203-208: `WorkoutAiForm(..., setsPerExercise = form.setsPerExercise)` uebergeben an `generationManager.startWorkoutGeneration()` |
| 6 | `WorkoutAiUseCase.buildUserMessage()` interpoliert `setsPerExercise: ${form.setsPerExercise}` zwischen `exerciseCount` und `splitStyle` | VERIFIED | `WorkoutAiUseCase.kt` Z167-169: Reihenfolge `exerciseCount` (Z167), `setsPerExercise` (Z168), `splitStyle` (Z169) korrekt |
| 7 | `workout-system-prompt.md` Hard-Rule `targetSets ∈ 3..4` wurde durch `targetSets: use setsPerExercise from user message exactly` ersetzt | VERIFIED | `workout-system-prompt.md` Z46: `- \`targetSets\`: use setsPerExercise from user message exactly.`; `grep -c "targetSets ∈ 3..4"` gibt 0 zurueck |
| 8 | User sieht im AI-Workout-Gen-Formular einen neuen Stepper `Anzahl Saetze` mit Range 1-6 unter `Anzahl Uebungen` und ueber `Aufteilung` | VERIFIED | `AiWorkoutGenScreen.kt` Z218 `SectionLabel("Anzahl Sätze")` steht nach Z192 `SectionLabel("Anzahl Übungen")` und vor Z244 `SectionLabel("Aufteilung")`; `+`-Button enabled bei `< 6`, `-`-Button enabled bei `> 1` |
| 9 | Waehrend einer laufenden AI-Generation erscheint ueber der `NavigationBar` ein 44dp-Banner mit animierten Pulse-Punkten + Label `KI generiert Workout…` / `KI generiert Mahlzeit…` | VERIFIED | `AiGenerationMiniBar.kt` Z62-64: `slideInVertically { it } + fadeIn(tween(300))`; Z69: `height(44.dp)`; Z85: `"KI generiert Workout…"` / `"KI generiert Mahlzeit…"`; `TypingIndicatorDots()` mit infiniteRepeatable keyframes vorhanden |
| 10 | Nach erfolgreicher Generation zeigt der Banner ein primary-getoentes Checkmark-Icon + Label `Workout bereit — tippen zum Ansehen` / `Mahlzeit bereit — tippen zum Ansehen` | VERIFIED | `AiGenerationMiniBar.kt` Z106-109: Labels korrekt; Z118: `Icons.Default.CheckCircle` mit `MaterialTheme.colorScheme.primary` Tint |
| 11 | Tap auf den Banner navigiert zum aktiven Gen-Screen mit `launchSingleTop = true` | VERIFIED | `MainScreen.kt` Z87-95: `selectedTab = 0` vor `workoutNavController.navigate(AiWorkoutGenRoute) { launchSingleTop = true }` und `selectedTab = 2` vor `nutritionNavController.navigate(AiMealGenRoute) { launchSingleTop = true }` |
| 12 | Banner ist nicht sichtbar bei `AiGenerationState.Idle` und `AiGenerationState.Error` (D-23-10) | VERIFIED | `AiGenerationMiniBar.kt` Z54: `val visible = genState is AiGenerationState.Generating \|\| genState is AiGenerationState.Success` — Idle und Error fallen in `else {}` zweig, nicht sichtbar |
| 13 | Banner-Enter/Exit nutzt `slideInVertically { it } + fadeIn(tween(300))` / `slideOutVertically { it } + fadeOut(tween(300))` | VERIFIED | `AiGenerationMiniBar.kt` Z63-64: Exakt diese Animation-Specs implementiert |
| 14 | `23-IOS-HANDOFF.md` existiert und enthaelt vollstaendige SwiftUI-Spec (Sets-Stepper + Mini-Bar, asyncSequence-Observer, Pitfalls, Copy-Strings, 150+ Zeilen) | VERIFIED | Datei existiert mit 498 Zeilen; enthaelt `Stepper(in: 1...6)` Snippet, `AiGenerationMiniBar` SwiftUI-View, `asyncSequence`-Observer, 4 Pitfalls, Acceptance Criteria; alle deutschen Strings korrekt |
| 15 | iOS-Implementation (Sets-Stepper + Mini-Bar in SwiftUI) durch User abgeschlossen | NICHT VERIFIZIERT | D-23-12: iOS-Seite ist bewusst User-Aufgabe. Agent-Scope endet bei Shared KMP + Android. Keine iosApp-Codeaenderungen in dieser Phase. Erfordert manuellen Test durch User. |

**Score:** 14/15 Wahrheiten verifiziert

---

## Pflicht-Artefakte

| Artefakt | Erwartet | Status | Details |
|----------|---------- |--------|---------|
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` | `WorkoutAiForm` mit `setsPerExercise` | VERIFIED | Z50: `val setsPerExercise: Int = 3` vorhanden |
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` | Handler + Form-State + defaultForm + generate()-Propagation | VERIFIED | `onSetsPerExerciseChanged` Z189, `coerceIn(1,6)` Z191, `defaultForm` Z101, `generate()` Z208 — alle vorhanden |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` | Prompt-Interpolation fuer `setsPerExercise` | VERIFIED | Z168: `setsPerExercise: ${form.setsPerExercise}` zwischen exerciseCount und splitStyle |
| `shared/src/commonMain/resources/workout-system-prompt.md` | Hard-Rule-Korrektur fuer `targetSets` | VERIFIED | Z46: `use setsPerExercise from user message exactly`; alte `∈ 3..4`-Regel nicht mehr vorhanden |
| `androidApp/.../AiWorkoutGenScreen.kt` | Sets-Stepper Row + `onSetsPerExerciseChanged` Callback | VERIFIED | Z162: Callback-Parameter; Z218-240: Stepper-Row mit korrekten enabled-Flags und `contentDescription`-Werten |
| `androidApp/.../AiGenerationMiniBar.kt` | Neues Composable AiGenerationMiniBar + TypingIndicatorDots + Helfer | VERIFIED | 171 Zeilen; `fun AiGenerationMiniBar`, `fun TypingIndicatorDots`, `MiniBarGenerating`, `MiniBarSuccess` alle vorhanden |
| `androidApp/.../MainScreen.kt` | `Scaffold.bottomBar = Column { AiGenerationMiniBar; NavigationBar }` | VERIFIED | Z82-96: Column-Wrapper mit AiGenerationMiniBar + onTap-Lambda + NavigationBar |
| `.planning/.../23-IOS-HANDOFF.md` | SwiftUI-Spec fuer Sets-Stepper + Mini-Bar | VERIFIED | 498 Zeilen, enthaelt alle spezifizierten Elemente |

---

## Key-Link-Verifikation

| Von | Nach | Via | Status | Details |
|-----|------|-----|--------|---------|
| `WorkoutAiViewModel.onSetsPerExerciseChanged` | `WorkoutAiUiState.Form.setsPerExercise` | `current.copy(setsPerExercise = count.coerceIn(1, 6))` | WIRED | Z191 explizit vorhanden |
| `WorkoutAiViewModel.generate()` | `AiGenerationManager.startWorkoutGeneration` | `WorkoutAiForm(..., setsPerExercise = form.setsPerExercise)` | WIRED | Z203-208: vollstaendige Propagation |
| `WorkoutAiUseCase.buildUserMessage()` | LLM-Prompt-Body | String-Interpolation `setsPerExercise: ${form.setsPerExercise}` | WIRED | Z168 vorhanden, korrekte Reihenfolge |
| `AiGenerationMiniBar` | `AiGenerationManager.state` | `koinInject<AiGenerationManager>()` + `state.collectAsState()` | WIRED | Z50-52: echter Koin-Inject + collectAsState auf realem StateFlow |
| `MainScreen.bottomBar onTap` | `workoutNavController` / `nutritionNavController.navigate` | `selectedTab = X` DANN `navigate(... launchSingleTop = true)` | WIRED | Z87-95: Reihenfolge korrekt (selectedTab vor navigate) |
| `WorkoutFormBody` | `WorkoutAiViewModel.onSetsPerExerciseChanged` | `onSetsPerExerciseChanged = viewModel::onSetsPerExerciseChanged` | WIRED | Z109: explizit verdrahtet |
| `23-IOS-HANDOFF.md` | shared `setsPerExercise` field | Dokument-Referenz | WIRED | Mehr als 5 Vorkommen von `setsPerExercise` im Dokument |
| `23-IOS-HANDOFF.md` | `AiGenerationManager.state` | asyncSequence observation | WIRED | Vollstaendiges Observer-Pattern-Snippet im Dokument vorhanden |

---

## Data-Flow-Trace (Level 4)

| Artefakt | Datenvariable | Quelle | Echt-Daten | Status |
|----------|---------------|--------|------------|--------|
| `AiGenerationMiniBar` | `genState` | `generationManager.state.collectAsState()` | `AiGenerationManager._state: MutableStateFlow<AiGenerationState>` — echter StateFlow, kein Static-Return | FLOWING |
| `WorkoutFormBody` (Sets-Stepper) | `state.setsPerExercise` | `WorkoutAiUiState.Form` via ViewModel | ViewModel-State wird durch `onSetsPerExerciseChanged` mit echten Klick-Events befuellt, `coerceIn(1,6)` appliziert | FLOWING |
| `WorkoutAiUseCase.buildUserMessage()` | `form.setsPerExercise` | `WorkoutAiForm` aus `generate()` | Wert fliesst von User-Eingabe -> ViewModel-State -> generate() -> WorkoutAiForm -> Prompt | FLOWING |

---

## Behavioral Spot-Checks

| Verhalten | Pruefung | Ergebnis | Status |
|-----------|----------|----------|--------|
| `setsPerExercise` in `WorkoutAiForm` vorhanden | `grep "val setsPerExercise: Int" WorkoutAiPreview.kt` | 1 Match | PASS |
| `onSetsPerExerciseChanged` mit `coerceIn(1, 6)` | `grep "coerceIn(1, 6)" WorkoutAiViewModel.kt` | 1 Match (Z191) | PASS |
| `generate()` propagiert `setsPerExercise` | `grep "setsPerExercise = form.setsPerExercise" WorkoutAiViewModel.kt` | 1 Match (Z208) | PASS |
| System-Prompt alte Hard-Rule entfernt | `grep -c "targetSets ∈ 3..4" workout-system-prompt.md` | 0 | PASS |
| System-Prompt neue Regel | `grep "use setsPerExercise from user message" workout-system-prompt.md` | 1 Match (Z46) | PASS |
| Sets-Stepper zwischen exerciseCount und Aufteilung | Zeilennummern: Uebungen=Z192, Saetze=Z218, Aufteilung=Z244 | Aufsteigend | PASS |
| AiGenerationMiniBar visible-Logik (kein Idle/Error) | `grep "genState is AiGenerationState.Generating \|\| genState is AiGenerationState.Success"` | Z54 Match | PASS |
| selectedTab vor navigate | awk WORKOUT-Branch | selectedTab Z87, navigate Z88 | PASS |
| Alle 6 Phase-23-Commits | git log | b9917b9, 3360ca8, e4c394d, 584612a, 072bb71, 661eeed — alle vorhanden | PASS |
| Visual UAT Task 4 approved | git log | Commit 694e649 "mark visual UAT checkpoint as approved" | PASS |

---

## Requirements Coverage (D-23-01 bis D-23-12)

| Requirement | Plan | Beschreibung | Status | Evidenz |
|-------------|------|-------------|--------|---------|
| D-23-01 | 23-01 | `setsPerExercise: Int`, Range 1-6, Default 3 | ERFUELLT | `WorkoutAiPreview.kt` + `WorkoutAiViewModel.kt` + `WorkoutAiUiState.Form` |
| D-23-02 | 23-01 | `setsPerExercise` als User-Message-Parameter in `buildUserMessage()`, System-Prompt Hard-Rule entfernt | ERFUELLT | `WorkoutAiUseCase.kt` Z168 + `workout-system-prompt.md` Z46 |
| D-23-03 | 23-02 | Android Sets-Stepper mit `+/-` IconButtons, Range 1-6 | ERFUELLT | `AiWorkoutGenScreen.kt` Z218-240 |
| D-23-04 | 23-02 | Mini-Bar observiert `AiGenerationManager.state` via `koinInject` (kein ViewModel) | ERFUELLT | `AiGenerationMiniBar.kt` Z50 + Z52 |
| D-23-05 | 23-02 | 44dp, direkt ueber NavigationBar, persistent (kein Floating-Overlay) | ERFUELLT | `AiGenerationMiniBar.kt` Z69 + `MainScreen.kt` Z82-96 |
| D-23-06 | 23-02 | Generating-State: TypingIndicatorDots + sprachspezifisches Label | ERFUELLT | `AiGenerationMiniBar.kt` Z84-100 |
| D-23-07 | 23-02 | Success-State: `CheckCircle` (primary) + "tippen zum Ansehen"-Label | ERFUELLT | `AiGenerationMiniBar.kt` Z105-130 |
| D-23-08 | 23-02 | Tap navigiert via Tab-Switch + `navigate(launchSingleTop)` | ERFUELLT | `MainScreen.kt` Z83-95 |
| D-23-09 | 23-02 | User kann Gen-Screen verlassen, `AiGenerationManager` laeuft weiter | ERFUELLT | Application-scoped Koin-Single; Mini-Bar haelt State via collectAsState |
| D-23-10 | 23-02 | Error-State: Mini-Bar nicht sichtbar (wie Idle) | ERFUELLT | `AiGenerationMiniBar.kt` Z54: `visible = Generating \|\| Success` |
| D-23-11 | 23-03 | iOS hatte alle Form-Felder ausser setsPerExercise; keine weiteren iOS-Only-Fixes | ERFUELLT | Handoff-Dokument bestaetigt: nur Stepper + Mini-Bar als neue iOS-Aufgaben |
| D-23-12 | 23-03 | iOS-Implementation in `23-IOS-HANDOFF.md` dokumentiert; User implementiert selbst | SPEC ERFUELLT, IMPLEMENTATION PENDING | `23-IOS-HANDOFF.md` (498 Zeilen) vorhanden und vollstaendig; iOS-Implementation noch nicht durch User durchgefuehrt |

---

## Gefundene Anti-Patterns

| Datei | Zeile | Pattern | Schwere | Auswirkung |
|-------|-------|---------|---------|------------|
| — | — | — | — | Keine Stubs, TODOs oder Platzhalter in den modifizierten Dateien gefunden |

---

## Menschliche Verifikation erforderlich

### 1. iOS Sets-Stepper und AiGenerationMiniBar SwiftUI-Implementation

**Test:** Laut `23-IOS-HANDOFF.md` implementieren:
1. `AIWorkoutGenView.swift`: `Stepper(in: 1...6)` fuer `setsPerExercise` zwischen "Anzahl Uebungen" und "Aufteilung" einfuegen; Binding gegen `viewModel.onSetsPerExerciseChanged(count: Int32($0))`
2. `AiGenerationManager`-Zugang in iOS anlegen (KoinHelper oder analog)
3. `AiGenerationMiniBarView` in `MainTabView.swift` integrieren: 44pt, `Color(.secondarySystemBackground)`, slide+fade 300ms, Generating-State mit Typing-Dots + Labels, Success-State mit Checkmark + Labels, Idle/Error nicht sichtbar
4. Tap-Navigation: `selectedTab` vor NavigationPath-Manipulation setzen

**Erwartet:**
- Sets-Stepper zeigt Default "3 Saetze", Range-Endpunkte korrekt begrenzt
- Mini-Bar erscheint waehrend Generation (nicht bei Idle/Error)
- Generating-State: animierte Dots + "KI generiert Workout..." / "KI generiert Mahlzeit..."
- Success-State: Checkmark + "Workout bereit — tippen zum Ansehen" / "Mahlzeit bereit — tippen zum Ansehen"
- Tap navigiert zum korrekten Gen-Screen mit Tab-Switch

**Warum menschliche Verifikation:** iOS-Implementation ist per D-23-12 (MEMORY-Konvention) bewusst User-Aufgabe. Kein iosApp-Code wurde in dieser Phase veraendert. Nur der User kann SwiftUI-Code schreiben und auf iOS-Simulator/Geraet testen.

---

## Luecken-Zusammenfassung

Kein BLOCKER. Alle 14 programmatisch pruefbaren Must-Haves sind VERIFIED.

Die einzige verbleibende Anforderung (D-23-12 / Wahrheit 15) ist die iOS-Implementation durch den User — dies ist per Design keine Agent-Aufgabe und kein Fehler in der bisherigen Durchfuehrung. Die Spec (`23-IOS-HANDOFF.md`) ist vollstaendig und bereit zur Verwendung.

**Android-Scope (Agent-Aufgabe):** Vollstaendig abgeschlossen.
- Shared KMP: `setsPerExercise` in allen relevanten Schichten propagiert
- System-Prompt: Hard-Rule korrekt ersetzt
- Android-UI: Sets-Stepper, AiGenerationMiniBar, MainScreen-Integration vollstaendig
- Visual UAT (Android): approved durch User (Commit 694e649)

**iOS-Scope (User-Aufgabe):** Spec vorhanden, Implementation ausstehend.

---

_Verifiziert: 2026-05-19T10:41:05Z_
_Verifier: Claude (gsd-verifier)_

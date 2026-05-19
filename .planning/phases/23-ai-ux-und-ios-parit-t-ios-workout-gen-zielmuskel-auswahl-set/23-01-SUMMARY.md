---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
plan: 01
subsystem: ai-workout
tags: [kmp, ai-workout, shared-domain, prompt-engineering, workout-ai-form]

# Dependency graph
requires:
  - phase: 22-anthropic-ai-provider
    provides: provider-agnostischer AiGenerationManager + WorkoutAiUseCase-Infrastruktur

provides:
  - setsPerExercise-Feld (Int, Range 1-6, Default 3) in WorkoutAiForm (commonMain)
  - setsPerExercise-Feld in WorkoutAiUiState.Form mit Default 3
  - onSetsPerExerciseChanged(count: Int) in WorkoutAiViewModel, klemmt via coerceIn(1, 6)
  - setsPerExercise-Propagation durch generate() in AiGenerationManager.startWorkoutGeneration
  - setsPerExercise-Interpolation in WorkoutAiUseCase.buildUserMessage zwischen exerciseCount und splitStyle
  - korrigierte targetSets-Hard-Rule in workout-system-prompt.md (nutzt jetzt User-Message-Wert)

affects:
  - plan 23-02 (Android Sets-Stepper UI konsumiert setsPerExercise aus WorkoutAiUiState.Form)
  - plan 23-03 (iOS Handoff-Spezifikation; User implementiert iOS Sets-Stepper)
  - iOS AIWorkoutGenView (konsumiert onSetsPerExerciseChanged via KMPNativeCoroutines)
  - LLM-Ausgaben fuer Workout-Generierung (setsPerExercise wird jetzt als User-Message-Parameter uebergeben)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "coerceIn(min, max) fuer Stepper-Handler-Bereich-Klemme analog onExerciseCountChanged"
    - "Default-Wert in data class Feld sichert Source-Kompatibilitaet aller Callsites ohne Pflicht-Migration"
    - "originatingForm-Rekonstruktion im init-Collector propagiert alle Form-Felder inkl. neuer Felder"

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/resources/workout-system-prompt.md
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt

key-decisions:
  - "D-23-01: setsPerExercise: Int = 3, Range 1-6 via coerceIn - nur Sets, keine Reps (Reps bleiben LLM-Entscheidung)"
  - "D-23-02: Interpolation als User-Message-Parameter, System-Prompt Hard-Rule entfernt um LLM-Override zu verhindern"
  - "AppDatabaseConstructor: expect object benoetigt override-Deklaration um abstracte initialize()-Methode zu befriedigen (Rule 3 Deviation - pre-existierender Fehler)"

patterns-established:
  - "Stepper-Handler-Pattern: fun on{Field}Changed(count: Int) mit coerceIn-Klemme, direkt nach analogem Handler"
  - "Form-Feld-Default-Wert = 3: Neue Felder in WorkoutAiForm + WorkoutAiUiState.Form erhalten Defaults fuer Source-Kompatibilitaet"

requirements-completed: []

# Metrics
duration: 15min
completed: 2026-05-19
---

# Phase 23 Plan 01: Shared KMP-Schicht setsPerExercise (Sets-Stepper Foundation) Summary

**setsPerExercise: Int (1-6, Default 3) in WorkoutAiForm, WorkoutAiUiState.Form und Prompt-Interpolation — LLM-Hard-Rule-Override-Schutz via System-Prompt-Korrektur**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-19T10:02:00Z
- **Completed:** 2026-05-19T10:17:43Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- `WorkoutAiForm` und `WorkoutAiUiState.Form` tragen das neue Feld `setsPerExercise: Int = 3` (D-23-01)
- `WorkoutAiViewModel` klemmt User-Eingaben via `coerceIn(1, 6)` und propagiert das Feld durch alle State-Pfade inkl. `generate()`, `Success`- und `Error`-originatingForm-Rekonstruktion
- `WorkoutAiUseCase.buildUserMessage()` interpoliert `setsPerExercise: ${form.setsPerExercise}` zwischen `exerciseCount` und `splitStyle` (D-23-02)
- `workout-system-prompt.md`: Hard-Rule `targetSets ∈ 3..4` ersetzt durch `targetSets: use setsPerExercise from user message exactly` — verhindert LLM-Override (T-23-03)
- Pre-existierender `compileCommonMainKotlinMetadata`-Fehler in `AppDatabase.kt` behoben (Rule 3 Deviation)

## Task Commits

1. **Task 1: setsPerExercise zu WorkoutAiForm, UiState.Form, defaultForm, Handler, generate()** - `b9917b9` (feat)
2. **Task 2: buildUserMessage-Interpolation + System-Prompt Hard-Rule Fix** - `3360ca8` (feat)

**Plan-Metadaten-Commit:** folgt nach SUMMARY-Erstellung (docs)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` — `setsPerExercise: Int = 3` zu `WorkoutAiForm` hinzugefuegt
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` — `defaultForm`, Handler `onSetsPerExerciseChanged`, `generate()`-Propagation, `WorkoutAiUiState.Form`-Feld, originatingForm-Rekonstruktion (Success + Error)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` — `setsPerExercise`-Zeile in `buildUserMessage()`
- `shared/src/commonMain/resources/workout-system-prompt.md` — Hard-Rule korrigiert
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Rule-3-Fix: `override fun initialize(): AppDatabase` im `expect object`

## Decisions Made

- `setsPerExercise` bekommt Default `= 3` in beiden data-classes fuer Source-Kompatibilitaet aller Callsites
- `originatingForm`-Rekonstruktion in `init { collect }` propagiert `form.setsPerExercise` explizit, damit Success/Error-States den User-Wert nicht auf Default zurueckfallen
- System-Prompt-Hard-Rule entfernt statt Wert-Range geaendert — Range-Kontrolle liegt bei Kotlin-seitigem `coerceIn`, nicht beim LLM

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existierender `compileCommonMainKotlinMetadata`-Fehler in AppDatabase.kt**
- **Found during:** Task 1 (Verifikations-Compile)
- **Issue:** `AppDatabaseConstructor expect object` implementierte abstrakte Methode `initialize(): T` nicht. Fehler existierte vor Plan-23-01-Aenderungen (via `git stash` verifiziert).
- **Fix:** `@Suppress("ABSTRACT_MEMBER_NOT_IMPLEMENTED")` ergaenzt und `override fun initialize(): AppDatabase` als abstract-Deklaration im `expect object` hinzugefuegt
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt`
- **Verification:** `compileCommonMainKotlinMetadata` BUILD SUCCESSFUL nach Fix
- **Committed in:** `b9917b9` (Task 1 commit)

**2. [Rule 2 - Missing Critical] setsPerExercise-Propagation in originatingForm-Rekonstruktion**
- **Found during:** Task 1 (Code-Review der bestehenden Form-Konstruktor-Aufrufe in init)
- **Issue:** Die `init { collect }`-Zuweisungen fuer Success und Error rekonstruierten `WorkoutAiUiState.Form` mit nur 3 expliziten Feldern (`targetMuscles, exerciseCount, splitStyle`), obwohl `form.setsPerExercise` im `WorkoutAiForm` vorhanden ist. Default-Wert wuerde greifen, aber User-Wert wuerde verloren gehen.
- **Fix:** Beide `WorkoutAiUiState.Form(...)`-Konstruktoren um `form.setsPerExercise` als 4. Argument erweitert
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`
- **Verification:** Implizit: compile-Check und Grep-Kontrolle der Konstruktor-Aufrufe
- **Committed in:** `b9917b9` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking, 1 Rule 2 missing critical propagation)
**Impact on plan:** Beide Auto-Fixes notwendig fuer korrekte Funktionalitaet. Kein Scope-Creep.

## Issues Encountered

- `compileCommonMainKotlinMetadata` schlug initial fehl durch pre-existierenden `AppDatabaseConstructor`-Fehler. Diagnose via `git stash` bestaetigte: Fehler unabhaengig von Plan-23-01-Aenderungen. Fix mit `@Suppress + override fun initialize()` loeste das Problem.

## Threat Surface Scan

Keine neuen Netzwerk-Endpoints, Auth-Pfade oder Schema-Aenderungen eingefuehrt. Das neue `setsPerExercise`-Feld fliesst ausschliesslich als Integer-Interpolation in den LLM-Prompt — keine neue Trust-Boundary.

T-23-01 (Tampering via out-of-range values) durch `coerceIn(1, 6)` mitigiert.
T-23-03 (LLM ignoriert User-Wert durch Hard-Rule) durch System-Prompt-Aenderung mitigiert.

## Known Stubs

Keine Stubs — alle Felder werden vollstaendig durch die gesamte Shared-Schicht propagiert. Android-UI (Plan 23-02) und iOS-Handoff (Plan 23-03) sind die naechsten Konsumenten.

## Next Phase Readiness

- **Plan 23-02 (Android Sets-Stepper UI):** Kann `state.setsPerExercise` und `viewModel::onSetsPerExerciseChanged` direkt konsumieren
- **iOS-Handoff:** `WorkoutAiViewModel.onSetsPerExerciseChanged` steht via KMPNativeCoroutines-Export fuer SwiftUI-Stepper bereit
- **LLM-Generierung:** Alle Providers (OpenAI, Together, Anthropic) erhalten `setsPerExercise`-Wert in der User-Message

## Self-Check: PASSED

- WorkoutAiPreview.kt: FOUND
- WorkoutAiViewModel.kt: FOUND
- WorkoutAiUseCase.kt: FOUND
- workout-system-prompt.md: FOUND
- Commit b9917b9: FOUND
- Commit 3360ca8: FOUND

---
*Phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set*
*Completed: 2026-05-19*

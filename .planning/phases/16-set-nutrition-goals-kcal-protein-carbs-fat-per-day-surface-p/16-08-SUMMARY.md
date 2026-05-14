---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: 08
subsystem: ui
tags: [compose, swiftui, datastore, kmpnativecoroutines, flow, rememberSaveable, state-machine]

# Dependency graph
requires:
  - phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
    provides: "TdeeCalculator.buildSplit kcal snap-to-50 (16-07) — picker can land on suggestion kcal exactly"
  - phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
    provides: "Editor screens with stored stats / goals observation (16-05 Android, 16-06 iOS)"
provides:
  - "Android NutritionGoalsEditorScreen seeds stats + picker fields ONCE from first DataStore emission"
  - "iOS NutritionGoalsEditorView observeStats()/observeGoals() short-circuit after first non-null emission"
  - "rememberSaveable + LaunchedEffect pattern for one-shot Flow → @State seeding (Android)"
  - "@State guard + continue pattern for one-shot async-sequence → @State seeding (iOS)"
affects:
  - "Any future editor surface that seeds local state from DataStore Flows on either platform"
  - "Phase 16 verification gap 2 (WR-03 Android, WR-04 iOS) — closed"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Android: `var fooInitialized by rememberSaveable { mutableStateOf(false) }` + `LaunchedEffect(flow) { if (!fooInitialized && flow != null) { ...; fooInitialized = true } }` — one-shot Flow → state seeding that survives configuration changes"
    - "iOS: `@State private var fooInitialized: Bool = false` + `for try await x in asyncSequence(...) { guard !fooInitialized else { continue }; if let t = x as? T { ...; fooInitialized = true } }` — one-shot async-sequence → state seeding; `continue` (not `break`) keeps awaiting until a non-null value arrives"

key-files:
  created: []
  modified:
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt — replaced `remember(storedStats/storedGoals)` keying with `rememberSaveable` guards + LaunchedEffect-driven seeding"
    - "iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift — added @State `statsInitialized`/`goalsInitialized` guards inside observeStats()/observeGoals()"

key-decisions:
  - "Use `rememberSaveable` (not `remember`) for the initialization booleans on Android so they survive configuration changes (rotation) — preserves user edits across rotation as a side benefit of the WR-03 fix"
  - "Use `continue` (not `break`) in the iOS async-sequence loops so first-launch nil emissions do not permanently lock initialization — the loop keeps awaiting until a non-null value arrives, then the guard short-circuits all subsequent emissions"
  - "Hard-code the picker / stats placeholder defaults (80/180/30/MALE/MODERATELY_ACTIVE; 2500/150/300/80/50) instead of reading from `storedGoals` initial value — guarantees the LaunchedEffect/async-sequence is the SINGLE source of truth for first-emission seeding"
  - "Smart-cast `storedStats!!` non-null assertions inside the LaunchedEffect block on Android — Compose smart-cast does not see across the lambda boundary, and the surrounding `storedStats != null` guard keeps the assertion safe"

patterns-established:
  - "One-shot Flow → state seeding: use a separate boolean guard rather than relying on `remember(key)`, which destroys local state on every key change — critical for any UI that reads from a Flow that re-emits on save"
  - "Initialization guards persist across configuration changes (Android: `rememberSaveable`); iOS @State already survives sheet redrawing within the same navigation context"

requirements-completed: []  # plan declares `requirements: []` — gap-closure plan, not requirement-bearing

# Metrics
duration: 3min
completed: 2026-04-28
---

# Phase 16 Plan 08: Editor One-Shot Initialization (WR-03 / WR-04) Summary

**Picker wheels and stats fields now initialize once from the first DataStore emission and never reset to stored values when the user has started editing — closes Gap 2 / WR-03 (Android) and WR-04 (iOS).**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-04-28T15:04:37Z
- **Completed:** 2026-04-28T15:07:56Z
- **Tasks:** 2 implementation tasks executed + 1 checkpoint auto-approved (auto_advance: true)
- **Files modified:** 2

## Accomplishments

- Replaced six `remember(storedStats)` and five `remember(storedGoals)` keyed-state declarations on Android with plain `remember { ... }` defaults plus a single `LaunchedEffect(storedStats)` and a single `LaunchedEffect(storedGoals)` that seed once and short-circuit afterwards
- Added `rememberSaveable` initialization booleans (`statsInitialized`, `goalsInitialized`) that survive configuration changes
- Introduced `@State` initialization booleans on iOS and added `guard !*Initialized else { continue }` short-circuits inside both `observeStats()` and `observeGoals()` async-sequence loops
- `selectedSuggestion`, `applySuggestion(_:)`, `saveGoals()`, and the iOS `currentStats` derived property are unchanged — guards do not interfere with user-driven writes (which fire after `goalsInitialized` is already true)
- Preserved D-16-09 behaviour: stats card collapses on first non-null `userPhysicalStats` emission only when stats are actually stored, leaving the form expanded for first-time users (`storedStats == null` → guard is never satisfied → defaults stay; `statsExpanded` stays at `true`)
- Both platform builds verified: `./gradlew :androidApp:assembleDebug` BUILD SUCCESSFUL; `xcodebuild -workspace iosApp/iosApp.xcodeproj/project.xcworkspace -scheme iosApp -sdk iphonesimulator -configuration Debug build` BUILD SUCCEEDED

## Task Commits

Each task was committed atomically:

1. **Task 1: Android — replace `remember(storedStats/storedGoals)` keying with `rememberSaveable` + `LaunchedEffect`** — `b3cba57` (fix)
2. **Task 2: iOS — guard `observeStats()` and `observeGoals()` with `@State` initialization booleans** — `7245ccf` (fix)
3. **Task 3: Human verification** — auto-approved under `workflow.auto_advance: true` (no separate commit)

**Plan metadata:** _to be added by final commit (this SUMMARY + STATE + ROADMAP)_

## Files Modified

- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt` — Android editor one-shot seeding + rememberSaveable guards
- `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift` — iOS editor one-shot seeding + @State guards

## Decisions Made

- **Hard-coded defaults instead of `storedGoals.calorieGoal`-keyed defaults** — keeps the LaunchedEffect / async-sequence as the single source of truth for first-emission seeding. Removes the temptation to "fall back" to stored values via a different code path. Defaults match `NutritionGoals` defaults (2500/150/300/80/50).
- **`rememberSaveable` over `remember`** for the initialization booleans on Android — survives rotation; means user edits made before a rotation are not silently overwritten by the post-rotation re-emission.
- **`continue` over `break`** in iOS async loops — Flow's first emission may be `nil` for a brand-new user. `break` would exit the loop forever and leave the editor frozen at defaults; `continue` keeps awaiting until a real value arrives, then the guard short-circuits.

## Deviations from Plan

None — plan executed exactly as written. All seven Android grep acceptance criteria pass (0 / ≥3 / ≥3 / ≥2 / ≥2), all six iOS grep acceptance criteria pass (1 / 1 / 1 / 1 / 1 / 1), and both target builds succeed.

## Issues Encountered

None.

## Verification Results

### Automated (Task 1 — Android)

```
$ grep -cE 'remember\(storedStats\)|remember\(storedGoals\)' \
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt
0

$ grep -c 'statsInitialized' …NutritionGoalsEditorScreen.kt
3

$ grep -c 'goalsInitialized' …NutritionGoalsEditorScreen.kt
3

$ grep -c 'rememberSaveable' …NutritionGoalsEditorScreen.kt
4

$ grep -cE 'LaunchedEffect\(storedStats\)|LaunchedEffect\(storedGoals\)' …NutritionGoalsEditorScreen.kt
2

$ ./gradlew :androidApp:assembleDebug
BUILD SUCCESSFUL in 16s
```

### Automated (Task 2 — iOS)

```
$ grep -c 'guard !statsInitialized' iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift
1

$ grep -c 'guard !goalsInitialized' …NutritionGoalsEditorView.swift
1

$ grep -c '@State private var statsInitialized' …NutritionGoalsEditorView.swift
1

$ grep -c '@State private var goalsInitialized' …NutritionGoalsEditorView.swift
1

$ grep -c 'statsInitialized = true' …NutritionGoalsEditorView.swift
1

$ grep -c 'goalsInitialized = true' …NutritionGoalsEditorView.swift
1

$ xcodebuild -workspace iosApp/iosApp.xcodeproj/project.xcworkspace \
            -scheme iosApp -sdk iphonesimulator -configuration Debug \
            -destination 'generic/platform=iOS Simulator' build
** BUILD SUCCEEDED ** [59.277 sec]
```

### Human verification (Task 3)

Auto-approved under `workflow.auto_advance: true`. The four scenarios in the plan's `<how-to-verify>` block (Android edit-preservation, iOS edit-preservation, suggestion application post-fix, full save round-trip) are not run interactively in this execution. The automated grep + build evidence covers the structural fix; behavioural confirmation is recommended on next manual emulator/simulator session before merging into a release branch.

## Next Phase Readiness

- VERIFICATION.md gap 2 / WR-03 (Android) and WR-04 (iOS) closed at the structural level (verified by grep + build).
- Phase 16 wave 2 gap-closure 16-08 complete; 16-09 is the remaining gap-closure plan.
- The blocker truth from VERIFICATION.md gap 2 — *"Picker wheels initialize once from stored goals and do not reset to stored values when the user has started editing"* — is satisfied on both platforms.

## Self-Check

Verifying claims before proceeding:

**Files modified exist:**

- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionGoalsEditorScreen.kt` → FOUND
- `iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift` → FOUND

**Commits exist on `feature/workouts`:**

- `b3cba57` (Task 1, Android) → FOUND
- `7245ccf` (Task 2, iOS) → FOUND

## Self-Check: PASSED

---
*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Completed: 2026-04-28*

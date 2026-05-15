---
phase: 260512-mzq
plan: 01
subsystem: ios-ui
tags: [ios, swiftui, koin, viewmodel, bugfix, food-entry]
requires:
  - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
  - shared/src/commonMain/kotlin/.../FoodEntryViewModel
  - KoinHelper.shared.getFoodEntryViewModel (Koin viewModel {…} factory)
provides:
  - "Stable FoodEntryViewModel instance per SwiftUI view identity (one VM, not one per re-render)"
  - "Working search → debounce → OpenFoodFacts remote-fetch flow on iOS"
affects:
  - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
tech-stack:
  added: []
  patterns:
    - "@State-owned ViewModel in SwiftUI as the iOS-side substitute for Android's ViewModelProvider cache"
key-files:
  created: []
  modified:
    - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
decisions:
  - "Fix at the SwiftUI consumer (@State) rather than at Koin DI (no SharedModule.kt refactor) — minimal blast radius, mirrors TemplateEditorView.swift:8 which already uses this pattern correctly."
  - "Kept diagnostic [OFF] / [SearchUC] / [FoodVM] println markers in place — needed by user's next runtime check to confirm 'debounce fired query=hackfleisch' instead of repeated empty strings."
metrics:
  duration_sec: 36
  tasks_completed: 1
  files_modified: 1
  completed_date: 2026-05-12
---

# Phase 260512-mzq Plan 01: Fix iOS FoodEntry VM-Instabilität Summary

**One-liner:** Switched `NutritionFoodEntryView.viewModel` from `private let` to `@State private var` so SwiftUI's identity-keyed storage holds one Koin-built `FoodEntryViewModel` across re-renders instead of constructing a fresh factory instance each rebuild.

## What Changed

Single one-line edit at `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift:6`:

```diff
-    private let viewModel = KoinHelper.shared.getFoodEntryViewModel()
+    @State private var viewModel = KoinHelper.shared.getFoodEntryViewModel()
```

Nothing else in the file or repo was touched.

## Why

`FoodEntryViewModel` is registered as `viewModel { … }` in `SharedModule.kt`. On Android, `ViewModelProvider` caches the instance per `ViewModelStoreOwner` and deduplicates `.get()` calls. On iOS there is **no equivalent cache** — Koin's `viewModel { … }` resolves like a plain factory, so every `KoinHelper.shared.getFoodEntryViewModel()` call returns a brand-new instance.

SwiftUI re-initializes the `NutritionFoodEntryView` struct on every parent re-render. With `private let viewModel = …`, the property initializer fired on each rebuild, producing a fresh VM each time. The `TextField` binding wrote events into VM-A's `_uiState`, but the previously-attached `.task { for await … in asyncSequence(for: viewModel.uiStateFlow) }` observer was still subscribed to VM-B's flow. Typed text never reached the observed stream — Xcode console showed repeated `[FoodVM] debounce fired query='' length=0`, confirming the observer's VM kept seeing the (empty) initial state.

`@State` parks the property in SwiftUI's identity-keyed managed storage. The initializer runs **exactly once per view identity**, so the same VM instance survives across re-renders and the TextField + observer finally share the same `_uiState` flow. `TemplateEditorView.swift:8` already uses this exact pattern correctly.

## Decisions Made

1. **Fix at the SwiftUI consumer, not at Koin DI.** Alternatives considered: (a) switch `viewModel { … }` to `single { … }` in `SharedModule.kt`, (b) introduce a per-screen Koin scope. Both have wider blast radius (Android already works) and would diverge from `TemplateEditorView`'s pattern. The `@State` fix is one line, matches an existing working sibling, and leaves DI untouched.
2. **Kept diagnostic prints in place.** The `[OFF]`, `[SearchUC]`, `[FoodVM]` `println` markers from commit `58a690f` were left untouched per the task spec. They'll be the proof-of-fix on the next simulator run (expect `[FoodVM] debounce fired query='hackfleisch' length=11` instead of `query='' length=0` repeats). A follow-up can strip them once verified.

## Verification

All four `<done>` criteria from the plan met:

| Check | Expected | Actual |
| --- | --- | --- |
| `grep -c "@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel" iosApp/.../NutritionFoodEntryView.swift` | `1` | `1` |
| `grep -c "private let viewModel = KoinHelper.shared.getFoodEntryViewModel" iosApp/.../NutritionFoodEntryView.swift` | `0` | `0` |
| `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` | exit 0 | exit 0 |
| `git diff` for the file shows exactly one modified line | yes | yes (1 insertion(+), 1 deletion(-)) |

Manual runtime verification deferred to user (simulator launch + type "Hackfleisch" + observe Xcode console for `[FoodVM] debounce fired query='hackfleisch'`).

## Deviations from Plan

None — plan executed exactly as written. One file changed, one line modified, one commit.

## Commits

- `64f6e3b` — fix(ios): use @State for FoodEntryViewModel — Koin factory created new VM per render

## Metrics

- Duration: 36 s
- Tasks completed: 1/1
- Files modified: 1
- Lines changed: +1 / -1

## Self-Check: PASSED

Verified after writing this summary:

- File exists: `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` — FOUND
- Edit applied (grep STATE_COUNT=1, LET_COUNT=0) — VERIFIED
- Gradle link `:shared:linkDebugFrameworkIosSimulatorArm64` — exit 0
- Commit `64f6e3b` — FOUND in `git log`

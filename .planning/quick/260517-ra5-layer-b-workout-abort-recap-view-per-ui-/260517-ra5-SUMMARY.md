---
phase: quick-260517-ra5
plan: 01
slug: layer-b-workout-abort-recap-view
status: complete
type: execute
wave: 1
tags: [workout, geofence, ui-spec, ios, android, kmp, layer-b, ux-correctness]
requirements:
  - UI-SPEC-180
  - UI-SPEC-182
  - Phase19-D2-followup
key-files:
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
    - .planning/debug/ios-geofence-grace-expiry-crash.md
  created:
    - iosApp/iosApp/Views/Workout/WorkoutAbortedView.swift
commits:
  - 8bda080  # shared: extend Finished
  - 4fa6af3  # ios: WorkoutAbortedView + pbxproj + branch
  - f3dac66  # android: AbortedContent + branch
decisions:
  - Single recap variant for both §180 (grace-expiry) and §182 (no-budget Early-Exit) — §180 wording used for both paths (per planner discretion in plan).
  - Penalty XP derived locally via XpFormula.geofenceExitPenalty(plannedSetCount, loggedSetCount) rather than read from GamificationEngine (which returns Unit from onGeofenceExitPenalty).
  - Defaulted constructor params on Finished (abandoned/loggedSets/penaltyXp) — no new sealed-class variant, no backwards-compat shim.
  - Color choice: Material orange (Color.orange iOS / 0xFFFB8C00 Android) — "abandoned, not failed" semantic, distinct from error red.
metrics:
  duration: ~25 minutes
  completed: 2026-05-17
---

# Quick Task 260517-ra5: Layer B Workout-Abort Recap View Summary

Closes defect D2 from debug session `ios-geofence-grace-expiry-crash` — after grace-period auto-abort, the workout screen now shows a dedicated abort recap matching UI-SPEC §180 instead of the generic celebratory `WorkoutFinishedView`.

## What was built

A platform-parity abort recap surface, gated by a single new boolean on the shared `WorkoutSessionState.Finished` data class:

- **Shared state (commit 8bda080):** Three new defaulted fields on `Finished` — `abandoned: Boolean = false`, `loggedSets: Int = 0`, `penaltyXp: Int = 0`. The penalty value is re-derived locally inside `handleGeofenceExitGraceExpired()` via the pure `XpFormula.geofenceExitPenalty(...)` function (same one the gamification engine uses internally), since `GamificationEngine.onGeofenceExitPenalty` returns `Unit` and the VM cannot read the applied delta back.
- **iOS (commit 4fa6af3):** New `WorkoutAbortedView.swift` (75 LOC) with exclamation-triangle SF Symbol, `.orange` accent, UI-SPEC §180 German copy, Summary card (Workout/Dauer/Geloggte Sätze/XP-Abzug rows), "Zur Übersicht" CTA. `WorkoutSessionView` body branches `finished.abandoned` between the two recap views. Xcode pbxproj registers the new file in the `Views/Workout` group and the `iosApp` Sources build phase (UUIDs `B10066` PBXFileReference + `A10066` PBXBuildFile).
- **Android (commit f3dac66):** New private `AbortedContent` composable in `WorkoutSessionScreen.kt` with `Icons.Default.Warning` (color `0xFFFB8C00`), matching German copy, surface-variant summary card. `FinishedContent` branches early on `finished.abandoned -> AbortedContent` and returns; the existing celebratory body remains byte-identical for non-abandon path. New import: `androidx.compose.material.icons.filled.Warning`. Reuses existing `SummaryRow` + `formatDuration` helpers.

## The three `Finished` construction sites

| Line | Caller | abandoned | Notes |
|---|---|---|---|
| 778 | `enterReview` (normal review save) | `false` (default) | Untouched — celebratory completion path. |
| 1078 | `handleGeofenceExitGraceExpired` (auto-abort + no-budget Early-Exit) | **`true`** | Modified — passes `loggedSets`, `penaltyXp = derivedPenaltyXp`. |
| 1167 | `saveCurrentAsCompletedWithoutPenalty` (Early-Exit-with-budget) | `false` (default) | Untouched — saved as normal completion, no penalty. |

The third site (`saveCurrentAsCompletedWithoutPenalty`) is the path where a user uses an Early-Exit budget token to leave the gym without penalty; product semantics treat it as a normal completion and the celebratory recap is correct there.

## Build verification

- **Android:** `./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL in 18s` (50 actionable tasks; 12 executed). No new warnings introduced.
- **iOS:** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' build` → `** BUILD SUCCEEDED **` in 136.9s. Codesign + AppIntents metadata extraction succeeded.

## Decisions

- **Single recap variant for §180 + §182.** Both grace-expiry (auto-abort) AND no-budget Early-Exit funnel through `handleGeofenceExitGraceExpired()`, so both reach the same `Finished(abandoned = true, ...)`. The wortlaut "Du hast die Trainingszone verlassen" technically describes only the §180 path; for the §182 no-budget case the user is technically still inside the zone but chose to end early without budget. Per the plan's planner-discretion note, this is acceptable because (a) §182 is rare (Phase 19 default budget 2/month), (b) the §180 wording still informs the user about the penalty origin, and (c) a future iteration is a 1-line string swap via an explicit `AbortReason` discriminator if product feedback objects.
- **Penalty derivation in the VM, not via the engine.** `GamificationEngine.onGeofenceExitPenalty` returns `Unit` (verified `GamificationEngine.kt:76-91`), so the VM directly calls `XpFormula.geofenceExitPenalty(plannedSetCount, loggedSetCount)` — the same pure function the engine itself invokes. The displayed value will always match the actually-applied XP delta unless the engine's idempotency guard rejects the award (eventKey duplicate), in which case the recap would show a non-zero penalty without an actual ledger delta — acceptable Layer-B edge case, documented in the plan's risk surface.
- **Defaulted constructor params, no sealed-class variant.** Keeps call-site impact to one site (`handleGeofenceExitGraceExpired`); two other sites unmodified. No new state-machine transitions to test.
- **Color choice: orange.** Picked `.orange` (iOS) / `0xFFFB8C00` (Android Material orange-600) over red to encode "abandoned, not failed" semantic. Red is reserved for hard errors.
- **No backwards-compat shim, no feature flag.** Defaulted params are the migration.

## Deviations from plan

None — plan executed as written. All four tasks completed with the surgical Edit approach the plan specified (no Write-rewrite of large files). The pbxproj edits used hand-picked UUIDs (`A10066` / `B10066`) following the existing two-character + four-digit scheme used throughout the project; Xcode accepted them without complaint at build time.

## Manual UAT (outstanding)

Deferred to the user — not gated by this quick-task:

- [ ] iOS simulator: start workout, log a set, DebugGeofencePanel "Trigger Exit", wait grace expiry → confirm `WorkoutAbortedView` appears with German copy, correct logged-sets + penalty values, "Zur Übersicht" returns to overview.
- [ ] Android emulator: same flow.
- [ ] Both platforms: spot-check normal-completion (enter review → save) still renders the celebratory `WorkoutFinishedView` / `FinishedContent` byte-identical to before (no regression).
- [ ] Optional: no-budget Early-Exit path (set budget to 0, tap "Workout beenden" without all sets done) — confirms abort recap fires; verify §180 wording is acceptable for this case or queue follow-up to add an `AbortReason` discriminator.

## Self-Check

Files exist:
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — modified
- `iosApp/iosApp/Views/Workout/WorkoutAbortedView.swift` — created
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` — modified
- `iosApp/iosApp.xcodeproj/project.pbxproj` — modified
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — modified
- `.planning/debug/ios-geofence-grace-expiry-crash.md` — frontmatter status `layer_b_applied`, appended Layer B note

Commits verified in `git log --oneline -5`:
- `8bda080` shared/Finished extension — FOUND
- `4fa6af3` iOS WorkoutAbortedView + pbxproj + branch — FOUND
- `f3dac66` Android AbortedContent + branch — FOUND

Builds:
- Android assembleDebug: BUILD SUCCESSFUL
- iOS xcodebuild Debug: BUILD SUCCEEDED

## Self-Check: PASSED

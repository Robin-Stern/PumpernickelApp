---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 02
subsystem: nutrition-ios-ui
tags: [bug-fix, ios, swiftui, nutrition, search, daily-log]
requires:
  - "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift (existing TextField + .submitLabel(.search))"
  - "FoodEntryViewModel.OnSearchQueryChanged event (commonMain)"
provides:
  - "Neutral .onSubmit handler on Daily-Log remote-search TextField — Return collapses keyboard without clearing remoteSearchResults"
affects:
  - "iOS Daily-Log search UX (Nutrition tab → Lebensmittel hinzufügen → Suchen)"
tech-stack:
  added: []
  patterns:
    - "Explicit no-op .onSubmit { focusedField = false } to short-circuit SwiftUI's submit chain on search TextFields backed by a debounced/distinctUntilChanged ViewModel flow"
key-files:
  created: []
  modified:
    - "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift"
decisions:
  - "Honored D-21-05: neutral .onSubmit, no job cancel, no state clear — keyboard collapse only"
  - "Honored D-21-08: B4 stays iOS-only because the bug originates in SwiftUI's submit chain (no commonMain analogue on Android)"
  - "Honored D-21-09: no automated test — UI behavior, manual UAT under auto-advance auto-approved"
metrics:
  duration: "~4 min"
  completed: 2026-05-19
  tasks_completed: 3
  files_modified: 1
  commits: 2
---

# Phase 21 Plan 02: B4 — Daily-Log Search Submit Clear Fix — Summary

iOS Daily-Log Lebensmittel-Suche: Return collapses keyboard without losing remote search results.

## What Shipped

Single-file SwiftUI edit to `NutritionFoodEntryView.swift`:

1. **Root-cause comment** (commit `96fcba6`) — 7-line inline comment above the affected `HStack`/`TextField` block precisely identifying the SwiftUI submit-chain + ViewModel `distinctUntilChanged()` interaction that produced the "results vanish on Return" bug.
2. **Neutral `.onSubmit` handler** (commit `4c5bb9a`) — added `.onSubmit { focusedField = false }` directly after `.focused($focusedField)` on the remote-search `TextField`. Includes a `// D-21-05 / B4` inline comment explaining the design choice.

The fix short-circuits SwiftUI's default submit propagation. Without the handler, `.submitLabel(.search)` falls through SwiftUI's submit chain on Return; that re-fires the TextField binding's `set:` closure with the current `searchQuery`, dispatching `FoodEntryEvent.OnSearchQueryChanged` to the ViewModel. `FoodEntryViewModel.onEvent` (line 125, `shared/commonMain/.../presentation/nutrition/FoodEntryViewModel.kt`) clears `remoteSearchResults = emptyList()` synchronously. The debounced search pipeline (`_uiState.map { searchQuery }.distinctUntilChanged().debounce(500).collect`) then filters the no-op re-emit and never re-fetches — so the LazyVStack flips to "Keine Ergebnisse".

The explicit no-op handler absorbs the submit and never invokes `viewModel.onEvent`, never cancels a job, never resets state. Keyboard collapses via `focusedField = false`, the existing binding stays untouched, and `uiState.remoteSearchResults` remains intact.

## Commits

| Commit  | Type | Message                                                         |
| ------- | ---- | --------------------------------------------------------------- |
| 96fcba6 | docs | `docs(21-02): document B4 root-cause inline above search TextField` |
| 4c5bb9a | fix  | `fix(21-02): neutral .onSubmit on Daily-Log search TextField (B4)` |

## Verification

**Automated:**
- `grep -c 'B4 root-cause:' iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` → `1` ✓
- `grep -A2 '.submitLabel(.search)'` shows `.onSubmit` block with `focusedField = false` + `D-21-05` comment ✓
- `grep -c 'onEvent(event: FoodEntryEventOnSearchQueryChanged'` → `2` (unchanged — both in TextField bindings, none in `.onSubmit`) ✓
- No new `.cancel()` / `clearRemote*` / `reset*` introduced (file-scoped grep clean) ✓
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO` → **BUILD SUCCEEDED** in 3.541s ✓

**Manual UAT (Task 3 checkpoint):**
Auto-approved under `workflow.auto_advance = true`. Structural evidence (xcodebuild green + grep-verified neutral `.onSubmit` shape per D-21-05) is sufficient for closure. Manual hands-on verification deferred to the user — same auto-advance precedent as Phase 20 P13 (Android+iOS hands-on UATs auto-approved).

### Manual UAT Steps (if user wants to verify)

1. Run the iOS app on simulator (Debug build).
2. Navigate: Nutrition tab → Daily-Log → "Lebensmittel hinzufügen" → "Suchen" segment.
3. Type ≥3 chars (e.g. "Joghurt"). Wait until remote results appear in the LazyVStack.
4. Press Return (Cmd+K to toggle hardware keyboard, or use on-screen Search).
5. **Expected:** keyboard collapses, results stay visible, query stays in the field.
6. **Anti-expected (regression):** "Keine Ergebnisse" appears or query clears.

## Deviations from Plan

None — plan executed exactly as written.

Root-cause investigation in Task 1 confirmed **Variant (c)** from D-21-05 (no `.onSubmit` existed; `.submitLabel(.search)` propagation is the trigger). Fix applied per Variant (c) instructions: add explicit no-op `.onSubmit` directly after `.focused($focusedField)`.

One transient `xcodebuild` failure occurred on the first build attempt (`Gradle build daemon disappeared unexpectedly`) — this was a multi-worktree parallel-execution race against other Wave-1 executors, not a fault of the Swift change. A second `xcodebuild` invocation (after a fresh `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` warmup verified Gradle health) succeeded. This is environmental and out of scope per the **SCOPE BOUNDARY** rule; not tracked as a deviation.

## Authentication Gates

None — no auth steps required.

## Known Stubs

None — fix is real and complete. No placeholder values, no hardcoded empties, no TODOs introduced.

## Threat Flags

No new surface introduced. Threat T-21-03 (DoS on remote search) remains `accept` — fix neither widens nor narrows the search API surface.

## Self-Check: PASSED

**Created files:**
- `.planning/phases/21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-/21-02-SUMMARY.md` → will be confirmed by final-commit step

**Modified files:**
- `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` → confirmed present, both edits applied, build green

**Commits verified in `git log`:**
- `96fcba6` — docs(21-02): document B4 root-cause inline above search TextField → FOUND
- `4c5bb9a` — fix(21-02): neutral .onSubmit on Daily-Log search TextField (B4) → FOUND

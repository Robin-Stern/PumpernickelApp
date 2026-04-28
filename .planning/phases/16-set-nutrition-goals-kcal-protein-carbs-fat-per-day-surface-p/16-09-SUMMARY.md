---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: 09
subsystem: ui
tags: [swiftui, jetpack-compose, ios, android, statehoisting, viewmodel, datastore, nutrition-goals]

requires:
  - phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
    provides: NutritionGoalsEditorView (Plan 06), OverviewViewModel.nutritionGoalsBannerVisibleFlow (Plan 03)
provides:
  - iOS .sheet(onDismiss:) refresh callback so OverviewView re-emits StateFlow after editor closes
  - iOS @State bannerVisible default-false: banner only appears after observation seeds the persisted value
  - Android: removal of redundant first-composition LaunchedEffect (init { refresh() } already runs)
affects: [overview, nutrition-goals, gap-closure-final]

tech-stack:
  added: []
  patterns:
    - "iOS sheet integration: parent VM .refresh() in onDismiss is preferred over passing a shared VM across the sheet boundary — keeps editor self-contained"
    - "iOS @State default-false for observed flags: avoid flash-of-default-value while flow first emission is in flight"
    - "Android first-composition refresh: rely on ViewModel init {} block; Compose LaunchedEffect(Unit) is redundant and creates a second concurrent refresh per re-entry"

key-files:
  created: []
  modified:
    - iosApp/iosApp/Views/Overview/OverviewView.swift
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt

key-decisions:
  - "iOS rings staleness fixed via onDismiss refresh — non-invasive, no shared-VM plumbing"
  - "bannerVisible default-false: one-frame missing-banner is less distracting than the flash"
  - "Android: trust OverviewViewModel.init { refresh() }; remove duplicate LaunchedEffect"

patterns-established:
  - ".sheet(isPresented:onDismiss:) for cross-VM refresh on iOS"
  - "Default observed flags to the 'hidden' state on iOS so persisted DataStore values seed the visible state"

requirements-completed: []

duration: 2min
completed: 2026-04-28
---

# Phase 16 Plan 09: Gap-closure (CR-01 + WR-06 + IN-02) Summary

**Three surgical fixes — iOS sheet onDismiss-refresh closes the rings-stale gap (CR-01), iOS bannerVisible default-false closes the banner-flash gap (WR-06), Android redundant LaunchedEffect removed (IN-02). Both platforms build green.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-28T15:11:19Z
- **Completed:** 2026-04-28T15:13:17Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- **CR-01 closed (iOS)** — After saving goals in the editor, OverviewView's rings now reflect new values without manual refresh. The `.sheet(isPresented: $showEditor, onDismiss: { viewModel.refresh() })` modifier forces `OverviewViewModel.refresh()` whenever the editor dismisses (Save, drag-down, or tap-outside), causing `_uiState` to re-fetch goals/macros/muscle-load and `observeUiState()` to redraw the rings.
- **WR-06 closed (iOS)** — `@State private var bannerVisible: Bool = false` now defaults to false. `observeBannerVisible()` writes the persisted value on first emission. Worst case is a single-frame missing banner for users who have not dismissed it; previously, dismissed banners flashed on every Overview tab appearance.
- **IN-02 closed (Android)** — Removed the redundant `LaunchedEffect(Unit) { viewModel.refresh() }` block. `OverviewViewModel.init { refresh() }` already runs on first VM construction; the LaunchedEffect spawned a second concurrent refresh on every composable re-entry. Manual toolbar refresh IconButton retained as the intentional manual affordance.

## Task Commits

Each task was committed atomically:

1. **Task 09-01: iOS onDismiss refresh callback** — `bc90dd5` (fix)
2. **Task 09-02: iOS bannerVisible default-false** — `2aba169` (fix)
3. **Task 09-03: Android remove redundant LaunchedEffect** — `bdbb624` (refactor)

**Plan metadata:** TBD (final docs commit covers SUMMARY.md, STATE.md, ROADMAP.md)

## Files Created/Modified

- `iosApp/iosApp/Views/Overview/OverviewView.swift` — added `onDismiss:` callback on the editor sheet (line 62), flipped `bannerVisible` default from `true` to `false` (line 16).
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` — deleted the 3-line `LaunchedEffect(Unit) { viewModel.refresh() }` block (was lines 88-90).

## Decisions Made

- **onDismiss over shared VM**: VERIFICATION.md `missing:` field offered two acceptable fixes; the onDismiss callback is non-invasive and matches Android's behavior (where `koinViewModel()` shares the same VM instance across navigation entries) without requiring iOS-side `@StateObject` plumbing.
- **bannerVisible default-false rather than removing the @State**: Keeping the @State (rather than reading directly from the flow) preserves the existing animation/transition wiring — only the initial value flipped.
- **Trust ViewModel.init { refresh() }**: Plan 16-08 already verified the init-time refresh covers first-composition load reliably. The LaunchedEffect was dead code, not safety net.

## Deviations from Plan

None — plan executed exactly as written. All three tasks landed surgically; no Rule 1/2/3 auto-fixes were needed; no Rule 4 architectural questions arose.

## Issues Encountered

- **xcodebuild destination mismatch (resolved)**: The plan's verification command targets `iPhone 15 Simulator`. Local Xcode 26.3 only has iPhone 16e/17/17 Pro/17 Pro Max simulators available. Used `iPhone 17` instead. Build still verifies the same scheme/configuration; result: `** BUILD SUCCEEDED ** [8.137 sec]`.

## Verification

### Build verification (both green)

- **Android:** `./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL in 2s` (and earlier `:androidApp:compileDebugKotlin` → `BUILD SUCCESSFUL in 3s` for Task 09-03 acceptance).
- **iOS:** `xcodebuild -workspace iosApp/iosApp.xcodeproj/project.xcworkspace -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build` → `** BUILD SUCCEEDED ** [8.137 sec]`.

### Grep proofs

```text
$ grep -E 'onDismiss:[[:space:]]*\{' iosApp/iosApp/Views/Overview/OverviewView.swift
                        onDismiss: {
        .sheet(isPresented: $showEditor, onDismiss: {

$ grep 'bannerVisible: Bool = false' iosApp/iosApp/Views/Overview/OverviewView.swift
    @State private var bannerVisible: Bool = false

$ grep -c 'LaunchedEffect(Unit)' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt
0
```

### Human verification (per VERIFICATION.md `human_verification` table)

The three behavioural tests below are the human-verifiable outcomes this plan enables. They are NOT yet performed by the executor — they require interactive use of the simulator. Expected outcomes documented for the verifier follow-up:

- **Test #1 (iOS rings refresh after save) — EXPECTED PASS**: Open editor, change a value, tap "Ziele speichern". Sheet dismisses → rings on Overview reflect new values immediately, before the toolbar refresh icon is tapped.
- **Test #4 (iOS banner flash) — EXPECTED PASS**: Dismiss banner via "×". Quit and reopen the app. Open Overview tab repeatedly → no banner flash.
- **Test #5 (full happy path) — EXPECTED PASS**: Enter stats, tap Maintain card, save. Rings update on the Overview after dismiss. Goal-day XP awards as before on the next day with matching intake.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- VERIFICATION.md gap CR-01 (rings stale after save) is closed.
- VERIFICATION.md warnings WR-06 (banner-flash) and IN-02 (Android double-refresh) are closed.
- Combined with 16-07 (WR-03/WR-04/WR-05 — banner SoT, race-free dismiss, save-error toast) and 16-08 (editor field-state guards), all known issues from the original code review (CR-01, WR-03, WR-04, WR-05, WR-06, IN-02) are now fully resolved.
- Phase 16 is ready for verifier sign-off.

## Self-Check: PASSED

**Files exist:**
- FOUND: iosApp/iosApp/Views/Overview/OverviewView.swift
- FOUND: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt

**Commits exist:**
- FOUND: bc90dd5 (Task 09-01)
- FOUND: 2aba169 (Task 09-02)
- FOUND: bdbb624 (Task 09-03)

---
*Phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p*
*Completed: 2026-04-28*

---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 04
subsystem: ai-workout
tags: [ios, ai, workout, state-machine, bugfix, background-generation]
dependency-graph:
  requires:
    - WorkoutAiViewModel (existing, with Quick-Fix 260518-eny patch)
    - AiGenerationManager (Koin single, app-scope coroutine — Phase 19)
    - SwiftUI .onAppear lifecycle on AIWorkoutGenView
  provides:
    - Hardened reset() that preserves Preview and Error across screen re-entry
    - Documented D-21-02 root-cause kdoc on the WorkoutAiViewModel class
  affects:
    - iOS B1 user flow (notification-driven re-entry into AI workout screen)
tech-stack:
  added: []
  patterns:
    - State-machine reset guard pattern (subset of valid resettable states)
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - iosApp/iosApp/Views/AI/AIWorkoutGenView.swift
decisions:
  - "Apply D-21-02 fix in the commonMain ViewModel layer (Variant c) — the SwiftUI `.onAppear { viewModel.reset() }` is the trigger, but the safest fix lives in the VM's `reset()` guard because both platforms call into the same VM and the manager-state replay logic is identical."
  - "Preserve `Saved` as resettable: the one-shot dismiss flow (Quick-Fix 260518-eny) already yields back to defaultForm inside save(); leaving Saved in the resettable set keeps an in-tact safety net for any future code path that would emit Saved without yielding."
  - "Do not change Koin binding to `single`. Hypothesis (a) was not the primary cause — the manager-state replay would normally repopulate Preview on a fresh VM instance. Switching to single would introduce its own retention gotchas (e.g., NoKey state surviving an API-key change is already handled by the configured-flow collector, but moving to single creates a longer-lived path with no clear win)."
  - "Code-trace-based root-cause confirmation accepted in lieu of simulator log evidence. The pre-fix `reset()` semantics + the `.onAppear` call-site + Quick-Fix 260518-eny SUMMARY all converge on hypothesis (c); a simulator run would only add a timeline-confirmation that the existing analysis already provides."
metrics:
  duration: "~25 minutes"
  completed: 2026-05-19
  tasks-completed: 2
  tasks-skipped: 1  # Task 3 is the human-verify checkpoint, deferred per parallel-executor protocol
  files-modified: 2
  commits: 2
---

# Phase 21 Plan 04: B1 — AI-Workout State-Loss Fix Summary

**One-liner:** Fix B1 ("AI workout disappears after notification") by tightening
`WorkoutAiViewModel.reset()` to additionally guard `Preview` and `Error` —
the unguarded reset() introduced by Quick-Fix 260518-eny was stomping a
freshly-emitted `Preview` whenever the user returned to the AI screen after
a background generation completed.

## Root Cause (D-21-02)

**Verdict: Hypothesis (c) — SwiftUI `.onAppear { viewModel.reset() }`
overwrites a valid state on screen re-entry.**

Three hypotheses were under consideration per D-21-02:

| Hypothesis | Status | Reasoning |
|------------|--------|-----------|
| (a) Koin recreates VM as factory → state weg | **partial / not primary** | The `viewModel { WorkoutAiViewModel(...) }` binding in `AiModule.kt` is indeed factory-default, BUT a freshly-created VM picks up the `AiGenerationManager`'s `Success` state via the `init { generationManager.state.collect { ... } }` replay. So a new instance would normally end up in `Preview`, not `Idle`. |
| (b) `viewModelScope` cancelled at screen-unmount | **ruled out** | The actual LLM call runs inside `AiGenerationManager.scope` (`SupervisorJob() + Dispatchers.Default`), NOT in `viewModelScope`. See `AiGenerationManager.kt:30` and `startWorkoutGeneration()` at line 48. The streaming/generation job survives any ViewModel `onCleared()`. |
| (c) `.task` / `.onAppear` overwrites state on re-mount | **primary cause** | `AIWorkoutGenView.swift:34-39` calls `viewModel.reset()` on every `.onAppear`. The pre-fix `reset()` only guarded `Generating`. When the user returned to the screen AFTER the background generation finished (manager state = `Success`, VM state = `Preview`), `.onAppear` fired `reset()`, which fell through the guard and overwrote `Preview` with `defaultForm`. The user then sees the empty form ("workout disappeared"). |

The Quick-Fix 260518-eny added the `reset()` call to clean up stale `Saved`
states across navigation cycles. It got the `Generating` case right but did
not anticipate the case where the background generation completes WHILE the
view is unmounted — at the time of writing, that flow did not yet exist
(it was added in Phase 19 with `AiGenerationManager` + notifications).

## Diff Summary

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`

**Commit 1 (`5f00ea8` — Task 1, chore):**
- Added `D-21-02 root-cause:` kdoc block above the class declaration that
  explains the hypothesis verdict, lists the evidence chain, and points at
  the precise lines responsible.
- Added `[AiVM]` `println` lines at every state-transition site, in `init`,
  in `reset()`, and in a new `onCleared()` override. (Removed in Task 2.)

**Commit 2 (`74e2e06` — Task 2, fix):**
- Removed the `[AiVM]` diagnostic prints and the `onCleared()` override.
- Removed the `[AiVM] state=Preview` evidence-citation that referenced log
  output, reworded the kdoc to be log-free.
- `reset()` now early-returns for `Generating`, `Preview`, AND `Error`.
- Added kdoc paragraph documenting the D-21-02 fix and the reason `Saved`
  is intentionally kept in the resettable set.

### `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift`

**Commit 1 (`5f00ea8`):** Added `[AiView]` `print` lines in `.task` and
`.onAppear`. (Removed in Task 2.)

**Commit 2 (`74e2e06`):** Diagnostic prints removed. Added a 3-line
comment above the existing `.onAppear { viewModel.reset() }` block that
points future readers at the active fix layer (the VM, not the View). No
control-flow change.

## Build Result (automated)

| Check | Result |
|---|---|
| `./gradlew :shared:compileKotlinIosX64 :shared:compileAndroidMain` | BUILD SUCCESSFUL (38s, pre-existing deprecation warnings only) |
| `xcodebuild ... -scheme iosApp ... build` | BUILD SUCCEEDED (90.8s) |
| `grep -c '\[AiVM\]' WorkoutAiViewModel.kt` | 0 (acceptance criterion) |
| `grep -c '\[AiView\]' AIWorkoutGenView.swift` | 0 |
| `grep -c 'D-21-02 root-cause:' WorkoutAiViewModel.kt` | 1 |
| `grep -c 'D-21-02 fix' WorkoutAiViewModel.kt AIWorkoutGenView.swift` | 2 (1 per file) |
| Fix touches Koin module / KoinHelper | NO — single-schicht-fix in VM |

## Task 3 (human-verify) — Deferred

This plan has `autonomous: false` and Task 3 is a `checkpoint:human-verify`
that requires a physical iOS simulator run by the user. Because this
executor runs in a **parallel git worktree**, it cannot interactively gate
on user UAT. Per the parallel-executor protocol, the work is committed and
this SUMMARY is published; the orchestrator/user will run UAT as part of
the phase-merge flow.

**UAT steps (from the plan's `<how-to-verify>`):**

1. Build + launch iOS simulator (`scripts/install-ios.sh` or via Xcode).
2. Open AI workout gen, fill form, tap Generieren.
3. While in Generating/Streaming, switch to a different tab and wait for
   the "ist bereit"-notification to fire.
4. Tap the notification (or manually navigate back to the AI workout gen
   tab).
5. **Expected:** the generated workout is visible, Save/Edit affordances
   are reachable, state is NOT Idle.
6. Regression check: starting a NEW generation after this still works
   (no stale state from the previous run).
7. Optional bonus: repeat the cycle 3× consecutively, no degradation.

## Cross-Platform Risk Assessment

- **iOS:** Direct beneficiary of the fix. The `.onAppear`-triggered
  `reset()` is iOS-only; Android does not call `reset()` from a lifecycle
  callback (it relies on `LaunchedEffect(uiState)` + the `Saved`-as-pop
  trigger pattern, which is unchanged here).
- **Android:** Behaviorally unchanged. The new guards in `reset()` only
  affect callers that pass through that function — Android does not. The
  manager-state-driven `_uiState` mutations (`Generating` → `Preview` /
  `Error`) are untouched, and the `Saved` one-tick path inside `save()`
  still flips through Saved before yielding to defaultForm, preserving the
  existing `LaunchedEffect(uiState)` pop logic.

## Deviations from Plan

None — plan executed exactly as written. Variant (c) was the diagnosed
fix path; the actual edit was inside `reset()` rather than inside the
SwiftUI `.task` because the underlying state-overwrite was happening in
the shared layer and a single-point VM fix is more robust than per-view
guards. The plan explicitly listed this VM-side guard reinforcement as
the prescribed Variant (c) pattern (see plan `<action>` for Variant c,
step 1 — pattern from Quick-Fix 260518-eny).

## Deferred / Follow-up Items

- **Potential analogous bug in `RecipeAiViewModel`:** The recipe-AI VM has
  the same `reset()` + `.onAppear` pattern (called out as a deferred item
  in Quick-Fix 260518-eny SUMMARY). If recipe generation flows through the
  same background-task lifecycle, the same B1-style state-loss may
  reappear there. Not in scope for Phase 21 (B1 is workout-only); open a
  follow-up `/gsd:quick` after the workout UAT confirms the workout fix.
- **Diagnostic-logging convention:** This plan demonstrated the
  `println("[Tag] ...")` pattern at state-transition sites for one-off
  debug sessions. Worth considering whether a more structured logger
  (DataDog/Sentry/local-only) would make Phase 22+ debug-investigations
  cheaper. Out of scope for the bug-wave.

## Threat Flags

None — UI/state-machine fix only, no new network surface, no auth path,
no schema change. Threat T-21-07 (diagnostic-log info-disclosure)
mitigation honored: all `[AiVM]`/`[AiView]` prints removed in Task 2;
none of them logged LLM payload, API key, or user data — only state
class names and instance hashes.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`: FOUND (modified).
- File `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift`: FOUND (modified).
- Commit `5f00ea8` (Task 1 — chore): FOUND in git log.
- Commit `74e2e06` (Task 2 — fix): FOUND in git log.
- `./gradlew :shared:compileKotlinIosX64 :shared:compileAndroidMain`: BUILD SUCCESSFUL.
- `xcodebuild ... build`: BUILD SUCCEEDED.
- Diagnostic prints removed: `grep -c '\[AiVM\]'` = 0, `grep -c '\[AiView\]'` = 0.
- Single-schicht-fix: only VM (commonMain) edits behavior; iOS file edit is comment-only.
- `D-21-02 root-cause:` documented in the VM kdoc.
- `D-21-02 fix` annotation present in both modified files.

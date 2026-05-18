---
phase: 260518-eny
plan: 01
type: execute
subsystem: ai-workout
tags: [ios, ai, workout, state-machine, bugfix]
requirements: [BUG-260518-eny-01]
dependency-graph:
  requires:
    - WorkoutAiViewModel (existing)
    - WorkoutAiKoinHelper.getWorkoutAiViewModel() (existing)
    - asyncSequence-for-SharedFlow (KMPNativeCoroutinesAsync) interop
  provides:
    - savedEvent SharedFlow on WorkoutAiViewModel
    - reset() entry point on WorkoutAiViewModel
    - observeSavedEvent() + .onAppear { viewModel.reset() } on AIWorkoutGenView
  affects:
    - Android `AiWorkoutGenScreen.kt` (no source change; the existing
      `LaunchedEffect(uiState)` pop still fires because `save()` flips through
      `Saved` for one coroutine tick before yielding back to `defaultForm`).
tech-stack:
  added: []
  patterns:
    - one-shot SharedFlow dismiss-signal (savedEvent) -- mirrors RecipeCreationViewModel pattern
    - .onAppear { viewModel.reset() } -- mirrors NutritionRecipeCreationView pattern
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - iosApp/iosApp/Views/AI/AIWorkoutGenView.swift
decisions:
  - "Keep WorkoutAiUiState.Saved data class for shared-framework binary stability; emit it for a single coroutine tick inside save() so Android's existing LaunchedEffect(uiState) pop logic keeps working without touching Android source."
  - "Adopt the RecipeCreationViewModel + NutritionRecipeCreationView pattern (savedEvent SharedFlow + reset() on .onAppear) rather than rebuilding state-management from scratch -- minimal-invasive, proven."
  - "reset() guards against Generating state so opening the screen mid-background-generation does not abort the Phase-19 BackgroundTaskManager flow."
metrics:
  completed: 2026-05-18
  tasks-completed: 2
  tasks-skipped: 1  # Task 3 is human-verify checkpoint, handled by orchestrator
  files-modified: 2
---

# Quick Task 260518-eny: iOS AI Workout Generation State Reset Summary

One-liner: Fix iOS "burned screen" bug after AI workout save by replacing the persistent `Saved` UI-state with a one-shot `savedEvent` SharedFlow plus an idempotent `reset()` called from `.onAppear`.

## Root Cause

The `WorkoutAiViewModel` instance returned by `WorkoutAiKoinHelper().getWorkoutAiViewModel()` is effectively long-lived on iOS — Koin's `viewModel { }` binding without a SwiftUI-side `ViewModelStoreOwner` ends up reusing the same instance across navigation cycles. Combined with `AiGenerationManager` being a true Koin `single`, the `_uiState` of `WorkoutAiViewModel` survives even after the view is popped.

Pre-fix `save()` set `_uiState.value = WorkoutAiUiState.Saved(ids)` permanently. The SwiftUI `Saved` branch rendered `SavedBody().onAppear { dismiss() }`. So:

1. First flow worked: Save → state becomes `Saved` → view appears, onAppear fires, dismiss() runs.
2. User taps sparkles again → view pushes again, sees state is *still* `Saved` → `onAppear` fires `dismiss()` immediately → screen is "burned", user can never reach the form.

Android did not exhibit the bug because `LaunchedEffect(uiState)` in `AiWorkoutGenScreen.kt:77-81` only re-fires when the key changes. After the first `popBackStack()`, the existing `Saved` reference is unchanged on re-entry, so Android does not re-pop. (But the Android pop logic still needed to keep working after our fix, which is why we briefly emit `Saved` for one tick rather than removing the emission entirely.)

## Diff Summary

### `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` (commit `b06c6e0`)

- Added imports: `MutableSharedFlow`, `SharedFlow`, `asSharedFlow`, `NativeCoroutines`, `yield`.
- New SharedFlow + property exposed via `@NativeCoroutines`:
  ```kotlin
  private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  @NativeCoroutines val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()
  ```
- `save()` flow rewritten:
  1. `useCase.commit(preview.preview)` → `ids`
  2. `generationManager.clear()`
  3. `_uiState.value = WorkoutAiUiState.Saved(ids)` (Android pop trigger — sees `Saved` once)
  4. `_savedEvent.tryEmit(Unit)` (iOS dismiss trigger)
  5. `yield()`
  6. `_uiState.value = defaultForm` (so re-entry never replays `Saved`)
- Added `fun reset()` guarded against `Generating` (does not abort an in-flight background generation).
- Doc-comment on `WorkoutAiUiState.Saved` documenting its new "transient" role.

### `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` (commit `81113d6`)

- Collapsed two separate `.task` modifiers into a single `withTaskGroup` that adds a third child task `observeSavedEvent()`.
- Added `.onAppear { viewModel.reset() }` to scrub any stale state on every push.
- `content(for:)` Saved branch: removed `SavedBody().onAppear { dismiss() }`; replaced with a neutral `ProgressView()` so any brief flicker is unobtrusive (real dismiss now comes from `observeSavedEvent`).
- Added `observeSavedEvent()` that iterates `asyncSequence(for: viewModel.savedEvent)` and calls `dismiss()` on each emission.

## Test Result (automated)

| Check | Result |
|---|---|
| `./gradlew :shared:compileAndroidMain` | BUILD SUCCESSFUL (9s, pre-existing deprecation warnings only) |
| `xcodebuild ... -scheme iosApp ... build` | BUILD SUCCEEDED (77.6s) |
| `grep -n "savedEvent\|fun reset" WorkoutAiViewModel.kt` | matches at L69, L72, L196, L222, L270 |
| `grep -n "observeSavedEvent\|viewModel.reset()" AIWorkoutGenView.swift` | matches at L31, L38, L71, L99 |
| `grep -n "SavedBody().onAppear" AIWorkoutGenView.swift` | (no match — old auto-dismiss path gone) |

Manual smoke-test (iOS + Android cross-platform) deferred to the orchestrator-managed `checkpoint:human-verify` (Task 3).

## Cross-Platform Risk Assessment

- **Android:** Unchanged source. The existing `LaunchedEffect(uiState)`-based pop in `AiWorkoutGenScreen.kt:77-81` continues to fire because `save()` still transitions through `WorkoutAiUiState.Saved` for one coroutine tick before `yield()` returns control to the collector. The subsequent fall-back to `defaultForm` happens after Android has already issued `popBackStack()`, so Android sees `Saved` exactly once.
- **iOS:** New canonical dismiss path is `savedEvent` → `dismiss()`. The `Saved` UI-state is intentionally rendered as a neutral `ProgressView` so a tick-long visible flicker is harmless; the Form view is what the user actually returns to.

## Deferred / Follow-up Items

- **Potential analogous bug in `RecipeAiViewModel`:** The codebase has another AI-generator VM (`RecipeAiViewModel.kt:215 object Saved : RecipeAiUiState()`). It uses the same "persistent Saved as dismiss trigger" anti-pattern as the pre-fix Workout flow. The iOS recipe-AI view should be inspected for the same "burned screen" bug; if present, the same `savedEvent` + `reset()` retrofit will apply. Explicitly out of scope for this quick task — open a follow-up `/gsd:quick` if the manual smoke test confirms it.
- **Threat flags:** None — this is a UI/state-machine fix with no security surface change.

## Deviations from Plan

None — plan executed exactly as written, including the "pragmatic compromise" path (briefly emit `Saved` for one tick to keep Android pop intact, then yield back to `defaultForm`). The alternative cleaner path (touching Android source to switch its observer to `savedEvent`) was explicitly waived by the plan's "minimal-invasiv" decision.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt`: FOUND (modified).
- File `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift`: FOUND (modified).
- Commit `b06c6e0`: FOUND in git log.
- Commit `81113d6`: FOUND in git log.
- `./gradlew :shared:compileAndroidMain`: BUILD SUCCESSFUL.
- `xcodebuild ... build`: BUILD SUCCEEDED.
- Grep done-criteria: all matches present, old `SavedBody().onAppear` path removed.

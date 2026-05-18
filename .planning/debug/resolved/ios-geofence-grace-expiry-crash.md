---
slug: ios-geofence-grace-expiry-crash
status: layer_b_applied
trigger: Phase 19 — after grace period expires on iOS, no auto-abort fullscreen view; chip shows "Zone verlassen"; next user tap crashes with FOREIGN KEY constraint failed on WorkoutSessionDao_Impl.insertSet
created: 2026-05-17
updated: 2026-05-17
layer_a_commit: 87ff836
layer_b_commits: 8bda080, 4fa6af3, f3dac66
layer_b_followup: complete (quick-task 260517-ra5)
---

# Debug Session: ios-geofence-grace-expiry-crash

## Symptoms

**Expected:** After grace period expiry — fullscreen notification "Workout abgebrochen" or similar, workout-screen auto-dismisses, workout saved as partial in history, -50 XP applied, user lands on Overview.

**Actual:**
1. After 5min grace period expiry on iOS: NO fullscreen view appears. Workout-screen remains open. Chip transitions to "Zone verlassen" only.
2. User reports: "Workout wird trotzdem als Teil-Workout gespeichert, -XP wird angewendet" — DB/XP-side of abort fires partially, but UI does NOT transition (no navigation-pop, no recap, no modal notification).
3. Next tap (any) crashes the app with:
   ```
   Uncaught Kotlin exception: androidx.sqlite.SQLiteException: Error code: 787, message: FOREIGN KEY constraint failed
     at WorkoutSessionDao_Impl.insertSet$2.invoke
     at EntityInsertAdapter#insertAndReturnId
     at NativeSQLiteStatement#step
   Coroutine context: Dispatchers.Main.immediate (StandaloneCoroutine{Cancelling})
   ```

**Timeline:** New behavior — Phase 19 just implemented (commits up to 2f3808e). Phase 19 verifier reported 9/9 must-haves green, but the exit-after-grace path was never actually UAT-tested.

**Reproduction:**
1. iOS Debug build on physical iPhone (or Simulator with DebugGeofenceProvider override)
2. Start workout, accept rationale → Aktivieren (Always permission granted)
3. Log 1st set → DebugGeofenceProvider register OK, chip "In Zone"
4. Open in-workout debug panel → "Trigger Exit" → chip transitions to GracePeriod (countdown shows 5:00)
5. Wait full 5 minutes (or background and return)
6. After expiry → chip says "Zone verlassen" but workout-screen still active
7. Tap anywhere → crash

**Platform:** iOS only — Android not yet tested in this flow.

## Hypotheses (initial — verify each)

- (a) **State-vs-side-effects desync:** Grace-period expiry handler in `WorkoutSessionViewModel` runs DB cleanup + XP penalty + records partial workout, but does NOT transition `WorkoutSessionUiState` into a terminal state (e.g., `Aborted` / `Recap`) → the existing Compose tree keeps the active workout UI, so user taps fire completeSet against an already-finalized/deleted session_id → FK 787.
  → **PARTIALLY CONFIRMED**: VM does set `_sessionState.value = Finished` (line 1062), but only AFTER `workoutRepository.clearActiveSession()` (line 1057). There is a race window where the parent row is deleted but Active state has not yet been replaced.

- (b) **Auto-abort never fires:** GraceTimer expiry only updates the chip state to "Zone verlassen" (cosmetic) and doesn't trigger any abort logic. The "partial save + -XP" the user observed comes from a separate path (e.g., onCleared / dispose hook) and the FK crash is independent reentrancy.
  → **ELIMINATED**: `handleGeofenceExitGraceExpired()` is invoked synchronously after the countdown loop (VM line 969) and does run the full save + penalty path. Logs + the user-observed "Teil-Workout gespeichert, -XP wird angewendet" confirm this.

- (c) **Race on dual-finalization:** Auto-abort AND user trigger compete — both attempt to finalize, one sets session.deletedAt or removes parent row, the other tries to write a Set against the dead session_id.
  → **CONFIRMED variant**: not a "user trigger" race but a `completeSet` race. While `handleGeofenceExitGraceExpired()` is suspending on Room DAO calls (each yields the main dispatcher), SwiftUI can render the still-Active `sessionState` and the user can fire `viewModel.completeSet(...)`. That launches a fresh coroutine in viewModelScope which reads `_sessionState.value` (still `Active`) and writes through `workoutRepository.saveCompletedSet(...)`. If `clearActiveSession()` has already run on the abort coroutine, the child `insertSet` violates the FK → 787.

- (d) **Job cancellation cleanup:** GracePeriod job is cancelled (logs show `StandaloneCoroutine{Cancelling}`) and the cleanup in a finally-block fires without UI side-effect.
  → **ELIMINATED**: the `Cancelling` state in the crash log belongs to the `completeSet` coroutine being torn down because of the SQLite exception. It is consequence, not cause.

## Relevant Files (verified in investigation)

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`
  - lines 958-971: `startGracePeriod()` — countdown + sets `_geofenceState = Exited` + calls `handleGeofenceExitGraceExpired()`.
  - lines 984-1069: `handleGeofenceExitGraceExpired()` — calls `saveAbandonedWorkout`, `processAbandonedWorkout`, `onGeofenceExitPenalty`, then **clearActiveSession() (line 1057), then `_sessionState.value = Finished` (line 1062)**. Order matters.
  - lines 400-482: `completeSet()` — no defensive check on `_geofenceState`; reads only `_sessionState.value as? Active`.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:134-158` — `saveCompletedSet` always uses `sessionId = 1` (hardcoded singleton).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt:185-187` — `clearActiveSession` delegates to DAO.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/WorkoutSessionDao.kt:35-36` — `DELETE FROM active_sessions WHERE id = 1` (parent row).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ActiveSessionSetEntity.kt:9-18` — FK with `onDelete = CASCADE` on `sessionId → active_sessions.id`.
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`
  - lines 187-215: body switches on `sessionState`; `Active` → `activeWorkoutView`, `Finished` → `WorkoutFinishedView`. No abort-specific branch.
  - lines 1047-1072: `handleGeofenceStateChange(old:new:)` — on `Exited` only posts a `UNUserNotification`; no UI swap.
  - lines 1074-1094: `observeSessionState()` — KMP-NativeCoroutines async sequence, assigns `self.sessionState = newState`.
- `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` — generic "Workout Complete!" celebration UI; no differentiation for `abandoned=true`.

## Log Evidence

```
[Geofence] _geofenceState set to InZone, starting observer
[Geofence] completeSet about to call maybeRegisterGeofenceForFirstSet — exIdx=0 setIdx=1 nextCursor=(0, 2)
[Geofence] gymLocation already set — SKIPPING register
Uncaught Kotlin exception: androidx.sqlite.SQLiteException: Error code: 787
  message: FOREIGN KEY constraint failed
  at WorkoutSessionDao_Impl.insertSet$2.invoke
  at EntityInsertAdapter#insertAndReturnId
  StandaloneCoroutine{Cancelling}@eb41668, Dispatchers.Main.immediate
```

The `completeSet → maybeRegisterGeofenceForFirstSet → "gymLocation already set — SKIPPING register"` trio shows the user tapped "complete set" while still on the Active screen. The FK 787 fires moments later when that coroutine reaches `workoutRepository.saveCompletedSet` and the parent row has just been deleted by the abort coroutine.

## Evidence

- timestamp: 2026-05-17 (initial report)
  - source: user iOS device logs
  - finding: `completeSet` runs AFTER grace-period expiry without any visible abort UI; FK 787 on insertSet → session FK parent missing.

- timestamp: 2026-05-17 (investigation)
  - source: WorkoutSessionViewModel.kt:984-1069
  - finding: `handleGeofenceExitGraceExpired` performs 4 suspend-call blocks before publishing the terminal `Finished` state: (1) `workoutRepository.saveAbandonedWorkout(completed)` (multiple DAO inserts), (2) `gamificationEngine.processAbandonedWorkout(savedWorkoutId)`, (3) `gamificationEngine.onGeofenceExitPenalty(...)`, (4) `workoutRepository.clearActiveSession()`. Only AFTER all of these does it set `_sessionState.value = Finished`. The chip transitions to `Exited` BEFORE this block runs (line 968), so the user sees "Zone verlassen" while the screen is still on `Active` and accepts taps.

- timestamp: 2026-05-17 (investigation)
  - source: WorkoutRepository.kt:134-158 + ActiveSessionSetEntity.kt:9-18 + WorkoutSessionDao.kt:35-36
  - finding: `saveCompletedSet` hardcodes `sessionId = 1`. `clearActiveSession` deletes `active_sessions WHERE id = 1`. FK is `onDelete = CASCADE`. Any `insertSet` after `clearActiveSession` triggers FK 787 since the parent row is gone (cascade does not back-fill the parent for new child inserts).

- timestamp: 2026-05-17 (investigation)
  - source: WorkoutSessionView.swift:187-215, 1047-1072
  - finding: SwiftUI host has NO defensive ignore of taps once `geofenceState == Exited`. The body swap from Active → Finished is entirely driven by `sessionState`, which arrives late. There is also no abort-specific view — even after Finished is published, the user sees the same celebratory "Workout Complete!" UI as for a normal completion, contrary to UI-SPEC line 180 ("Workout beendet. Du hast die Trainingszone verlassen. {n} Sätze gespeichert, {p} XP abgezogen.").

- timestamp: 2026-05-17 (investigation)
  - source: WorkoutSessionViewModel.kt:118-120, 168-170 (`@NativeCoroutinesState`)
  - finding: `sessionState` and `geofenceState` are correctly annotated; the Swift bridge is fine. The problem is timing/ordering inside the VM, not the bridge.

## Eliminated

- Hypothesis (b): the abort logic does run.
- Hypothesis (d): the `Cancelling` state in the log is cancellation of the crashed `completeSet` coroutine, not a finally-block-only path.
- KMP-NativeCoroutines bridge bug: properly annotated; behaves correctly for other state flows.

## Resolution

### Root Cause (confirmed)

The grace-period expiry handler `WorkoutSessionViewModel.handleGeofenceExitGraceExpired()` performs ~3 seconds of suspend work (DB writes + gamification processing) BEFORE it publishes the terminal `_sessionState.value = Finished`. During that suspend window:

1. The user sees the chip flip to "Zone verlassen" (`GeofenceUiState.Exited`, set BEFORE the suspend work begins on VM line 968) while the underlying screen is still the Active workout view.
2. `completeSet` and every other VM mutator remain reachable from the UI; they only guard on `_sessionState.value as? Active` and do NOT consult `_geofenceState`.
3. Mid-suspend, `clearActiveSession()` (line 1057) deletes `active_sessions WHERE id = 1`, the singleton parent row.
4. The user taps "complete set" → `completeSet` launches a fresh coroutine, sees `_sessionState.value` still `Active`, and calls `workoutRepository.saveCompletedSet(...)` → `insertSet(sessionId = 1)`. The parent is gone → FK 787 (cascade deleted children but blocks new orphan inserts).

Two distinct defects emerge from this one root cause:

- **D1 (crash)**: `completeSet` (and every other mutator) has no guard against `_geofenceState` being `Exited`/post-expiry, and `handleGeofenceExitGraceExpired` orders its work so that DB destruction precedes UI-state transition.
- **D2 (UX)**: even once `Finished` does arrive, `WorkoutFinishedView` shows a generic "Workout Complete!" celebration UI — there is no abort-specific recap matching UI-SPEC §180 ("Workout beendet — Trainingszone verlassen, {n} Sätze gespeichert, {p} XP abgezogen"). The user reasonably concludes "nothing happened" because the only visible signal during the suspend window is the chip text, and the local notification fires only if app is backgrounded or if foreground delegate explicitly presents it.

### Fix direction (NOT applied — user must choose scope)

There are two layers of fix; they are independent and can ship separately.

**Layer A — Stop the crash (minimal, ~15 LOC change, no new UI):**

1. In `WorkoutSessionViewModel.handleGeofenceExitGraceExpired()`, **publish the terminal `Finished` state BEFORE the destructive Room cleanup**. Order:
   - (a) Build `completedExercises`, compute `plannedSetCount`/`loggedSetCount`.
   - (b) `_sessionState.value = Finished(...)` immediately (with `savedWorkoutId = -1L` placeholder OR a sentinel `WorkoutSessionState.Aborted` if you want a dedicated terminal state).
   - (c) Cancel jobs and set `_hasActiveSession.value = false` (also blocks repeated entry).
   - (d) Then run the slow path: `saveAbandonedWorkout`, gamification, penalty.
   - (e) Then `clearActiveSession()` LAST.
   - (f) Optionally re-publish `Finished` with the now-known `savedWorkoutId` so `WorkoutFinishedView` can deep-link to detail.
   Rationale: the SwiftUI body switches the moment `Finished` arrives. Any pre-existing tap-in-flight that already left `completeSet`'s guard at `as? Active` would still race, so:

2. In `completeSet` (and `addExtraSet`, `editLastSet`, every other mutator that calls into `workoutRepository.saveCompletedSet`/`insertSet`), add a defensive guard at the top: `if (_geofenceState.value is GeofenceUiState.Exited) return@launch`. This closes the residual race window between "user already tapped" and "Finished published".

3. (Belt-and-braces) wrap the `workoutSessionDao.insertSet(...)` call in `WorkoutRepository.saveCompletedSet` in a try/catch for `SQLiteException` and silently swallow it during a known-terminal state. NOT recommended as the primary fix — guards in (1) and (2) are cleaner — but acceptable as a fallback.

**Layer B — UX correctness per UI-SPEC §180 (~80-150 LOC, new SwiftUI view, optional shared state):**

1. Introduce a dedicated terminal state for the auto-abort path. Either:
   - reuse `WorkoutSessionState.Finished` with a new `abandoned: Boolean` (and optional `loggedSets: Int`, `penaltyXp: Int`) field, OR
   - add a new `WorkoutSessionState.Aborted` sealed-class variant.
2. Create `WorkoutAbortedView.swift` (sibling to `WorkoutFinishedView.swift`) that renders the abort messaging from UI-SPEC line 180 (title "Workout beendet", body "Du hast die Trainingszone verlassen. {n} Sätze gespeichert, {p} XP abgezogen").
3. In `WorkoutSessionView.swift` body, branch on the new field/variant: `if abandoned` → `WorkoutAbortedView` else `WorkoutFinishedView`.
4. Mirror on Android (Compose) so parity is maintained on this branch.

### Recommendation

- If goal is "stop the crash this week": **Layer A only** is sufficient and is the change required regardless of UX scope. It is ~15-20 LOC across two files, no new UI types, no platform-specific work.
- Layer B is a separate UX-correctness ticket and should be planned via `/gsd-plan-phase --gaps` since it touches shared state, SwiftUI, Compose, and the spec.

**Stopping here per orchestrator guidance** — extra_context instructed: "If the root cause requires non-trivial work (e.g. new abort-modal SwiftUI view), STOP at root-cause-found and let the user decide on remediation scope." Layer B qualifies as non-trivial. Layer A is small enough that the user may still want it applied now; awaiting decision.

### Layer A applied — 2026-05-17, commit 87ff836

Three changes in `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt`:

1. **Reorder** in `handleGeofenceExitGraceExpired()` (line ~1057): publish `_sessionState.value = Finished(...)` BEFORE calling `workoutRepository.clearActiveSession()`, `_hasActiveSession.value = false`, and resetting `_geofenceState`. Any newly-launched `completeSet` coroutine now sees non-Active state on its entry check and returns early.
2. **Race guard in `completeSet()`** (line ~432): re-check `_sessionState.value is Active` right before the suspending `workoutRepository.saveCompletedSet(...)` call. Closes the in-flight-coroutine window where the entry check passed but the state transitioned during the lambda's pre-DB work.
3. **Defense-in-depth try/catch** wrapping the entire `completeSet` body: catches `androidx.sqlite.SQLiteException` (covers FK 787) and logs+swallows. Prevents app crash even if a residual race slips through both guards.

iOS Debug build: BUILD SUCCEEDED (99.7s).

D2 (UX — generic "Workout Complete!" instead of abort recap per UI-SPEC §180) NOT addressed — tracked as Layer B follow-up quick-task.

### Layer B applied — 2026-05-17, commits 8bda080 / 4fa6af3 / f3dac66

Quick-task `260517-ra5` closed defect D2. Three changes across shared + iOS + Android:

1. **Shared** (8bda080): `WorkoutSessionState.Finished` extended with three defaulted fields — `abandoned: Boolean = false`, `loggedSets: Int = 0`, `penaltyXp: Int = 0`. Only `handleGeofenceExitGraceExpired()` sets `abandoned = true`; penalty is derived locally via `XpFormula.geofenceExitPenalty(plannedSetCount, loggedSetCount)` since `GamificationEngine.onGeofenceExitPenalty` returns `Unit`. The two non-abandon construction sites (`enterReview`, `saveCurrentAsCompletedWithoutPenalty`) are unchanged and rely on the defaults.
2. **iOS** (4fa6af3): new `WorkoutAbortedView.swift` (UI-SPEC §180 verbatim, exclamation-triangle + orange + "Zur Übersicht" CTA). `WorkoutSessionView` body branches `finished.abandoned`. Xcode pbxproj registers the new file (UUIDs `A10066` / `B10066`).
3. **Android** (f3dac66): `FinishedContent` branches early on `finished.abandoned -> AbortedContent(...)`. New `AbortedContent` composable with `Icons.Default.Warning` (color `0xFFFB8C00`), matching German copy and CTA. Reuses existing `SummaryRow` + `formatDuration` helpers.

Build verification:
- `./gradlew :androidApp:assembleDebug` → **BUILD SUCCESSFUL** in 18s.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' build` → **BUILD SUCCEEDED** in 136.9s.

Manual UAT (outstanding, not gated by this quick-task):
- Trigger geofence-exit grace expiry on iOS simulator → confirm abort recap renders with German copy + correct logged-sets/penalty values, "Zur Übersicht" returns to overview.
- Normal-completion regression spot-check on both platforms (Reviewing → Save) — celebratory `WorkoutFinishedView` / `FinishedContent` must still render unchanged.
- Optional no-budget Early-Exit path (also routes through `handleGeofenceExitGraceExpired`) — per planner discretion, single recap variant uses §180 wording for both reasons.

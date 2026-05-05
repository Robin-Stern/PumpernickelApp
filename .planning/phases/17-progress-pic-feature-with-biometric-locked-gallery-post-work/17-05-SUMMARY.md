---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 05
subsystem: capture-flow-ui
tags: [kmp, compose, viewmodel, koin, fragment-activity, biometric, activity-result, mainactivity, post-workout]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 02
    provides: "ProgressPictureRepository.observePhotoCount + savePicture, expect class PhotoCaptureLauncher"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 03
    provides: "BiometricGateActivityHolder + PhotoCaptureLauncherActivityHolder + PhotoCaptureLauncherHost; FileProvider already wired"
provides:
  - "WorkoutSessionState.Finished now carries workoutId: Long + photoCount: Int = 0 — the post-workout photo prompt knows which workout to attach to"
  - "ProgressPicturePromptViewModel — shared VM in commonMain exposing PromptUiState (workoutId, photoCount, busy, error, dismissed) + showAddAnother computed property; three intents (onTakePhotoClick / onPickFromLibraryClick / onSkipClick)"
  - "ProgressPicturePromptCard — Material 3 Compose card with three actions row (Foto aufnehmen / Aus Galerie / Überspringen), busy + count footer, error inline render"
  - "WorkoutSessionScreen.FinishedContent now mounts the prompt card between the summary Surface and the Done button — non-blocking"
  - "MainActivity is now FragmentActivity, attaches BiometricGateActivityHolder + PhotoCaptureLauncherHost in onCreate, detaches in onDestroy"
affects:
  - "17-06-PLAN — gallery VM is unblocked; observePhotoCount(workoutId) is the live source of truth for the prompt count, and the gallery's tile-tap → BiometricGate path now has a FragmentActivity host"
  - "17-07-PLAN — must register progressGalleryModule including viewModel { (workoutId: Long) -> ProgressPicturePromptViewModel(workoutId, get(), get()) }; the card already calls koinViewModel { parametersOf(workoutId) }"
  - "17-08-PLAN (iOS handoff) — Finished.workoutId field is now part of the Kotlin → Swift bridge; iOS handoff doc must describe the SwiftUI prompt sheet using the same VM contract"

# Tech tracking
tech-stack:
  added: []  # no new libraries — pure VM + Compose + Activity wiring against contracts that 17-02/17-03 shipped
  patterns:
    - "Workout-tab VM-per-instance via Koin parametersOf(workoutId) — first use of the parametersOf pattern in this codebase's Compose layer (precedent for any future per-id VMs the gallery viewer in 17-07 will follow)"
    - "Compose Material 3 prompt-card body: title + supporting text + actions Row + footer Row with busy/count + skip — mirrors NutritionGoalsBanner's translucent surfaceVariant @ 0.6 alpha, scaled up to a 3-action layout"
    - "ActivityHolder wiring before setContent in MainActivity — registerForActivityResult lifecycle contract demands CREATED-not-STARTED, so PhotoCaptureLauncherHost must be constructed before Compose hands control to setContent"
    - "FragmentActivity superclass swap with no API change to the Compose body — FragmentActivity is a strict superset of ComponentActivity, so setContent + enableEdgeToEdge keep working unchanged"
    - "Plan-local Clock import-route fix: kotlin.time.Clock.System over kotlinx.datetime.Clock — matches the ambient codebase convention (TemplateRepository, WorkoutRepository, GamificationEngine)"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt"
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt (Finished data class + construction site)"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt (import + FinishedContent body)"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt (FragmentActivity superclass + holder attach/detach)"

key-decisions:
  - "kotlin.time.Clock.System (NOT kotlinx.datetime.Clock) for capturedAtMillis — the ambient codebase convention is kotlin.time.Clock used inline-qualified across TemplateRepository, WorkoutRepository, GamificationEngine. Plan PATTERNS suggested kotlinx.datetime.Clock but the import wouldn't resolve in commonMain; switched to match the existing convention. Documented inline as a Rule 3 deviation."
  - "Card slots between the existing summary Surface and the existing Done button — exactly per plan; no other layout changes. Spacer.height(16.dp) above + the card itself + the existing Spacer.weight(1f) between card and Done preserve the original 'flex-grow at top, flex-grow at bottom' rhythm."
  - "Card pad = horizontal 32.dp matching the surrounding summary Surface and Done button — visual gutter is consistent with the rest of FinishedContent."
  - "Done button left untouched. No `enabled = ` parameter added (defaults to true) — the plan's hard constraint (D-17-01 non-blocking) is honored. The Button construct on lines 1119–1128 starts directly with `onClick = onDone,` and has no conditional guards."
  - "MainActivity holders attached in onCreate BEFORE setContent — the lifecycle order is super.onCreate → wire holders → enableEdgeToEdge → setContent. PhotoCaptureLauncherHost.<init> internally calls registerForActivityResult which fails after STARTED; setContent transitions the activity through STARTED, so wiring must precede it."
  - "PhotoCaptureLauncherHost passed `applicationContext` for its `context` parameter (per plan note) — using `this` would leak the Activity into the Host, which outlives the Activity if the Host is ever held longer than expected (it isn't, but the conservative choice keeps the contract robust)."
  - "FileProvider was already wired in AndroidManifest.xml by 17-03 — verified via `grep -q 'androidx.core.content.FileProvider'` returning 0; no manifest work in this plan."

requirements-completed:
  - D-17-01
  - D-17-02
  - D-17-03
  - D-17-04
  - D-17-13
  - D-17-14
  - D-17-15

# Metrics
duration: 8min 33sec
completed: 2026-05-01
---

# Phase 17 Plan 05: Capture flow VM + prompt card + WorkoutSessionScreen integration + MainActivity wiring Summary

**Five atomic commits land the post-workout photo capture flow on Android: WorkoutSessionState.Finished gains workoutId; ProgressPicturePromptViewModel ships in commonMain with three intents and a single combine()-driven UI state; ProgressPicturePromptCard renders three Material 3 buttons + busy/count/error footer over the just-shipped 17-02/17-03 contracts; WorkoutSessionScreen mounts the card above its Done button without changing Done's behavior; MainActivity swaps to FragmentActivity and attaches BiometricGateActivityHolder + PhotoCaptureLauncherHost in onCreate (detaches in onDestroy), unblocking BiometricPrompt and per-tile auth for 17-06/17-07. iOS shared compile, Android shared compile, and androidApp:assembleDebug all exit 0.**

## Performance

- **Duration:** 8 min 33 sec
- **Started:** 2026-05-01T15:41:51Z
- **Completed:** 2026-05-01T15:50:24Z
- **Tasks:** 5 (all atomic commits)
- **Files created:** 2 (`ProgressPicturePromptViewModel.kt`, `ProgressPicturePromptCard.kt`)
- **Files modified:** 3 (`WorkoutSessionViewModel.kt`, `WorkoutSessionScreen.kt`, `MainActivity.kt`)

## Accomplishments

- **WorkoutSessionState.Finished extended with workoutId + photoCount** — the data class gains `val workoutId: Long` (required) and `val photoCount: Int = 0` (default). The single production construction site at `WorkoutSessionViewModel.saveReviewedWorkout()` line 593–600 now threads the `Long` returned by `workoutRepository.saveCompletedWorkout(...)` (already in scope from line 574) into the new arg. No test sources reference Finished, so no test patches were required. Both shared targets compile.
- **ProgressPicturePromptViewModel ships in `shared/src/commonMain/.../presentation/progresspic/`** — a brand-new package. The VM exposes a single `@NativeCoroutinesState val uiState: StateFlow<PromptUiState>` built via `combine(observePhotoCount, _busy, _error, _dismissed) { ... }.stateIn(viewModelScope, WhileSubscribed(5000), initial)`. Three intents — `onTakePhotoClick`, `onPickFromLibraryClick`, `onSkipClick` — guard against double-fire via `if (_busy.value) return`, route bytes through `repository.savePicture` with a fresh `Uuid.random().toString()` id and `Clock.System.now().toEpochMilliseconds()` epoch, and surface failures only inline via `state.error` (no toast, no error dialog — D-17-17). Skip is a pure dismiss (`_dismissed.value = true`); the host's Done button stays enabled regardless (D-17-01).
- **ProgressPicturePromptCard ships in `androidApp/.../ui/components/`** — a Material 3 Card with translucent `surfaceVariant.copy(alpha = 0.6f)` container (matching `NutritionGoalsBanner`), title + supporting text that switch via `state.showAddAnother`, a Row of three actions ("Foto aufnehmen" filled Button + AddAPhoto icon, "Aus Galerie" OutlinedButton + PhotoLibrary icon, "Überspringen" TextButton), and a footer Row that shows either `CircularProgressIndicator + "Speichere…"` (when busy), or `"N Foto(s) angehängt"` (when photoCount > 0), or nothing — alongside a TextButton that switches between "Überspringen" / "Fertig" based on photoCount. The Skip button always calls `viewModel.onSkipClick()` regardless of label. Errors render inline in `MaterialTheme.colorScheme.error` per D-17-17.
- **WorkoutSessionScreen.FinishedContent mounts the card** between the existing summary `Surface` and the existing `Done` `Button`, with a 16.dp spacer above and the original `Spacer(Modifier.weight(1f))` between card and Done preserved. The Done button's body is unchanged (no `enabled =` guard added) — D-17-01 non-blocking constraint fully honored. Import added next to existing `RepsPicker` / `WeightPicker` imports.
- **MainActivity swapped from `ComponentActivity` to `androidx.fragment.app.FragmentActivity`** — BiometricPrompt requires a FragmentActivity (D-17-15); without this swap, the gallery viewer's biometric gate would crash at runtime. In `onCreate`, before `setContent`: `BiometricGateActivityHolder.attach(this)`, then `photoCaptureHost = PhotoCaptureLauncherHost(activity = this, context = applicationContext)`, then `PhotoCaptureLauncherActivityHolder.attach(photoCaptureHost)`. Wiring runs before `setContent` because `PhotoCaptureLauncherHost.<init>` calls `registerForActivityResult` which requires the activity to be in CREATED-not-STARTED; setContent transitions through STARTED. `onDestroy` detaches both holders FIRST, then `super.onDestroy()`.
- **D-17-04 negative wiring confirmed via grep** — `WorkoutHistoryListScreen.kt` and `WorkoutHistoryDetailScreen.kt` contain zero references to `ProgressPicturePrompt`, `ProgressPictureCapture`, `ProgressGalleryRoute`, or `ProgressPicturePromptViewModel`. Retro-add stays explicitly deferred.
- **Builds clean:** `:shared:compileKotlinIosSimulatorArm64`, `:shared:compileDebugKotlinAndroid`, and `:androidApp:assembleDebug` all exit 0 with only existing warnings (unrelated to this plan's edits).

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: Extend WorkoutSessionState.Finished with workoutId + photoCount** — `0921064` (feat)
2. **Task 2: Create ProgressPicturePromptViewModel in commonMain** — `a6541e9` (feat)
3. **Task 3: Create ProgressPicturePromptCard Compose component** — `35b8588` (feat)
4. **Task 4: Mount ProgressPicturePromptCard on the Finished branch of WorkoutSessionScreen** — `e102f63` (feat)
5. **Task 5: Wire MainActivity — FragmentActivity superclass swap + holder attach/detach** — `a8536ea` (feat)

## Files Created/Modified

### Created

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt` — 143 lines including KDoc. The package directory was new and was created as part of this task. KDoc documents D-17-01 (non-blocking), D-17-04 (no retro-add), D-17-17 (silent on failure). One private suspend helper (`saveBytes`) carries the `@OptIn(ExperimentalUuidApi::class)` annotation; the rest of the class is opt-in-free because only the helper touches `Uuid.random()`.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt` — 162 lines. Pure Compose Material 3, no Activity Result API imports (those live in `PhotoCaptureLauncherHost` per 17-03). The composable is a thin shell over the shared VM resolved via `koinViewModel { parametersOf(workoutId) }`.

### Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — two additive edits:
  1. `data class Finished(...)` declaration at lines 50–57: added `val workoutId: Long` and `val photoCount: Int = 0`.
  2. Construction site at lines 593–600: `_sessionState.value = WorkoutSessionState.Finished(...)` now passes `workoutId = workoutId, photoCount = 0`. The `val workoutId` it reads was already in scope from line 574 (`val workoutId = workoutRepository.saveCompletedWorkout(...)`) — no other code changes required.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — two additive edits:
  1. Import block at lines 61–63: added `import com.pumpernickel.android.ui.components.ProgressPicturePromptCard` next to the existing `RepsPicker` / `WeightPicker` imports.
  2. `FinishedContent` body: between the summary `Surface(...)` (lines 1090–1103) and the original `Spacer(Modifier.weight(1f))` (now line 1117), inserted `Spacer(Modifier.height(16.dp))` + `ProgressPicturePromptCard(workoutId = finished.workoutId, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))`. Done button at lines 1119–1129 is byte-for-byte unchanged.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt` — substantial re-wire:
  - Superclass: `androidx.activity.ComponentActivity` → `androidx.fragment.app.FragmentActivity`.
  - Imports: dropped `androidx.activity.ComponentActivity`, added `androidx.fragment.app.FragmentActivity`, `com.pumpernickel.domain.progresspic.BiometricGateActivityHolder`, `PhotoCaptureLauncherActivityHolder`, `PhotoCaptureLauncherHost`.
  - New private field `private lateinit var photoCaptureHost: PhotoCaptureLauncherHost`.
  - `onCreate` now: `super.onCreate(savedInstanceState)` → `BiometricGateActivityHolder.attach(this)` → `photoCaptureHost = PhotoCaptureLauncherHost(activity = this, context = applicationContext)` → `PhotoCaptureLauncherActivityHolder.attach(photoCaptureHost)` → `enableEdgeToEdge()` → `setContent { ... }` (Compose body unchanged).
  - New `override fun onDestroy()`: detach both holders, then `super.onDestroy()`.

## Decisions Made

- **`kotlin.time.Clock.System` (NOT `kotlinx.datetime.Clock`).** The plan's PATTERNS section recommended `kotlinx.datetime.Clock`, but the `kotlinx.datetime.Clock` symbol is not exported into commonMain on this codebase's classpath (the `kotlinx-datetime` library exports `Instant`, `LocalDate`, `LocalDateTime`, `TimeZone`, etc., but the `Clock` interface that ships with stdlib's `kotlin.time` package is the one actually used across the repo — see `TemplateRepository.kt:85`, `WorkoutRepository.kt:143`, `GamificationEngine.kt:129`). The first compile attempt produced `Unresolved reference 'System'`; switching the import to `kotlin.time.Clock` resolved it cleanly. Logged as a Rule 3 (blocking) deviation.
- **`@OptIn(ExperimentalUuidApi::class)` placed at the function level (on `private suspend fun saveBytes`)** rather than file-level. Only `saveBytes` calls `Uuid.random()`; keeping the opt-in narrow is more accurate than file-level. The plan called this "acceptable" with file-level as alternative.
- **Card mounted with `padding(horizontal = 32.dp)`** to match the summary `Surface` and Done `Button` gutters around it. Vertical rhythm: 24.dp spacer above the summary Surface (existing) → summary Surface → 16.dp spacer (new) → prompt card (new) → flex-grow Spacer (existing) → Done button (existing) → 32.dp bottom spacer (existing).
- **`PhotoCaptureLauncherHost` constructed with `applicationContext`** (not `this`) for its `context` parameter — the Host stores the context as a field for FileProvider URI generation, and `applicationContext` is the right scope for that long-lived field. The `activity` parameter receives `this` (the FragmentActivity) for `registerForActivityResult` lifecycle binding. Two roles, two correct values.
- **Done button left without an `enabled =` parameter** — defaults to `true` and stays enabled even while the prompt is busy or rendering an error. D-17-01 hard constraint.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `kotlinx.datetime.Clock` import did not resolve; switched to `kotlin.time.Clock`**

- **Found during:** Task 2 verification (first run of `./gradlew :shared:compileKotlinIosSimulatorArm64` after writing `ProgressPicturePromptViewModel.kt`).
- **Issue:** Compile error `e: ... ProgressPicturePromptViewModel.kt:115:25 Unresolved reference 'System'`. Cause: my `saveBytes` body called `Clock.System.now().toEpochMilliseconds()` with the import `kotlinx.datetime.Clock`, but `Clock.System` isn't exposed at that import path on this codebase's `kotlinx-datetime` classpath. The repo's existing convention is `kotlin.time.Clock.System.now().toEpochMilliseconds()` — verified across `TemplateRepository.kt:85`, `WorkoutRepository.kt:143/161/168`, `GamificationEngine.kt:129/421`, and `LogConsumptionUseCase.kt:19`.
- **Fix:** Changed the import line from `import kotlinx.datetime.Clock` to `import kotlin.time.Clock`. Body and call site unchanged. Re-ran the iOS compile — exits 0 with only pre-existing warnings.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt`
- **Committed in:** `a6541e9` (Task 2 — folded into the original commit, edit applied before commit).
- **Why this is Rule 3, not Rule 4:** Single-line import-route fix to match the ambient codebase convention. No architectural change. The plan's PATTERNS note about `kotlinx.datetime.Clock` was a discoverable mismatch; the right Clock symbol was already chosen across the rest of the repo.

**2. [Rule 3 - Environment] Restored Room schema JSONs in worktree's gitignored `shared/schemas/`**

- **Found during:** Pre-Task-1 environment check (same root cause as 17-01 / 17-02 / 17-03 deviations).
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty (in fact, did not exist). Room's KSP needs schemas `2.json` through `9.json` on disk to validate the existing AutoMigrations during `:androidApp:assembleDebug`. Without them, the Android build path fails at the KSP step before reaching Kotlin compile.
- **Fix:** Created the directory and copied `2.json` through `9.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/`. `shared/schemas/` is gitignored (verified with `git check-ignore`), so this fix is purely a local KSP working-set repair and never enters the commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..9}.json` (gitignored — not committed)
- **Verification:** `:androidApp:assembleDebug -q` exits 0 from Task 3 onward.
- **Committed in:** N/A (gitignored — does not enter the commit graph).

---

**Total deviations:** 2 auto-fixed (one Rule 3 import-route fix, one Rule 3 environment restore). Neither alters the source-code logic of the plan; the Clock fix matches the existing codebase convention, and the schema restore is invisible to git.

**Impact on plan:** Zero scope creep. Plan source-code spec executed exactly as written for the VM body, the card body, the screen edit, and the MainActivity wiring. Only the `Clock` import path was corrected.

## Issues Encountered

- **Pre-existing failure: `:shared:compileDebugUnitTestKotlinAndroid` cannot resolve `kotlin.test.Test/assertTrue/assertEquals` in `TdeeCalculatorTest.kt`.** This was found during Task 1 verification when the plan asks for `./gradlew :shared:allTests -q` to exit 0. The failure reproduces on the main repo (HEAD = `e988ce4`) and originates in Phase 16 work — it's an Android-target test source-set wiring issue unrelated to this plan's changes (`WorkoutSessionViewModel.kt`, `ProgressPicturePromptViewModel.kt`, etc., do not reference TDEE / nutrition test code). Per scope-boundary rule (only auto-fix issues directly caused by this task's changes), the failure is logged to `.planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/deferred-items.md` and not fixed in this plan. iOS test compile (`compileTestKotlinIosSimulatorArm64`) exits 0 cleanly, confirming the failure is Android-target-specific. The plan's Task 1 acceptance criterion "tests still pass" is satisfied for the relevant scope (no test references `WorkoutSessionState.Finished`); the broader `allTests` failure is pre-existing infrastructure drift.
- No other issues. The four production verification gates (`:shared:compileKotlinIosSimulatorArm64`, `:shared:compileDebugKotlinAndroid`, `:androidApp:assembleDebug`, and the negative wiring grep on History screens) all pass cleanly.

## TDD Gate Compliance

Plan type: `execute` (not TDD). No RED/GREEN/REFACTOR gate required. All five commits are `feat(...)` commits per the plan's task descriptions.

## User Setup Required

- **Manifest already wired by 17-03** — no Info.plist or AndroidManifest.xml work in this plan. The user does not need to grant any new permissions. On first run after the next debug APK install:
  - The OS will prompt for `CAMERA` permission the first time the user taps "Foto aufnehmen" (already declared in the manifest from the barcode scanner phase).
  - The OS will show the system Photo Picker (no permission prompt on Android 13+) the first time the user taps "Aus Galerie".
  - No biometric prompt is triggered by this plan — that surface lives in 17-06 (gallery tile tap) and 17-07 (viewer entry).

## File Provider Status

**FileProvider was already wired in `androidApp/src/androidMain/AndroidManifest.xml` by plan 17-03.** Verified during Task 4 with `grep -q 'androidx.core.content.FileProvider' androidApp/src/androidMain/AndroidManifest.xml` returning 0. The corresponding `androidApp/src/androidMain/res/xml/file_paths.xml` resource is also present from 17-03. **No FileProvider work was needed in 17-05.** Future Android-side plans in this phase do not need to revisit this — the camera-capture URI grant pipeline is already complete.

## Threat Flags

None. The plan's `<threat_model>` register (T-17-05-01 Tampering, T-17-05-02 Spoofing, T-17-05-03 DoS) covers every new surface introduced in this plan. T-17-05-03 is mitigated at the API level — both `onTakePhotoClick` and `onPickFromLibraryClick` guard against double-fire via `if (_busy.value) return`, and the card buttons bind `enabled = !state.busy` so the UI surfaces the guard. T-17-05-01 and T-17-05-02 stay accepted per plan disposition. No new file-system or network surfaces; no schema changes; no new auth paths beyond the plan's `<threat_model>`.

## Known Stubs

None. Stub-detection scan over the four edited/created files (`ProgressPicturePromptViewModel.kt`, `ProgressPicturePromptCard.kt`, `WorkoutSessionScreen.kt`, `MainActivity.kt`) returned zero hits for `TODO`, `FIXME`, `placeholder`, `coming soon`, or `not available`. The card's data flow is fully wired end-to-end (VM → repository → DAO + PhotoVault).

## Next Plan Readiness

- **Plan 17-06 (gallery VM)** has the locked surface area: `WorkoutSessionState.Finished.workoutId` is plumbed for the post-workout flow; the gallery VM observes `repo.observeGalleryTiles()` independently, but its tile-tap path will use the same `BiometricGateActivityHolder.current` that this plan now keeps populated whenever MainActivity is alive. No cross-plan coupling beyond that.
- **Plan 17-07 (Koin DI)** has a clean, minimal wiring obligation: register `viewModel { (workoutId: Long) -> ProgressPicturePromptViewModel(workoutId, get(), get()) }` inside `progressGalleryModule`. The card already calls `koinViewModel { parametersOf(workoutId) }` against this exact contract. The two `get()` calls resolve `ProgressPictureRepository` (already registered by 17-02's repository binding plan) and `PhotoCaptureLauncher` (registered by 17-03's `PlatformModule.android.kt` and 17-04's `PlatformModule.ios.kt`). No surprises.
- **Plan 17-08 (iOS handoff)** must include the new `WorkoutSessionState.Finished.workoutId: Long` field in the SwiftUI bridge documentation and describe the iOS prompt sheet using the same `ProgressPicturePromptViewModel` + `PromptUiState` shape this plan ships in commonMain. The SwiftUI side will use `KoinPlatform.getKoin().get<ProgressPicturePromptViewModel> { parametersOf(workoutId) }` via a small `ProgressPicturePromptKoinHelper(workoutId: Long)` to match the per-id resolution pattern.
- No blockers. Build gates green across the board.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt ]` -> FOUND
- `[ -f androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/ProgressPicturePromptCard.kt ]` -> FOUND
- `git log --oneline | grep 0921064` -> FOUND: `feat(17-05): extend WorkoutSessionState.Finished with workoutId and photoCount`
- `git log --oneline | grep a6541e9` -> FOUND: `feat(17-05): add ProgressPicturePromptViewModel in commonMain`
- `git log --oneline | grep 35b8588` -> FOUND: `feat(17-05): add ProgressPicturePromptCard Compose component`
- `git log --oneline | grep e102f63` -> FOUND: `feat(17-05): mount ProgressPicturePromptCard on Finished branch`
- `git log --oneline | grep a8536ea` -> FOUND: `feat(17-05): wire MainActivity for biometric + photo capture`
- All Task-1/2/3/4/5 acceptance grep checks PASS (verified inline; only Task 4's whitespace-strict regex was relaxed because the plan-supplied regex did not match the multi-line `Button(\n    onClick = onDone,...)` construction — the spirit of the check (Done still calls onDone) is satisfied via an awk multi-line check)
- `:shared:compileKotlinIosSimulatorArm64 -q` -> exit 0
- `:shared:compileDebugKotlinAndroid -q` -> exit 0
- `:androidApp:assembleDebug -q` -> exit 0
- D-17-04 negative wiring grep over `WorkoutHistoryListScreen.kt` and `WorkoutHistoryDetailScreen.kt` returns zero hits for any progress-pic symbol

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*

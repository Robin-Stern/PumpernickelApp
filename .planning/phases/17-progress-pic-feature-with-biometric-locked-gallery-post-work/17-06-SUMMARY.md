---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 06
subsystem: presentation-and-navigation
tags: [kmp, viewmodel, compose, biometric, lazyverticalgrid, horizontalpager, navigation, koin, t-biometric-bypass]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 02
    provides: "ProgressPictureRepository (observeGalleryTiles + observePicturesForWorkout + deletePicture), BiometricGate expect, ProgressGalleryTile + ProgressPicture domain models, UnlockResult sealed class"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 03
    provides: "Android actuals: PhotoVault, BiometricGate, PhotoCaptureLauncher — needed at runtime by gallery + viewer screens (PhotoVault.read for cover bytes; BiometricGate.requestUnlock for the gate)"
provides:
  - "ProgressGalleryViewModel — shared VM enriching repo placeholder tiles with PR count + isGoalDay flag (Option A — pinned at 17-02); emits NavEvent.OpenViewer on tile tap"
  - "ProgressViewerViewModel — shared VM with the SINGLE explicit auth gate state `_unlockedWorkoutId: MutableStateFlow<Long?>` (T-BIOMETRIC-BYPASS mitigation); requestUnlock() / relock() are the only writers"
  - "ProgressGalleryScreen — Android Compose: LazyVerticalGrid(GridCells.Fixed(2)) of square Cards, blurred cover photo (24dp), caption strip with date/workoutName/volume/PR/goal-day (D-17-12 empty stats dropped)"
  - "ProgressViewerScreen — Android Compose: full-screen black-backed HorizontalPager; first-composition LaunchedEffect calls requestUnlock; DisposableEffect onDispose calls relock; LockedPlaceholder when unlockedWorkoutId == null"
  - "ProgressGalleryRoute (data object) + ProgressViewerRoute(workoutId: Long) data class in Routes.kt"
  - "Overview-tab NavHost composables for both routes in MainScreen.kt"
  - "ProgressGalleryEntry card on OverviewScreen — tappable, navigates to ProgressGalleryRoute (D-17-10)"
affects:
  - "17-07-PLAN — iOS UI handoff (parallel; not blocked by this plan)"
  - "17-08-PLAN — Koin DI: must bind ProgressGalleryViewModel(repository, gamificationDao, nutritionGoalDayPolicy, nutritionDao, settingsRepository) — Rule 3 deviation expanded the ctor from 3 → 5 params; ProgressViewerViewModel takes (workoutId: Long, repository, biometricGate) and uses koinViewModel { parametersOf(workoutId) } from the screen — NutritionGoalDayPolicy is registered via single { NutritionGoalDayPolicy } (the existing object reference)"

# Tech tracking
tech-stack:
  added: []  # no new libraries — uses existing compose-foundation HorizontalPager + Modifier.blur, kmp-nativecoroutines, kotlinx-datetime
  patterns:
    - "Two-VM split for the gallery feature: list VM (ProgressGalleryViewModel) + per-item VM (ProgressViewerViewModel) — mirrors the per-item AchievementGalleryViewModel + (no per-item drill-in equivalent) pattern but with explicit auth-gate state in the per-item VM"
    - "Single explicit auth gate in commonMain: `MutableStateFlow<Long?>` initialised null, mutated only by requestUnlock (Success → workoutId) and relock (any → null). Two write sites total — verifiable by grep on `_unlockedWorkoutId.value =`"
    - "`koinViewModel { parametersOf(workoutId) }` for per-item VMs taking nav-route parameters as ctor args — clean alternative to fetching+caching workoutId state inside the VM"
    - "PhotoVault.read via produceState(key1 = relativePath) — bytes load once per tile/photo; Compose remember(bytes) decodes the ImageBitmap once per byte change"
    - "Modifier.blur(radius = 24.dp) for the privacy-preserving thumbnail render — D-17-13's tone/colour-visible-but-faces-unrecognisable balance lands at 24dp on the test devices the team uses"
    - "DisposableEffect(Unit) { onDispose { vm.relock() } } pattern to re-lock on natural screen exit (popBackStack, system back, configuration change)"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt"
  modified:
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt"
    - "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt"

key-decisions:
  - "Auth state lives ONLY in ProgressViewerViewModel.unlockedWorkoutId — single write site for non-null assignment (UnlockResult.Success branch), single write site for null reset (relock()). T-BIOMETRIC-BYPASS verifiable by grep: `grep -n '_unlockedWorkoutId.value' ProgressViewerViewModel.kt` returns exactly two lines."
  - "Gallery VM is auth-free — emits NavEvent.OpenViewer unconditionally; the screen's LaunchedEffect collector navigates; the viewer VM's first-composition LaunchedEffect fires the actual biometric. This is the cleanest mapping of D-17-14 (per-tile every-tap) onto the screen lifecycle: 'every tap' = 'every fresh viewer composition'."
  - "Rule 3 deviation: ProgressGalleryViewModel ctor expanded from the plan-pinned 3 params to 5 (added nutritionDao + settingsRepository) — see Deviations. Documented prominently here so 17-07 (iOS UI) and 17-08 (DI) bind directly without consulting upstream PLAN.md."
  - "Coil 3 NOT introduced (per CONTEXT line 99 — discretion). Raw BitmapFactory.decodeByteArray is acceptable for prototype scope; produceState gates re-decoding."
  - "Pager state via `rememberPagerState(pageCount = { uiState.photos.size })` — Compose Foundation 1.6+ API; pager swipes are local UI state (no auth re-prompt — D-17-14)."
  - "ProgressGalleryEntry placement on OverviewScreen between MuscleActivityCard and NutritionGoalsBanner — matches the visual flow (rank → muscle activity → progress photos → nutrition) and keeps the entry visually distinct from rank/nutrition concerns. Card uses the same surfaceVariant alpha 0.6 + RoundedCornerShape(12.dp) treatment as NutritionGoalsBanner so the two entries feel consistent."

# Performance metric: per-tile DAO call (Option A enrichment)
performance-notes:
  - "ProgressGalleryViewModel.uiState performs N+1 DAO calls per emission: one map() iteration over rawTiles where each iteration calls gamificationDao.getPrLedgerEntriesForWorkout(workoutId). For typical prototype gallery sizes (<50 photographed workouts) this is acceptable. Future optimisation: add a single `SELECT workoutId, COUNT(*) FROM xp_ledger WHERE source='pr' GROUP BY workoutId` aggregating query to GamificationDao and join it in one Flow.combine."
  - "Gallery enrichment also reads NutritionDao.getAllEntries() once per emission, then filters in-memory by ISO date for each tile. Same scaling story — bounded by photographed workouts × per-day consumption rows. If consumption_entries grows to thousands of rows, switch to a date-range DAO query."

requirements-completed:
  - D-17-10
  - D-17-11
  - D-17-12
  - D-17-13
  - D-17-14
  - D-17-17

# Metrics
duration: 7min 31sec
completed: 2026-05-01
---

# Phase 17 Plan 06: Gallery + Viewer VMs + Android Screens + Nav wiring Summary

**Two shared VMs (ProgressGalleryViewModel + ProgressViewerViewModel) plus two Android Compose screens (LazyVerticalGrid of blurred tiles + full-screen HorizontalPager carousel) plus the Overview-tab nav wiring (Routes.kt + MainScreen.kt + OverviewScreen.kt entry card) — `:androidApp:assembleDebug` exits 0 and the gallery is reachable from the Overview tab. T-BIOMETRIC-BYPASS is mitigated by the single explicit auth gate state in ProgressViewerViewModel.unlockedWorkoutId — exactly two write sites, both internal to the VM. Per-tile every-tap auth (D-17-14) lands by mapping "tile tap" → "fresh viewer composition" → first-composition LaunchedEffect calls requestUnlock; closing the viewer fires DisposableEffect.onDispose.relock so the tile re-blurs in the grid.**

## Performance

- **Duration:** 7 min 31 sec
- **Started:** 2026-05-01T15:44:34Z
- **Completed:** 2026-05-01T15:52:05Z
- **Tasks:** 4 (all atomic commits)
- **Files created:** 4 (2 VM files in commonMain, 2 Android screen files)
- **Files modified:** 3 (Routes.kt, MainScreen.kt, OverviewScreen.kt)

## Accomplishments

- **ProgressGalleryViewModel.kt** in commonMain enriches the repository's placeholder tiles with the per-tile PR count and goal-day flag. The repo emits `prCount = 0` / `isGoalDay = false` (Option A pinned at 17-02); this VM combines that Flow with `GamificationDao.getPrLedgerEntriesForWorkout(workoutId).size` for the PR count and `NutritionGoalDayPolicy.isGoalDay(entriesForDate, goals)` for the goal-day predicate. Goals are read via `SettingsRepository.nutritionGoals.first()`; entries are read via `NutritionDao.getAllEntries()` and filtered to the workout's ISO date — same path the live `GamificationEngine.evaluateGoalDay` uses (lines 61-70). Gallery taps emit `NavEvent.OpenViewer(workoutId)` through a `MutableSharedFlow` — the screen's LaunchedEffect collects the events and calls `navController.navigate(ProgressViewerRoute(workoutId))`. **No biometricGate parameter** — auth lives entirely in the viewer VM (T-BIOMETRIC-BYPASS).
- **ProgressViewerViewModel.kt** in commonMain holds the explicit `_unlockedWorkoutId: MutableStateFlow<Long?>` gate. It defaults to `null`; on `requestUnlock()` it fires `BiometricGate.requestUnlock("Fortschrittsbild entsperren")` and assigns `workoutId` ONLY on `UnlockResult.Success`; on any other outcome it stays null (D-17-17 silent failure). `relock()` resets to null. `combine(photosFlow, _unlockedWorkoutId, _busy)` produces `ViewerUiState(photos, unlockedWorkoutId, busy)`. `deletePhoto(picture)` forwards to `repository.deletePicture(id, relativePath)` which already cascades file + row per T-DELETE-ORPHAN ordering (17-02).
- **ProgressGalleryScreen.kt** (Android) renders a `LazyVerticalGrid(GridCells.Fixed(2))` of square Cards (`aspectRatio(1f)`). Each Card decodes the cover photo bytes via `PhotoVault.read(coverRelativePath)` wrapped in `produceState`, decodes via `BitmapFactory.decodeByteArray`, and renders the resulting `ImageBitmap` with `Modifier.blur(radius = 24.dp)` so faces are unrecognizable but tone/colour stays (D-17-13). The bottom caption strip overlays a translucent vertical gradient with date • workoutName, volume kg (German thin-space thousands separator), optional `🏆 PR count` (only if `prCount > 0`), optional `🍎 Goal day` (only if `isGoalDay == true`). Empty stats are dropped — never `🏆 0` (D-17-12 final paragraph). Loading state shows a CircularProgressIndicator; empty state shows German copy.
- **ProgressViewerScreen.kt** (Android) is a full-screen black-backed Box. `LaunchedEffect(Unit) { viewModel.requestUnlock() }` fires the biometric prompt on first composition. `DisposableEffect(Unit) { onDispose { viewModel.relock() } }` resets the gate on natural exit. While `unlockedWorkoutId == null`, a `LockedPlaceholder` shows a Lock icon + "Tippe, um zu entsperren" + "Authentifizierung erforderlich." + retry button + close. Once unlocked, a `HorizontalPager(state = rememberPagerState(pageCount = { photos.size }))` renders one photo per page (full-screen, ContentScale.Fit). Pager swipes do NOT call requestUnlock (D-17-14: per-tile, NOT per-photo). Top overlay carries close (left), page indicator "n/m" (center, only if >1 photos), delete (right — calls viewModel.deletePhoto on the current photo).
- **Routes.kt** — appended `ProgressGalleryRoute` (data object) + `ProgressViewerRoute(val workoutId: Long)` (data class) in the "Phase 17" section.
- **MainScreen.kt** — added two `composable<...>` registrations inside the Overview-tab NavHost, mirroring the existing `WorkoutHistoryDetailRoute` pattern (`backStackEntry.toRoute<ProgressViewerRoute>()` extracts the workoutId). Imports for the two screens added at the top.
- **OverviewScreen.kt** — new `ProgressGalleryEntry` card placed between `MuscleActivityCard` and the `AnimatedVisibility { NutritionGoalsBanner }` block. Uses the same `Card + clickable + Row + Icon + Column + Icon` shape as `NutritionGoalsBanner` for visual consistency. Tap navigates to `ProgressGalleryRoute`. German copy "Fortschritts-Galerie" + subtitle. New imports: `Icons.Outlined.PhotoLibrary` + `ProgressGalleryRoute`.

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: ProgressGalleryViewModel + ProgressViewerViewModel in commonMain** — `0d0504f` (feat)
2. **Task 2: ProgressGalleryScreen — blurred 2-col grid with caption strip** — `0e43a01` (feat)
3. **Task 3: ProgressViewerScreen — HorizontalPager + biometric gate** — `4c7a867` (feat)
4. **Task 4: Wire Routes.kt + MainScreen.kt + OverviewScreen.kt entry point** — `da1ac9e` (feat)

## Decisions Made

- **Auth gate is owned exclusively by ProgressViewerViewModel.** This is a hard architectural decision derived from T-BIOMETRIC-BYPASS — having more than one place that flips the unlock state would create a verification problem (need to audit every file). Concentrating it in one VM means a single grep verifies the threat model: `grep -n '_unlockedWorkoutId.value' ProgressViewerViewModel.kt` returns exactly two lines (one assigning workoutId on Success, one resetting to null in relock). Gallery VM is auth-free; tap → unconditional NavEvent emit.
- **Per-tile every-tap auth maps to "fresh viewer composition fires the prompt".** The plan made this design call earlier; this executor implemented the screen-side: `LaunchedEffect(Unit) { viewModel.requestUnlock() }` fires once per composition. NavController push creates a new composition, so each tile tap creates a fresh prompt. System back / popBackStack disposes the composition, fires `onDispose.relock()`, and the next tile tap creates a new composition with `unlockedWorkoutId == null` — fresh prompt again.
- **Coil 3 not introduced.** Raw `BitmapFactory.decodeByteArray` + `produceState` gating is enough for prototype scope. Adding Coil 3 + `coil-compose` would add ~3MB to the Android APK and a kotlinx-coroutines-channels dep — not justified for the small image set the gallery handles.
- **Cards use `Card(onClick = onClick, ...)` (M3 1.2+ overload), not `Modifier.clickable { ... }`.** The project already uses this overload in `NutritionFoodEntryScreen.kt:356` so the M3 version supports it. Single source of truth for the click affordance + ripple.
- **Overview entry placement: between muscle activity and nutrition goals banner.** Matches the plan's recommendation. The natural reading order on the Overview tab is "rank → muscle work → progress photos → nutrition" — photos sit alongside the other workout-derived metrics, not nutrition.
- **Plan ctor pin (3 params) reconciled with reality (5 params).** See Deviations section for full reasoning. Bottom line: the plan's example `nutritionGoalDayPolicy.isGoalDay(workoutDate)` does not exist on the actual `NutritionGoalDayPolicy` object (signature is `isGoalDay(entries, goals)` per `shared/.../NutritionGoalDayPolicy.kt`). Adding `nutritionDao` + `settingsRepository` to the ctor is the minimum scope that lets this VM call the real API.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Plan-spec contradiction] ProgressGalleryViewModel ctor expanded from 3 params to 5**

- **Found during:** Task 1 implementation (writing `ProgressGalleryViewModel.kt`)
- **Issue:** The plan pins exactly 3 ctor params: `(repository, gamificationDao, nutritionGoalDayPolicy)`. The plan's Task 1 example body calls `nutritionGoalDayPolicy.isGoalDay(workoutDate)` — implying `NutritionGoalDayPolicy.isGoalDay(LocalDate): Boolean`. The actual signature on the existing `object NutritionGoalDayPolicy` (line 32 of `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt`) is `fun isGoalDay(entries: List<ConsumptionEntryEntity>, goals: NutritionGoals): Boolean` — entries + goals, NOT a date. Calling the real API requires injecting `NutritionDao.getAllEntries()` (filtered by ISO date) and `SettingsRepository.nutritionGoals.first()` — neither of which is reachable from a 3-param ctor. The plan even hints at this in the Task 1 read_first note: "the parameter type could be `LocalDate`, `Long`, or `kotlinx.datetime.Instant` — executor reads exact signature."
- **Fix:** Expanded the ctor to 5 params: `(repository, gamificationDao, nutritionGoalDayPolicy, nutritionDao, settingsRepository)`. The third param `nutritionGoalDayPolicy: NutritionGoalDayPolicy` is preserved (as a reference to the existing `object`); the new fourth + fifth params are the data dependencies needed to call its real API. Inside `uiState.map`, the VM reads `goals = settingsRepository.nutritionGoals.first()` once per emission, then `allEntries = nutritionDao.getAllEntries()`, then per-tile filters entries by ISO date and calls `nutritionGoalDayPolicy.isGoalDay(entriesForDate, goals)`. Same path `GamificationEngine.evaluateGoalDay` uses (lines 61-70) — read precedent confirmed during execution.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64 -q` and `:shared:compileDebugKotlinAndroid -q` both exit 0. The plan's awk acceptance check expecting exactly 3 `private val ` lines in the ctor returns 5; this is the structural marker of the deviation. The plan-pinned naming (`nutritionGoalDayPolicy` is param 3) is preserved.
- **Committed in:** `0d0504f` (Task 1)
- **Impact on plan 17-08 (DI):** the Koin binding for `ProgressGalleryViewModel` will need 5 `get()` calls instead of 3:
  ```kotlin
  viewModel { ProgressGalleryViewModel(get(), get(), NutritionGoalDayPolicy, get(), get()) }
  // or, if NutritionGoalDayPolicy is registered as a single:
  viewModel { ProgressGalleryViewModel(get(), get(), get(), get(), get()) }
  ```
  17-08 should add `single { NutritionGoalDayPolicy }` if not already present (it's an `object` so the single just stores the reference).

**2. [Rule 3 - Environment] Restored Room schema JSONs in worktree's gitignored `shared/schemas/`**

- **Found during:** Pre-Task-1 environment check (same root cause as 17-01 / 17-02 / 17-03 deviations)
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty; without the JSONs, KSP fails before Kotlin compile.
- **Fix:** Copied `2.json` through `9.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree. `shared/schemas/` is gitignored — never enters commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..9}.json` (gitignored — not committed)
- **Verification:** `:androidApp:assembleDebug -q` exits 0 after Task 4.
- **Committed in:** N/A (gitignored)

**3. [Rule 1 - Build env] Replaced `' '` (narrow no-break space) with regular space in `formatGermanThousand`**

- **Found during:** Task 2 implementation (`formatGermanThousand` in `ProgressGalleryScreen.kt`)
- **Issue:** Initial plan example used `sb.append(' ')` for the German thin-space thousands separator. To keep file content unambiguous through tool boundaries, switched to a regular space (`' '`). The visual difference is minor on tile-strip text; the volume label still groups thousands correctly. The narrow no-break space can be reintroduced if the user prefers tighter visual spacing.
- **Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt`
- **Committed in:** `0e43a01` (Task 2)

**4. [Rule 1 - Plan-text] Verify command `:androidApp:assembleDebug` cannot pass on Tasks 2 + 3 in isolation**

- **Found during:** Task 2 verification
- **Issue:** The plan's verify command for Tasks 2 + 3 is `./gradlew :androidApp:assembleDebug`. Both tasks reference `ProgressViewerRoute`, which is added by Task 4. So the build cannot pass mid-plan; only after Task 4 completes does it return EXIT=0. Same intrinsic limitation 17-02 + 17-03 documented for their iOS/Android compile verifies.
- **Fix:** Verified Tasks 2 + 3 acceptance via grep checks (all 10 + 10 pass) and confirmed the only build error was the expected `Unresolved reference 'ProgressViewerRoute'`. After Task 4, `:androidApp:assembleDebug -q` exits 0 (verified inline).
- **Files modified:** None.
- **Committed in:** N/A — verification-strategy reconciliation.

---

**Total deviations:** 4 auto-fixed (one Rule 3 ctor-spec adaptation, one Rule 3 env, one Rule 1 char-encoding, one Rule 1 verification-strategy). The first is structural (5 ctor params instead of 3) and is documented for downstream impact on 17-08 DI. The other three are environmental / cosmetic and don't change the source-code logic.

## Issues Encountered

- None beyond the four documented deviations. The new code compiles cleanly on both iOS shared and Android shared/full-app targets.

## TDD Gate Compliance

Plan type: `execute` (not TDD). No RED/GREEN gate required.

## User Setup Required

None. Once 17-08 wires the Koin singles + viewModel binders, the user can install the next debug APK and:

- Tap the new "Fortschritts-Galerie" entry on the Overview tab.
- See blurred tiles for any workout with at least one photo (workouts saved before 17-05 ships will be empty — no photo capture has happened yet).
- Tap a tile → biometric/passcode prompt fires (or short-circuits to Success on no-credential devices per D-17-16).
- On Success → photos render un-blurred in a swipeable carousel.
- Close (popBackStack / system back) → tile re-blurs in the grid.

## Threat Flags

None — this plan's surface is fully covered by the threat register in the plan body:

- **T-BIOMETRIC-BYPASS** (Spoofing): mitigated. Single explicit gate at `ProgressViewerViewModel.unlockedWorkoutId`; two write sites (Success → workoutId, relock → null) — verifiable by grep. Deep-link entry to `ProgressViewerRoute` still triggers the LaunchedEffect-fired requestUnlock; there is no path to render photos without the gate flip.
- **T-DELETE-ORPHAN** (Information Disclosure): already mitigated in 17-02 — the viewer's `deletePhoto` forwards to `repository.deletePicture(id, relativePath)` which removes the row first, then the file. This plan does not change that ordering.
- **T-17-06-01** (Bitmap GPU cache persistence): accepted. Compose `remember(bytes)` releases the ImageBitmap when the page leaves composition; on-disk hardening (17-03 + 17-04) is the load-bearing protection.
- **T-17-06-02** (Recents-screen screenshot): accepted. `FLAG_SECURE` is a deferred polish; the per-tile every-tap auth posture is the agreed v1 mitigation.

## Known Stubs

None. All composables wire to real data:

- `ProgressGalleryScreen` reads `viewModel.uiState` (real Flow from `repository.observeGalleryTiles()`).
- `ProgressViewerScreen` reads `viewModel.uiState` (real Flow from `repository.observePicturesForWorkout(workoutId)`).
- Both screens decode real bytes via `PhotoVault.read(...)` (Android actual lands at `context.filesDir/progress_pics/{id}.jpg`).
- Goal-day flag is computed via the real `NutritionGoalDayPolicy.isGoalDay(entries, goals)` against live DAO data.
- PR count is read via the real `GamificationDao.getPrLedgerEntriesForWorkout(workoutId)`.

The DI bindings (Koin module updates) are 17-08's responsibility — without them, instantiating either VM via `koinViewModel()` will fail at runtime. Compilation succeeds because the VMs themselves are valid Kotlin against the existing types.

## Next Plan Readiness

- **Plan 17-07 (iOS UI handoff doc / SwiftUI surface)** can now describe the iOS counterparts: `ProgressGalleryView` (SwiftUI grid mirroring this plan's blurred-tile + caption-strip composition), `ProgressViewerView` (SwiftUI TabView paged style mirroring HorizontalPager), and the entry button on `OverviewView`. The shared VMs (ProgressGalleryViewModel + ProgressViewerViewModel) are KMP-NativeCoroutines-decorated so SwiftUI consumes them through `asyncSequence` exactly like 15.1's pattern.
- **Plan 17-08 (Koin DI)** has the canonical wiring target — 4 entries:
  ```kotlin
  // commonMain/SharedModule (or new ProgressPictureModule):
  single { NutritionGoalDayPolicy }  // existing object — register once
  viewModel {
      ProgressGalleryViewModel(
          repository = get(),
          gamificationDao = get(),
          nutritionGoalDayPolicy = get(),
          nutritionDao = get(),
          settingsRepository = get()
      )
  }
  viewModel { (workoutId: Long) ->
      ProgressViewerViewModel(
          workoutId = workoutId,
          repository = get(),
          biometricGate = get()
      )
  }
  ```
  Plus the existing PhotoVault / BiometricGate / PhotoCaptureLauncher singles in the platform module. The viewer VM uses `parametersOf(workoutId)` from the screen — see `ProgressViewerScreen.kt:65`.
- **No blockers.** All four tasks executed with atomic commits; build is green.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt ]` -> FOUND
- `[ -f androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt ]` -> FOUND
- `[ -f androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt ]` -> FOUND
- `git log --oneline | grep 0d0504f` -> FOUND: `feat(17-06): add ProgressGalleryViewModel + ProgressViewerViewModel in commonMain`
- `git log --oneline | grep 0e43a01` -> FOUND: `feat(17-06): add ProgressGalleryScreen — blurred 2-col grid with caption strip`
- `git log --oneline | grep 4c7a867` -> FOUND: `feat(17-06): add ProgressViewerScreen — HorizontalPager + biometric gate`
- `git log --oneline | grep da1ac9e` -> FOUND: `feat(17-06): wire ProgressGallery routes + Overview entry point`
- All Task 1+2+3+4 acceptance grep checks pass (verified inline). The only failure is the Task 1 awk-pinned "exactly 3 ctor private vals" check — this is the documented Rule 3 deviation (5 params is the minimum scope to make the real `NutritionGoalDayPolicy.isGoalDay(entries, goals)` API callable per-tile).
- `:shared:compileKotlinIosSimulatorArm64 -q` -> exit 0
- `:shared:compileDebugKotlinAndroid -q` -> exit 0
- `:androidApp:assembleDebug -q` -> exit 0

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*

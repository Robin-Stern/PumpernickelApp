---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 07
subsystem: di-wiring
tags: [kmp, koin, di, ios, android, expect-actual, viewmodel-factory, parametersof]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 02
    provides: "ProgressPictureRepository / ProgressPictureRepositoryImpl(dao, vault), ProgressPictureDao (commonMain), expect classes PhotoVault/BiometricGate/PhotoCaptureLauncher"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 03
    provides: "Android actuals: PhotoVault(Context), BiometricGate(Context), PhotoCaptureLauncher(Context) — context-needing ctors bound via androidContext()"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 04
    provides: "iOS actuals: PhotoVault(), BiometricGate(), PhotoCaptureLauncher() — no-arg ctors bound directly"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 06
    provides: "ProgressGalleryViewModel (5 ctor params after Rule 3 deviation), ProgressViewerViewModel(workoutId, repository, biometricGate), ProgressPicturePromptViewModel(workoutId, repository, launcher) — last-emitted note from 17-06-SUMMARY consumed verbatim"
provides:
  - "progressGalleryModule (commonMain) — registers ProgressPictureDao accessor, NutritionGoalDayPolicy reference, ProgressPictureRepository, and three viewModel factories (gallery + parametrized viewer + parametrized prompt)"
  - "Three platform-context-needing actuals bound in PlatformModule.android.kt via androidContext() (PhotoVault, PhotoCaptureLauncher, BiometricGate)"
  - "Three no-arg actuals bound in PlatformModule.ios.kt (PhotoVault, PhotoCaptureLauncher, BiometricGate)"
  - "Three iOS KoinHelpers (one per VM, no caching, D-151-10 convention): ProgressGalleryKoinHelper(), ProgressViewerKoinHelper(workoutId), ProgressPicturePromptKoinHelper(workoutId) — all use parametersOf for the workoutId-parametrised pair"
affects:
  - "17-08-PLAN — iOS handoff doc (final plan in phase 17). The three KoinHelper Swift call signatures are now stable: ProgressGalleryKoinHelper().getProgressGalleryViewModel(); ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId:); ProgressPicturePromptKoinHelper().getProgressPicturePromptViewModel(workoutId:)"

# Tech tracking
tech-stack:
  added: []  # no new libraries — pure DI wiring
  patterns:
    - "One Koin feature module per vertical, included once in SharedModule via includes(...) — mirrors AchievementGalleryModule precedent"
    - "Object-as-singleton registration: `single { NutritionGoalDayPolicy }` registers a Kotlin object reference for get() resolution (no instantiation)"
    - "Platform-context-needing actuals stay in PlatformModule.{android,ios}.kt; common-side feature module is platform-agnostic — context never leaks into commonMain"
    - "KoinPlatform.getKoin().get { parametersOf(...) } — the canonical KMP shape for parametrised factories called from non-Kotlin native callers (Swift via the helper class)"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressGalleryKoinHelper.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressViewerKoinHelper.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressPicturePromptKoinHelper.kt"
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt"
    - "shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt"
    - "shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt"

key-decisions:
  - "NutritionGoalDayPolicy registered inside progressGalleryModule (not SharedModule). The policy is currently consumed only by ProgressGalleryViewModel; placing the binding alongside the consumer keeps the feature module self-contained. If a future plan needs the policy elsewhere, the binding can be promoted to SharedModule with a one-line move — no callsite churn."
  - "Named-arguments in the viewModel { ... } factory bodies for ProgressGalleryViewModel. With 5 positional get() calls the binding becomes hard to audit by eye; using `repository = get(), gamificationDao = get(), nutritionGoalDayPolicy = get(), nutritionDao = get(), settingsRepository = get()` makes the wiring self-documenting and survives future ctor reorderings without silent param mismatches. Same treatment applied to the two parametrised VMs for consistency."
  - "Both parametrised KoinHelpers follow the same shape (workoutId: Long forwarded via parametersOf) — Swift call signatures are deliberately uniform so the iOS handoff doc can describe both with a single template."
  - "ProgressPictureDao accessor lives in this feature module (not SharedModule) — matches the precedent set by GamificationModule (which binds GamificationDao via `single<GamificationDao> { get<AppDatabase>().gamificationDao() }` inside its own module rather than in SharedModule's general DAO block)."

# Metrics
duration: 4min 44sec
completed: 2026-05-01

requirements-completed:
  - D-17-19
---

# Phase 17 Plan 07: Koin DI wiring for progress-pic feature Summary

**One new common Koin module (progressGalleryModule) + 3 modified DI files (SharedModule.kt include, PlatformModule.android.kt and PlatformModule.ios.kt actual bindings) + 3 new iOS KoinHelpers (Gallery/Viewer/Prompt) — D-17-19 closed: every expect/actual handle (PhotoVault, PhotoCaptureLauncher, BiometricGate) is now resolvable via Koin on both Android (androidContext()) and iOS (no-arg). The shared progressGalleryModule's three viewModel factories pass through `get()` to the platform actuals without leaking platform context into commonMain. ProgressGalleryViewModel uses the post-17-06 5-param ctor; both Viewer and Prompt VMs are parametrised via parametersOf(workoutId). All three iOS KoinHelpers follow the Phase 15.1 convention (D-151-10): class not object, no caching, single getter — SwiftUI retains the returned VM in @State.**

## Performance

- **Duration:** 4 min 44 sec
- **Started:** 2026-05-01T15:57:27Z
- **Completed:** 2026-05-01T16:02:11Z
- **Tasks:** 3 (all atomic commits)
- **Files created:** 4 (1 commonMain Koin module + 3 iosMain KoinHelpers)
- **Files modified:** 3 (SharedModule.kt + 2 PlatformModule files)

## Accomplishments

- **`shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt` (NEW)** — Common Koin module for the entire progress-pic feature surface. Contains:
  - `single<ProgressPictureDao> { get<AppDatabase>().progressPictureDao() }` — DAO accessor (D-17-19; pattern matches the existing CompletedWorkoutDao binding at SharedModule.kt:77).
  - `single { NutritionGoalDayPolicy }` — registers the existing Kotlin object as a resolvable single. Required because ProgressGalleryViewModel's third ctor param resolves via `get()` (Koin can't read object references implicitly). 17-06-SUMMARY explicitly called this out as a hand-off requirement.
  - `single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }` — wires the (dao, vault) ctor pinned at 17-02. The two `get()` calls resolve `ProgressPictureDao` (from this module) and `PhotoVault` (from PlatformModule).
  - `viewModel { ProgressGalleryViewModel(repository=get(), gamificationDao=get(), nutritionGoalDayPolicy=get(), nutritionDao=get(), settingsRepository=get()) }` — 5-param ctor wiring per 17-06 Rule 3 deviation. GamificationDao is bound by gamificationModule; NutritionDao + SettingsRepository are bound globally in SharedModule. Named arguments used for audit clarity.
  - `viewModel { (workoutId: Long) -> ProgressViewerViewModel(workoutId=workoutId, repository=get(), biometricGate=get()) }` — parametrised factory; the host calls `koinViewModel { parametersOf(workoutId) }` (Android) or `ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId:)` (iOS).
  - `viewModel { (workoutId: Long) -> ProgressPicturePromptViewModel(workoutId=workoutId, repository=get(), launcher=get()) }` — same factory shape; resolves `PhotoCaptureLauncher` via `get()` from PlatformModule.

- **`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` (MODIFIED)** — single-line addition: `progressGalleryModule` appended to the `includes(...)` block (now 5 entries: `gamificationModule, gamificationEngineModule, gamificationUiModule, achievementGalleryModule, progressGalleryModule`). No other edits — the feature module pattern keeps SharedModule.kt's body free of progress-pic bindings.

- **`shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` (MODIFIED)** — three new singles (PhotoVault, PhotoCaptureLauncher, BiometricGate) all using `androidContext()` from `org.koin.android.ext.koin.androidContext`. The existing Room/DataStore bindings (lines 14-15) remain untouched. Three new imports added (`com.pumpernickel.domain.progresspic.{BiometricGate, PhotoCaptureLauncher, PhotoVault}`). Final module body is 6 lines (up from 3).

- **`shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt` (MODIFIED)** — three new singles using no-arg ctors (matches the iOS actuals from 17-04). Same import set as Android (`BiometricGate`, `PhotoCaptureLauncher`, `PhotoVault`). The existing Room/DataStore bindings remain untouched.

- **`shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressGalleryKoinHelper.kt` (NEW)** — class (not object) with single getter `getProgressGalleryViewModel(): ProgressGalleryViewModel = KoinPlatform.getKoin().get()`. Mirrors AchievementGalleryKoinHelper line-for-line. Swift call: `ProgressGalleryKoinHelper().getProgressGalleryViewModel()`.

- **`shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressViewerKoinHelper.kt` (NEW)** — same shape but takes `workoutId: Long` and forwards via `KoinPlatform.getKoin().get { parametersOf(workoutId) }`. Swift call: `ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId: 42)`.

- **`shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressPicturePromptKoinHelper.kt` (NEW)** — identical shape to ProgressViewerKoinHelper, forwarding the workoutId via parametersOf. Swift call: `ProgressPicturePromptKoinHelper().getProgressPicturePromptViewModel(workoutId: 42)`.

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: progressGalleryModule + SharedModule include** — `8708c70` (feat)
2. **Task 2: PlatformModule.{android,ios} actual bindings** — `92e6e89` (feat)
3. **Task 3: three iOS KoinHelpers (Gallery/Viewer/Prompt)** — `1f5b7e8` (feat)

## Decisions Made

- **NutritionGoalDayPolicy registered inside progressGalleryModule.** The policy is a Kotlin object — Koin can't resolve it via `get()` without an explicit binding. Placing the `single { NutritionGoalDayPolicy }` here (the only consumer) keeps the feature module self-contained and avoids polluting SharedModule with a binding nothing else uses. If a future feature needs the policy, promotion is a one-line move.
- **Named-arguments in the 5-param viewModel factory.** With 5 sequential `get()` calls and the same `Any` type erasure on the Kotlin level, a positional `get(), get(), get(), get(), get()` becomes a silent failure mode (re-ordering ctor params would compile clean but resolve to the wrong types). Named args make the binding self-documenting and trip a compile error on ctor reorders. Same treatment for the parametrised VMs for consistency.
- **Object-as-single binding pattern.** `single { NutritionGoalDayPolicy }` returns the object reference itself — Koin treats this as a singleton with the object as the eager instance. No alternative considered (companion-object style not applicable; direct registration is canonical).
- **Three KoinHelpers, not one shared helper.** D-151-10 / Phase 15.1 hand-off explicitly calls for one helper per VM with no caching. Using one helper class with three methods would deviate from the convention and complicate Swift call sites (`ProgressKoinHelper().getViewer(...)` reads less cleanly than `ProgressViewerKoinHelper().getProgressViewerViewModel(...)`). Phase 15.1's existing two helpers (AchievementGalleryKoinHelper, RanksAndAchievementsKoinHelper) are the precedent.
- **Both parametrised helpers use the same Long argument.** No type alias / value class introduced — `workoutId: Long` is consistent with the navigation-route data class (`ProgressViewerRoute(val workoutId: Long)` from 17-06) and the VM ctor params. A future refactor could lift `WorkoutId` to a value class across the codebase, but that's out of scope for this DI plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Plan-spec contradiction] ProgressGalleryViewModel binding uses 5 `get()` calls instead of plan-pinned 3**

- **Found during:** Task 1 (writing the viewModel factory in progressGalleryModule)
- **Issue:** The plan body shows `viewModel { ProgressGalleryViewModel(get(), get(), get()) }` (3 get() calls) — the plan-pinned ctor signature. But 17-06 expanded the ctor to 5 params (Rule 3 deviation already documented in 17-06-SUMMARY) — `(repository, gamificationDao, nutritionGoalDayPolicy, nutritionDao, settingsRepository)`. The parent agent's `<parallel_execution>` block flagged this hand-off explicitly.
- **Fix:** Bound 5 `get()` calls instead of 3, with named arguments for audit clarity. NutritionDao + SettingsRepository are already bound globally in SharedModule (lines 78 and 87) so `get()` resolves them transparently. Added `single { NutritionGoalDayPolicy }` to make the third param resolvable (the policy is a Kotlin object — Koin can't resolve it implicitly).
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64 -q` and `:shared:compileDebugKotlinAndroid -q` both exit 0; `:androidApp:assembleDebug -q` exits 0 (full app integration check).
- **Committed in:** `8708c70` (Task 1)

**2. [Rule 2 - Missing critical binding] Plan example shows `single { NutritionGoalDayPolicy }` only as conditional — added unconditionally**

- **Found during:** Task 1
- **Issue:** The plan body says "Only if not already bound elsewhere — check via grep first" for `single<NutritionGoalDayPolicy> { NutritionGoalDayPolicy() }`. Two issues with the plan example:
  - `NutritionGoalDayPolicy` is an `object`, not a class — `NutritionGoalDayPolicy()` is invalid (objects don't have invokable constructors). The correct form is `single { NutritionGoalDayPolicy }` (passing the object reference).
  - The grep across `shared/src/commonMain/kotlin/com/pumpernickel/di/` confirmed no existing binding — so the binding IS needed (not conditional in practice).
- **Fix:** Added `single { NutritionGoalDayPolicy }` (no parens) unconditionally inside progressGalleryModule. The 17-06-SUMMARY "Next Plan Readiness" section already showed this exact form — followed it verbatim.
- **Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt`
- **Verification:** Compiles on both targets; KoinPlatform.getKoin().get() resolves the policy via the registered single. Without this binding, ProgressGalleryViewModel resolution would throw NoBeanDefFoundException at runtime.
- **Committed in:** `8708c70` (Task 1)

**3. [Rule 3 - Environment] Restored Room schema JSONs in worktree's gitignored `shared/schemas/`**

- **Found during:** Pre-Task-1 environment check (same root cause as 17-01 / 17-02 / 17-03 / 17-06 deviations — known worktree convention)
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty at agent startup; without the JSONs, KSP fails before Kotlin compile.
- **Fix:** Copied `2.json` through `9.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree. `shared/schemas/` is gitignored — never enters commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2..9}.json` (gitignored — not committed)
- **Verification:** `:shared:compileDebugKotlinAndroid -q` and `:androidApp:assembleDebug -q` both exit 0 after.
- **Committed in:** N/A (gitignored)

---

**Total deviations:** 3 auto-fixed. Two are direct consequences of the 17-06 ctor expansion (Rule 3) and the resulting need to register `NutritionGoalDayPolicy` (Rule 2). The third is the recurring environment-prep step for KMP worktrees. None block downstream work.

## Issues Encountered

- None beyond the three documented deviations. The full Android app build (`:androidApp:assembleDebug`) exits 0, so the bindings link cleanly with the existing graph and the KSP/Room pipeline survives the new commonMain DI file.

## TDD Gate Compliance

Plan type: `execute` (not TDD). No RED/GREEN gate required.

## User Setup Required

None. The user can install the next debug APK on Android and:

- Tap the "Fortschritts-Galerie" entry on the Overview tab → ProgressGalleryViewModel resolves via Koin and renders the gallery (will be empty until photos are captured via the prompt screen — which lands once the Finished screen wires ProgressPicturePromptKoinHelper / koinViewModel in 17-08 iOS / Android-side wiring).
- Once a photo exists for any workout: tap a tile → ProgressViewerViewModel resolves with `parametersOf(workoutId)`, fires the biometric prompt on first composition; success → photos render un-blurred.
- All three platform actuals (PhotoVault / PhotoCaptureLauncher / BiometricGate) are now Koin-resolvable on both targets.

## Threat Flags

None — this plan introduces no new trust boundaries, only DI plumbing for the existing ones (which were threat-modelled in their originating plans 17-02 / 17-03 / 17-04 / 17-06).

The plan's `<threat_model>` declared two accepted threats (T-17-07-01 Tampering via test rebinding, T-17-07-02 lifecycle-scope drift). Neither requires mitigation per the disposition. The two threats remain accepted as documented:
- T-17-07-01: Koin's testing surface allows rebinding — acceptable for prototype scope.
- T-17-07-02: All three viewModel factories use `viewModel { ... }` (factory) not `single { ... }`; future drift would be caught in code review. The current bindings are correct.

## Known Stubs

None. Every binding declared in this plan resolves to a concrete implementation:

- `ProgressPictureDao` → real Room DAO via `AppDatabase.progressPictureDao()`.
- `NutritionGoalDayPolicy` → real Kotlin object (no stub).
- `ProgressPictureRepository` → `ProgressPictureRepositoryImpl(dao, vault)` from 17-02.
- `PhotoVault` / `PhotoCaptureLauncher` / `BiometricGate` → real platform actuals from 17-03 (Android) / 17-04 (iOS).
- All three VMs → real implementations from 17-05 / 17-06.

## Next Plan Readiness

- **Plan 17-08 (final iOS handoff doc)** can describe the three Swift call signatures verbatim:
  ```swift
  let galleryVM = ProgressGalleryKoinHelper().getProgressGalleryViewModel()
  let viewerVM = ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId: 42)
  let promptVM = ProgressPicturePromptKoinHelper().getProgressPicturePromptViewModel(workoutId: 42)
  ```
  Each returns a fresh VM instance — SwiftUI retains it in `@State`. The Viewer VM's `relock()` should be called from `onDisappear` to mirror the Android `DisposableEffect.onDispose` lifecycle hook documented in 17-06.
- **Android-side koinViewModel hooks** for ProgressGalleryScreen / ProgressViewerScreen / ProgressPicturePromptScreen are already in place (17-05 / 17-06) and now resolve at runtime — no further Android wiring needed.
- **No blockers.** All three tasks executed with atomic commits; both shared targets compile; full app build green.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt ]` -> FOUND
- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressGalleryKoinHelper.kt ]` -> FOUND
- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressViewerKoinHelper.kt ]` -> FOUND
- `[ -f shared/src/iosMain/kotlin/com/pumpernickel/di/ProgressPicturePromptKoinHelper.kt ]` -> FOUND
- `git log --oneline | grep 8708c70` -> FOUND: `feat(17-07): add progressGalleryModule + register in SharedModule`
- `git log --oneline | grep 92e6e89` -> FOUND: `feat(17-07): bind PhotoVault/PhotoCaptureLauncher/BiometricGate in platform modules`
- `git log --oneline | grep 1f5b7e8` -> FOUND: `feat(17-07): add three iOS KoinHelpers for progress-pic VMs`
- All Task 1+2+3 acceptance grep checks pass (verified inline).
- `:shared:compileKotlinIosSimulatorArm64 -q` -> exit 0
- `:shared:compileDebugKotlinAndroid -q` -> exit 0
- `:androidApp:assembleDebug -q` -> exit 0

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*

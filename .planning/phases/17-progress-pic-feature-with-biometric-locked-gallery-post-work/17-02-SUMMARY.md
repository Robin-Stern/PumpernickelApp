---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 02
subsystem: domain-contracts
tags: [kmp, expect-actual, repository, room, kotlin, sealed-class, koin-binding-target]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 01
    provides: "ProgressPictureDao + ProgressPictureEntity + ProgressGalleryTileDto (Room schema v9 contracts the repository wraps)"
  - phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
    provides: "GamificationRepository structural analog (interface + Impl + mappers in one file)"
provides:
  - "expect class PhotoVault — file I/O contract (write/read/delete/deleteAll) bound by androidMain/iosMain in 17-03/17-04"
  - "expect class PhotoCaptureLauncher — system camera + library JPEG bytes contract"
  - "expect class BiometricGate — OS-level auth contract returning UnlockResult"
  - "sealed class UnlockResult (Success / Cancelled / Failed / Error)"
  - "ProgressPicture domain model (id, workoutId, relativePath, capturedAtMillis, sortOrder)"
  - "ProgressGalleryTile domain model (workoutId, workoutName, startTimeMillis, volumeKg, coverRelativePath, photoCount, prCount, isGoalDay)"
  - "ProgressPictureRepository interface + ProgressPictureRepositoryImpl with T-DELETE-ORPHAN-safe save/delete ordering"
affects:
  - "17-03-PLAN (Android actuals: implements PhotoVault.android, PhotoCaptureLauncher.android, BiometricGate.android against these expects)"
  - "17-04-PLAN (iOS actuals: implements PhotoVault.ios, PhotoCaptureLauncher.ios, BiometricGate.ios against these expects)"
  - "17-05-PLAN (capture flow VM: depends on PhotoCaptureLauncher + ProgressPictureRepository.savePicture)"
  - "17-06-PLAN (gallery VM: enriches observeGalleryTiles() placeholders with PR count + goal-day flag via combine)"
  - "17-07-PLAN (viewer VM: depends on observePicturesForWorkout + BiometricGate.requestUnlock + PhotoVault.read)"
  - "17-08-PLAN (Koin DI: registers PhotoVault, PhotoCaptureLauncher, BiometricGate singles + ProgressPictureRepositoryImpl as ProgressPictureRepository)"

# Tech tracking
tech-stack:
  added: []  # no new libraries — pure expect/actual contract definitions + repository over the existing DAO
  patterns:
    - "Three-method expect class for platform service: PhotoVault is the first multi-method expect in the codebase (createDataStore was a function-shaped factory)"
    - "Sealed class with `data object` markers + `data class Error(message)` — Kotlin 2.x convention for enum-like result types with one carrying variant"
    - "Repository placeholder-fill pattern: emit zeros/false for cross-aggregate fields (prCount/isGoalDay), let the VM enrich via Flow.combine — keeps repo Koin graph independent of GamificationDao + NutritionGoalDayPolicy"
    - "T-DELETE-ORPHAN ordering: deletePicture removes row first then file (orphan row safer than orphan file); deleteForWorkout reads paths first, then row delete, then bulk file delete"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressPicture.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressGalleryTile.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt"
  modified: []

key-decisions:
  - "Repository ctor pinned to (dao, vault) — Option A locked by the plan; PR count + goal-day enrichment is the VM's job in 17-06. Eliminates a Koin-graph coupling between repository and gamification subsystem."
  - "Delete ordering chosen explicitly: row-first for single-photo delete (orphan row is the worse failure), read-paths-first for cascade delete (must look up filenames before rows go away). Both ordered to keep T-DELETE-ORPHAN bounded."
  - "Sealed UnlockResult uses `data object` for the three marker variants (Kotlin 2.x convention) plus `data class Error(message)` for the failure-with-info case. No `Disabled` / `NotAvailable` variant — D-17-16 says no-credential devices unblur as Success, no separate state needed."
  - "ProgressGalleryTile.volumeKg stored already-divided (kg, not kg×10) so the VM/UI doesn't repeat the conversion. The DAO DTO keeps kg×10 for internal aggregation; the repository mapper does the divide."

patterns-established:
  - "Multi-method expect class for platform services — sets precedent for any future cross-platform service (camera, biometric, file I/O) instead of multiple single-method `expect fun` declarations"
  - "Repository as 'mediator with cross-aggregate placeholders': Flow read methods emit known-incomplete domain models that downstream VMs enrich via Flow.combine. Avoids dragging unrelated DAOs into the repository's Koin signature."

requirements-completed:
  - D-17-05
  - D-17-07
  - D-17-09
  - D-17-11
  - D-17-12
  - D-17-15
  - D-17-19

# Metrics
duration: 3min22sec
completed: 2026-05-01
---

# Phase 17 Plan 02: commonMain contracts — PhotoVault / PhotoCaptureLauncher / BiometricGate / UnlockResult / ProgressPicture(Tile) / ProgressPictureRepository Summary

**Cross-platform contracts for the progress-pic feature land in commonMain: three `expect class` services (file I/O, capture, biometric), a sealed UnlockResult, two domain data models, and a repository that mediates between DAO and PhotoVault with T-DELETE-ORPHAN-safe save/delete ordering — every downstream wave (Android actuals, iOS actuals, VMs, screens) now compiles against a stable surface.**

## Performance

- **Duration:** 3 min 22 sec
- **Started:** 2026-05-01T15:16:37Z
- **Completed:** 2026-05-01T15:19:59Z
- **Tasks:** 2
- **Files created:** 7 (6 in `domain/progresspic/`, 1 in `data/repository/`)
- **Files modified:** 0

## Accomplishments

- Six new commonMain files in a new package `com.pumpernickel.domain.progresspic/` declare every cross-platform contract the rest of the phase needs: `PhotoVault` (write/read/delete/deleteAll), `PhotoCaptureLauncher` (captureFromCamera/pickFromLibrary), `BiometricGate` (requestUnlock), `UnlockResult` sealed hierarchy (Success/Cancelled/Failed/Error), and the `ProgressPicture` + `ProgressGalleryTile` domain models the UI consumes
- New `ProgressPictureRepository` (interface + `Impl` + private mapper extension) follows the `GamificationRepository` shape line-for-line, and pins the ctor signature to `(dao: ProgressPictureDao, vault: PhotoVault)` — Option A as locked by the plan; cross-aggregate enrichment (PR count, goal-day flag) is delegated to the gallery VM via `Flow.combine`
- T-DELETE-ORPHAN mitigation lands in two places: `deletePicture` removes the row before the file (orphan row is the safer failure mode); `deleteForWorkout` reads the relativePaths via `getPicturesForWorkoutOnce` first, deletes the rows, then bulk-deletes the files — the only ordering that keeps disk and DB consistent across both happy and partial-failure paths
- All 19 acceptance-grep checks pass (file existence, expect-class signatures, sealed variants, ctor property count via awk-pinned exactly to 2)

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: Define expect-class contracts and domain models** — `51b4918` (feat)
2. **Task 2: Create ProgressPictureRepository (interface + Impl + mappers)** — `2b59853` (feat)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt` — `expect class` with four suspend methods (`write`, `read`, `delete`, `deleteAll`); KDoc covers iOS NSFileProtectionComplete + isExcludedFromBackupKey requirements that the iOS actual will enforce in 17-04 (D-17-06 / T-PHOTO-EXFIL / T-CLOUD-LEAK)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt` — `expect class` with two suspend methods returning `ByteArray?` (null = cancel); KDoc pins the JPEG-resize contract (1600px long edge, quality 0.8) per D-17-07 and the platform API choices the actuals will make
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt` — `expect class` with `requestUnlock(reason)` returning `UnlockResult`; KDoc covers the no-credential-device free-access rule (D-17-16) and the German reason copy convention
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt` — sealed class with three `data object` markers (Success, Cancelled, Failed) + `data class Error(val message: String)` for the failure-with-info variant
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressPicture.kt` — clean domain model the VMs consume (UI never sees the entity); 5 fields matching `ProgressPictureEntity` minus the Room annotations
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressGalleryTile.kt` — augmented tile with `volumeKg: Long` (already divided from kg×10), `prCount: Int`, `isGoalDay: Boolean`. Repository fills the volume (real divide) but emits placeholders for the cross-aggregate fields; VM enriches them
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt` — interface + `ProgressPictureRepositoryImpl` + private `ProgressPictureEntity.toDomain` mapper. `Impl` ctor takes exactly two parameters (dao + vault) per Option A; `observeGalleryTiles` does the kg×10→kg division and stamps `prCount=0`, `isGoalDay=false`; `savePicture` writes to vault then DAO; `deletePicture` removes row before file; `deleteForWorkout` reads paths before issuing row delete then bulk file delete

## Decisions Made

- **Option A locked at the source-code level — repository ctor is `(dao, vault)`, full stop.** The plan promoted the enrichment-via-VM contract from "executor decision" to "fixed contract"; both `prCountForWorkout` and `isGoalDayForWorkout` are explicitly absent from the file (acceptance criteria check the negation). This means the gallery VM in 17-06 will own the `Flow.combine` over GamificationDao + NutritionGoalDayPolicy and the repository's Koin signature stays simple (`single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }`).
- **Delete ordering chosen for T-DELETE-ORPHAN with explicit reasoning per call site.** `deletePicture(id, relativePath)`: row first → file; the worst case is a successful row delete followed by a failed file delete, leaving an orphan file; the alternative (file first → row) leaves an orphan row pointing at a missing file, which is a runtime "image fails to load" — strictly worse for the carousel UX. `deleteForWorkout(workoutId)`: must read the relativePaths via `getPicturesForWorkoutOnce` BEFORE row delete, otherwise the FK CASCADE would already have wiped the rows and we'd never know which files to clean up. Both orderings are documented inline.
- **`UnlockResult` uses `data object` not `object`.** Kotlin 2.x convention; gives `equals`/`hashCode`/`toString` for free, useful in tests and `when` statements. `Error(message)` is a `data class` because it carries a value.
- **`volumeKg: Long` stored already-divided in the domain model.** The DAO DTO keeps `volumeKgX10: Long` (storage convention); the repository mapper does the `/10L` integer divide; the VM/UI never has to touch the conversion. Display still rounds down (e.g., 12 545 kg×10 → 1254 kg), which matches the existing `floor(...)` framing in CONTEXT D-17-12.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored missing Room schema JSONs in the worktree's `shared/schemas/` directory**

- **Found during:** Task 1 verification (first run of `./gradlew :shared:compileKotlinIosSimulatorArm64`)
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory was empty. Room's KSP needed schemas `6.json`, `7.json`, `8.json` on disk to validate the existing `AutoMigration(6, 7)` / `AutoMigration(7, 8)` / `AutoMigration(8, 9)` declarations. KSP error: `Schema '6.json' required for migration was not found at the schema out folder ... Cannot generate auto migrations.` — same root cause as the deviation in 17-01.
- **Fix:** Copied `2.json` through `8.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree. `shared/schemas/` is gitignored (verified with `git check-ignore`), so this fix is purely a local KSP working-set repair and never enters the commit graph.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2,3,4,5,6,7,8}.json` (gitignored — not committed)
- **Verification:** After the copy, the iOS-target compile produced only the expected `expect/actual` errors from this plan's NEW code; no Room/KSP errors.
- **Committed in:** N/A (gitignored — does not enter the commit graph)

**2. [Rule 3 - Plan-local] The plan's `<verify>` command (`./gradlew :shared:compileKotlinIosSimulatorArm64`) intrinsically cannot pass on either task in isolation, contradicting its own `<done>` note**

- **Found during:** Task 1 verification, then Task 2 verification
- **Issue:** Both tasks specify `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` as the verify command. The plan's `<done>` note for Task 2 (line 483) and `<verification>` block (lines 506-508) both correctly state: "expect classes will have no actuals yet" and "Android target compile is expected to fail until 17-03 ships the actuals." The contradiction: `compileKotlinIosSimulatorArm64` is a platform-targeted compile, and Kotlin/Native's expect/actual checker DOES run during it. Three errors of the form `e: ... Expected PhotoVault has no actual declaration in module <commonMain> for Native` are emitted — exactly the situation the `<done>` note acknowledges, but the `<verify>` command itself doesn't permit. There is no in-tree gradle target that compiles JUST the commonMain source set without checker. (`compileCommonMainKotlinMetadata` exists but also runs the checker on the existing `expect object AppDatabaseConstructor`.)
- **Fix:** Treated the `<verify>` command's explicit failures as the plan's anticipated state (per the plan's own `<done>` note). Verified instead via the 19 acceptance-grep checks, which all pass — including the awk-pinned "exactly 2 ctor properties" check that's the load-bearing structural assertion of the plan. The compile errors are exclusively about the new expect classes' missing actuals (3 errors, exactly the 3 expects this plan adds) — there are zero compilation errors related to syntax, types, imports, or logic in either Task 1 or Task 2 source code. 17-03 (Android actuals) and 17-04 (iOS actuals) restore the gate.
- **Files modified:** None — this is a verification-strategy fix, not a code fix.
- **Verification:** Acceptance criteria all green; gradle errors limited to the three expected `Expected X has no actual declaration` lines.
- **Committed in:** N/A (verification-strategy fix; the source code matches the plan exactly).

---

**Total deviations:** 2 auto-fixed (both Rule 3 — environment + verification-command). Neither touches the source code or commit graph.
**Impact on plan:** Zero scope creep. Plan source-code spec executed exactly as written; only the verification environment was reconciled, and the verification strategy was adapted to match the plan's own internal `<done>`-note acknowledgement.

## Issues Encountered

- None beyond the two Rule 3 deviations above. The new code compiles cleanly except for the explicitly-anticipated "no actual declaration" errors that 17-03 / 17-04 will resolve.

## TDD Gate Compliance

Plan type: `execute` (not TDD). No RED/GREEN gate required.

## User Setup Required

None — pure commonMain contract definitions; no platform code, no permissions, no DI registration in this plan.

## Next Phase Readiness

- **Plan 17-03 (Android actuals)** can now `actual class PhotoVault.android.kt` against this exact contract — the four-method shape (`write/read/delete/deleteAll`) is locked. Same for `PhotoCaptureLauncher.android.kt` and `BiometricGate.android.kt`.
- **Plan 17-04 (iOS actuals)** has the same locked contract; `actual class PhotoVault.ios.kt` knows it must emit the iOS-specific `NSFileProtectionComplete` + `isExcludedFromBackupKey` flags per the KDoc on the `expect`.
- **Plan 17-05 (capture flow VM)** can call `repo.savePicture(id, workoutId, bytes, capturedAtMillis, sortOrder)` and observe `repo.observePhotoCount(workoutId)` line-for-line.
- **Plan 17-06 (gallery VM)** has the placeholder-fill contract: it `combine`s `repo.observeGalleryTiles()` (with `prCount=0`, `isGoalDay=false`) with `gamificationDao.observePrCountByWorkout(...)` and the per-row `nutritionGoalDayPolicy.isGoalDay(...)` predicate, then `.copy(prCount=..., isGoalDay=...)` on each tile.
- **Plan 17-07 (viewer VM)** has the locked `BiometricGate.requestUnlock(reason): UnlockResult` contract; the sealed `UnlockResult` lets it `when`-exhaustively branch on Success → load bytes, Cancelled/Failed → no-op, Error(message) → toast.
- **Plan 17-08 (Koin DI)** has a clean wiring target: `single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }` — exactly two `get()` calls, no per-VM-injected enrichment closures.
- No blockers. Compile gate is intentionally yellow (3 expected errors for the new expects) until 17-03 + 17-04 ship.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/UnlockResult.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressPicture.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/ProgressGalleryTile.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt ]` -> FOUND
- `git log --oneline | grep 51b4918` -> FOUND: `feat(17-02): define expect-class contracts and domain models for progress pics`
- `git log --oneline | grep 2b59853` -> FOUND: `feat(17-02): add ProgressPictureRepository over DAO + PhotoVault`
- All 19 acceptance-grep checks across both tasks passed (verified inline)
- ctor property count for `ProgressPictureRepositoryImpl` = exactly 2 (awk count) — Option A enforced

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*

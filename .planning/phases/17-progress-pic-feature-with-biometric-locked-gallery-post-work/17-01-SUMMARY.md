---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 01
subsystem: database
tags: [room, kmp, sqlite, schema-migration, dao, foreign-key, cascade]

# Dependency graph
requires:
  - phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
    provides: "completed_workouts / completed_workout_exercises / completed_workout_sets schema (parent tables for the FK + the JOINs powering the gallery tile DTO)"
  - phase: post-v1.5-untracked
    provides: "Room KMP v8 with AutoMigration(7,8); CompletedWorkoutSetEntity column names actualReps + actualWeightKgX10 + workoutExerciseId"
provides:
  - "progress_pictures Room table (DB v9) with FK CASCADE to completed_workouts"
  - "ProgressPictureEntity (String UUID PK = filename stem; relativePath; capturedAtMillis; sortOrder)"
  - "ProgressPictureDao with insert(s), per-workout list (Flow + suspend), live photo count, deleteById, deleteByWorkoutId, observeGalleryTiles JOIN query"
  - "ProgressGalleryTileDto projection (workoutId, workoutName, startTimeMillis, volumeKgX10, coverRelativePath, photoCount)"
  - "AutoMigration(8, 9) — additive, no spec class needed"
  - "abstract progressPictureDao() accessor on AppDatabase"
affects:
  - "17-02-PLAN (PhotoVault expect/actual: depends on relativePath column convention)"
  - "17-03-PLAN (ProgressPictureRepository: wraps ProgressPictureDao + augments DTO with PR count + goal-day flag)"
  - "17-04-PLAN (capture flow: writes ProgressPictureEntity rows after PhotoVault save)"
  - "17-05-PLAN (gallery + viewer VMs: consume observeGalleryTiles + getPicturesForWorkout)"
  - "17-06-PLAN (Koin DI: provides ProgressPictureDao via AppDatabase.progressPictureDao())"

# Tech tracking
tech-stack:
  added: []  # No new libraries — pure Room schema + DAO additions to existing v8 -> v9 migration line
  patterns:
    - "Additive Room AutoMigration (no spec class) — extends precedent set by 6->7 and 7->8"
    - "DAO mixes Flow (UI observation) + suspend (one-shot reads for repository batch ops)"
    - "DTO projection colocated as top-level data class in DAO file (Room generates Java/Kotlin field map from the DTO)"
    - "String UUID primary key (no autoGenerate) where the PK doubles as a file-system identifier"

key-files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureEntity.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureDao.kt"
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt"

key-decisions:
  - "String UUID PK over Long autoGenerate — PK doubles as filename stem so the on-disk file and the Room row are joined by identity (no surrogate id mapping table)"
  - "JOIN tile-DTO query keyed by EXISTS subquery on progress_pictures (not INNER JOIN) so the LEFT JOIN through completed_workout_exercises + completed_workout_sets still aggregates volume even when a workout has photos but no logged sets"
  - "volumeKgX10 returned in the storage convention (kg*10) and divided by 10.0 in the caller — matches existing CompletedWorkoutDao.getWorkoutSummaries shape; consumers already know the kg*10 convention"
  - "Cover photo selected via correlated MAX(capturedAtMillis) subquery rather than a window function (Room/SQLite-on-Android compatibility; matches D-17-11 'most recent photo' definition)"

patterns-established:
  - "Phase 17 v8 -> v9 follows the 6->7 and 7->8 additive precedent line-for-line: bump version, append Entity::class, append AutoMigration(from, to). No spec class, no manual SQL."
  - "Tile DTOs are data-class projections in the DAO file (analog: WorkoutSummaryDto colocated with CompletedWorkoutDao via WorkoutSummaryDto.kt)"

requirements-completed:
  - D-17-08
  - D-17-02
  - D-17-09
  - D-17-11
  - D-17-12

# Metrics
duration: 3min
completed: 2026-04-28
---

# Phase 17 Plan 01: Schema foundation — progress_pictures table + DAO + AutoMigration(8, 9) Summary

**Room v9 schema introduced for progress pictures: new `progress_pictures` table with CASCADE FK to `completed_workouts`, typed DAO covering per-workout list / gallery tile aggregation / cover-photo selection / deletes, additive AutoMigration(8, 9) that Room's KSP synthesized cleanly with no hand-rolled SQL.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-28T20:23:02Z
- **Completed:** 2026-04-28T20:26:42Z
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- New `ProgressPictureEntity` Room entity with FK CASCADE + Index — mirrors `CompletedWorkoutExerciseEntity` shape and uses a String UUID primary key so the row id and on-disk filename stem stay joined
- New `ProgressPictureDao` with eight methods covering the full lifecycle: bulk + single insert, per-workout list (Flow + suspend variants), live photo count, by-id and by-workout deletes, and the heavy-lifting `observeGalleryTiles` JOIN that produces `ProgressGalleryTileDto` (workoutId, name, startTimeMillis, volumeKgX10, coverRelativePath, photoCount) for the Phase-17 gallery surface
- `AppDatabase` bumped from v8 to v9 with `AutoMigration(8, 9)` registered — purely additive, no spec class needed; Room KSP successfully synthesized the migration and emitted `9.json` with the expected `progress_pictures` table + `index_progress_pictures_workoutId` index
- Both compile gates green: `:shared:compileKotlinIosSimulatorArm64` and `:shared:kspDebugKotlinAndroid` exit 0

## Task Commits

Each task was committed atomically (all `--no-verify` per worktree convention):

1. **Task 1: Create ProgressPictureEntity + ProgressPictureDao** — `7a395cf` (feat)
2. **Task 2: Bump AppDatabase to v9 with AutoMigration(8, 9) + DAO accessor** — `ac32c11` (feat)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureEntity.kt` — Room entity for `progress_pictures` table; FK CASCADE to `completed_workouts(id)` via `workoutId`, `Index("workoutId")`, String UUID PK doubling as filename stem
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureDao.kt` — DAO with insert(s), per-workout Flow + suspend list, live photo count, deletes, and `observeGalleryTiles` JOIN query returning `ProgressGalleryTileDto` (top-level data class colocated in this file)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Three additive edits: `ProgressPictureEntity::class` appended to entities array, `version = 8` -> `version = 9`, `AutoMigration(from = 8, to = 9)` appended, `abstract fun progressPictureDao(): ProgressPictureDao` accessor added

## Decisions Made

- **String UUID PK over `Long autoGenerate`** — D-17-05 requires the PK to double as the on-disk filename stem (`<filesDir>/progress_pics/{uuid}.jpg`). A surrogate `Long` id would force an extra mapping step between row and file. The DAO contract (`deleteById(id: String)`) flows directly from this choice.
- **`EXISTS` subquery, not `INNER JOIN`, in the tile query** — the gallery tile only shows workouts with >= 1 photo, but the JOIN through `completed_workout_exercises` + `completed_workout_sets` is for **volume aggregation**. Using `INNER JOIN progress_pictures` would silently drop tiles for any workout-with-photos that happened to have no logged sets. `WHERE EXISTS (... progress_pictures p WHERE p.workoutId = w.id)` keeps the photo-presence filter independent of the volume LEFT JOIN.
- **Correlated subquery for the cover photo** — `(SELECT relativePath FROM progress_pictures p WHERE p.workoutId = w.id ORDER BY p.capturedAtMillis DESC LIMIT 1)` rather than a window function, because Room/SQLite + bundled SQLite are conservative on window-function support and the correlated form is unambiguous for D-17-11 ("most recently captured photo per workout").
- **`volumeKgX10` returned in the storage convention** — matches existing `CompletedWorkoutDao.getWorkoutSummaries.totalVolume` shape (also kg*10). Consumers convert to kg before formatting per D-17-12.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored missing Room schema JSONs in the worktree's `shared/schemas/` directory**

- **Found during:** Task 1 verification (first run of `./gradlew :shared:compileKotlinIosSimulatorArm64`)
- **Issue:** The worktree's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` directory only contained `8.json`. Room's KSP needed the prior schemas (`6.json`, `7.json`) on disk to validate the existing `AutoMigration(6, 7)` and `AutoMigration(7, 8)` declarations and would have refused to synthesize `AutoMigration(8, 9)` without them. KSP error: `Schema '6.json' required for migration was not found at the schema out folder ... Cannot generate auto migrations.`
- **Fix:** Copied `2.json` through `7.json` from the main repo's `shared/schemas/com.pumpernickel.data.db.AppDatabase/` into the worktree. The `shared/schemas/` directory is gitignored (verified via `git check-ignore` — entry on line 27 of `.gitignore`), so this is purely a local KSP working-set fix and does not pollute the commit.
- **Files modified:** `shared/schemas/com.pumpernickel.data.db.AppDatabase/{2,3,4,5,6,7}.json` (gitignored — not committed)
- **Verification:** `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` exit 0 after the copy; `./gradlew :shared:kspDebugKotlinAndroid -q` exit 0 in Task 2; `9.json` correctly generated by KSP with `tableName "progress_pictures"` and `index_progress_pictures_workoutId` present.
- **Committed in:** N/A (gitignored — does not enter the commit graph)

**2. [Rule 3 - Blocking] Plan-specified Gradle task `:shared:kspKotlinAndroid` is ambiguous on this build — used `:shared:kspDebugKotlinAndroid`**

- **Found during:** Task 2 verification
- **Issue:** Running `./gradlew :shared:kspKotlinAndroid` failed with `Cannot locate tasks that match ':shared:kspKotlinAndroid' as task 'kspKotlinAndroid' is ambiguous in project ':shared'. Candidates are: 'kspDebugAndroidTestKotlinAndroid', 'kspDebugKotlinAndroid', 'kspDebugUnitTestKotlinAndroid', 'kspReleaseKotlinAndroid', 'kspReleaseUnitTestKotlinAndroid'.` The post-v1.5 `android-kmp-library` plugin migration split the KSP task into per-build-variant tasks; the plan's task name was written for the older single-variant shape.
- **Fix:** Ran `:shared:kspDebugKotlinAndroid` — exercises the same KSP pipeline and is the canonical "validate Room schema" task on the new build setup.
- **Files modified:** None (verification command only)
- **Verification:** `./gradlew :shared:kspDebugKotlinAndroid -q` exit 0; `9.json` schema produced; KSP-generated AutoMigration class implicit in successful build.
- **Committed in:** N/A (verification fix)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — environment-level blocking issues in the worktree; neither touches the source code or commit graph)
**Impact on plan:** Zero scope creep. Plan source-code spec executed exactly as written; only the verification environment was reconciled.

## Issues Encountered

- None beyond the two Rule 3 environment fixes above. The acceptance-grep step required `grep -F` (fixed-string mode) for patterns containing brackets or `@` — that's a tooling shell-quoting nuance, not a code issue.

## User Setup Required

None — purely additive Room schema changes; existing v8 installs migrate automatically on next launch. No external service configuration touched.

## Next Phase Readiness

- **Plan 17-02 (PhotoVault expect/actual)** can now reference `ProgressPictureEntity.relativePath` as the canonical on-disk path convention. The String PK = filename stem invariant is enforced at the schema layer.
- **Plan 17-03 (ProgressPictureRepository)** can wrap `ProgressPictureDao` directly and augment `ProgressGalleryTileDto` with PR count (from `xp_ledger`) and goal-day flag (from `NutritionGoalDayPolicy`) into a domain `ProgressGalleryTile`.
- **Plan 17-05 (gallery + viewer VMs)** can `collect` on `observeGalleryTiles()` and `getPicturesForWorkout(id)` line-for-line.
- **Plan 17-06 (Koin DI)** has the `progressPictureDao(): ProgressPictureDao` accessor already declared on `AppDatabase` — wiring `single { get<AppDatabase>().progressPictureDao() }` is a one-liner.
- No blockers. KSP, iOS compile, schema generation all green.

## Self-Check: PASSED

Verified before returning:

- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureEntity.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/data/db/ProgressPictureDao.kt ]` -> FOUND
- `[ -f shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt ]` -> FOUND (modified)
- `[ -f shared/schemas/com.pumpernickel.data.db.AppDatabase/9.json ]` -> FOUND (generated; gitignored)
- `git log --oneline | grep 7a395cf` -> FOUND: `feat(17-01): add ProgressPictureEntity + ProgressPictureDao`
- `git log --oneline | grep ac32c11` -> FOUND: `feat(17-01): bump AppDatabase to v9 with AutoMigration(8, 9)`
- `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` -> exit 0
- `./gradlew :shared:kspDebugKotlinAndroid -q` -> exit 0

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-04-28*

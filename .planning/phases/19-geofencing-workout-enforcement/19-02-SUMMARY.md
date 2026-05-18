---
phase: 19-geofencing-workout-enforcement
plan: "02"
subsystem: shared/data/db
tags: [room, kmp, migration, schema, workout, abandoned]
requirements: [D-19-14]

dependency_graph:
  requires: []
  provides:
    - CompletedWorkoutEntity.abandoned (Room column, defaultValue="0")
    - AppDatabase v11 with AutoMigration from v10
    - WorkoutRepository.saveAbandonedWorkout interface + impl
    - CompletedWorkout.abandoned domain field
  affects:
    - Wave 3 Plan 05 (GamificationEngine.processAbandonedWorkout caller)
    - History UI (abandoned flag visible for future analytics)

tech_stack:
  added: []
  patterns:
    - Room additive AutoMigration with @ColumnInfo(defaultValue) for NOT NULL column migration
    - Entity-domain field symmetry (entity and domain model carry identical field with same default)
    - Explicit named argument for default-value fields in constructor calls (abandoned = false / true)

key_files:
  created:
    - shared/schemas/com.pumpernickel.data.db.AppDatabase/11.json
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/CompletedWorkout.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt

decisions:
  - "Boolean flag (not status enum) for abandoned — simplest additive migration, matches plan CONTEXT.md Claude's Discretion"
  - "Explicit abandoned = false on saveCompletedWorkout constructor call — intent clarity over relying on default"
  - "room.schemaLocation not added to build.gradle.kts — already configured (copyRoomSchemas task exists); KSP generated 11.json without changes to build config"

metrics:
  duration_minutes: 3
  tasks_completed: 3
  files_modified: 4
  files_created: 1
  completed_date: "2026-05-15"
---

# Phase 19 Plan 02: Room Schema v11 — abandoned Flag Summary

**One-liner:** Additive Room AutoMigration v10→v11 adding `abandoned BOOLEAN DEFAULT 0` to `completed_workouts`, with `WorkoutRepository.saveAbandonedWorkout` persistence target for Wave 3 geofence auto-abort path.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add `abandoned` field to entity and domain model | b389b07 | CompletedWorkoutEntity.kt, CompletedWorkout.kt |
| 2 | Bump AppDatabase to v11 with AutoMigration | 513e7b4 | AppDatabase.kt |
| 3 | Add saveAbandonedWorkout to WorkoutRepository | 421f561 | WorkoutRepository.kt |
| — | Track Room schema v11 JSON | fec12e1 | schemas/11.json |

## Implementation Details

### AutoMigration Setup

Room's KSP processor was already configured via `copyRoomSchemas` task — no `room.schemaLocation` argument was needed in `build.gradle.kts`. The build generated `shared/schemas/com.pumpernickel.data.db.AppDatabase/11.json` automatically during `:shared:kspKotlinIosArm64`.

The generated SQL confirms the correct migration DDL:
```sql
ALTER TABLE `completed_workouts` ADD COLUMN `abandoned` INTEGER NOT NULL DEFAULT 0
```

KSP raised no AutoMigration validation warnings beyond the pre-existing `expect/actual` Beta warnings in unrelated iOS platform files (`BiometricGate.ios.kt`, `PhotoCaptureLauncher.ios.kt`, `PhotoVault.ios.kt`). These are pre-existing warnings, not introduced by this plan.

### saveAbandonedWorkout Location in WorkoutRepository.kt

- **Interface declaration:** lines 40-53 (after `saveCompletedWorkout` at line 37)
- **Implementation:** `WorkoutRepositoryImpl` lines 225-259 (immediately after `saveCompletedWorkout` impl ending at line 222)

### Wave 3 Integration Note

Wave 3 (Plan 05) must construct the abandoned `CompletedWorkout` with **only the sets that were actually logged** before the geofence-exit grace period expired — NOT the full template set list. The entity stores whatever the caller provides in `workout.exercises`; there is no filtering in `saveAbandonedWorkout`. The caller (VM in Plan 05) is responsible for passing only completed sets.

## Deviations from Plan

None — plan executed exactly as written. The `autoMigrations` array already existed in `AppDatabase.kt` (contrary to the plan note suggesting it might not), so only `version` bump and new entry were needed.

## Verification Results

| Check | Result |
|-------|--------|
| `:shared:kspKotlinIosArm64` | PASS |
| `:shared:compileKotlinIosArm64` | PASS |
| `:shared:compileKotlinIosX64` | PASS |
| `:shared:compileKotlinIosSimulatorArm64` | PASS |
| `:androidApp:assembleDebug` | PASS |
| `version = 11` in AppDatabase.kt | 1 occurrence |
| `AutoMigration(from = 10, to = 11)` in AppDatabase.kt | 1 occurrence |
| `val abandoned: Boolean = false` in entity | 1 occurrence |
| `@ColumnInfo(defaultValue = "0")` in entity | 1 occurrence |
| `val abandoned: Boolean = false` in domain model | 1 occurrence |
| `saveAbandonedWorkout` in interface + impl | 2 occurrences |
| schema 11.json generated with `defaultValue: "0"` | confirmed |

## Known Stubs

None. All fields are wired to actual persistence — no placeholder values.

## Threat Surface Scan

No new threat surface beyond what is documented in the plan's `<threat_model>`. The `abandoned` column is app-private SQLite data (T-19-02-02 accepted). AutoMigration is compile-time generated (T-19-02-01 mitigated via `defaultValue = "0"`).

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutEntity.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/CompletedWorkout.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — FOUND
- `shared/schemas/com.pumpernickel.data.db.AppDatabase/11.json` — FOUND
- Commit b389b07 — FOUND
- Commit 513e7b4 — FOUND
- Commit 421f561 — FOUND
- Commit fec12e1 — FOUND

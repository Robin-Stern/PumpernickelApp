---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "01"
subsystem: shared-domain-model
tags: [domain-model, nutrition, user-stats, kotlin, commonMain]
dependency_graph:
  requires: []
  provides:
    - "com.pumpernickel.domain.model.UserPhysicalStats"
    - "com.pumpernickel.domain.model.Sex"
    - "com.pumpernickel.domain.model.ActivityLevel"
  affects:
    - "shared/.../data/repository/SettingsRepository.kt (plan 02)"
    - "shared/.../domain/nutrition/TdeeCalculator.kt (plan 02)"
    - "shared/.../presentation/overview/OverviewViewModel.kt (plan 03)"
tech_stack:
  added: []
  patterns:
    - "Plain data class in domain/model package (no imports, no defaults, no serialization annotations)"
key_files:
  created:
    - "shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt"
  modified: []
decisions:
  - "D-16-10: DataStore separate keys — no model-level serialization needed (no @Serializable)"
  - "D-16-11: No defaults on data class constructor — null at repo level means never set"
  - "D-16-12: kg/cm only in model — WeightUnit from workout module not referenced here"
metrics:
  duration: "1m 20s"
  completed: "2026-04-28"
  tasks_completed: 1
  tasks_total: 1
  files_created: 1
  files_modified: 0
---

# Phase 16 Plan 01: UserPhysicalStats Domain Model Summary

**One-liner:** Plain commonMain data class with Sex + ActivityLevel enums for Mifflin-St Jeor TDEE inputs, no defaults (null-is-never-set contract), stored as individual DataStore string keys downstream.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create UserPhysicalStats.kt with Sex + ActivityLevel enums + data class | 315e18d | shared/.../domain/model/UserPhysicalStats.kt (created) |

## What Was Built

Created `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` — a single self-contained file declaring:

- `enum class Sex` with entries `MALE`, `FEMALE` (in that order)
- `enum class ActivityLevel` with five entries: `SEDENTARY`, `LIGHTLY_ACTIVE`, `MODERATELY_ACTIVE`, `VERY_ACTIVE`, `EXTRA_ACTIVE` (matching D-16-05 activity tier order)
- `data class UserPhysicalStats` with fields `weightKg: Double`, `heightCm: Int`, `age: Int`, `sex: Sex`, `activityLevel: ActivityLevel` — **no default values** (D-16-11)

The file has no imports and no annotations, matching the `NutritionGoals.kt` analog structure exactly.

## Decisions Honored

- **D-16-10:** No `@Serializable` annotation — DataStore persists each field as individual string key. The enums will be stored as `.name` strings in downstream plans.
- **D-16-11:** Zero default values on `UserPhysicalStats` constructor. `SettingsRepository` will expose `Flow<UserPhysicalStats?>` where null = user has never entered stats.
- **D-16-12:** Weight unit is `Double weightKg` (kg only). Height is `Int heightCm` (cm only). The app-wide `WeightUnit` enum for workout weights is not referenced in this file.

## Compile Targets Verified

- `./gradlew :shared:compileKotlinIosSimulatorArm64` — PASSED (warnings pre-existing, no new errors)
- `./gradlew :shared:compileDebugKotlinAndroid` — PASSED (no output = clean)

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None — this file is a pure model declaration with no data sourcing or UI rendering.

## Threat Flags

None — this is a plain data class in commonMain with no network, auth, file access, or trust boundary exposure.

## Self-Check: PASSED

- [x] `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/UserPhysicalStats.kt` exists
- [x] Commit `315e18d` exists in git log
- [x] All acceptance criteria grep checks pass (package=1, Sex enum=1, MALE,FEMALE=1, ActivityLevel=1, 5 activity levels, data class=1, weightKg=1, heightCm=1, defaults=0, imports=0)
- [x] iOS simulator arm64 compile: PASSED
- [x] Android debug compile: PASSED

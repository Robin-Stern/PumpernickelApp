---
phase: 15
plan: 03
subsystem: data/repository + di
tags: [gamification, repository, koin, di, seeder, datastore, room]
dependency_graph:
  requires: [15-01 (GamificationDao, entities), 15-02 (domain models: Rank, RankState, AchievementCatalog, AchievementProgress)]
  provides: [GamificationRepository, AchievementStateSeeder, SettingsRepository.retroactiveApplied, gamificationModule, gamificationEngineModule, gamificationUiModule, achievementGalleryModule]
  affects: [plans 04-09 which wire engine/ViewModel bindings into the pre-created feature module files]
tech_stack:
  added: []
  patterns: [interface+Impl in single file (WorkoutRepository convention), Flow<domain> via DAO-backed combine/map, Koin includes() feature-module split, DataStore boolean sentinel flag]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AchievementStateSeeder.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationUiModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AchievementGalleryModule.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
decisions:
  - "rankState flow uses combine(rankStateFlow(), totalXpFlow()) so XP updates from ledger inserts propagate reactively to the UI without requiring rank_state upsert on every award"
  - "GamificationRepository placed in data/repository package alongside WorkoutRepository; AchievementStateSeeder placed in data/db alongside NutritionDataSeeder — matches existing split"
  - "Four feature module files pre-created so plans 04/05/07 (engine), 08 (UI), 09 (gallery) each edit a distinct file — Blocker 1 resolved"
  - "SharedModule.kt edited exactly once: includes() call added at top of module block before AppDatabase singleton"
  - "GamificationDao binding in gamificationModule (not SharedModule) — only source per grep verification"
metrics:
  duration_seconds: 420
  completed_date: "2026-04-22"
  tasks_completed: 3
  files_created: 6
  files_modified: 2
---

# Phase 15 Plan 03: Gamification Repository, Seeder, and Koin Module Architecture Summary

**One-liner:** Thin GamificationRepository (interface+Impl) bridging Room DAO to domain flows, idempotent AchievementStateSeeder seeding 36 locked rows on first launch, retroactiveApplied DataStore sentinel (D-13), and four feature-scoped Koin module files mounted via SharedModule includes() to eliminate wave-level file conflicts (Blocker 1).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add retroactiveApplied DataStore flag to SettingsRepository | 3f1743e | SettingsRepository.kt |
| 2 | Create GamificationRepository (interface+Impl) and AchievementStateSeeder | 4ed33fb | GamificationRepository.kt, AchievementStateSeeder.kt |
| 3 | Create four feature-scoped Koin modules and mount from SharedModule via includes() | 86cc74c | GamificationModule.kt, GamificationEngineModule.kt, GamificationUiModule.kt, AchievementGalleryModule.kt, SharedModule.kt |

## Feature Koin Module Ownership Map

| Module File | Plan That Populates | Bindings Added |
|-------------|---------------------|----------------|
| `GamificationModule.kt` | Plan 03 (this plan — complete) | `GamificationDao`, `GamificationRepository`, `AchievementStateSeeder` |
| `GamificationEngineModule.kt` | Plans 04, 05, 07 | `GamificationEngine` (04), `RetroactiveWalker` + `GamificationStartup` (05), `GoalDayTrigger` (07) |
| `GamificationUiModule.kt` | Plan 08 | `GamificationViewModel` |
| `AchievementGalleryModule.kt` | Plan 09 | `AchievementGalleryViewModel` |

## SharedModule.kt includes() Location

The `includes(...)` call was inserted as the **first statement** inside `val sharedModule = module { ... }`, before the `single<AppDatabase>` binding (line 58 in the original file, now line 58 in the updated file). This ensures feature module bindings are registered before any `get()` call in the same module block resolves them.

```kotlin
val sharedModule = module {
    // Gamification feature modules (plan 03) -- mounted here once
    includes(
        gamificationModule,
        gamificationEngineModule,
        gamificationUiModule,
        achievementGalleryModule
    )
    // ... rest of existing bindings unchanged
}
```

No imports needed — all four feature modules are in the same package (`com.pumpernickel.di`).

## AchievementStateSeeder Row Count

`AchievementStateSeeder.seedIfEmpty()` iterates `AchievementCatalog.all` which contains **36 entries** (12 achievement families × 3 tiers each: BRONZE, SILVER, GOLD). Each seeder call produces exactly one locked `AchievementStateEntity` row per catalog entry via `insertAchievementStateIfMissing` (IGNORE on conflict). Total rows on first launch: **36**.

## GamificationRepository Flow Architecture

- `totalXp: Flow<Long>` — backed by `dao.totalXpFlow()` (reactive `COALESCE(SUM(xpAmount), 0)` query)
- `rankState: Flow<RankState>` — `combine(rankStateFlow(), totalXpFlow())` so XP ledger changes reactively update rank display without requiring a `rank_state` upsert on every award
- `achievements: Flow<List<AchievementProgress>>` — `dao.achievementStateFlow().map { rows -> rows.mapNotNull { it.toDomain() } }` joining DB state with `AchievementCatalog`

## Build Verification

- `./gradlew :shared:compileKotlinMetadata` — BUILD SUCCESSFUL (925ms)
- `./gradlew :shared:kspKotlinIosSimulatorArm64` — BUILD SUCCESSFUL (2s, Room KSP accepted all new types)

## Grep Verification

- `grep -r "GamificationRepository" di/` — binding present in `GamificationModule.kt` ONLY, not in `SharedModule.kt`
- `grep -c "includes(" SharedModule.kt` — exactly **1** match (the four-module include block)
- `SettingsRepository.kt` has `retroactiveApplied: Flow<Boolean>` and `setRetroactiveApplied(applied: Boolean)`
- `AchievementStateSeeder.seedIfEmpty()` iterates `AchievementCatalog.all.forEach`

## Deviations from Plan

None — plan executed exactly as written. All artifacts at the stated paths with the specified contents.

## Known Stubs

None — this plan delivers data-layer infrastructure only. No UI data paths, no placeholder values flowing to rendering.

## Threat Flags

None — all changes are local Room/Koin wiring and DataStore key-value additions. No new network endpoints, auth paths, or file access patterns introduced.

## Self-Check

- [x] `SettingsRepository.kt` has `booleanPreferencesKey("gamification_retroactive_applied")`, `retroactiveApplied: Flow<Boolean>`, `setRetroactiveApplied(Boolean)`
- [x] `GamificationRepository.kt` exists with interface + GamificationRepositoryImpl
- [x] `AchievementStateSeeder.kt` exists with `seedIfEmpty()` iterating `AchievementCatalog.all`
- [x] `GamificationModule.kt` has `val gamificationModule = module` with three bindings
- [x] `GamificationEngineModule.kt` has `val gamificationEngineModule = module` (empty shell)
- [x] `GamificationUiModule.kt` has `val gamificationUiModule = module` (empty shell)
- [x] `AchievementGalleryModule.kt` has `val achievementGalleryModule = module` (empty shell)
- [x] `SharedModule.kt` has exactly one `includes(gamificationModule, ...)` call at top of module block
- [x] Commit 3f1743e exists (Task 1)
- [x] Commit 4ed33fb exists (Task 2)
- [x] Commit 86cc74c exists (Task 3)
- [x] Gradle kspKotlinIosSimulatorArm64: BUILD SUCCESSFUL

## Self-Check: PASSED

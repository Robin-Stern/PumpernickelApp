---
phase: 15
plan: 01
subsystem: data/db
tags: [room, schema-migration, gamification, xp-ledger, achievements, rank]
dependency_graph:
  requires: []
  provides: [XpLedgerEntity, AchievementStateEntity, RankStateEntity, GamificationDao, AppDatabase-v8]
  affects: [AppDatabase.kt, all downstream gamification plans (15-02 through 15-09)]
tech_stack:
  added: []
  patterns: [Room AutoMigration additive, singleton-row pattern, unique composite index for idempotency, OnConflictStrategy.IGNORE for ledger dedup]
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/XpLedgerEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AchievementStateEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/RankStateEntity.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/GamificationDao.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
decisions:
  - "OnConflictStrategy.IGNORE chosen for insertLedgerEntry: returns -1L on dedup so engine can detect skip without querying"
  - "getGoalDayIsoDates() uses substr(eventKey, 9) to extract ISO date without a new column — 'goalday:' prefix is exactly 8 chars, 1-indexed substr(col, 9) gives chars after colon"
  - "getPrLedgerEntriesForWorkout uses LIKE '%:' || :workoutId rather than Kotlin-side filter to avoid full ledger scan"
  - "All three entity types are brand-new in v8 — no @ColumnInfo(defaultValue) required on existing entities since zero columns added to v7 tables"
metrics:
  duration_seconds: 135
  completed_date: "2026-04-22"
  tasks_completed: 3
  files_created: 4
  files_modified: 1
---

# Phase 15 Plan 01: Gamification DB Foundation (Room v8 Schema) Summary

**One-liner:** Room schema v7 to v8 via additive AutoMigration adding three gamification tables (xp_ledger with unique dedup index, achievement_state keyed by stable string ID, rank_state singleton) plus GamificationDao with idempotent ledger insert and reactive flows.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create three gamification Room entities | 06024f6 | XpLedgerEntity.kt, AchievementStateEntity.kt, RankStateEntity.kt |
| 2 | Create GamificationDao | b52a04c | GamificationDao.kt |
| 3 | Bump AppDatabase to v8 | e9a1bc6 | AppDatabase.kt |

## Tables Created

| Table | Entity | Purpose |
|-------|--------|---------|
| `xp_ledger` | `XpLedgerEntity` | Append-only XP award log; unique composite index `(source, eventKey)` enforces dedup at DB level |
| `achievement_state` | `AchievementStateEntity` | Per-achievement unlock record keyed by stable `achievementId` matching AchievementCatalog IDs |
| `rank_state` | `RankStateEntity` | Singleton row (id=1); `isUnranked=true` default per D-11 until first workout saves |

## Event Key Formats

Documented on `XpLedgerEntity.kt` source column comment and in `GamificationDao.kt` query comments:

| Source | Event Key Format |
|--------|-----------------|
| `workout` | `workout:<workoutId>` |
| `pr` | `pr:<exerciseId>:<workoutId>` |
| `nutrition_goal_day` | `goalday:<YYYY-MM-DD>` |
| `streak_workout` | `streak:workout:<threshold>:<runStartEpochDay>` |
| `streak_nutrition` | `streak:nutrition:<threshold>:<runStartEpochDay>` |
| `achievement` | `achievement:<achievementId>` |

## Blocker Queries Added (Blockers 3 + 4)

- **`getGoalDayIsoDates(): List<String>`** — Extracts ISO dates from `goalday:YYYY-MM-DD` event keys via `substr(eventKey, 9)`. Used by downstream nutrition-streak evaluator (Blocker 3). Returns chronological list so retroactive replay walks in order.
- **`getPrLedgerEntries(): List<XpLedgerEntity>`** — All PR-award ledger rows ordered ASC. Engine parses `pr:<exerciseId>:<workoutId>` keys to derive totalPrsSet, distinctExercisesWithPr, bestPrsInSingleSession (Blocker 4).
- **`getPrLedgerEntriesForWorkout(workoutId: Long): List<XpLedgerEntity>`** — Single-workout PR rows via `LIKE '%:' || :workoutId` suffix match. Avoids full ledger scan for post-workout snapshot.

## GamificationDao API Summary

- `insertLedgerEntry(entry): Long` — `@Insert(IGNORE)`, returns `-1L` on dedup
- `findLedgerEntry(src, key): XpLedgerEntity?` — point lookup before streak bonus
- `totalXpFlow(): Flow<Long>` — reactive sum for rank strip
- `allEntriesFlow(): Flow<List<XpLedgerEntity>>` — full ledger feed
- `getGoalDayIsoDates(): List<String>` — Blocker 3
- `getPrLedgerEntries(): List<XpLedgerEntity>` — Blocker 4
- `getPrLedgerEntriesForWorkout(workoutId): List<XpLedgerEntity>` — Blocker 4 (single workout)
- `rankStateFlow(): Flow<RankStateEntity?>` — reactive rank for Overview strip
- `getRankState(): RankStateEntity?` — one-shot rank read
- `upsertRankState(state)` — `@Insert(REPLACE)` singleton upsert
- `achievementStateFlow(): Flow<List<AchievementStateEntity>>` — reactive gallery feed
- `getAchievementState(id): AchievementStateEntity?` — point lookup
- `insertAchievementStateIfMissing(state): Long` — `@Insert(IGNORE)` for seeder
- `updateAchievementProgress(id, progress)` — progress-only update
- `unlockAchievement(id, ts, progress)` — sets `unlockedAtMillis` + progress
- `applyRetroactive(entries, rankState, achievements)` — `@Transaction` batch for D-13 retroactive walker

## Gradle Verification

`./gradlew :shared:compileKotlinMetadata` completed successfully (BUILD SUCCESSFUL in ~7s). Room KSP accepted the v8 schema: new entities valid, DAO queries resolve, AutoMigration(7,8) parses cleanly.

## Deviations from Plan

None — plan executed exactly as written. All five artifacts in `must_haves` are present at the stated paths with the specified contents.

## Known Stubs

None — this plan delivers schema infrastructure only. No UI data paths.

## Threat Flags

None — new entities are local Room tables in shared commonMain. No new network endpoints, auth paths, or trust boundaries introduced.

## Self-Check

- [x] `XpLedgerEntity.kt` exists at correct path
- [x] `AchievementStateEntity.kt` exists at correct path
- [x] `RankStateEntity.kt` exists at correct path
- [x] `GamificationDao.kt` exists at correct path
- [x] `AppDatabase.kt` modified with version=8, AutoMigration(7,8), gamificationDao()
- [x] Commit 06024f6 exists (Task 1 entities)
- [x] Commit b52a04c exists (Task 2 GamificationDao)
- [x] Commit e9a1bc6 exists (Task 3 AppDatabase)
- [x] Gradle compileKotlinMetadata: BUILD SUCCESSFUL

## Self-Check: PASSED

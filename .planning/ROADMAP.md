# Roadmap: PumpernickelApp

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-29)
- ✅ **v1.1 Workout Polish & Firmware Parity** — Phases 5-10 (shipped 2026-03-31)
- ✅ **v1.5 Android Material 3 UI** — Phases 11-14 (shipped 2026-03-31)
- ⚠️ **Post-v1.5 (Untracked)** — Nutrition + theming (merged 2026-04-14 on `feature/workouts` outside GSD)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-29</summary>

- [x] Phase 1: Foundation & Exercise Catalog (3/3 plans) — completed 2026-03-28
- [x] Phase 2: Template Management (3/3 plans) — completed 2026-03-28
- [x] Phase 3: Workout Session (3/3 plans) — completed 2026-03-28
- [x] Phase 4: History & Settings (3/3 plans) — completed 2026-03-29

</details>

<details>
<summary>✅ v1.1 Workout Polish & Firmware Parity (Phases 5-10) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Scroll Wheel Pickers & Auto-Increment (2/2 plans) — completed 2026-03-29
- [x] Phase 6: Personal Best Display (1/1 plan) — completed 2026-03-29
- [x] Phase 7: Post-Workout Recap & Edit (1/1 plan) — completed 2026-03-29
- [x] Phase 8: Mid-Workout Exercise Reorder (2/2 plans) — completed 2026-03-29
- [x] Phase 9: Abandon Guards & Context Menu (1/1 plan) — completed 2026-03-30
- [x] Phase 10: Minimal Set Screen & UI Polish (2/2 plans) — completed 2026-03-30

</details>

<details>
<summary>✅ v1.5 Android Material 3 UI (Phases 11-14) — SHIPPED 2026-03-31</summary>

### Phase 11: Android Shell & Navigation
**Requirements:** ANDROID-01, ANDROID-02
**Goal:** Bootstrap the Android app with Material 3 theme, bottom navigation, navigation graph with type-safe routes, and Koin DI wiring — making the app runnable with placeholder screens.
**Plans:** 1/1 plans complete
- [x] 11-01-PLAN.md — Build config, Koin init, Material 3 theme, NavigationBar with 3 tabs, type-safe routes, placeholder screens

### Phase 12: Exercise Catalog & Templates
**Requirements:** ANDROID-03, ANDROID-04
**Depends on:** Phase 11
**Goal:** Port exercise catalog (search, detail, create) and template management (list, editor, exercise picker) screens to Jetpack Compose with Material 3 components.
**Plans:** 2/2 plans complete
- [x] 12-01-PLAN.md — ExerciseCatalogScreen, ExerciseDetailScreen, CreateExerciseScreen + route fix
- [x] 12-02-PLAN.md — TemplateListScreen, TemplateEditorScreen, ExercisePickerScreen, WorkoutEmptyStateScreen

### Phase 13: Workout Session Core
**Requirements:** ANDROID-05, ANDROID-06, ANDROID-07
**Depends on:** Phase 12
**Goal:** Port the complete workout execution flow — active session with custom drum picker set entry, rest timer, exercise overview bottom sheet, abandon guards, post-workout recap with edit, and finished state.
**Plans:** 4/4 plans complete
- [x] 13-01-PLAN.md — Custom drum/wheel picker composable with snap fling behavior (Wave 1)
- [x] 13-02-PLAN.md — WorkoutSessionScreen Active state: set entry, rest timer, toolbar menu, abandon dialog, nav wiring (Wave 2)
- [x] 13-03-PLAN.md — ExerciseOverviewSheet with Completed/Current/Up Next sections, move reorder, skip (Wave 3)
- [x] 13-04-PLAN.md — Reviewing state (recap with tap-to-edit) and Finished state (summary + Done) (Wave 4)

### Phase 14: History, Settings & Anatomy
**Requirements:** ANDROID-08, ANDROID-09
**Depends on:** Phase 11
**Goal:** Port workout history, settings, and anatomy picker with Canvas-drawn body maps to Jetpack Compose.
**Plans:** 2/2 plans complete
- [x] 14-01-PLAN.md — WorkoutHistoryListScreen, WorkoutHistoryDetailScreen, SettingsSheet with kg/lbs toggle
- [x] 14-02-PLAN.md — AnatomyPickerSheet with Compose Canvas front/back body drawings, shared MuscleRegionPaths, touch region detection

**v1.5 Dependency Graph**

```
Phase 11 ──► Phase 12 ──► Phase 13
Phase 11 ──► Phase 14 (independent of 12/13)
```

</details>

### ⚠️ Post-v1.5 (Untracked) — Merged 2026-04-14

Work landed on `feature/workouts` at `fe297ad` **without GSD planning artifacts**. No PLAN.md / RESEARCH.md files exist. Recorded for traceability only — see `MILESTONES.md` → "Post-v1.5 (Untracked)" for the full scope, and `git log 4d02ce0..fe297ad` for commit-level history.

**Delivered:**
- Nutrition feature (F2): Food/Recipe/Consumption CRUD, 11 use cases, OpenFoodFacts barcode lookup, iOS + Android UI (6 iOS views, Android navigation)
- Dynamic theming: light/dark/system + 8 accent color presets, persisted in DataStore
- Nutrition goals on Overview tab (calorie/protein/fat/carb/sugar)
- Template editor redesign; workout history detail set-count + RIR; PB calculation fix
- Room schema v4 → v7 (AutoMigration 6→7)
- Android: `android-kmp-library` plugin migration, `androidApp` module extracted
- Ktor CIO + kotlinx-datetime added to the stack

**Gap:** no per-phase artifacts, no verification reports, no decision log entries in `.planning/phases/`. Before the next milestone opens, consider a retroactive `/gsd:map-codebase` to re-anchor `.planning/` intel files to the current tree.

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Exercise Catalog | v1.0 | 3/3 | Complete | 2026-03-28 |
| 2. Template Management | v1.0 | 3/3 | Complete | 2026-03-28 |
| 3. Workout Session | v1.0 | 3/3 | Complete | 2026-03-28 |
| 4. History & Settings | v1.0 | 3/3 | Complete | 2026-03-29 |
| 5. Scroll Wheel Pickers & Auto-Increment | v1.1 | 2/2 | Complete | 2026-03-29 |
| 6. Personal Best Display | v1.1 | 1/1 | Complete | 2026-03-29 |
| 7. Post-Workout Recap & Edit | v1.1 | 1/1 | Complete | 2026-03-29 |
| 8. Mid-Workout Exercise Reorder | v1.1 | 2/2 | Complete | 2026-03-29 |
| 9. Abandon Guards & Context Menu | v1.1 | 1/1 | Complete | 2026-03-30 |
| 10. Minimal Set Screen & UI Polish | v1.1 | 2/2 | Complete | 2026-03-30 |
| 11. Android Shell & Navigation | v1.5 | 1/1 | Complete    | 2026-03-31 |
| 12. Exercise Catalog & Templates | v1.5 | 2/2 | Complete    | 2026-03-31 |
| 13. Workout Session Core | v1.5 | 4/4 | Complete    | 2026-03-31 |
| 14. History, Settings & Anatomy | v1.5 | 2/2 | Complete    | 2026-03-31 |
| Post-v1.5 (Untracked) | — | n/a | Merged outside GSD | 2026-04-14 |

### Phase 15: Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks

**Requirements:** GAME-01 (F4 from Lastenheft)
**Depends on:** Workout tracking (Phases 1–10/11–14) + Nutrition (Post-v1.5)
**Goal:** Local-only gamification layer that awards XP for completed workouts, new personal records (PRs), daily nutrition goal-days, and streak thresholds; tracks achievements across 4 categories × 3 tiers (Bronze/Silver/Gold); assigns a CSGO-style 10-rank ladder (Silver → Global Elite) on an exponential ×1.5 threshold curve with permanent ranks (no decay); surfaces rank/XP on the Overview tab (D-18) and achievements under Settings (D-21); fires celebratory modal + haptic on unlocks (D-19). Retroactive walker on first-launch replays existing history idempotently (D-12/D-13). Room schema v7 → v8 via non-destructive AutoMigration.

**Scope notes:**
- XP sources: workout completed (volume-scaled, D-02), new PR (+50, D-03), nutrition goal-day (±10% strict macros, D-04), streak bonuses (D-06), achievement unlocks (D-17).
- Persistence: 3 new Room entities (xp_ledger with unique (source, eventKey) dedupe index, achievement_state singleton-per-tier, rank_state singleton), new GamificationDao, AutoMigration(7, 8).
- Surfaces: Android fully implemented; iOS ships VM contracts + KoinHelper factories, user hand-writes SwiftUI per MEMORY.md.
- Catalog: static code-defined 10–15 achievements × 3 tiers in `AchievementCatalog.kt`.
- Out of scope (deferred per CONTEXT.md): compounding streaks, rank decay, leaderboards, custom achievements, sound effects, progress charts.

**Plans:** 9 plans

**Wave structure** (serialized where plans touch the same file — revision-iter-1 BLOCKER-1 fix):
- Wave 1 (foundation, parallel): 01 (Room schema + entities + DAO), 02 (pure domain — Rank/XpFormula/AchievementCatalog/UnlockEvent/NutritionGoalDayPolicy)
- Wave 2: 03 (repository + seeder + DataStore sentinel + 4 Koin sub-modules via `includes(...)` in SharedModule.kt)
- Wave 3: 04 (GamificationEngine + StreakCalculator + AchievementRules + nutrition-streak + PR-hunter snapshot fill — registers in GamificationEngineModule.kt)
- Wave 4: 05 (retroactive walker + GamificationStartup — registers in GamificationEngineModule.kt)
- Wave 5: 06 (workout-save hook D-20 — edits SharedModule.kt for WorkoutSessionVM binding)
- Wave 6: 07 (OverviewVM rankState + GoalDayTrigger D-22 — edits SharedModule.kt for OverviewVM binding + GamificationEngineModule.kt for GoalDayTrigger)
- Wave 7: 08 (OverviewRankStrip + UnlockModalHost — GamificationUiModule.kt + GamificationUiKoinHelper.kt; wraps Android MainScreen.kt in Box + UnlockModalHost())
- Wave 8: 09 (AchievementGalleryScreen + Settings entry — AchievementGalleryModule.kt + AchievementGalleryKoinHelper.kt; adds composable<AchievementGalleryRoute> to Android MainScreen.kt, serialized after 08 to avoid same-file overlap)

Plans:
- [x] 15-01-PLAN.md — Room schema v7 → v8: XpLedgerEntity + AchievementStateEntity + RankStateEntity + GamificationDao + AutoMigration(7, 8)
- [x] 15-02-PLAN.md — Pure domain: Rank + RankLadder + RankState + XpFormula + GamificationEvent + UnlockEvent + AchievementCatalog + EventKeys
- [x] 15-03-PLAN.md — GamificationRepository + AchievementStateSeeder + SettingsRepository sentinel flag + Koin wiring
- [x] 15-04-PLAN.md — GamificationEngine + StreakCalculator + AchievementRules (with unit tests)
- [x] 15-05-PLAN.md — RetroactiveWalker (D-12/D-13) + GamificationStartup first-launch orchestrator
- [x] 15-06-PLAN.md — Workout-save integration: WorkoutSessionViewModel.saveReviewedWorkout() + WorkoutRepository.saveCompletedWorkout returning Long (D-20)
- [x] 15-07-PLAN.md — OverviewViewModel rankState StateFlow + GoalDayTrigger (D-22)
- [x] 15-08-PLAN.md — GamificationViewModel + Android OverviewRankStrip (D-18) + UnlockModalHost (D-19/D-20) + iOS contract
- [ ] 15-09-PLAN.md — AchievementGalleryViewModel + Android AchievementGalleryScreen + Settings entry + Route + iOS contract (D-21)

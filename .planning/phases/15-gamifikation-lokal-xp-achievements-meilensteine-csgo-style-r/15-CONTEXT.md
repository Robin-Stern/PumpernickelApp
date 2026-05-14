# Phase 15: Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

A local-only gamification layer that:
- Awards **XP** from three sources — completed workouts, new PRs, nutrition goal-days — plus streak bonuses.
- Tracks **achievements/milestones** across four categories (volume, consistency, PR hunter, exercise variety), tiered Bronze/Silver/Gold.
- Assigns a **CSGO-style rank** on a condensed 10-rank ladder with exponential thresholds and no decay.
- Surfaces XP/rank on the **Overview tab** (compact strip) and achievements gallery **under Settings**.
- Fires celebratory **modal + haptic** on rank promotions and achievement unlocks after `saveReviewedWorkout`.
- Walks existing `completed_workouts` history on first launch to compute a **retroactive** starting XP total.

No backend. No social/leaderboard surface. No rank decay. Extends Room schema (v7 → v8) with XP ledger, achievement state, and rank state entities.

</domain>

<decisions>
## Implementation Decisions

### XP Sources & Formulas

- **D-01 (XP sources):** Four XP sources are active — workout completed, new PR hit, nutrition goal-day, and streak bonuses. All four are first-class; the engine must be open to additional sources later without schema reshape.
- **D-02 (Workout XP formula):** Volume-scaled. `workoutXp = floor(sum(actualReps × actualWeightKg) / 100)` across the saved sets of a workout (weight in kg, not kgX10). Reuses totals already computable from `CompletedWorkoutSetEntity`.
- **D-03 (PR XP):** Fixed `+50 XP` per new PR. Reuses the existing volume-weighted PB query (`CompletedWorkoutDao` / `ExercisePbDto`) to decide "is this a new PR" when the workout is saved.
- **D-04 (Nutrition goal-day):** A day counts as a goal-day when **every user-configured macro** in `NutritionGoals` is strict ±10% of the goal. Macros with no user goal (null / 0 / "not set") are skipped from the check. Per-day evaluation runs on the aggregate of `ConsumptionEntryEntity` rows for that date.
- **D-05 (Goal-day XP trigger):** XP for a goal-day is awarded at most once per calendar day. Engine must idempotently check "already awarded for this date" before crediting.
- **D-06 (Streak bonuses — flat on threshold):**
  - Workout streak: +25 XP @ 3 consecutive-day, +100 XP @ 7-day, +500 XP @ 30-day.
  - Nutrition streak: +100 XP @ 7 consecutive goal-days.
  - No multiplicative streaks, no per-tier repeats (each threshold fires once per streak run; streak breaks reset the counter).
- **D-07 (Base-rate tuning:** Claude's discretion. Numbers in D-02 through D-06 are starting anchors; planner should add TODOs to re-tune after play-testing.

### Rank Ladder & Thresholds

- **D-08 (Ladder size — 10 ranks, condensed CSGO set):**
  1. Silver
  2. Silver Elite
  3. Gold Nova I
  4. Gold Nova II
  5. Gold Nova III
  6. Master Guardian
  7. Distinguished Master Guardian (DMG)
  8. Legendary Eagle
  9. Supreme Master First Class (Supreme)
  10. Global Elite
- **D-09 (Threshold curve — exponential ×1.5):** `threshold(n) = base × 1.5^(n-1)` for rank n ≥ 2. Rank 1 (Silver) unlocks on first workout. Base XP anchor is Claude's discretion (planner picks a reasonable starting number, e.g. 500 or 1000; expect tuning).
- **D-10 (Permanent ranks):** Users cannot rank down. No inactivity decay. No XP deduction. Rank is monotonically non-decreasing.
- **D-11 (Unranked until first workout):** Before the first workout finishes (on either a fresh install *or* a retroactive-empty account), the Overview strip shows an "Unranked — complete a workout to unlock Silver" state. After the first save (live or retroactive), the user enters Silver at a minimum.

### Retroactive XP

- **D-12 (One-shot migration walker on first launch of Phase 15 build):** On first launch after upgrade, walk all rows in `completed_workouts` + `completed_workout_exercises` + `completed_workout_sets` + `consumption_entries`. Award:
  - Workout XP (per D-02) for each completed workout.
  - PR XP (per D-03) for each historical PR, processed in chronological order so the "PB at that point in time" is correct.
  - Nutrition goal-day XP (per D-04/D-05) for every past date where the rule held.
  - Streak-threshold bonuses (per D-06) based on the reconstructed streak timeline.
  - Retroactive achievement unlocks per D-14.
- **D-13 (Migration is atomic + idempotent):** The walker runs inside a single Room transaction; on success writes a sentinel flag (e.g., `gamification.retroactive_applied=true` in DataStore) so it never re-runs. If interrupted, it rolls back and re-runs next launch. XP ledger entries use event-dedupe keys (workout id, set id, date) so partial re-runs can't double-count.

### Achievement Catalog

- **D-14 (Four categories, tiered Bronze/Silver/Gold):**
  - **Volume milestones** — lifetime total kg·reps (e.g., Bronze 10 000, Silver 100 000, Gold 1 000 000).
  - **Consistency streaks** — longest workout streak + total workouts completed (e.g., longest-streak Bronze 3d / Silver 7d / Gold 30d; total-workouts Bronze 10 / Silver 50 / Gold 250).
  - **PR hunter** — total PRs set (e.g., Bronze 1 / Silver 10 / Gold 50), and PRs across distinct exercises.
  - **Exercise variety** — distinct exercises used (Bronze 5 / Silver 15 / Gold 30) and muscle-region coverage (using `MuscleRegionPaths`: e.g., trained all front regions / all back regions).
- **D-15 (10–15 achievements total, curated):** Planner proposes the final list during planning; each achievement has 3 tiers, so unlock count will be roughly 30–45 tier unlocks. Exact thresholds are Claude's discretion; pick numbers that align with the XP curve in D-09.
- **D-16 (Code-defined static catalog):** Catalog lives in a Kotlin object/resource in `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/` (e.g., `AchievementCatalog.kt`) and is seeded into Room on first launch (pattern: see `DatabaseSeeder` / `NutritionDataSeeder`). Users cannot author custom achievements.
- **D-17 (Achievement unlocks award XP):** Unlocking a tier awards fixed XP on top of the event that triggered it — `+25 Bronze / +75 Silver / +200 Gold`. Achievements are first-class XP sources (they write to the XP ledger).

### Surface UX

- **D-18 (Overview tab — compact strip at top):** Single horizontal strip above the nutrition-goals section. Renders: rank icon/badge + rank name + XP total + progress-to-next-rank hint (e.g., `Gold Nova II — 2 340 / 3 375 XP`). Strip is a tap target → achievement gallery link is NOT here (see D-21).
- **D-19 (Unlock FX — modal + haptic):** On any unlock (rank promotion or achievement tier), present a full-screen celebratory modal: rank/achievement icon, name, short flavour line, dismiss button. Fires `UISelectionFeedbackGenerator`-level success haptic on iOS (match the existing set-completion haptic pattern) and equivalent on Android.
- **D-20 (Unlock trigger timing — on `saveReviewedWorkout`):** All XP computation, streak-threshold checks, achievement checks, and rank-promotion checks run as a single post-save step after `saveReviewedWorkout()` / the Android equivalent. If multiple unlocks fire from one save, queue the modals (show one, dismiss, show next — not stacked). Nutrition goal-day checks run on a separate trigger (see D-22).
- **D-21 (Achievement gallery — under Settings):** New screen reachable from the existing Settings sheet (the one already mounted on TemplateListScreen's gear icon and iOS equivalent). Grid of tiles per category, locked tiles dimmed, unlocked tiles in full colour with unlock date. No gallery link on the Overview strip.
- **D-22 (Nutrition goal-day evaluation timing):** Deferred to planning, but the heuristic: evaluate on the next app resume after day rollover (midnight local). Planner should confirm — Claude's discretion on exact trigger.

### Claude's Discretion

- Exact XP base-rate anchor for D-09 (e.g., 500 or 1000 XP at rank 2).
- Final numeric thresholds for each achievement tier in D-14 (align with XP curve).
- Rank icon/badge assets — use Material 3 iconography on Android, SF Symbols on iOS, or pick simple shape/colour pairs (silver gradient, gold gradient, etc.).
- Flavour copy on unlock modals.
- Nutrition goal-day evaluation trigger timing (D-22).
- Migration walker ordering/batching (D-12/D-13) for large-history performance.

### Folded Todos

None — no pending todos matched Phase 15 scope at discussion time.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements / Vision
- `.planning/milestones/v1.0-REQUIREMENTS.md` §"Full Lastenheft Scope" — GAME-01 (F4) is the mapped requirement for this phase.
- `.planning/PROJECT.md` §"Out of Scope" — Lists "Gamification (F4)" which is now being pulled in; confirm remaining exclusions (no leaderboards, no backend, no progress charts) still hold.
- `.planning/ROADMAP.md` §"Phase 15" — Phase goal + scope notes (XP sources, persistence, surfaces).
- `.planning/MILESTONES.md` — Historical milestone summaries (v1.0 / v1.1 / v1.5 / Post-v1.5) that document what the existing workout + nutrition stack delivers.

### Workout Integration Points (where XP/PR hooks fire)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — Owns `saveReviewedWorkout()` / `finishForReview()` state transitions. D-20 unlock-check hook lives here.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — Workout persistence boundary; natural place to emit a "workout saved" event for the gamification engine.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutDao.kt` — Existing volume-weighted PB query (used by D-03 PR detection and D-12 retroactive PR replay).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutEntity.kt` / `CompletedWorkoutExerciseEntity.kt` / `CompletedWorkoutSetEntity.kt` — Source tables for D-12 retroactive walker.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/ExercisePbDto.kt` — DTO already used by the PB pipeline; referenced by D-03.

### Nutrition Integration Points (goal-day detection)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt` — `calorie/protein/fat/carb/sugar` goal fields. D-04 requires treating null/0/"not set" as "skip this macro."
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — Where NutritionGoals is loaded from DataStore; gamification engine reads it here.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/NutritionDao.kt` / `ConsumptionEntryEntity.kt` — Daily macro aggregation for D-04 goal-day checks.

### Overview Tab Surface (D-18)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` — Currently exposes macro state for the Overview tab; will gain rank/XP StateFlow(s) for the compact strip.
- `iosApp/iosApp/Views/` (search for Overview tab root — confirm during planning) — iOS Overview tab composition root.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/` (Overview screen — confirm during planning) — Android Overview composable.

### Settings Gallery Entry (D-21)
- `iosApp/iosApp/Views/Settings/SettingsView.swift` — iOS Settings screen; new "Achievements" row links from here.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/` — Android SettingsSheet (currently triggered from TemplateListScreen toolbar gear icon per `14-CONTEXT.md` D-05).

### Shared Assets / Patterns
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegionPaths.kt` — Source of truth for "which muscle regions exist" — used by D-14 exercise-variety/muscle-coverage checks.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/DatabaseSeeder.kt` + `NutritionDataSeeder.kt` — Patterns for seeding catalog data on first launch (D-16).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — Central Room DB; schema bump v7 → v8 will be registered here (plus `AutoMigration(7, 8)`).
- `shared/src/commonMain/kotlin/com/pumpernickel/di/` — Koin modules; gamification repository/engine will wire here.
- `.planning/phases/14-history-settings-anatomy/14-CONTEXT.md` — Confirms Settings sheet location + MuscleRegionPaths shared-module placement.
- `.planning/research/ARCHITECTURE.md` — v1.1 architecture doc; patterns for extending ViewModel state + Room schema migrations still apply.

### Reference Codebase (ESP32 firmware — patterns only, not a dependency)
- `/Users/olli/schenanigans/gymtracker` — Firmware repo referenced across the project for flow/logic patterns. No direct gamification code to port; listed for completeness.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Volume-weighted PB pipeline** (`CompletedWorkoutDao`, `ExercisePbDto`) — powers D-03 PR XP awarding without re-inventing the wheel.
- **Workout save pipeline** (`WorkoutRepository.saveCompletedWorkout` / `WorkoutSessionViewModel.saveReviewedWorkout`) — single hook point for D-20 post-save unlock checks.
- **NutritionGoals + ConsumptionEntry aggregation** — already aggregates daily macros via `NutritionDao`; D-04 goal-day check plugs in here.
- **DatabaseSeeder / NutritionDataSeeder pattern** — established "seed on first launch" pattern that D-16 achievement-catalog seeding should mirror.
- **MuscleRegionPaths in commonMain** — canonical muscle-region enumeration for D-14 exercise-variety checks; avoids hardcoding region lists in the achievement catalog.
- **Haptic on set completion** (iOS + Android) — pattern to reuse for D-19 unlock feedback haptic.
- **Settings sheet (Android) / SettingsView (iOS)** — existing entry point for D-21 achievement gallery link.
- **DataStore SettingsRepository** — pattern for persisting simple key-value flags; the retroactive-applied sentinel (D-13) lives here.

### Established Patterns
- **Single sealed-class state per ViewModel surface** — OverviewViewModel will either expand its state class with rank/XP fields or expose a secondary `gamificationState: StateFlow<GamificationSummary>` that the Overview composes alongside macros.
- **Room schema versioning with `AutoMigration`** — v4 → v7 happened via AutoMigration(6, 7). v7 → v8 for gamification follows the same pattern; non-destructive column/table additions only.
- **Idempotent seed-on-first-launch flag** — pattern used for the exercise catalog and nutrition catalog. Gamification's retroactive-walker sentinel (D-13) uses the same approach, stored in DataStore.
- **KMP Shared VM + Platform UI** — iOS SwiftUI views bind via `@NativeCoroutines` / `asyncSequence`; Android uses `collectAsState()`. Unlock modals must be implemented per-platform but driven by the same shared event/state.
- **Event dedupe via keys** — existing patterns use `(workoutId, setIndex)` / `date` keys; gamification XP ledger should use `(source, eventKey)` to allow idempotent retroactive replay.

### Integration Points
- `WorkoutSessionViewModel.saveReviewedWorkout()` → emit `GamificationEvent.WorkoutCompleted` / `GamificationEvent.PrHit` to the engine (or the engine inspects the just-saved workout).
- `OverviewViewModel` → add a `rankState: StateFlow<RankState>` (current rank, current XP, next-rank threshold, unranked flag) for the D-18 strip.
- `NutritionDao` daily aggregation → `GamificationEngine.checkGoalDay(date)` → queue any awarded XP / streak-threshold / achievement unlocks.
- `AppDatabase` → new entities for XP ledger, achievement state, rank cache; register `AutoMigration(7, 8)`.
- New Koin module for the gamification engine + repository + use cases; wires into `OverviewViewModel`, `WorkoutSessionViewModel`, and the (new) achievement-gallery ViewModel.
- New settings navigation entry on both platforms → `AchievementGalleryScreen` (Android) / `AchievementGalleryView` (iOS).

</code_context>

<specifics>
## Specific Ideas

- **"CSGO vibe" tone** — unlock modal copy should lean into the rank metaphor (e.g., "Promoted to Gold Nova II" rather than "You reached rank 4"). Flavour copy is Claude's discretion but should feel game-like, not corporate.
- **Strict-range nutrition check** — user emphasised **strict ±10%** despite sugar/protein being conceptually one-sided. Implementation should still respect this: over-eating protein or under-eating sugar by >10% fails the day. If this feels punitive during play-testing, revisit in a future phase (noted as deferred idea below).
- **"Unranked until first workout" display** — show a literal "Unranked — complete a workout to unlock Silver" state on the Overview strip, not a blank/empty area. Retroactive walker typically resolves this immediately on upgrade, but new installs will see it.
- **Rank 1 (Silver) unlocks at XP = 0 on first workout save** — rank 1 is not behind a threshold; it's unlocked by the act of completing any workout. Ranks 2–10 use the exponential curve.

</specifics>

<deferred>
## Deferred Ideas

- **One-sided nutrition checks (hit-or-exceed protein, under-threshold sugar)** — user chose strict ±10% uniform for now; revisit if play-testing shows it feels punitive.
- **Multiplicative / compounding streaks (XP ×1.1 per streak day)** — explicitly rejected for this phase in favour of flat thresholds.
- **Rank decay on inactivity (CSGO-authentic)** — rejected for this phase (permanent ranks only); could be revisited as an opt-in "hard mode" setting.
- **User-defined / custom achievements** — static catalog only for now; user-authoring UI would be its own phase.
- **Leaderboards / social sharing / backend sync of rank** — out of scope; requires backend (explicitly excluded per PROJECT.md).
- **Progress charts (XP over time, rank progression graph)** — project-wide deferral (no charting lib); not reintroduced here.
- **Sound on unlock** — discussion settled on haptic + modal; adding sound is an easy future enhancement but out of current scope.
- **Per-exercise-type XP multipliers** — not asked for; flat volume formula for now.
- **Daily XP cap / anti-grinding protection** — not added; if play-testing shows degenerate farming (e.g., 50-rep bodyweight sets), revisit.

### Reviewed Todos (not folded)

None — no pending todos were reviewed during this phase.

</deferred>

---

*Phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r*
*Context gathered: 2026-04-22*

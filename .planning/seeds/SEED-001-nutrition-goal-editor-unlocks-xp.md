---
id: SEED-001
status: dormant
planted: 2026-04-23
planted_during: Phase 15 UAT (gamification)
trigger_when: A Nutrition Goals configuration UI is being planned, OR a broader nutrition-polish milestone opens.
scope: Small
---

# SEED-001: Nutrition goal editor unlocks the nutrition XP path

## Why This Matters

Phase 15 shipped the full nutrition-XP plumbing — `NutritionGoalDayPolicy` evaluates whether a day's logged macros fall within ±10% of the user's `NutritionGoals` (D-04), `GoalDayTrigger` fires XP on goal-met days, and `GamificationEngine` / `OverviewViewModel` consume it. But today `NutritionGoals` is only ever the hard-coded defaults (2500 kcal / 150g protein / 80g fat / 300g carbs / 50g sugar) persisted in `SettingsRepository` — there's no editor surface. Real users therefore earn nutrition XP only against numbers they never chose, which is effectively decorative.

Closing this:
- Makes the nutrition tab a meaningful, intentional XP source instead of a near-dead-code path.
- Finishes F4 / D-04 of the Lastenheft for real users, not just the engine.
- Unlocks the nutrition-streak achievement family (already defined in `AchievementCatalog`) — currently unachievable in practice because the thresholds depend on user-intended goal-days, not arbitrary defaults.

Tech-debt framing: the seed is not "add nutrition goals" (that already exists as a domain model) — it's "let the user set them." The rest of the chain is live and tested.

## When to Surface

**Trigger:** When a Nutrition Goals configuration UI (Settings → edit daily macro targets) is being planned, OR when a broader nutrition-polish milestone opens.

This seed should be presented during `/gsd:new-milestone` when the milestone scope matches any of these conditions:
- Scope mentions nutrition, macros, daily goals, or settings editor work
- Scope mentions gamification polish, XP sources, or achievement reachability
- Post-v1.5 nutrition feature gets a follow-up milestone
- Any work on `SettingsRepository` editor surfaces or a Settings tab redesign

## Scope Estimate

**Small** — probably a few hours of UI work plus a verification pass. Breakdown:
- Shared VM: expose current `NutritionGoals` + update method (`SettingsRepository` already has the persistence).
- iOS: one form view (5 rows, number steppers/pickers, kg ↔ g consistent with existing weight-unit convention) hung off the existing Settings view. User hand-writes SwiftUI per MEMORY.md.
- Android: one Compose screen or bottom sheet, Material 3 form controls.
- Verification: once a user sets custom goals, open an iOS simulator, log macros that meet the ±10% band, confirm the `xp_ledger` row + nutrition-streak achievement progress. Do the same with macros that just miss the band to confirm no phantom XP.

No new domain code, no schema changes, no new XP sources.

## Breadcrumbs

Related code and decisions found in the current codebase:

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt` — domain model (calorie/protein/fat/carb/sugar). Defaults are here.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — already persists `NutritionGoals` via DataStore (string-encoded ints). Read + write both exist — the UI is the only missing piece.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt` — ±10% goal-met evaluator.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GoalDayTrigger.kt` — fires the XP event when a day meets the policy.
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicyTest.kt` — existing coverage for the policy.
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt` — already surfaces goals on the Overview tab (displays, doesn't edit).
- `iosApp/iosApp/Views/Overview/OverviewView.swift` — current iOS display surface.
- `.planning/phases/15-.../15-07-SUMMARY.md` — details on how `GoalDayTrigger` was integrated.
- Decision D-04 (captured in PROJECT.md) — ±10% strict-macros rule for goal-met days.

## Notes

- Source: Phase 15 UAT on 2026-04-23. User explicitly reported: "doesn't get XP for achieving nutrition goals because the user can't set a daily goal for nutrition yet."
- Related debug session (unblocked): `.planning/debug/fresh-install-rank-silver1.md` — same UAT session.
- Related inserted phase: 15.1 — Ranks & Achievements Browser. The achievement browser from 15.1 will make the currently-unreachable nutrition-streak achievements visible to the user, which will make their unachievability even more obvious — the nutrition-goal editor is arguably blocking on 15.1's surface area landing.
- If this seed is picked up as part of a nutrition-polish milestone, consider also: (a) a one-time migration for users whose default goals are stale, (b) a visible "goals set" empty state on Overview before the user configures them.

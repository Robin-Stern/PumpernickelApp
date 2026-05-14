# Phase 15: Gamifikation lokal — XP, Achievements, Meilensteine, CSGO-Style Ranks - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
**Areas discussed:** XP sources & formulas, Rank ladder & thresholds, Achievement catalog, Surface UX (feedback + Overview integration)

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| XP sources & formulas | What events award XP and how much (fixed vs. scaled). | ✓ |
| Rank ladder & thresholds | CSGO 18 ranks vs. condensed, curve shape, rank-down policy. | ✓ |
| Achievement catalog | Categories, tiering, scope, static vs. user-authored. | ✓ |
| Surface UX — feedback & Overview integration | Unlock feedback style, Overview surface, gallery location. | ✓ |

**User's choice:** All four areas selected.

---

## Area 1 — XP Sources & Formulas

### Q1.1 — Which XP sources should count?

| Option | Description | Selected |
|--------|-------------|----------|
| Workout completed | Baseline XP per completed workout. | ✓ |
| New PR hit | Bonus XP on new volume-weighted PB. | ✓ |
| Nutrition goal day | XP for macro-in-range days. | ✓ |
| Streak / consistency bonus | Extra XP for consecutive days. | ✓ |

**User's choice:** All four (workout, PR, nutrition goal day, streaks).

### Q1.2 — Workout XP formula

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed amount per workout | E.g. 100 XP flat, regardless of volume. | |
| Scaled by total volume (kg×reps) | Longer/heavier workouts earn more. | ✓ |
| Scaled by exercise count + completion % | XP = (completed / total) × base. | |

**User's choice:** Scaled by total volume (kg × reps).

### Q1.3 — Nutrition goal-day definition

| Option | Description | Selected |
|--------|-------------|----------|
| All 5 macros in range | Calorie + protein + fat + carb + sugar all ±10%. | |
| Calorie ± protein in range | Two key levers only. | |
| Calorie only | Most permissive. | |
| **(Other — user free text)** | All macros in range of the goal — but only the macros the user has set a goal for (e.g., if user has no carb goal, carbs don't count). | ✓ |

**User's choice (free text):** "all macros in range of the goal. but the user can define his goal in the nutrition tab. lets say the user wants to reach 3000kcal and 200g protein and 50g sugar but has no carb goals. then carbs dont count ofc"
**Notes:** Distilled into D-04 — treat null/0/"not set" `NutritionGoals` fields as "skip from check."

### Q1.4 — Repeat-event bonuses

| Option | Description | Selected |
|--------|-------------|----------|
| Keep it flat for now | No streak multipliers. | |
| Flat streak bonus on threshold | Named-threshold bonuses only. | ✓ |
| Multiplicative streaks | Daily XP scales with streak length. | |

**User's choice:** Flat streak bonus on threshold.

### Q1.5 — Volume-scaled base rate

| Option | Description | Selected |
|--------|-------------|----------|
| 1 XP per 100 kg·reps | Clean scaling; ~100–200 XP per typical session. | ✓ |
| 10 XP base + 1 per 200 kg·reps | Floor for bodyweight sessions + bonus for lifting. | |
| You decide | Claude picks during planning. | |

**User's choice:** 1 XP per 100 kg·reps.

### Q1.6 — PR XP amount

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed +50 XP per PR | Flat and predictable. | ✓ |
| Scaled by PR delta | Bigger jumps feel bigger. | |
| You decide | Claude picks during planning. | |

**User's choice:** Fixed +50 XP per PR.

### Q1.7 — Streak thresholds

| Option | Description | Selected |
|--------|-------------|----------|
| Workout: 3-day streak (+25) | Short-term hook. | ✓ |
| Workout: 7-day streak (+100) | Full week. | ✓ |
| Workout: 30-day streak (+500) | Long-term jackpot. | ✓ |
| Nutrition: 7-day goal-day streak (+100) | Mirrors workout side. | ✓ |

**User's choice:** All four streak thresholds.

### Q1.8 — Macro-range semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Strict ±10% of goal | Over- and under-eating both count as miss. | ✓ |
| Hit or exceed (one-sided) | Protein: reach/exceed; sugar: stay under. | |
| Simple ±15% band | Uniform across all macros. | |

**User's choice:** Strict ±10% of goal.
**Notes:** Captured as a potential re-visit if play-testing shows it's too punitive (see Deferred Ideas in CONTEXT.md).

---

## Area 2 — Rank Ladder & Thresholds

### Q2.1 — Ladder size

| Option | Description | Selected |
|--------|-------------|----------|
| Full CSGO 18-rank ladder | Authentic CSGO feel. | |
| Condensed 10-rank ladder | Captures vibe, fewer thresholds. | ✓ |
| Minimal 6-rank ladder | Fastest to ship. | |

**User's choice:** Condensed 10-rank ladder.

### Q2.2 — Threshold curve

| Option | Description | Selected |
|--------|-------------|----------|
| Exponential (×1.5 per rank) | CSGO-like grind curve. | ✓ |
| Linear (fixed increment) | Predictable but flat feel. | |
| Hand-tuned per rank | Most flexibility. | |

**User's choice:** Exponential ×1.5 per rank.

### Q2.3 — Rank down?

| Option | Description | Selected |
|--------|-------------|----------|
| No — rank is permanent | Monotonically non-decreasing. | ✓ |
| Yes — CSGO-style decay on inactivity | Loses rank after N days idle. | |
| Rank-down only from losing XP | Automatic from XP deduction. | |

**User's choice:** No — permanent.

### Q2.4 — Starting state

| Option | Description | Selected |
|--------|-------------|----------|
| Start at Silver I with 0 XP | Immediate rank on install. | |
| Unranked until first workout | Onboarding moment. | ✓ |

**User's choice:** Unranked until first workout.

### Q2.5 — Retroactive XP

| Option | Description | Selected |
|--------|-------------|----------|
| Apply retroactively on first launch | Walk existing tables, award XP + unlocks. | ✓ |
| Start fresh from phase-15 ship | Ignore history. | |
| User choice on first launch | Opt-in modal. | |

**User's choice:** Apply retroactively on first launch.

### Q2.6 — Curve anchor

| Option | Description | Selected |
|--------|-------------|----------|
| Rank 2 @ 500 XP | ~3 months to Global Elite. | |
| Rank 2 @ 1000 XP | ~6 months to Global Elite. | |
| You decide | Claude picks during planning. | ✓ |

**User's choice:** You decide.

---

## Area 3 — Achievement Catalog

### Q3.1 — Categories

| Option | Description | Selected |
|--------|-------------|----------|
| Volume milestones | Lifetime kg·reps. | ✓ |
| Consistency streaks | Streaks + total workouts. | ✓ |
| PR hunter | PR counts across exercises. | ✓ |
| Exercise variety | Distinct exercises + muscle coverage. | ✓ |

**User's choice:** All four categories.

### Q3.2 — Tier style

| Option | Description | Selected |
|--------|-------------|----------|
| Tiered (Bronze / Silver / Gold) | 3 thresholds per achievement. | ✓ |
| Single-level with individual thresholds | Each a distinct milestone. | |
| Hybrid | Tiered for structural, single for specials. | |

**User's choice:** Tiered Bronze/Silver/Gold.

### Q3.3 — Catalog size

| Option | Description | Selected |
|--------|-------------|----------|
| 10–15 (focused, shippable) | Curated set. | ✓ |
| 20–30 (richer collection) | More coverage. | |
| You decide | Claude proposes. | |

**User's choice:** 10–15 achievements.

### Q3.4 — Catalog source

| Option | Description | Selected |
|--------|-------------|----------|
| Code-defined static catalog | Kotlin object/resource, seeded to Room. | ✓ |
| Room-seeded + user-creatable | With CRUD UI. | |

**User's choice:** Code-defined static catalog.

### Q3.5 — Achievement XP

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — fixed XP per tier | +25 / +75 / +200. | ✓ |
| No — trophy-only | Achievements don't move XP bar. | |

**User's choice:** Fixed XP per tier (+25 Bronze / +75 Silver / +200 Gold).

---

## Area 4 — Surface UX (feedback & Overview integration)

### Q4.1 — Overview tab surface

| Option | Description | Selected |
|--------|-------------|----------|
| Hero card at top with rank icon + progress-to-next | High visibility card. | |
| Compact strip (rank badge + XP number) | One-line strip at top. | ✓ |
| Bottom of Overview, below macros | Ambient placement. | |

**User's choice:** Compact strip at top.

### Q4.2 — Unlock feedback style

| Option | Description | Selected |
|--------|-------------|----------|
| Celebratory modal + haptic | Full-screen modal + success haptic. | ✓ |
| Toast/snackbar only | Non-interrupting. | |
| Haptic only + badge on tab | Minimal interruption. | |

**User's choice:** Celebratory modal + haptic.

### Q4.3 — Trigger timing

| Option | Description | Selected |
|--------|-------------|----------|
| On saveReviewedWorkout (after recap save) | Single post-save hook. | ✓ |
| On set completion (inline during workout) | Live mid-workout banners. | |
| Both — PR live, rank/XP on save | Hybrid. | |

**User's choice:** On `saveReviewedWorkout`.

### Q4.4 — Gallery location

| Option | Description | Selected |
|--------|-------------|----------|
| New Achievements screen pushed from Overview | Fresh surface. | |
| Under Settings | Reuses existing settings sheet. | ✓ |
| Bottom sheet from Overview | ModalBottomSheet. | |

**User's choice:** Under Settings.

---

## Claude's Discretion

Areas where Claude has flexibility during planning (captured in CONTEXT.md §"Claude's Discretion"):

- Exact XP base-rate anchor for the rank curve (Rank 2 threshold).
- Final numeric thresholds for each achievement tier.
- Rank icon/badge assets and unlock flavour copy.
- Nutrition goal-day evaluation trigger timing.
- Migration walker ordering/batching for large-history performance.

## Deferred Ideas

Ideas raised or considered during discussion, noted for future phases (see CONTEXT.md §"Deferred Ideas"):

- One-sided nutrition checks (hit-or-exceed protein, under-threshold sugar).
- Multiplicative / compounding streaks.
- Rank decay on inactivity.
- User-defined / custom achievements.
- Leaderboards / social sharing / backend sync.
- Progress charts (XP over time).
- Sound on unlock.
- Per-exercise-type XP multipliers.
- Daily XP cap / anti-grinding protection.

# Phase 7: Post-Workout Recap & Edit - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-29
**Phase:** 07-post-workout-recap-edit
**Areas discussed:** Recap trigger & flow, Recap layout, Set detail level, Edit mechanism
**Mode:** --auto (all decisions auto-selected using recommended defaults)

---

## Recap Trigger & Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Direct to Reviewing | "Finish Workout" goes directly to Reviewing state (no intermediate screen) | ✓ |
| Intermediate Done screen | Show "DONE!" screen first (matching firmware WorkoutFinishState), then navigate to recap | |

**User's choice:** [auto] Direct to Reviewing (recommended default)
**Notes:** Firmware has a two-step flow (WorkoutFinishState → WorkoutRecapState) because the hardware UI benefits from a confirmation screen. On mobile, going directly to recap is more natural — the user tapped "Finish Workout" intentionally.

---

## Recap Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Scrollable list | Vertical list with exercise headers and per-set detail rows | ✓ |
| Card-based | Cards per exercise with collapsed/expandable sets | |

**User's choice:** [auto] Scrollable list (recommended default)
**Notes:** Matches firmware WorkoutRecapState's nested list pattern. Cards would add visual complexity without benefit for this use case. The list pattern is already established in the app (exercise overview, workout history detail).

---

## Set Detail Level

| Option | Description | Selected |
|--------|-------------|----------|
| Full per-set detail | "Set N: X reps @ Y.Y kg" per row, tappable for editing | ✓ |
| Compact summary | Exercise-level summary (set count + best weight, like firmware) | |

**User's choice:** [auto] Full per-set detail (recommended default)
**Notes:** FLOW-02 requires individual set editing via tap. Compact summary would not support this — each set must be its own tappable row.

---

## Edit Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Wheel picker sheet | Reuse existing editSetSheet pattern from WorkoutSessionView | ✓ |
| Inline editing | Edit reps/weight directly in the list row | |

**User's choice:** [auto] Wheel picker sheet (recommended default)
**Notes:** Existing editSetSheet in WorkoutSessionView.swift provides a proven, consistent pattern. Inline editing would require custom text field handling and wouldn't match the established wheel picker interaction.

---

## Claude's Discretion

- Visual styling of recap screen (follow existing workout screen patterns)
- Workout duration/stats in recap header
- Back navigation behavior from recap

## Deferred Ideas

None — discussion stayed within phase scope

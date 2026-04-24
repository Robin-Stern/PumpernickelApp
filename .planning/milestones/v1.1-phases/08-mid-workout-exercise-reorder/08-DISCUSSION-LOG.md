# Phase 8: Mid-Workout Exercise Reorder - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-29
**Phase:** 08-mid-workout-exercise-reorder
**Areas discussed:** Reorder mechanism, Reorder UI gesture, Skip exercise behavior, Crash recovery for order
**Mode:** Auto (all areas auto-selected, recommended defaults chosen)

---

## Reorder Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory list reorder | Swap items directly in exercises list, simpler than indirection | ✓ |
| Firmware-style indirection array | exerciseOrder[] maps logical→physical indices, more complex | |

**User's choice:** [auto] In-memory list reorder (recommended default)
**Notes:** Firmware uses indirection because C arrays can't resize. Kotlin lists support direct reorder. Simpler approach, same result.

---

## Reorder UI Gesture

| Option | Description | Selected |
|--------|-------------|----------|
| SwiftUI .onMove + EditButton | Matches existing TemplateEditorView pattern, native iOS feel | ✓ |
| Custom drag gesture | More control but reinvents the wheel | |
| Dedicated reorder screen | Separate full-screen view for reordering | |

**User's choice:** [auto] SwiftUI .onMove + EditButton (recommended default)
**Notes:** Proven pattern already in codebase (TemplateEditorView line 57). Consistent UX across template editing and workout reordering.

---

## Skip Exercise Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Skip to next, 0 sets in history | Advance cursor, skipped exercise excluded from saved history (no completed sets) | ✓ |
| Skip to next, mark as skipped | Add explicit "skipped" status to exercise | |
| Remove from workout | Remove skipped exercise entirely from session | |

**User's choice:** [auto] Skip to next, 0 sets in history (recommended default)
**Notes:** Matches FLOW-07 UAT criteria. saveReviewedWorkout() already filters out exercises with 0 completed sets, so skipped exercises are naturally excluded from history.

---

## Crash Recovery for Exercise Order

| Option | Description | Selected |
|--------|-------------|----------|
| Persist order to Room (new column) | Add exerciseOrder column to active_sessions, Room migration 3→4 | ✓ |
| In-memory only | No persistence, lose order on crash | |

**User's choice:** [auto] Persist order to Room (recommended default)
**Notes:** FLOW-04 requires crash recovery integrity after reorder. Migration 3→4 is additive (new column with default) so AutoMigration may apply.

---

## Claude's Discretion

- Visual styling of reorder screen
- Sheet vs inline presentation for exercise list
- Animation details for drag-and-drop
- Whether skip needs confirmation (firmware: no confirmation)

## Deferred Ideas

None — discussion stayed within phase scope

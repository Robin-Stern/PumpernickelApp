# Phase 2: Template Management - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 02-template-management
**Areas discussed:** Workout tab layout, Exercise picker flow, Target configuration, Delete confirmation
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Workout Tab Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Template list as home screen | Templates become the primary content on the Workout tab, replacing the empty state | ✓ |
| Keep exercises as home, add templates section | Exercise catalog stays on Workout tab with a templates section added | |
| Separate templates screen via navigation | Templates on a dedicated screen, Workout tab stays as-is | |

**User's choice:** [auto] Template list as home screen (recommended default)
**Notes:** Natural progression — Phase 1 used empty state with "Browse Exercises" CTA because templates didn't exist yet. Templates are the real starting point for workouts.

---

## Exercise Picker Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse exercise catalog in picker mode | Same ExerciseCatalogView with anatomy SVG filter, but tap-to-select instead of tap-to-detail | ✓ |
| Multi-select exercise picker | New picker with checkboxes for batch-adding exercises | |
| Search-only simplified picker | Stripped-down picker with just search, no filtering | |

**User's choice:** [auto] Reuse exercise catalog in picker mode (recommended default)
**Notes:** Maximizes code reuse from Phase 1. Anatomy SVG picker is a distinctive feature (D-12 from Phase 1) that should appear in exercise selection contexts.

---

## Target Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Inline editable fields in template editor | Each exercise row shows sets/reps/weight/rest as editable fields directly in the template view | ✓ |
| Separate config screen per exercise | Tapping an exercise opens a dedicated configuration screen | |
| Bottom sheet per exercise | Tapping an exercise shows a half-sheet with target fields | |

**User's choice:** [auto] Inline editable fields in template editor (recommended default)
**Notes:** Fewer navigation steps. Matches gymtracker's compact approach where all targets are visible at once. Weight stored as kg×10 (gymtracker pattern).

---

## Delete Confirmation

| Option | Description | Selected |
|--------|-------------|----------|
| Standard iOS destructive alert | System alert dialog with "Delete" as destructive action | ✓ |
| Swipe-to-delete with undo | Swipe gesture deletes, shows undo snackbar | |
| Confirmation sheet with details | Bottom sheet showing template name and exercise count before confirming | |

**User's choice:** [auto] Standard iOS destructive alert (recommended default)
**Notes:** Familiar iOS pattern, minimal implementation effort. TMPL-04 just requires "with confirmation."

---

## Claude's Discretion

- Database migration strategy
- ViewModel state management approach (follow Phase 1 patterns)
- Navigation patterns between template screens
- Template name validation rules
- Sheet vs full-screen for template creation/editing

## Deferred Ideas

None — all decisions stayed within phase scope.

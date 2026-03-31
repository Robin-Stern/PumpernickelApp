# Phase 12: Exercise Catalog & Templates - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 12-exercise-catalog-templates
**Areas discussed:** Search UX, List items, Filter chips, Template actions, Empty state
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Search UX

| Option | Description | Selected |
|--------|-------------|----------|
| Material 3 SearchBar in TopAppBar | Always-visible search, most Material-native | ✓ |
| Search icon → expanding search | Tap to reveal, saves space | |
| DockedSearchBar | Persistent below app bar | |

**User's choice:** [auto] Material 3 SearchBar in TopAppBar
**Notes:** Matches iOS `.searchable(displayMode: .always)` behavior.

---

## List Items

| Option | Description | Selected |
|--------|-------------|----------|
| Material 3 ListItem composable | Standard list with headline/supporting/leading/trailing slots | ✓ |
| Card-based layout | Each item in an elevated card | |

**User's choice:** [auto] Material 3 ListItem
**Notes:** Clean, standard Material pattern. Cards would add visual noise for dense lists.

---

## Filter Chips

| Option | Description | Selected |
|--------|-------------|----------|
| FilterChip in LazyRow | Horizontal scroll, single select with "All" default | ✓ |
| Dropdown menu | Single select via dropdown | |

**User's choice:** [auto] FilterChip in LazyRow
**Notes:** Direct Material 3 equivalent of iOS horizontal chip row.

---

## Template Actions

| Option | Description | Selected |
|--------|-------------|----------|
| SwipeToDismiss + FAB + tap to edit | Standard Material list management pattern | ✓ |
| Long-press context menu | Context menu for all actions | |
| Action buttons on each row | Visible edit/delete buttons | |

**User's choice:** [auto] SwipeToDismiss + FAB + tap to edit
**Notes:** Matches iOS swipe-to-delete + navigation pattern.

---

## Empty State

| Option | Description | Selected |
|--------|-------------|----------|
| Centered icon + text + CTA button | Standard Material empty state | ✓ |
| Illustrated empty state | Custom illustration | |

**User's choice:** [auto] Centered icon + text + CTA
**Notes:** Matches iOS WorkoutEmptyStateView pattern.

---

## Claude's Discretion

- Card vs ListItem per screen
- Typography scale choices
- Equipment type icons
- Screen transition animations
- Drag handle styling

## Deferred Ideas

None

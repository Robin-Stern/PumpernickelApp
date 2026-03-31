# Phase 9: Abandon Guards & Context Menu - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-30
**Phase:** 09-abandon-guards-context-menu
**Areas discussed:** Abandon trigger, Abandon options, Context menu access, Context menu items
**Mode:** --auto (all decisions auto-selected using recommended defaults)

---

## Abandon Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| Close/X button in nav bar | Explicit xmark button in leading toolbar position | ✓ |
| Swipe-back gesture intercept | Override interactiveDismissDisabled to catch swipe | |
| Repurpose system back button | Show back button and intercept navigation | |

**User's choice:** [auto] Close/X button in navigation bar (recommended default)
**Notes:** Navigation back button already hidden. Explicit X gives clear exit intent. Firmware uses BACK button — closest iOS equivalent is an explicit close button.

---

## Abandon Options

| Option | Description | Selected |
|--------|-------------|----------|
| Three options (Save/Discard/Cancel) | .confirmationDialog with Save & Exit, Discard (.destructive), Cancel | ✓ |
| Two options (Save/Discard) | Firmware pattern — BACK as implicit cancel | |
| Two options (Discard/Cancel) | No save option, simpler dialog | |

**User's choice:** [auto] Three options per FLOW-05 UAT criteria (recommended default)
**Notes:** FLOW-05 explicitly requires 3 options. iOS .confirmationDialog naturally supports this pattern with automatic cancel button on dismiss.

---

## Context Menu Access

| Option | Description | Selected |
|--------|-------------|----------|
| Toolbar Menu (ellipsis.circle) | Standard iOS action menu in trailing nav bar | ✓ |
| Long-press .contextMenu | Native context menu on exercise area | |
| Dedicated action sheet | Button that opens .actionSheet | |

**User's choice:** [auto] Toolbar menu button with ellipsis.circle (recommended default)
**Notes:** Most discoverable iOS pattern. .contextMenu requires long-press which is less obvious. Existing list.bullet overview button folds into this menu.

---

## Context Menu Items

| Option | Description | Selected |
|--------|-------------|----------|
| Skip + Overview + Finish | Three actions covering FLOW-06 requirements | ✓ |
| Skip + Reorder + Finish | Firmware-matching items (dedicated reorder action) | |
| Skip + Overview + Reorder + Finish | All possible actions | |

**User's choice:** [auto] Skip Exercise, Exercise Overview, Finish Workout (recommended default)
**Notes:** Overview sheet already contains drag reorder, so a separate "Reorder" item is redundant. Finish Workout provides early exit to recap screen via enterReview().

---

## Claude's Discretion

- Destructive styling for "Discard" button in confirmation dialog
- Whether "Finish Workout" needs its own confirmation before entering recap
- Animation and transition details for dialog presentation

## Deferred Ideas

None — discussion stayed within phase scope

# Phase 10: Minimal Set Screen & UI Polish - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-30
**Phase:** 10-minimal-set-screen-ui-polish
**Areas discussed:** Minimal set screen behavior, Haptic feedback pattern, Accessibility coverage, Visual consistency targets
**Mode:** Auto (all decisions auto-selected from recommended defaults)

---

## Minimal Set Screen Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Firmware-style intermediate screen | Show "SET N" + exercise name + "Tap when done", user taps to reveal pickers | ✓ |
| Simplified mode toggle | Button to switch between minimal and full views | |
| Post-rest transition card | Minimal display shown briefly after rest, auto-transitions to full input | |

**User's choice:** [auto] Firmware-style intermediate screen (recommended default)
**Notes:** Matches firmware's WorkoutStartSetState pattern. Implemented as SwiftUI @State toggle, no KMP changes. Screen appears when new set begins, user taps to reveal pickers.

---

## Haptic Feedback Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Success notification on Complete Set only | UINotificationFeedbackGenerator .success on set completion | ✓ |
| Success + light impact on other actions | Haptic on Complete Set plus lighter feedback on skip, finish, etc. | |

**User's choice:** [auto] Success notification on Complete Set only (recommended default)
**Notes:** Consistent with existing rest-complete haptic pattern. Keeps feedback focused on primary lifting action per UX-02.

---

## Accessibility Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Core interactive elements in workout views | Pickers, buttons, set rows, menu items in workout screens only | ✓ |
| Full VoiceOver audit of all app views | Comprehensive pass across templates, exercises, settings too | |

**User's choice:** [auto] Core interactive elements in workout views (recommended default)
**Notes:** Pragmatic scope for university deadline. Covers UX-03 requirement. Picker labels include current value context.

---

## Visual Consistency Targets

| Option | Description | Selected |
|--------|-------------|----------|
| Standardize workout screens | Extract accent color, unify padding, verify typography hierarchy | ✓ |
| Full design system pass | Colors, typography, spacing, corner radii across all app screens | |

**User's choice:** [auto] Standardize workout screens (recommended default)
**Notes:** Scoped to workout tab per milestone goal. Extract hardcoded Color(red: 0.4, green: 0.733, blue: 0.416) to shared constant.

---

## Claude's Discretion

- Transition animation between minimal set screen and full picker input
- Whether minimal screen shows elapsed workout time
- Specific padding value to standardize on
- Whether to add accessibilityHint alongside accessibilityLabel
- Color constant naming and file location

## Deferred Ideas

None — discussion stayed within phase scope

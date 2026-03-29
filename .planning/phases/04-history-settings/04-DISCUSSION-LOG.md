# Phase 4: History & Settings - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-29
**Phase:** 04-history-settings
**Areas discussed:** History navigation, History list presentation, History detail layout, Previous performance display, Weight unit settings, Unit conversion approach
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## History Navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Section on Workout tab | History accessible via button/icon on template list, pushes to history view | ✓ |
| New navigation tab | Add a History tab to bottom navigation | |
| Inline on Workout tab | Show history entries below templates on same screen | |

**User's choice:** [auto] Section on Workout tab (recommended default)
**Notes:** Keeps navigation simple, no new tabs needed. Consistent with existing tab structure where only Workout is functional.

---

## History List Presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Full summary row | Date, template name, exercise count, total volume, duration | ✓ |
| Minimal row | Date and template name only | |
| Card layout | Rich cards with exercise breakdown preview | |

**User's choice:** [auto] Full summary row (recommended default)
**Notes:** Covers HIST-02 requirements directly. Exercise count and volume give at-a-glance insight without over-cluttering.

---

## History Detail Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Push navigation detail view | Exercise sections with set rows, standard navigation pattern | ✓ |
| Bottom sheet detail | Partial sheet overlay showing workout detail | |
| Expandable list rows | Expand history row in-place to show detail | |

**User's choice:** [auto] Push navigation detail view (recommended default)
**Notes:** Matches existing navigation patterns (NavigationStack + NavigationLink). Provides full screen for detailed data.

---

## Previous Performance Display

| Option | Description | Selected |
|--------|-------------|----------|
| Inline subtitle | Secondary text under each exercise showing last session's sets | ✓ |
| Expandable section | Collapsible "Last time" section per exercise | |
| Side-by-side comparison | Current vs previous in adjacent columns | |

**User's choice:** [auto] Inline subtitle (recommended default)
**Notes:** Non-intrusive, immediately visible without extra taps. Shows summary like "Last: 3×10 @ 50.0 kg".

---

## Weight Unit Settings

| Option | Description | Selected |
|--------|-------------|----------|
| Gear icon on Workout tab | Settings via gear icon in navigation bar, opens settings sheet | ✓ |
| Dedicated Settings tab | Replace Overview or Nutrition tab with Settings | |
| In-app profile section | Settings nested under a profile/account area | |

**User's choice:** [auto] Gear icon on Workout tab (recommended default)
**Notes:** Lightweight, no new tabs. Settings sheet with just kg/lbs toggle for now.

---

## Unit Conversion Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Display-only conversion | Storage stays kg×10, convert at presentation layer | ✓ |
| Dual storage | Store both kg and lbs values | |
| Input-unit storage | Store in whatever unit the user enters | |

**User's choice:** [auto] Display-only conversion (recommended default)
**Notes:** Avoids data migration, clean separation of concerns. All existing data remains in kg×10.

---

## Claude's Discretion

- Volume calculation display format
- Date formatting specifics (relative vs absolute thresholds)
- Settings sheet vs pushed navigation view
- DAO query strategy for previous performance
- New ViewModel vs extending existing ones
- DataStore Preferences Koin integration

## Deferred Ideas

None — all discussion stayed within phase scope

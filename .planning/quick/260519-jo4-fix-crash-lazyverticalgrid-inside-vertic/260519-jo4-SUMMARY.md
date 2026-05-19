---
phase: quick-260519-jo4
plan: "01"
subsystem: android-ui
tags: [crash-fix, compose, android, settings]
dependency_graph:
  requires: []
  provides: [stable-settings-sheet-android]
  affects: [androidApp/ui/screens/SettingsSheet]
tech_stack:
  added: []
  patterns: [chunked-rows-instead-of-lazy-grid]
key_files:
  created: []
  modified:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt
decisions:
  - "Replace LazyVerticalGrid with Column+accentPresets.chunked(4)+Row: eliminates the infinity-height-constraints crash with no visual change since accentPresets always has exactly 8 items"
metrics:
  duration: "~3 min"
  completed: "2026-05-19"
  tasks_completed: 1
  tasks_total: 1
---

# Phase quick-260519-jo4 Plan 01: Fix LazyVerticalGrid crash in SettingsSheet Summary

**One-liner:** Replace LazyVerticalGrid accent color picker with Column+chunked(4) Rows to fix infinity-height-constraints crash in the vertically-scrollable SettingsSheet.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace LazyVerticalGrid with chunked Rows | 9e8edab | SettingsSheet.kt |

## What Was Built

The accent color picker in `SettingsSheet.kt` was implemented as a `LazyVerticalGrid(columns = GridCells.Fixed(4))` nested inside a `Column` with `.verticalScroll(rememberScrollState())`. Compose forbids lazy layouts inside unbounded scrollable parents — the lazy container receives infinite height constraints and throws `IllegalStateException` on every Settings sheet open.

The fix replaces the lazy grid with a plain `Column` whose children are `Row`s built via `accentPresets.chunked(4)`. Since `accentPresets` always has exactly 8 entries, `chunked(4)` produces two rows of four — identical to `GridCells.Fixed(4)`. The inner `Box` for each preset is unchanged: 48.dp fixed size, `Arrangement.spacedBy(16.dp)` on both axes, same border+shadow+check-icon selection visuals. The three now-unused lazy-grid imports (`GridCells`, `LazyVerticalGrid`, `lazy.grid.items`) were removed.

## Verification

- Grep guard: no `LazyVerticalGrid`, `GridCells`, or `androidx.compose.foundation.lazy.grid.*` references in SettingsSheet.kt — PASS
- Grep guard: `accentPresets.chunked(4)` present exactly once — PASS
- `:androidApp:assembleDebug` — BUILD SUCCESSFUL in 24s

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- File modified: `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/SettingsSheet.kt` — confirmed
- Commit 9e8edab — confirmed in git log

---
phase: 16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p
plan: "06"
subsystem: iOS UI
tags: [ios, swiftui, nutrition, goals-editor, overview, banner]
dependency_graph:
  requires: ["16-02", "16-04"]
  provides: ["NutritionGoalsEditorView", "NutritionGoalsBannerView", "OverviewView banner+pencil"]
  affects: ["iosApp/iosApp/Views/Overview/OverviewView.swift"]
tech_stack:
  added: []
  patterns:
    - "SwiftUI Form with Section + DisclosureGroup (collapsible stats)"
    - "Picker(.wheel) for numeric macro input (D-16-03)"
    - "asyncSequence(for: viewModel.*Flow) observation pattern"
    - "NutritionGoalsBannerView private struct with transition(.move.combined(with: .opacity))"
key_files:
  created:
    - iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift
  modified:
    - iosApp/iosApp/Views/Overview/OverviewView.swift
    - iosApp/iosApp.xcodeproj/project.pbxproj
decisions:
  - "D-16-01: Sheet presentation (.sheet) from Overview edit pencil and banner"
  - "D-16-02: Single-screen 3-section Form (Stats + Suggestions + Pickers)"
  - "D-16-03: Picker(.wheel) used for all 5 macro inputs"
  - "D-16-08: Live suggestion card updates via computed currentStats/suggestions properties"
  - "D-16-09: DisclosureGroup for collapsible Stats; starts expanded when null, collapsed when stats persisted"
  - "D-16-13: Subtle NutritionGoalsBannerView above rings card"
  - "D-16-14: Banner dismissed by × (viewModel.dismissBanner()) or Save path"
metrics:
  duration: "~35 minutes"
  completed: "2026-04-28"
  tasks_completed: 4
  files_modified: 3
---

# Phase 16 Plan 06: iOS Nutrition Goals Editor UI Summary

**One-liner:** SwiftUI sheet editor with DisclosureGroup stats, live TDEE suggestion cards, five Picker(.wheel) macros, and a dismissable Overview banner.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create NutritionGoalsEditorView.swift | 52228c9 | NutritionGoalsEditorView.swift (new) |
| 2 | Register in iosApp Xcode target | 31ff205 | project.pbxproj |
| 3 | Add banner + edit pencil + sheet wiring to OverviewView | ed6657b | OverviewView.swift |
| 4 | Human verification (auto-approved) | — | — |

---

## What Was Built

### NutritionGoalsEditorView.swift (new — 319 lines)

Three-section SwiftUI `Form` presented as a `.sheet`:

1. **Stats section** — `DisclosureGroup("Meine Stats")` containing TextFields for weight/height/age, `Picker(.segmented)` for sex (Männlich/Weiblich), `Picker(.menu)` for activity tier (5 German labels per D-16-05). Starts expanded when `userPhysicalStats == null`; collapses after first save.

2. **Suggestion cards section** — `HStack(spacing: 8)` of three `SuggestionCardView` instances (Defizit/Erhalt/Aufbau). Live-computed via `SharedTdeeCalculator.shared.suggestions(stats: currentStats)`. Tapping a card highlights it (2pt accent stroke + 8% accent fill) and pre-fills all five wheel pickers. Any manual picker change deselects the card.

3. **Pickers section** — Five `Picker(.wheel)` instances with `Array(stride(...))` ranges (kcal 800–6000 step 50; protein 20–400 step 5; carbs 20–700 step 5; fat 10–250 step 5; sugar 0–200 step 5).

Toolbar: "Abbrechen" (leading, dismisses without saving) + "Ziele speichern" (trailing, semibold, calls `updateUserPhysicalStats` + `updateNutritionGoals` then `dismiss()`).

Async observation: `.task { withTaskGroup }` with `observeStats()` (pre-fills fields, collapses section) and `observeGoals()` (pre-fills pickers).

### OverviewView.swift (extended)

- Two new `@State` properties: `bannerVisible: Bool = true`, `showEditor: Bool = false`
- `NutritionGoalsBannerView` inserted above `nutritionRingsSection` with `.transition(.move(edge: .top).combined(with: .opacity))` inside `if bannerVisible { … }`
- `nutritionRingsSection` header replaced from plain `Text` to `HStack { Spacer · title · Spacer · pencil Button }`, pencil uses `.accessibilityLabel("Ziele bearbeiten")`
- `.sheet(isPresented: $showEditor) { NutritionGoalsEditorView().presentationDragIndicator(.visible) }` on the ScrollView
- Third `addTask { await observeBannerVisible() }` in the `withTaskGroup`
- `observeBannerVisible()` observes `viewModel.nutritionGoalsBannerVisibleFlow` and updates `bannerVisible` with `withAnimation(.easeOut(duration: 0.3))`

`NutritionGoalsBannerView` private struct: SF Symbol `target` icon in accent, "Persönliche Ziele setzen" body text, "Berechne deinen Tagesbedarf und passe deine Makros an." caption, trailing chevron + × button with `.accessibilityLabel("Banner ausblenden")`. Tap body → `onTap()`. Tap × → `onDismiss()` which calls `viewModel.dismissBanner()` + animates banner out.

### project.pbxproj (extended)

- PBXBuildFile ID: `68C7E602B72D2ACCFA53C0EF`
- PBXFileReference ID: `53FEB17CFAAB339866E7BF48`
- Added to `E10017 /* Overview */` PBXGroup children
- Added to iosApp target Sources build phase

---

## KMP Swift Name Resolution

The plan noted uncertainty about whether KMP-NativeCoroutines exports flat `SharedUserPhysicalStats` or nested `Shared.UserPhysicalStats`. Verified via xcodebuild success (BUILD SUCCEEDED): the `NutritionGoalsEditorView.swift` file uses `Shared.UserPhysicalStats` etc. via explicit `private typealias` declarations at the bottom of the file — this avoids any ambiguity and mirrors the pattern in OverviewView.swift.

Used type aliases:
- `SharedUserPhysicalStats = Shared.UserPhysicalStats`
- `SharedActivityLevel = Shared.ActivityLevel`
- `SharedSex = Shared.Sex`
- `SharedNutritionGoals = Shared.NutritionGoals`
- `SharedTdeeCalculator = Shared.TdeeCalculator`
- `SharedTdeeSuggestions = Shared.TdeeSuggestions`
- `SharedMacroSplit = Shared.MacroSplit`

---

## Deviations from Plan

None — plan executed exactly as written. The plan template used `KoinHelper.shared.getOverviewViewModel()` directly inside `NutritionGoalsEditorView` (rather than injecting via @ObservedObject), which matches the existing codebase pattern in `OverviewView.swift`.

---

## Human Verification Items

Task 4 was a `checkpoint:human-verify` — auto-approved per auto-mode configuration. The following items require manual testing on a real iOS simulator or device:

1. **First-launch state**: Banner appears above rings on Overview tab with target SF Symbol, correct German copy, and pencil button in rings header.
2. **Banner tap navigation**: Tapping banner presents sheet with drag indicator. Stats section expanded with placeholder defaults 80/180/30/Männlich/Mäßig aktiv.
3. **Live suggestion cards**: Changing weight or activity updates suggestion card kcal values immediately.
4. **Card selection**: Tapping "Defizit" highlights with accent border + fill; wheel pickers scroll to Cut values.
5. **Picker deselects card**: Spinning kcal wheel removes card highlight.
6. **Save round-trip**: "Ziele speichern" dismisses sheet; banner slides out; Overview rings update.
7. **Re-open editor via pencil**: Stats section collapsed; persisted values visible on expand.
8. **Banner × dismiss**: Tap × → banner slides up + fades out (~300ms); persists after relaunch.
9. **Persistence across kill**: Force-quit + relaunch → rings reflect saved goals; banner gone; editor opens with persisted stats.
10. **iOS specifics**: Drag indicator visible; swipe-down dismisses without saving; accent border matches ThemeManager accent color; VoiceOver reads correct labels.

---

## Known Stubs

None — `NutritionGoalsEditorView` is fully wired to `OverviewViewModel` via real `asyncSequence` observation. Suggestion cards compute live via `SharedTdeeCalculator`. Save path calls both ViewModel setters. No placeholder/mock data.

---

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes. This plan is purely iOS UI backed by the shared ViewModel already hardened in Plans 02 and 04.

---

## Self-Check

- FOUND: iosApp/iosApp/Views/Overview/NutritionGoalsEditorView.swift
- FOUND: iosApp/iosApp/Views/Overview/OverviewView.swift
- FOUND: commit 52228c9 (feat: NutritionGoalsEditorView)
- FOUND: commit 31ff205 (chore: pbxproj registration)
- FOUND: commit ed6657b (feat: OverviewView banner + pencil + sheet)

## Self-Check: PASSED

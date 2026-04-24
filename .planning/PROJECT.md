# PumpernickelApp

## What This Is

A fitness tracking mobile app built with Kotlin Multiplatform + SwiftUI (iOS), shipping a complete workout tracking flow with firmware-grade UX polish — exercise catalog, template management, workout execution with scroll wheel input and auto-increment, post-workout recap/edit, mid-workout reorder/skip, abandon guards, personal best display, and a minimal lifting screen with VoiceOver accessibility.

## Core Value

Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow that mirrors proven embedded firmware logic.

## Current State

**Shipped:** v1.1 Workout Polish & Firmware Parity (2026-03-31)

- 873-exercise seeded catalog with search and custom exercise creation
- Workout templates with CRUD, drag-and-drop reorder, exercise picker
- Full workout execution: set logging, rest timer with haptics, crash recovery
- Workout history with detail views and previous performance inline
- kg/lbs unit toggle with global application via DataStore Preferences
- iOS scroll wheel pickers for reps (0-50) and weight (0-1000 @ 2.5kg steps)
- Auto-increment: set 2+ pre-fills from previous actuals, set 1 from template targets
- Volume-weighted PB display per exercise during set entry
- Post-workout recap screen with tap-to-edit before saving
- Mid-workout exercise reorder (drag) and skip with crash recovery
- Abandon confirmation dialog (save & exit / discard / cancel)
- Context menu (skip exercise, exercise overview, finish workout)
- Firmware-style minimal SET N lifting screen with tap-to-reveal input
- Haptic feedback on set completion, VoiceOver accessibility labels
- Color.appAccent design token across workout views
- ~43,900 LOC Kotlin (KMP shared) + ~19,400 LOC Swift (iOS UI)

**Tech stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.3, Room KMP 2.8.4 (schema v4), Koin 4.2.0, Navigation Compose 2.9.2, DataStore Preferences 1.2.1, SwiftUI (iOS)

## Requirements

### Validated

- ✓ Exercise catalog (local, seeded or user-created) — v1.0
- ✓ Bottom navigation: Workout, Overview, Nutrition (only Workout functional) — v1.0
- ✓ Workout templates with exercises, target sets/reps/weight, rest periods — v1.0
- ✓ CRUD operations on workout templates — v1.0
- ✓ Execute a workout: select template → work through exercises → log sets → rest → finish — v1.0
- ✓ Set entry: log reps and weight per set — v1.0
- ✓ Rest timer between sets with countdown — v1.0
- ✓ Track workout progress (current exercise, current set) — v1.0
- ✓ Save completed workouts locally — v1.0
- ✓ View workout history (list of completed workouts) — v1.0
- ✓ Weight unit toggle (kg/lbs) with global application — v1.0
- ✓ Previous performance display during active workouts — v1.0
- ✓ Scroll wheel pickers for reps and weight input — v1.1
- ✓ Auto-increment: set 2+ pre-fills from previous actuals — v1.1
- ✓ 0-reps validation guard — v1.1
- ✓ Personal best display during set entry — v1.1
- ✓ Post-workout recap screen with edit before saving — v1.1
- ✓ Mid-workout exercise reorder with crash recovery — v1.1
- ✓ Skip current exercise — v1.1
- ✓ Abandon confirmation dialog — v1.1
- ✓ Context menu (skip, overview, finish) — v1.1
- ✓ Minimal lifting screen — v1.1
- ✓ Haptic feedback on set completion — v1.1
- ✓ VoiceOver accessibility labels — v1.1
- ✓ Visual consistency (Color.appAccent, spacing standardization) — v1.1

### Active

#### Planned Milestone: v1.5 Android Material 3 UI

**Goal:** Port all iOS SwiftUI screens to Jetpack Compose with Material 3, achieving full feature parity on Android with platform-native design.

**Target features:**
- Material 3 theme with app accent color, bottom navigation, navigation graph
- Exercise catalog, detail, create — with anatomy picker (Canvas-drawn body maps)
- Template management — list, editor, exercise picker, drag reorder
- Full workout session — custom drum picker for reps/weight, rest timer, auto-increment, PB display
- Exercise overview bottom sheet with drag reorder and skip
- Abandon guards, post-workout recap with edit, finished state
- Workout history list/detail, settings (kg/lbs toggle)

**Phases:** 11 (Shell & Nav) → 12 (Catalog & Templates) → 13 (Workout Session) → 14 (History, Settings & Anatomy)

### Out of Scope

- Spring Boot backend / PostgreSQL — deferred, prototype is local-only
- Nutrition tracking (F2) — future milestone
- Overview/dashboard (F3) — future milestone
- Gamification (F4) — future milestone
- Location/GPS features (F5) — future milestone
- AI workout generation (F6) — future milestone
- Auto macro calculation (F7) — future milestone
- AI meal generation (F8) — future milestone
- All Should Have (S1-S6) and Nice to Have (N1-N7) features — future milestones
- Progress charts/graphs — requires charting library, deferred

## Context

- **University project** for mobile app development course (semester 6), deadline ~end of May 2026
- **Reference implementation:** `/Users/olli/schenanigans/gymtracker` — an ESP32 firmware + Rust API + SvelteKit dashboard for gym tracking. The firmware's FSM-driven workout flow was the model for this app's workout logic
- **Data model from gymtracker:** Workout → Exercises → Sets (with reps, weight as kg×10, rest period in seconds). Templates define target sets/reps/weight per exercise
- **Platform:** KMP with SwiftUI iOS UI (not Compose Multiplatform for iOS UI — Compose is used for shared logic)
- **The two projects are independent** — gymtracker is a reference for flow and logic, not a dependency
- **Lastenheft** defines the full app vision (F1-F8 Must Haves, S1-S6 Should Haves, N1-N7 Nice to Haves)

## Constraints

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform (per Lastenheft)
- **Platform focus**: iOS first (user handles iOS UI in SwiftUI), Android next (v1.5)
- **Storage**: Local/offline only for prototype (Room KMP)
- **Timeline**: University deadline ~end of May 2026
- **Scope**: v1.5 — Android Material 3 UI parity

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Local-only storage for prototype | Backend adds complexity, prototype needs to prove workout flow works | ✓ Good — Room KMP worked well |
| iOS-first development | User's personal focus area for the UI | ✓ Good — SwiftUI + KMP ViewModels pattern works |
| Gymtracker as reference, not dependency | Independent projects; reuse flow/logic patterns, not code | ✓ Good — FSM workout flow adapted cleanly |
| Workout feature first | Core loop that everything else builds on; matches Lastenheft F1 priority | ✓ Good — complete workout loop shipped |
| Weight stored as kg×10 integer | Matches gymtracker convention, avoids floating point | ✓ Good — display-only conversion for lbs |
| SwiftUI for iOS UI (not Compose Multiplatform UI) | Compose iOS UI was experimental; SwiftUI is native and polished | ✓ Good — clean separation: KMP shared logic + SwiftUI views |
| Room KMP over SQLDelight | Annotation-based DAOs faster to write; Google long-term backing | ✓ Good — smooth experience across 4 schema versions |
| Koin over kotlin-inject | No KSP overhead on top of Room's KSP; simpler for prototype | ✓ Good — runtime DI worked fine at prototype scale |
| DataStore Preferences for settings | Lighter than Room for single key-value (weight unit) | ✓ Good — simple, platform-specific file paths handled cleanly |
| Volume-weighted PB over simple max | Matches firmware TrendCalculator.cpp; more meaningful than raw max weight | ✓ Good — consistent with gymtracker |
| Two-step recap (enterReview + saveReviewedWorkout) | Separates review from save; enables recap editing without affecting active state | ✓ Good — clean state machine extension |
| exerciseOrder CSV in active_sessions | Lightweight crash recovery for reorder; avoids new table | ✓ Good — single-column migration, backward-compatible |
| Color.appAccent design token | Consistent branding; single source of truth for accent color | ✓ Good — eliminated 9 hardcoded RGB values |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-31 after v1.1 milestone completion*

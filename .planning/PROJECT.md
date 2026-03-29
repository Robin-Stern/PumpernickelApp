# PumpernickelApp

## What This Is

A fitness tracking mobile app built with Kotlin Multiplatform + SwiftUI (iOS), shipping a complete workout tracking flow — exercise catalog, template management, workout execution with set logging and rest timers, workout history with previous performance, and kg/lbs unit settings. v1.0 MVP shipped.

## Core Value

Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow that mirrors proven embedded firmware logic.

## Current State

**Shipped:** v1.0 MVP (2026-03-29)

- 873-exercise seeded catalog with search and custom exercise creation
- Workout templates with CRUD, drag-and-drop reorder, exercise picker
- Full workout execution: set logging, rest timer with haptics, crash recovery
- Workout history with detail views and previous performance inline
- kg/lbs unit toggle with global application via DataStore Preferences
- ~34,700 LOC Kotlin (KMP shared) + ~13,500 LOC Swift (iOS UI)

**Tech stack in use:** Kotlin 2.3.20, Compose Multiplatform 1.10.3, Room KMP 2.8.4, Koin 4.2.0, Navigation Compose 2.9.2, DataStore Preferences 1.2.1, SwiftUI (iOS)

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

### Active

#### Current Milestone: v1.1 Workout Polish & Firmware Parity

**Goal:** Elevate the workout tab from prototype to polished firmware-grade experience with iOS-native scroll wheel input, full firmware feature parity, and general UX polish.

**Target features:**
- Scroll wheel pickers for reps (0–50) and weight (0–1000, 2.5kg steps)
- Auto-increment: next set pre-fills with previous set's actual values
- Minimal "doing set" screen while lifting
- Post-workout recap/edit before saving
- Mid-workout exercise reorder
- Abandon guards (save & exit vs discard)
- Context menu (skip exercise, reorder)
- Personal best display on set entry
- General UI polish (validation, keyboard handling, accessibility)

### Out of Scope

- Spring Boot backend / PostgreSQL — deferred, prototype is local-only
- Nutrition tracking (F2) — future milestone
- Overview/dashboard (F3) — future milestone
- Gamification (F4) — future milestone
- Location/GPS features (F5) — future milestone
- AI workout generation (F6) — future milestone
- Auto macro calculation (F7) — future milestone
- AI meal generation (F8) — future milestone
- All Should Have (S1–S6) and Nice to Have (N1–N7) features — future milestones
- Progress charts/graphs — requires charting library, deferred to v2

## Context

- **University project** for mobile app development course (semester 6), deadline ~end of May 2026
- **Reference implementation:** `/Users/olli/schenanigans/gymtracker` — an ESP32 firmware + Rust API + SvelteKit dashboard for gym tracking. The firmware's FSM-driven workout flow was the model for this app's workout logic
- **Data model from gymtracker:** Workout → Exercises → Sets (with reps, weight as kg×10, rest period in seconds). Templates define target sets/reps/weight per exercise
- **Platform:** KMP with SwiftUI iOS UI (not Compose Multiplatform for iOS UI — Compose is used for shared logic)
- **The two projects are independent** — gymtracker is a reference for flow and logic, not a dependency
- **Lastenheft** defines the full app vision (F1–F8 Must Haves, S1–S6 Should Haves, N1–N7 Nice to Haves)

## Constraints

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform (per Lastenheft)
- **Platform focus**: iOS first (user handles iOS UI in SwiftUI)
- **Storage**: Local/offline only for prototype (Room KMP)
- **Timeline**: University deadline ~end of May 2026
- **Scope**: v1.1 — workout tab polish and firmware feature parity

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Local-only storage for prototype | Backend adds complexity, prototype needs to prove workout flow works | ✓ Good — Room KMP worked well |
| iOS-first development | User's personal focus area for the UI | ✓ Good — SwiftUI + KMP ViewModels pattern works |
| Gymtracker as reference, not dependency | Independent projects; reuse flow/logic patterns, not code | ✓ Good — FSM workout flow adapted cleanly |
| Workout feature first | Core loop that everything else builds on; matches Lastenheft F1 priority | ✓ Good — complete workout loop shipped |
| Weight stored as kg×10 integer | Matches gymtracker convention, avoids floating point | ✓ Good — display-only conversion for lbs |
| SwiftUI for iOS UI (not Compose Multiplatform UI) | Compose iOS UI was experimental; SwiftUI is native and polished | ✓ Good — clean separation: KMP shared logic + SwiftUI views |
| Room KMP over SQLDelight | Annotation-based DAOs faster to write; Google long-term backing | ✓ Good — smooth experience across 3 schema versions |
| Koin over kotlin-inject | No KSP overhead on top of Room's KSP; simpler for prototype | ✓ Good — runtime DI worked fine at prototype scale |
| DataStore Preferences for settings | Lighter than Room for single key-value (weight unit) | ✓ Good — simple, platform-specific file paths handled cleanly |

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
*Last updated: 2026-03-29 after v1.1 milestone start*

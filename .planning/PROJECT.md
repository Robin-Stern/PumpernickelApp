# PumpernickelApp

## What This Is

A fitness tracking mobile app built with Kotlin Multiplatform (Compose Multiplatform), targeting iOS first. The current focus is a workout tracking prototype — logging workouts, managing templates, and executing workout sessions with the same flow and logic as the user's existing gymtracker firmware project, adapted for mobile.

## Core Value

Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow that mirrors proven embedded firmware logic.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Workout templates with exercises, target sets/reps/weight, rest periods
- [ ] CRUD operations on workout templates
- [ ] Execute a workout: select template → work through exercises → log sets → rest → finish
- [ ] Set entry: log reps and weight per set
- [ ] Rest timer between sets with countdown
- [ ] Track workout progress (current exercise, current set)
- [ ] Save completed workouts locally
- [ ] View workout history (list of completed workouts)
- [ ] Exercise catalog (local, seeded or user-created)
- [ ] Bottom navigation: Workout, Overview, Nutrition (only Workout functional for now)
- [ ] Local/offline storage (Room or SQLDelight — no backend for prototype)

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
- Polished UI — this is a prototype

## Context

- **University project** for mobile app development course (semester 6), deadline ~end of May 2026
- **Reference implementation:** `/Users/olli/schenanigans/gymtracker` — an ESP32 firmware + Rust API + SvelteKit dashboard for gym tracking. The firmware's FSM-driven workout flow (template selection → SET N → reps/weight entry → rest timer → next set/exercise → finish → save) is the model for this app's workout logic
- **Data model from gymtracker:** Workout → Exercises → Sets (with reps, weight as kg×10, rest period in seconds). Templates define target sets/reps/weight per exercise
- **Platform:** KMP with Compose Multiplatform, iOS-first development
- **The two projects are independent** — gymtracker is a reference for flow and logic, not a dependency
- **Lastenheft** defines the full app vision (F1–F8 Must Haves, S1–S6 Should Haves, N1–N7 Nice to Haves) — serves as a guardrail for the overall project scope across milestones

## Constraints

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform (per Lastenheft)
- **Platform focus**: iOS first (user handles iOS UI)
- **Storage**: Local/offline only for prototype (Room or SQLDelight)
- **Timeline**: University deadline ~end of May 2026
- **Scope**: Workout feature only for current milestone — no backend, no nutrition, no gamification

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Local-only storage for prototype | Backend adds complexity, prototype needs to prove workout flow works | — Pending |
| iOS-first development | User's personal focus area for the UI | — Pending |
| Gymtracker as reference, not dependency | Independent projects; reuse flow/logic patterns, not code | — Pending |
| Workout feature first | Core loop that everything else builds on; matches Lastenheft F1 priority | — Pending |

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
*Last updated: 2026-03-28 after initialization*

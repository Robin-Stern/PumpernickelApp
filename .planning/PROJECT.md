# PumpernickelApp

## What This Is

A fitness tracking mobile app built with Kotlin Multiplatform + SwiftUI (iOS) + Jetpack Compose (Android). Ships workout tracking with firmware-grade UX polish (exercise catalog, templates, execution flow with scroll wheel input, recap/edit, reorder/skip, PB display, VoiceOver), a nutrition tracker with OpenFoodFacts barcode scanning and custom recipes, and a dynamic theming system (light/dark/system + 8 accent colors).

## Core Value

Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow that mirrors proven embedded firmware logic.

## Current State

**Branch:** `feature/workouts` @ `fe297ad` (2026-04-14)

**Shipped:**

- **v1.0 MVP (2026-03-29)** — Foundation, exercise catalog, templates, workout session, history & settings
- **v1.1 Workout Polish & Firmware Parity (2026-03-31)** — scroll wheel pickers, auto-increment, PB display, recap/edit, reorder/skip, abandon guards, minimal SET N screen, haptics, VoiceOver, Color.appAccent design token
- **v1.5 Android Material 3 UI (2026-03-31)** — full feature parity on Android via Jetpack Compose with Material 3 theme, navigation graph, catalog + templates + workout session (drum picker) + history + settings + anatomy picker (Canvas body drawings)
- **Post-v1.5 work (2026-04-14, untracked by GSD)** — nutrition feature (Food/Recipe/Consumption with OpenFoodFacts barcode lookup), dynamic theming (light/dark/system + 8 accent colors), nutrition goals on Overview tab, template editor redesign, workout history set-count + RIR, PB calculation fix, Room v4 → v7, Android `android-kmp-library` plugin migration + `androidApp` module extraction

**Key capability inventory:**
- 873-exercise seeded catalog with search, filter, and custom exercise creation
- Workout templates with CRUD, drag reorder, exercise picker
- Full workout execution: set logging, rest timer with haptics, crash recovery, PB display, auto-increment, recap/edit, skip, mid-workout reorder, abandon guards, minimal SET N screen
- Workout history list + detail (set count, RIR, previous performance)
- Nutrition: daily macro log, food CRUD, custom recipes, barcode scanning (OpenFoodFacts), per-100g hints, goals (calorie/protein/fat/carb/sugar)
- Dynamic theming: light/dark/system mode, 8 accent presets, persisted in DataStore
- kg/lbs toggle with global application
- iOS + Android parity on all workout flows; iOS exclusive: scroll wheel pickers (Android uses custom drum picker); Android exclusive: none

**Tech stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.x (Android), SwiftUI (iOS), Room KMP 2.8.4 (schema v7), Koin 4.2.0, Navigation Compose 2.9.2, DataStore Preferences, Ktor Client (CIO) for OpenFoodFacts, kotlinx-datetime 0.7.x, KMPNativeCoroutinesAsync (iOS ↔ Flow bridge)

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
- ✓ Android Material 3 theme with bottom nav + type-safe routes — v1.5
- ✓ Android parity: exercise catalog, detail, create — v1.5
- ✓ Android parity: template list, editor, exercise picker — v1.5
- ✓ Android parity: full workout session with custom drum picker, rest timer, auto-increment, PB — v1.5
- ✓ Android parity: exercise overview sheet with reorder + skip — v1.5
- ✓ Android parity: post-workout recap + finished state — v1.5
- ✓ Android parity: workout history list + detail, settings (kg/lbs) — v1.5
- ✓ Anatomy picker with Canvas-drawn body maps sharing `MuscleRegionPaths` from commonMain — v1.5
- ✓ Nutrition tracking (F2): Food/Recipe/Consumption CRUD, daily macro log — post-v1.5
- ✓ OpenFoodFacts barcode scanning with Ktor + AVFoundation camera — post-v1.5
- ✓ Nutrition goals (calorie/protein/fat/carb/sugar) persisted in DataStore — post-v1.5
- ✓ Dynamic theming: light/dark/system mode + 8 accent color presets — post-v1.5
- ✓ Workout history detail shows per-set count + RIR — post-v1.5
- ✓ PB calculation fix (volume-weighted aggregate consistent with firmware) — post-v1.5
- ✓ Android `android-kmp-library` plugin migration + `androidApp` module extraction — post-v1.5

### Active

No active milestone. Next step: `/gsd:new-milestone` before planning additional work.

### Out of Scope

- Spring Boot backend / PostgreSQL — deferred, prototype is local-only
- Overview/dashboard full experience (F3) — partial (macro goals only); full dashboard with training-intensity heatmap deferred
- Gamification (F4) — future milestone
- Location/GPS features (F5) — future milestone
- AI workout generation (F6) — future milestone
- Auto macro calculation from food macros (F7) — partial (daily totals + goals), full auto-calc deferred
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

- **Tech stack**: Kotlin Multiplatform + SwiftUI (iOS) + Compose Multiplatform (Android)
- **Platform focus**: iOS + Android both at feature parity on workout & nutrition flows
- **Storage**: Local-first (Room KMP v7, DataStore Preferences); network only for OpenFoodFacts barcode lookup (no auth, no backend)
- **Timeline**: University deadline ~end of May 2026
- **Scope**: currently between milestones; active iOS work branch is `feature/workouts`

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
| Room KMP v7 with 4 nutrition entities | Colocate nutrition in the same DB as workouts; single source of truth, single backup path | ✓ Good — AutoMigration 6→7 registered, no data loss |
| OpenFoodFacts over local food DB | Avoid shipping a multi-GB food database; barcode lookup on demand is acceptable UX | ✓ Post-v1.5 — no caching yet, network required per scan |
| Ktor CIO client over platform HTTP clients | Single KMP dependency, consistent error handling, smaller iOS binary than adding NSURLSession wrapper | ✓ Post-v1.5 — works, but adds ~1MB to binary |
| `ThemeManager.shared` (@Observable) on iOS | iOS 17 Observation macro gives automatic view invalidation; avoids `@EnvironmentObject` plumbing through every view | ✓ Post-v1.5 — `Color.appAccent` computed extension reads from the observable |
| Theme + accent persisted in DataStore, not Room | Key-value settings don't belong in relational DB; DataStore already used for `weightUnit` | ✓ Post-v1.5 — consistent with existing pattern |
| NutritionGoals defaults (2500/150/80/300/50) | Baseline numbers for a moderately active adult; easier to tune than asking on first launch | ✓ Post-v1.5 — hard-coded defaults in SettingsRepository `Flow.map` |
| `MuscleRegionPaths` moved to commonMain | Android Canvas + iOS SwiftUI Path both consume the same coordinate data | ✓ v1.5 — single source of truth across platforms |
| `android-kmp-library` plugin + `androidApp` extraction | Separate per-module Gradle config; enables future per-platform build tuning | ✓ Post-v1.5 — clean module boundaries |
| Nutrition work shipped outside GSD | Team/branch context; work progressed faster than planning cadence | ⚠️ Gap in `.planning/phases/`; git log is authoritative for this work |

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
*Last updated: 2026-04-22 — reconciled with branch tip `fe297ad` after v1.5 shipped and untracked post-v1.5 work (nutrition + theming) merged outside GSD*

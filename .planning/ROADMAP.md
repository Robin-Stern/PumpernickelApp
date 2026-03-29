# Roadmap: PumpernickelApp

## Overview

PumpernickelApp delivers a complete workout tracking loop on iOS via KMP Compose Multiplatform. The roadmap builds from the ground up: a working database and exercise catalog, then template management, then the core workout session flow (mirroring the gymtracker firmware FSM), and finally workout history with polish. Each phase delivers a testable vertical slice -- no phase ships without something a user can interact with.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation & Exercise Catalog** - Database, DI, navigation shell, seeded exercises, and exercise browsing
- [ ] **Phase 2: Template Management** - Full CRUD for workout templates with exercise picker and reordering
- [ ] **Phase 3: Workout Session** - Execute workouts set-by-set with rest timer, progress tracking, crash recovery, and save
- [ ] **Phase 4: History & Settings** - Workout history, previous performance display, and kg/lbs unit toggle

## Phase Details

### Phase 1: Foundation & Exercise Catalog
**Goal**: Users can launch the app, see the navigation shell, and browse a seeded exercise catalog with search, filtering, and custom exercise creation
**Depends on**: Nothing (first phase)
**Requirements**: EXER-01, EXER-02, EXER-03, NAV-01
**Success Criteria** (what must be TRUE):
  1. App launches on iOS and displays a bottom navigation bar with Workout, Overview, and Nutrition tabs (only Workout functional)
  2. User can browse and search a seeded exercise catalog with muscle group filtering
  3. User can create a custom exercise with name and primary muscle group, and it appears in the catalog
  4. App compiles and runs on both Android and iOS from the shared KMP module
**Plans**: 3 plans

Plans:
- [x] 01-01-PLAN.md — KMP project scaffolding, Room database, exercise data layer with 873-exercise seeding
- [x] 01-02-PLAN.md — ViewModels, Koin DI wiring, iOS navigation shell with placeholder screens
- [x] 01-03-PLAN.md — iOS exercise catalog, detail, create exercise, and anatomy SVG picker
**UI hint**: yes

### Phase 2: Template Management
**Goal**: Users can create, edit, and organize workout templates with exercises and targets
**Depends on**: Phase 1
**Requirements**: TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05
**Success Criteria** (what must be TRUE):
  1. User can create a new workout template with a name and add exercises from the catalog
  2. Each exercise in a template has configurable target sets, target reps, target weight, and rest period duration
  3. User can edit a template (rename, add/remove exercises, change targets) and delete a template with confirmation
  4. User can reorder exercises within a template via drag-and-drop
**Plans**: 3 plans
**UI hint**: yes

Plans:
- [x] 02-01-PLAN.md — Room entities, DAO, repository, domain models, DB migration for template data layer
- [x] 02-02-PLAN.md — TemplateList and TemplateEditor ViewModels, Koin DI, KoinHelper iOS getters
- [x] 02-03-PLAN.md — iOS SwiftUI template list, editor, exercise picker views, and MainTabView update

### Phase 3: Workout Session
**Goal**: Users can execute a full workout from template selection through set logging, rest timers, and saving the completed workout
**Depends on**: Phase 2
**Requirements**: WORK-01, WORK-02, WORK-03, WORK-04, WORK-05, WORK-06, WORK-07, WORK-08, WORK-09
**Success Criteria** (what must be TRUE):
  1. User can start a workout by selecting a template, log reps and weight for each set, and mark sets complete
  2. Rest timer auto-starts after completing a set with a countdown matching the exercise's configured rest period, and alerts via vibration when done
  3. User can see current workout progress (exercise X of Y, set X of Y) and the workout's elapsed duration
  4. User can finish a workout and it is saved to local storage with all sets, reps, weights, and duration
  5. If the app is killed or crashes during a workout, the session can be resumed on next launch
**Plans**: 3 plans
**UI hint**: yes

Plans:
- [x] 03-01-PLAN.md — Room entities, DAOs, domain models, WorkoutRepository, DB migration v2 to v3
- [x] 03-02-PLAN.md — WorkoutSessionViewModel state machine, rest timer, elapsed ticker, Koin DI wiring
- [x] 03-03-PLAN.md — iOS SwiftUI workout session views, template list integration, crash recovery UI

### Phase 4: History & Settings
**Goal**: Users can review past workouts, see previous performance during active sessions, and configure weight units
**Depends on**: Phase 3
**Requirements**: HIST-01, HIST-02, HIST-03, HIST-04, NAV-02, NAV-03
**Success Criteria** (what must be TRUE):
  1. User can view a list of completed workouts sorted by date (newest first), each showing date, name, exercises, and total volume
  2. User can tap a history entry to see full workout detail with all exercises, sets, reps, and weights
  3. During an active workout, user can see what they did last time for each exercise (previous performance inline)
  4. User can toggle between kg and lbs in settings, and the selected unit applies globally to all weight displays and entries
**Plans**: 3 plans
**UI hint**: yes

Plans:
- [x] 04-01-PLAN.md — DAO history queries, DataStore Preferences, WeightUnit domain model, SettingsRepository, WorkoutRepository extensions
- [ ] 04-02-PLAN.md — WorkoutHistoryViewModel, SettingsViewModel, WorkoutSessionViewModel previous performance, Koin DI wiring
- [ ] 04-03-PLAN.md — iOS SwiftUI history list, detail, settings views, previous performance display, global unit integration

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Exercise Catalog | 3/3 | Complete | 2026-03-28 |
| 2. Template Management | 3/3 | Complete | 2026-03-28 |
| 3. Workout Session | 3/3 | Complete | 2026-03-28 |
| 4. History & Settings | 0/3 | Not started | - |

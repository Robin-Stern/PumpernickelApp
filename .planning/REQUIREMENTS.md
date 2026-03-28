# Requirements: PumpernickelApp

**Defined:** 2026-03-28
**Core Value:** Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Exercise Management

- [x] **EXER-01**: App ships with a seeded exercise catalog sourced from gymtracker's `free_exercise_db.json` (~873 exercises with muscle groups, equipment, level). Path: `/Users/olli/schenanigans/gymtracker/api/free_exercise_db.json`
- [x] **EXER-02**: User can create custom exercises with name and primary muscle group
- [x] **EXER-03**: User can browse and search the exercise catalog when building templates or during workouts

### Template Management

- [x] **TMPL-01**: User can create a workout template with a name and a list of exercises
- [x] **TMPL-02**: Each exercise in a template has target sets, target reps, target weight, and rest period duration
- [x] **TMPL-03**: User can edit an existing template (rename, add/remove exercises, change targets)
- [x] **TMPL-04**: User can delete a template with confirmation
- [x] **TMPL-05**: User can reorder exercises within a template via drag-and-drop

### Workout Execution

- [ ] **WORK-01**: User can start a workout by selecting a template, which loads exercises and targets into an active session
- [ ] **WORK-02**: User can log reps and weight for each set of each exercise
- [ ] **WORK-03**: User can mark a set as complete, advancing to the rest timer
- [ ] **WORK-04**: Rest timer auto-starts after completing a set with a countdown matching the exercise's configured rest period
- [ ] **WORK-05**: Rest timer alerts the user (vibration) when rest period ends
- [ ] **WORK-06**: User can see current progress during a workout (current exercise X of Y, current set X of Y)
- [ ] **WORK-07**: User can finish and save a completed workout to local storage
- [ ] **WORK-08**: Workout duration is automatically tracked (start time, end time, total duration)
- [ ] **WORK-09**: Active workout session persists across app restarts (crash recovery / session resume)

### History

- [ ] **HIST-01**: User can view a list of completed workouts sorted by date (newest first)
- [ ] **HIST-02**: Each history entry shows date, workout name, exercises performed, and total volume
- [ ] **HIST-03**: User can tap a history entry to see full workout detail (all exercises, sets, reps, weight)
- [ ] **HIST-04**: During an active workout, user can see what they did last time for each exercise (previous performance inline)

### Navigation & Settings

- [x] **NAV-01**: App has bottom navigation with three tabs: Workout, Overview, Nutrition (only Workout functional)
- [ ] **NAV-02**: User can toggle between kg and lbs as the weight unit
- [ ] **NAV-03**: Selected unit applies globally to all weight displays and entries

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Workout Execution Enhancements

- **WORK-10**: User can start an empty/ad-hoc workout without a template
- **WORK-11**: User can add free-text notes to a workout and individual exercises
- **WORK-12**: User can tag sets as warm-up, drop set, or failure
- **WORK-13**: User can group exercises into supersets

### History & Progress Enhancements

- **HIST-05**: User can save a completed workout as a new reusable template
- **HIST-06**: App detects and displays personal records (PR) with live notification during workout
- **HIST-07**: App calculates estimated 1RM per exercise using standard formula (Epley/Brzycki)
- **HIST-08**: User can view progress charts per exercise (weight, volume, 1RM over time)
- **HIST-09**: User can view workout history in a calendar view

### Full Lastenheft Scope (Future Milestones)

- **NUTR-01**: Nutrition tracking (F2)
- **DASH-01**: Overview dashboard (F3)
- **GAME-01**: Gamification — XP, achievements, ranks (F4)
- **LOC-01**: Location-based workout features (F5)
- **AI-01**: AI workout generation (F6)
- **MACRO-01**: Auto macro calculation (F7)
- **MEAL-01**: AI meal suggestions (F8)
- **LEAD-01**: Leaderboard with backend (S1)
- **PIC-01**: Progress pictures (S2)
- **SCAN-01**: Barcode scanner (S3)
- **MUSC-01**: Muscle history / undertrained analysis (S4)
- **FOOD-01**: Custom foods & recipes (S5)
- **BF-01**: Body fat calculator (S6)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Spring Boot backend | Prototype is local-only; backend deferred to future milestone |
| Cloud sync | Requires backend infrastructure |
| Social features (feed, sharing) | Out of scope, requires backend, community dislikes in trackers |
| AI workout/meal generation | Requires backend + OpenAI API integration |
| Nutrition tracking | Separate milestone per Lastenheft |
| Gamification (XP, ranks) | Separate milestone per Lastenheft |
| Smartwatch companion | Separate platform development |
| Exercise video demonstrations | Massive content investment, not feasible for prototype |
| Progress charts/graphs | Requires charting library, defer to v2 |
| CSV/JSON export | Not needed for prototype |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| EXER-01 | Phase 1 | Complete |
| EXER-02 | Phase 1 | Complete |
| EXER-03 | Phase 1 | Complete |
| TMPL-01 | Phase 2 | Complete |
| TMPL-02 | Phase 2 | Complete |
| TMPL-03 | Phase 2 | Complete |
| TMPL-04 | Phase 2 | Complete |
| TMPL-05 | Phase 2 | Complete |
| WORK-01 | Phase 3 | Pending |
| WORK-02 | Phase 3 | Pending |
| WORK-03 | Phase 3 | Pending |
| WORK-04 | Phase 3 | Pending |
| WORK-05 | Phase 3 | Pending |
| WORK-06 | Phase 3 | Pending |
| WORK-07 | Phase 3 | Pending |
| WORK-08 | Phase 3 | Pending |
| WORK-09 | Phase 3 | Pending |
| HIST-01 | Phase 4 | Pending |
| HIST-02 | Phase 4 | Pending |
| HIST-03 | Phase 4 | Pending |
| HIST-04 | Phase 4 | Pending |
| NAV-01 | Phase 1 | Complete |
| NAV-02 | Phase 4 | Pending |
| NAV-03 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 24 total
- Mapped to phases: 24
- Unmapped: 0

---
*Requirements defined: 2026-03-28*
*Last updated: 2026-03-28 after roadmap creation*

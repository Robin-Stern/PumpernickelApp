# Feature Landscape: Workout Tracking

**Domain:** Workout tracking mobile app (strength training focus)
**Researched:** 2026-03-28
**Competitors analyzed:** Strong, Hevy, JEFIT, FitNotes, Setgraph, RepCount, StrengthLog

## Table Stakes

Features users expect from any workout tracker. Missing any of these and users will leave for a competitor. Every major app in the space (Strong, Hevy, JEFIT) ships all of these.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Start workout from template** | Core flow: pick a routine, execute it. Every competitor has this. Users don't want to rebuild their workout every session. | Low | Strong limits free users to 3 templates. PumpernickelApp should be unlimited (local-only, no monetization concern). |
| **Start empty/ad-hoc workout** | Sometimes users deviate from plan. All apps support both template-based and blank-slate sessions. | Low | Simple variant of template flow -- just start with no pre-loaded exercises. |
| **Set logging: weight + reps** | The atomic unit of workout tracking. Every app records weight and reps per set. This is literally the minimum viable interaction. | Low | Weight stored as number (kg or lbs). Reps stored as integer. Matches gymtracker model (weight as kg*10, reps). |
| **Mark sets as complete** | Users check off each set as they finish it. Provides progress feedback and drives the rest timer trigger. Strong, Hevy, FitNotes all use a checkbox/tap-to-complete pattern. | Low | Checkbox or tap interaction. Triggers rest timer. Visual distinction between pending/completed sets. |
| **Rest timer between sets** | Users need timed rest periods for effective training. Reddit users specifically cite broken rest timers as a dealbreaker. Every major app has auto-start timers after completing a set. | Medium | Auto-starts on set completion. Countdown with audio/vibration alert. Configurable duration (30s, 60s, 90s, 120s common presets). Must work when app is backgrounded. Aligns with gymtracker FSM rest state. |
| **Exercise catalog/library** | Users need a list of exercises to pick from. All competitors ship 300-1400+ exercises. At minimum, a seeded local database of common exercises. | Medium | Seeded with ~50-100 common exercises. Categorized by muscle group (Chest, Back, Shoulders, Arms, Legs, Core). Include exercise name and primary muscle group at minimum. |
| **Custom exercise creation** | Users have unique exercises (home gym, machines, variations). Strong, Hevy, FitNotes all support this. | Low | Name + muscle group + optional equipment type. Added to local catalog. |
| **Workout history list** | Users need to see past workouts. All apps have a History tab showing completed sessions with date, exercises, and volume summary. | Medium | List of completed workouts sorted by date (newest first). Each entry shows: date, workout name/template, exercises performed, total volume. Tap to view full detail. |
| **Previous performance display** | During active workout, showing what you did last time for each exercise. Hevy shows this in a "PREVIOUS" column. Strong shows last session's reps/weight. This is critical for progressive overload -- the core reason people track workouts at all. | Medium | For each exercise in the active workout, display the weight/reps from the most recent session containing that exercise. Shown inline alongside current set entry fields. |
| **Template CRUD** | Create, read, update, delete workout templates. Strong and Hevy both offer full template management. Users reorganize their training programs regularly. | Medium | Create template with name, exercises, target sets/reps/weight per exercise, rest periods. Edit all fields. Delete with confirmation. Reorder exercises via drag-and-drop. |
| **Save workout as template** | After finishing an ad-hoc workout, save it as a reusable template. Strong explicitly supports this from the History view. Natural workflow for users who discover a routine they want to repeat. | Low | Post-workout option to "Save as Template". Copies exercise list and set configuration. |
| **Workout progress indicator** | During active workout, show where you are: current exercise, current set, how much is left. Without this, users lose track in longer sessions. | Low | Display current exercise X of Y, current set X of Y. Visual progress (progress bar or fraction). |
| **Unit support (kg/lbs)** | Users in different regions use different units. All major apps support both metric and imperial. | Low | Global setting for kg or lbs. Display and entry in selected unit. Store internally in one canonical unit (kg, matching gymtracker). |

## Differentiators

Features that set a product apart. Not expected in every app, but valued when present. These are the features that make users choose one app over another.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Set type tags (warm-up, drop set, failure)** | Hevy and Strong let users tag sets as warm-up, failure, or drop set. Provides richer training data and excludes warm-up sets from working volume calculations. | Low | Tag on each set: Normal (default), Warm-up, Failure, Drop Set. Affects rest timer behavior (no auto-rest between drop sets). Affects volume calculations. |
| **Supersets / exercise grouping** | Strong and Hevy support grouping exercises that are performed back-to-back. Common training technique, but not every app handles it well (JEFIT requires 5-7 clicks). | Medium | Group 2+ exercises. During execution, alternate between grouped exercises. Distinct visual treatment. Rest timer starts after completing all exercises in the superset, not after each individual one. |
| **Personal records (PR) tracking** | Hevy shows live PR notifications during workouts. Strong, FitNotes, RepCount all track rep-max PRs (1RM, 3RM, 5RM, etc.). Motivating and useful for programming. | Medium | Track best weight at each rep count per exercise. Detect new PRs during active workout. Display notification/badge when PR is achieved. Show PR history per exercise. |
| **Estimated 1RM calculation** | Strong, Hevy, JEFIT, and RepCount all calculate estimated one-rep max from logged sets. Standard formula (Brzycki or Epley). Useful for programming and ego. | Low | Calculate from weight and reps using standard formula. Display per exercise. Track over time. |
| **Progress charts/graphs** | Strong shows volume and 1RM progression charts. Hevy shows muscle group volume over time. Visualizing progress is the payoff for diligent logging. | High | Line charts for weight, volume, estimated 1RM over time per exercise. Requires charting library. Defer to later phase -- functional without it. |
| **Workout notes** | Strong and Hevy support adding free-text notes to workouts and individual exercises. Useful for recording conditions, injuries, form cues. | Low | Free-text field on workout level. Optional free-text per exercise. Stored with workout history. |
| **Exercise instructions / demo images** | JEFIT has 1400+ exercises with HD video demos. Hevy has 3D animations. Helps beginners with form. | High | Requires sourcing or creating content for each exercise. Significant asset investment. Could use simple text descriptions as lightweight alternative. |
| **Plate calculator** | Strong and Hevy offer plate calculators showing which plates to load on the barbell. Eliminates mental math, especially useful for warm-up progressions. | Medium | Input target weight, output plate configuration per side. Configurable available plates. Nice but not critical for MVP. |
| **Warm-up calculator** | Strong offers automatic warm-up weight suggestions based on working weight. Saves time and reduces injury risk. | Medium | Input working weight, output progressive warm-up sets (e.g., bar only, 50%, 70%, 85%). Algorithm-based, not complex but niche. |
| **Workout duration tracking** | Most apps track start time, end time, and total duration of each workout automatically. | Low | Auto-start timer when workout begins. Calculate duration on finish. Display on history entries. |
| **Calendar view of workout history** | Hevy shows workouts on a calendar with blue dots on training days. Provides quick visual of training frequency and consistency. | Medium | Calendar grid with indicators on days with workouts. Tap day to see workout. Nice for motivation but not core functionality. |
| **RPE/RIR tracking** | Strong and RepLog support Rate of Perceived Exertion (RPE) or Reps in Reserve (RIR) per set. Used by intermediate-advanced lifters for autoregulation. | Low | Optional numeric field (RPE: 1-10 scale, or RIR: 0-5) per set. Simple to implement, niche audience. |

## Anti-Features

Features to explicitly NOT build for this prototype. Either they are out of scope per PROJECT.md, add complexity without proportional value for a university project, or are actively disliked by the target audience.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Social features (feed, sharing, following)** | Hevy's main differentiator but requires backend infrastructure. PROJECT.md is local-only. Reddit users frequently cite social features as unwanted bloat in workout trackers. | Focus on the Strong model: minimalist, personal logging tool. Social can be a future milestone with backend. |
| **AI workout generation / auto-programming** | JEFIT and Fitbod offer AI-powered suggestions. Requires ML infrastructure, training data, and backend. Out of scope per PROJECT.md (F6). | Let users create their own templates. The app is a tracker, not a coach. |
| **Nutrition tracking** | Bundling nutrition into workout apps is a common complaint. Users prefer dedicated apps (MyFitnessPal). Out of scope per PROJECT.md (F2). | Keep the bottom nav placeholder for Nutrition but leave it non-functional for this milestone. |
| **Gamification (badges, streaks, XP)** | Out of scope per PROJECT.md (F4). Reddit users cite excessive gamification as annoying bloat. Adds no value to core workout logging. | Focus on intrinsic motivation via PRs and progress visibility, not extrinsic rewards. |
| **Smartwatch companion app** | Apple Watch and Wear OS integration is a differentiator for Strong/Hevy/JEFIT, but building a watch app is a separate significant development effort for a university project timeline. | Phone-only for prototype. Watch support is a future milestone if ever. |
| **Cloud sync / backend** | Requires server infrastructure. PROJECT.md explicitly defers Spring Boot backend. Local-only for prototype. | Use local storage (Room or SQLDelight). Design data model to be sync-ready for future backend integration, but don't build sync. |
| **Pre-built workout program library** | JEFIT ships hundreds of community routines. Requires content curation, database, and potentially licensing. Users who want programs already have them from coaches or the internet. | Ship with an empty template list. Users create their own. Optionally seed 2-3 example templates for demo purposes. |
| **Advanced analytics / body measurements** | Strong PRO offers advanced charts, body measurements tracking. High complexity charting, orthogonal to core logging flow. | Basic history view and previous-performance display provide the core value. Charts can be a future phase. |
| **CSV/JSON data export** | Important for production apps (data portability). Unnecessary for university prototype. | Skip for now. Design data model cleanly so export could be added trivially later. |
| **Exercise video demonstrations** | JEFIT has HD videos for 1400+ exercises. Massive content investment. Not feasible for a prototype. | Text-only exercise names with muscle group tags. Optionally add static placeholder images later. |

## Feature Dependencies

```
Exercise Catalog ──────────────────────────────────────┐
   │                                                    │
   ├── Custom Exercise Creation (extends catalog)       │
   │                                                    │
   ▼                                                    │
Template CRUD (requires exercises to exist) ────────────┤
   │                                                    │
   ▼                                                    │
Start Workout from Template ────────────────────────────┤
   │                                                    │
   ├── Start Empty Workout (parallel, also needs catalog)
   │                                                    │
   ▼                                                    │
Set Logging: weight + reps (core interaction) ──────────┤
   │                                                    │
   ├── Mark Sets Complete (enhances set logging)        │
   │   │                                                │
   │   └── Rest Timer (triggered by set completion)     │
   │                                                    │
   ├── Workout Progress Indicator (reads set state)     │
   │                                                    │
   ├── Set Type Tags (metadata on sets, optional)       │
   │                                                    │
   └── Workout Notes (metadata on workout, optional)    │
                                                        │
   ▼                                                    │
Save Completed Workout (finalize session) ──────────────┤
   │                                                    │
   ├── Save Workout as Template (post-save action)      │
   │                                                    │
   ▼                                                    │
Workout History List (reads saved workouts) ────────────┘
   │
   ├── Previous Performance Display (reads history during active workout)
   │
   ├── Personal Records Tracking (computed from history)
   │
   └── Estimated 1RM (computed from set data)
```

**Critical path for MVP:**
Exercise Catalog -> Template CRUD -> Start Workout -> Set Logging + Mark Complete + Rest Timer -> Save Workout -> Workout History -> Previous Performance Display

## MVP Recommendation

### Must Ship (Phase 1 Core)

These are the absolute minimum to have a usable workout tracker. Without any one of these, the app fails to serve its core purpose.

1. **Exercise catalog** (seeded, ~50-100 exercises with muscle group categorization)
2. **Template CRUD** (create/edit/delete workout templates with exercises, target sets/reps/weight, rest periods)
3. **Start workout from template** (select template, load into active session)
4. **Set logging** (enter weight + reps per set, mark sets complete)
5. **Rest timer** (auto-start countdown on set completion, configurable duration, audio/vibration alert)
6. **Workout progress indicator** (current exercise/set tracking)
7. **Save completed workout** (persist to local storage with date, duration, all set data)
8. **Workout history** (list of past workouts, tap for detail view)
9. **Unit support** (kg/lbs toggle)

This maps directly to the gymtracker FSM flow: Template Selection -> SET N -> Reps/Weight Entry -> Rest Timer -> Next Set/Exercise -> Finish -> Save.

### Should Ship (Phase 1 Polish)

These make the app genuinely useful day-to-day rather than just functional. Add after core flow works end-to-end.

1. **Previous performance display** (show last session's values during logging -- this is what makes progressive overload practical)
2. **Custom exercise creation** (users will immediately need exercises not in the seed catalog)
3. **Start empty workout** (for ad-hoc sessions)
4. **Save workout as template** (natural follow-on from empty workout)
5. **Workout duration tracking** (automatic, near-zero effort)
6. **Workout notes** (free text on workout and exercise level)

### Defer (Future Phases)

1. **Set type tags** -- nice but not essential for logging
2. **Supersets** -- complex interaction pattern, defer until core flow is solid
3. **Personal records tracking** -- requires history analysis, valuable but not blocking
4. **Estimated 1RM** -- simple formula but needs UI surface, defer
5. **Progress charts** -- requires charting library integration, high effort relative to value for prototype
6. **Plate calculator** -- nice utility, not core
7. **Calendar view** -- visual nicety, not core
8. **RPE/RIR** -- niche audience feature

## Sources

- [Strong App - Official Site](https://www.strong.app/)
- [Strong Help Center - Templates](https://help.strongapp.io/article/105-about-templates)
- [Strong Help Center - First Workout](https://help.strongapp.io/article/229-my-first-workout)
- [Hevy App - Feature List](https://www.hevyapp.com/features/)
- [Hevy - Workout Set Types](https://www.hevyapp.com/features/workout-set-types/)
- [Hevy - Rest Timer](https://www.hevyapp.com/features/workout-rest-timer/)
- [Hevy - Previous Workout Values](https://www.hevyapp.com/features/track-exercises/)
- [JEFIT - Official Site](https://www.jefit.com)
- [Best Workout Log Apps 2026: Hevy vs Strong vs JEFIT vs RepLog](https://www.replog.co.uk/blog/best-workout-log-apps-2026/)
- [12 Essential Features for Workout Tracking Apps](https://setgraph.app/ai-blog/app-to-track-my-workouts)
- [Best Workout Tracker App Reddit Recommendations](https://setgraph.app/ai-blog/best-workout-tracker-app-reddit)
- [Best Gym Workout Tracker Apps of 2026 - JEFIT Guide](https://www.jefit.com/wp/guide/best-gym-workout-tracker-apps-of-2026-top-5-reviewed-and-compared-for-every-fitness-goal/)
- [Strong App Review 2026 - PRPath](https://www.prpath.app/blog/strong-app-review-2026.html)
- [Strong vs Hevy Comparison 2026 - GymGod](https://gymgod.app/blog/strong-vs-hevy)

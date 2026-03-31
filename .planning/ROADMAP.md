# Roadmap: PumpernickelApp

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-29)
- ✅ **v1.1 Workout Polish & Firmware Parity** — Phases 5-10 (shipped 2026-03-31)
- 📋 **v1.5 Android Material 3 UI** — Phases 11-14

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-29</summary>

- [x] Phase 1: Foundation & Exercise Catalog (3/3 plans) — completed 2026-03-28
- [x] Phase 2: Template Management (3/3 plans) — completed 2026-03-28
- [x] Phase 3: Workout Session (3/3 plans) — completed 2026-03-28
- [x] Phase 4: History & Settings (3/3 plans) — completed 2026-03-29

</details>

<details>
<summary>✅ v1.1 Workout Polish & Firmware Parity (Phases 5-10) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Scroll Wheel Pickers & Auto-Increment (2/2 plans) — completed 2026-03-29
- [x] Phase 6: Personal Best Display (1/1 plan) — completed 2026-03-29
- [x] Phase 7: Post-Workout Recap & Edit (1/1 plan) — completed 2026-03-29
- [x] Phase 8: Mid-Workout Exercise Reorder (2/2 plans) — completed 2026-03-29
- [x] Phase 9: Abandon Guards & Context Menu (1/1 plan) — completed 2026-03-30
- [x] Phase 10: Minimal Set Screen & UI Polish (2/2 plans) — completed 2026-03-30

</details>

### v1.5 Android Material 3 UI

### Phase 11: Android Shell & Navigation
**Requirements:** ANDROID-01, ANDROID-02
**Goal:** Bootstrap the Android app with Material 3 theme, bottom navigation, navigation graph with type-safe routes, and Koin DI wiring — making the app runnable with placeholder screens.
**Plans:** 1/1 plans complete
Plans:
- [x] 11-01-PLAN.md — Build config, Koin init, Material 3 theme, NavigationBar with 3 tabs, type-safe routes, placeholder screens

### Phase 12: Exercise Catalog & Templates
**Requirements:** ANDROID-03, ANDROID-04
**Depends on:** Phase 11
**Goal:** Port exercise catalog (search, detail, create) and template management (list, editor, exercise picker) screens to Jetpack Compose with Material 3 components.
**Plans:** 2/2 plans complete
Plans:
- [x] 12-01-PLAN.md — ExerciseCatalogScreen, ExerciseDetailScreen, CreateExerciseScreen + route fix
- [x] 12-02-PLAN.md — TemplateListScreen, TemplateEditorScreen, ExercisePickerScreen, WorkoutEmptyStateScreen

### Phase 13: Workout Session Core
**Requirements:** ANDROID-05, ANDROID-06, ANDROID-07
**Depends on:** Phase 12
**Goal:** Port the complete workout execution flow — active session with custom drum picker set entry, rest timer, exercise overview bottom sheet, abandon guards, post-workout recap with edit, and finished state.
**Plans:** 4/4 plans complete
Plans:
- [x] 13-01-PLAN.md — Custom drum/wheel picker composable with snap fling behavior (Wave 1)
- [x] 13-02-PLAN.md — WorkoutSessionScreen Active state: set entry, rest timer, toolbar menu, abandon dialog, nav wiring (Wave 2)
- [x] 13-03-PLAN.md — ExerciseOverviewSheet with Completed/Current/Up Next sections, move reorder, skip (Wave 3)
- [x] 13-04-PLAN.md — Reviewing state (recap with tap-to-edit) and Finished state (summary + Done) (Wave 4)

### Phase 14: History, Settings & Anatomy
**Requirements:** ANDROID-08, ANDROID-09
**Depends on:** Phase 11
**Goal:** Port workout history, settings, and anatomy picker with Canvas-drawn body maps to Jetpack Compose.
**Plans:** 0 plans
Plans:
- [ ] 14-01-PLAN.md — WorkoutHistoryListScreen, WorkoutHistoryDetailScreen, SettingsScreen
- [ ] 14-02-PLAN.md — AnatomyPickerScreen with Compose Canvas front/back body drawings and touch region detection

## v1.5 Dependency Graph

```
Phase 11 ──► Phase 12 ──► Phase 13
Phase 11 ──► Phase 14 (independent of 12/13)
```

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Exercise Catalog | v1.0 | 3/3 | Complete | 2026-03-28 |
| 2. Template Management | v1.0 | 3/3 | Complete | 2026-03-28 |
| 3. Workout Session | v1.0 | 3/3 | Complete | 2026-03-28 |
| 4. History & Settings | v1.0 | 3/3 | Complete | 2026-03-29 |
| 5. Scroll Wheel Pickers & Auto-Increment | v1.1 | 2/2 | Complete | 2026-03-29 |
| 6. Personal Best Display | v1.1 | 1/1 | Complete | 2026-03-29 |
| 7. Post-Workout Recap & Edit | v1.1 | 1/1 | Complete | 2026-03-29 |
| 8. Mid-Workout Exercise Reorder | v1.1 | 2/2 | Complete | 2026-03-29 |
| 9. Abandon Guards & Context Menu | v1.1 | 1/1 | Complete | 2026-03-30 |
| 10. Minimal Set Screen & UI Polish | v1.1 | 2/2 | Complete | 2026-03-30 |
| 11. Android Shell & Navigation | v1.5 | 1/1 | Complete    | 2026-03-31 |
| 12. Exercise Catalog & Templates | v1.5 | 2/2 | Complete    | 2026-03-31 |
| 13. Workout Session Core | v1.5 | 4/4 | Complete    | 2026-03-31 |
| 14. History, Settings & Anatomy | v1.5 | 0/2 | Not started | — |

# Requirements — v1.5 Android Material 3 UI

**Milestone:** v1.5
**Created:** 2026-03-31
**Source:** iOS app feature parity — port all SwiftUI screens to Jetpack Compose with Material 3
**Confidence:** HIGH — all features already implemented in iOS, this is a 1:1 UI port

## Categories

### Android Foundation

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| ANDROID-01 | Android app launches with Material 3 theme, accent green (#66BB6A), and bottom navigation (Workout/Overview/Nutrition) | Must | App opens to Workout tab, bottom nav switches tabs, theme matches iOS accent color |
| ANDROID-02 | Navigation graph supports type-safe routes with back stack handling across all screens | Must | Forward/back navigation works correctly for all screen transitions; deep links maintain proper back stack |

### Exercise & Templates

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| ANDROID-03 | Exercise catalog with search, detail view, anatomy region display, and custom exercise creation — identical to iOS | Must | Search filters 873 exercises, detail shows muscle groups, create form validates and saves, anatomy picker renders front/back body with touch regions |
| ANDROID-04 | Template management with list, editor, exercise picker, drag reorder — identical to iOS | Must | Create/edit/delete templates, add exercises via picker, reorder exercises with drag, launch workout from template |

### Workout Session

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| ANDROID-05 | Custom drum/wheel picker for reps (0–50) and weight (0–1000, step 2.5kg) that mimics iOS scroll wheel feel | Must | Picker scrolls with fling physics, snaps to values, displays correctly in kg/lbs modes, matches iOS scroll wheel UX |
| ANDROID-06 | Full workout session flow — Active state with set entry, rest timer, auto-increment, PB display, toolbar menu, abandon guards | Must | Complete a workout start-to-finish: log sets, rest timer counts down, auto-increment fills next set, PB shows, menu works, abandon dialog with save/discard/cancel |
| ANDROID-07 | Exercise overview bottom sheet with sections (Completed/Current/Up Next), drag reorder, skip; post-workout recap with edit; finished state | Must | Overview sheet shows exercise progress, drag reorders pending, skip advances; recap shows all sets with tap-to-edit; finished shows summary |

### History, Settings & Anatomy

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| ANDROID-08 | Workout history list and detail views, settings screen with kg/lbs toggle — identical to iOS | Must | History shows completed workouts chronologically, detail shows all exercises/sets, kg/lbs toggle applies globally |
| ANDROID-09 | Anatomy picker with Canvas-drawn front/back body maps and touch-based muscle region selection | Must | Body outline renders with muscle groups highlighted, touch selects region, selected region filters exercises |

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| Custom drum picker over Material text fields | User preference: mimic iOS scroll wheel feel on Android for consistent cross-platform UX |
| Material 3 NavigationBar (not NavigationRail) | Phone-first layout matching iOS tab bar; NavigationRail for tablets is out of scope |
| Compose Canvas for anatomy (not WebView/SVG) | Keep it native; iOS uses Shape/Path, Android uses Canvas/Path — same data, different renderer |
| Material 3 BottomSheet for exercise overview | Direct equivalent of iOS .sheet presentation; Material 3 has ModalBottomSheet composable |
| Material 3 AlertDialog for abandon guards | Direct equivalent of iOS .confirmationDialog |

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tablet/foldable layout | Phone-first, matching iOS scope |
| Dynamic color (Material You wallpaper theming) | Keep consistent brand color; can add later |
| Android widgets | Not in iOS version |
| Wear OS companion | Not in iOS version |
| Notifications | Not in iOS version |

## Dependencies

- iOS reference: `iosApp/iosApp/Views/` — 21 SwiftUI files defining all screen layouts and interactions
- Shared KMP module: All ViewModels, repositories, Room DB, domain models already multiplatform
- Android platform setup: `androidApp/` — Koin Android init, Room Android database builder already exist
- Compose Multiplatform 1.10.3 + Material 3 components
- Navigation Compose 2.9.2 for Android navigation graph

## Traceability

| Requirement | iOS Reference | Risk |
|-------------|---------------|------|
| ANDROID-01 | MainTabView.swift, Color+App.swift | Low — standard Material 3 setup |
| ANDROID-02 | NavigationStack per tab, NavigationLink | Low — Navigation Compose well-documented |
| ANDROID-03 | ExerciseCatalogView, ExerciseDetailView, CreateExerciseView, AnatomyPickerView | Medium — anatomy Canvas drawing needs porting |
| ANDROID-04 | TemplateListView, TemplateEditorView, ExercisePickerView | Low — standard list/form screens |
| ANDROID-05 | iOS UIPickerView(.wheel) | **High** — custom composable needed, fling physics and snap behavior |
| ANDROID-06 | WorkoutSessionView (766 lines), RestTimerView, WorkoutSetRow | **High** — largest screen, complex state machine |
| ANDROID-07 | ExerciseOverviewSheet, WorkoutFinishedView | Medium — bottom sheet with drag reorder |
| ANDROID-08 | WorkoutHistoryListView, WorkoutHistoryDetailView, SettingsView | Low — simple list/detail screens |
| ANDROID-09 | AnatomyPickerView, AnatomyFrontShape, AnatomyBackShape, MuscleRegionPaths | **High** — Canvas path data porting |

---
*Requirements approved: 2026-03-31*

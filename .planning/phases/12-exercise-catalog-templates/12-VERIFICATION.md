---
phase: 12-exercise-catalog-templates
verified: 2026-03-31T18:00:00Z
status: human_needed
score: 12/13 must-haves verified
human_verification:
  - test: "Confirm anatomy picker deferral is acceptable for ANDROID-03 sign-off"
    expected: "Phase 12 accepted without anatomy picker; ANDROID-03 marked fully satisfied when Phase 14 delivers it, or the anatomy picker UAT criterion is explicitly removed from Phase 12 scope"
    why_human: "REQUIREMENTS-v1.5.md ANDROID-03 UAT criteria includes 'anatomy picker renders front/back body with touch regions'. The Phase 12-01-PLAN.md explicitly defers this to Phase 14. The requirement text itself does not partition the anatomy picker into a separate phase. A human decision is needed on whether this UAT criterion counts against Phase 12 or is formally deferred."
  - test: "Confirm move-up/down button reorder satisfies ANDROID-04 for Phase 12"
    expected: "Phase 12 accepted with move-up/down button reorder; drag-and-drop remains a Phase 14 enhancement, or ANDROID-04 UAT explicitly updated to accept buttons"
    why_human: "REQUIREMENTS-v1.5.md ANDROID-04 UAT says 'reorder exercises with drag'. The implementation uses move-up/down arrow IconButtons (explicitly decided in 12-02 plan as pragmatic alternative to external drag library). Both the plan and summary document this. A human should confirm the prototype acceptance bar permits this deviation."
---

# Phase 12: Exercise Catalog & Templates Verification Report

**Phase Goal:** Port exercise catalog (search, detail, create) and template management (list, editor, exercise picker) screens to Jetpack Compose with Material 3 components.
**Verified:** 2026-03-31T18:00:00Z
**Status:** human_needed — 12/13 must-haves verified; 2 UAT criterion deviations require human sign-off
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can browse the full exercise list with search filtering | VERIFIED | ExerciseCatalogScreen.kt:79 — OutlinedTextField bound to `viewModel.onSearchQueryChanged()`; ViewModel uses `repository.searchExercises()` via Room DAO |
| 2 | User can filter exercises by muscle group via horizontal chip row | VERIFIED | ExerciseCatalogScreen.kt:94-117 — `LazyRow` of `FilterChip` for all `MuscleGroup.entries`; single-select toggle calls `viewModel.onMuscleGroupSelected()` |
| 3 | User can tap an exercise to see its detail (muscles, metadata, instructions) | VERIFIED | ExerciseCatalogScreen.kt:148-151 — `navigate(ExerciseDetailRoute(exerciseId = exercise.id))`; ExerciseDetailScreen.kt shows primary/secondary muscles, metadata table, numbered instructions |
| 4 | User can create a custom exercise via the FAB and form | VERIFIED | ExerciseCatalogScreen.kt:59-69 — FAB navigates to `CreateExerciseRoute`; CreateExerciseScreen.kt has name, muscle group, equipment, category fields all bound to `CreateExerciseViewModel`; save calls `viewModel.createExercise()` |
| 5 | Navigation between catalog, detail, and create screens works with proper back stack | VERIFIED | MainScreen.kt:120-132 — all three exercise routes registered with typed `composable<>` entries in workout NavHost; back via `navController.popBackStack()` |
| 6 | User sees empty state when no templates exist, with Create Template button | VERIFIED | TemplateListScreen.kt:95-100 — `WorkoutEmptyStateScreen` shown when `templates.isEmpty()`; WorkoutEmptyStateScreen.kt has "No Templates Yet" title and "Create Template" button wired to navigate `TemplateEditorRoute` |
| 7 | User can see template list with name, exercise count, and start workout button | VERIFIED | TemplateListScreen.kt:147-176 — `ListItem` with `headlineContent` (template.name), `supportingContent` (exercise count string), `trailingContent` (PlayArrow icon navigating `WorkoutSessionRoute`) |
| 8 | User can create a new template via FAB | VERIFIED | TemplateListScreen.kt:80-93 — FAB shown when `templates.isNotEmpty()`; navigates to `TemplateEditorRoute(templateId = null)` |
| 9 | User can tap a template to edit it (name + exercises with inline targets) | VERIFIED | TemplateListScreen.kt:171-176 — `clickable { navigate(TemplateEditorRoute(templateId = template.id)) }`; TemplateEditorScreen.kt has `OutlinedTextField` for name and `CompactTargetField` rows for sets/reps/weight/rest |
| 10 | User can add exercises to a template via exercise picker | VERIFIED | TemplateEditorScreen.kt:146-153 — "Add Exercise" TextButton navigates `ExercisePickerRoute`; MainScreen.kt:97-111 — ExercisePicker composable calls `editorViewModel.addExercise(id, name, muscles)` via parent back stack entry ViewModel sharing |
| 11 | User can reorder exercises in a template | PARTIAL | TemplateEditorScreen.kt:252-283 — move-up/down `IconButton` controls call `viewModel.moveExercise(index, index-1)` / `viewModel.moveExercise(index, index+2)`; ANDROID-04 UAT specifies drag reorder; implementation uses buttons (plan-documented deferral) |
| 12 | User can delete a template with swipe-to-dismiss and confirmation | VERIFIED | TemplateListScreen.kt:108-213 — `SwipeToDismissBox` (EndToStart) triggers `AlertDialog` with "Delete Template?" confirmation; confirm calls `viewModel.deleteTemplate()` |
| 13 | User can launch a workout from a template (navigates to WorkoutSessionRoute) | VERIFIED | TemplateListScreen.kt:159-166 — PlayArrow `IconButton` navigates `WorkoutSessionRoute(templateId = template.id)`; MainScreen.kt:112-119 — route registered (placeholder for Phase 13) |

**Score: 12/13 truths verified** (Truth 11 is PARTIAL — functional reorder exists via buttons, UAT specifies drag)

---

### Required Artifacts

| Artifact | Min Lines | Actual Lines | Status | Details |
|----------|-----------|--------------|--------|---------|
| `androidApp/.../screens/ExerciseCatalogScreen.kt` | 80 | 168 | VERIFIED | Search bar, FilterChip row, equipment-icon ListItems, FAB wired to CreateExerciseRoute |
| `androidApp/.../screens/ExerciseDetailScreen.kt` | 60 | 223 | VERIFIED | Primary/secondary muscle chips, metadata table, numbered instructions, loading indicator |
| `androidApp/.../screens/CreateExerciseScreen.kt` | 80 | 233 | VERIFIED | Name OutlinedTextField, MuscleGroup ExposedDropdownMenuBox, equipment/category pickers, form validation, SharedFlow save result |
| `androidApp/.../screens/TemplateListScreen.kt` | 80 | 214 | VERIFIED | SwipeToDismissBox, AlertDialog, FAB, PlayArrow trailing, tap-to-edit, empty state delegation |
| `androidApp/.../screens/TemplateEditorScreen.kt` | 100 | 356 | VERIFIED | Name field, exercise list with inline CompactTargetField rows, move-up/down reorder, per-exercise delete, save with result handling |
| `androidApp/.../screens/ExercisePickerScreen.kt` | 60 | 156 | VERIFIED | Search, FilterChip row, tap-to-select ListItems with AddCircle trailing icon, onExerciseSelected callback |
| `androidApp/.../screens/WorkoutEmptyStateScreen.kt` | 30 | 78 | VERIFIED | FitnessCenter icon, "No Templates Yet" title, description text, "Create Template" button |
| `androidApp/.../navigation/Routes.kt` | — | 19 | VERIFIED | `ExerciseDetailRoute(val exerciseId: String)` — String type confirmed (Long bug fixed) |
| `androidApp/.../navigation/MainScreen.kt` | — | 155 | VERIFIED | All 7 workout routes registered; WorkoutPlaceholderScreen removed; TemplateListRoute is startDestination |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ExerciseCatalogScreen.kt | ExerciseCatalogViewModel | `koinViewModel()` + `collectAsState()` | WIRED | Line 49: `val viewModel: ExerciseCatalogViewModel = koinViewModel()` |
| ExerciseCatalogScreen.kt | ExerciseDetailRoute | `navigate(ExerciseDetailRoute(exerciseId))` | WIRED | Line 149: `navController.navigate(ExerciseDetailRoute(exerciseId = exercise.id))` |
| ExerciseDetailScreen.kt | ExerciseDetailViewModel | `viewModel.loadExercise(exerciseId)` | WIRED | Line 49: `LaunchedEffect(exerciseId) { viewModel.loadExercise(exerciseId) }` |
| CreateExerciseScreen.kt | CreateExerciseViewModel | `koinViewModel()` + `collectAsState()` | WIRED | Line 44: `val viewModel: CreateExerciseViewModel = koinViewModel()` |
| MainScreen.kt NavHost | ExerciseCatalogScreen, ExerciseDetailScreen, CreateExerciseScreen | `composable<Route>{}` | WIRED | Lines 120-132: all three composable entries present |
| TemplateListScreen.kt | TemplateListViewModel | `koinViewModel()` + `collectAsState()` | WIRED | Line 52-53 |
| TemplateListScreen.kt | TemplateEditorRoute + WorkoutSessionRoute | `navController.navigate()` | WIRED | Lines 83, 98, 162, 174 |
| TemplateEditorScreen.kt | TemplateEditorViewModel | `koinViewModel()` + `collectAsState()` | WIRED | Line 58 |
| TemplateEditorScreen.kt | ExercisePickerRoute | `navigate(ExercisePickerRoute(templateId))` | WIRED | Line 147-149 |
| ExercisePickerScreen.kt | ExerciseCatalogViewModel | `koinViewModel()` + `collectAsState()` | WIRED | Line 46 |
| MainScreen.kt ExercisePickerRoute | TemplateEditorViewModel via parent back stack | `koinViewModel(viewModelStoreOwner = parentEntry)` | WIRED | Lines 98-110: `editorViewModel.addExercise(id, name, muscles)` confirmed |
| MainScreen.kt NavHost | TemplateListScreen replaces WorkoutPlaceholderScreen | `composable<TemplateListRoute> { TemplateListScreen(...) }` | WIRED | Line 87-89; WorkoutPlaceholderScreen import absent |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ExerciseCatalogScreen.kt | `exercises` (StateFlow) | `ExerciseCatalogViewModel` → `repository.searchExercises()` → `ExerciseDao.searchExercises()` | Yes — Room DAO query | FLOWING |
| ExerciseDetailScreen.kt | `exercise` (StateFlow) | `ExerciseDetailViewModel` → `repository.getExerciseById(id)` → `ExerciseDao.getExerciseById()` | Yes — Room DAO query | FLOWING |
| CreateExerciseScreen.kt | `equipmentOptions`, `categoryOptions` | `CreateExerciseViewModel` → `repository.getDistinctEquipment()` / `getDistinctCategories()` | Yes — Room DAO distinct queries | FLOWING |
| TemplateListScreen.kt | `templates` (StateFlow) | `TemplateListViewModel` → `repository.getAllTemplates()` → Room DAO | Yes — Room DAO query | FLOWING |
| TemplateEditorScreen.kt | `exercises` (StateFlow<List<TemplateExercise>>) | `TemplateEditorViewModel` → `repository` with local mutable state + persist | Yes — VM manages list, persists on save | FLOWING |

All 5 ViewModels registered in `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` lines 58-62.

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points available without building and deploying to Android emulator/device. The project requires `./gradlew androidApp:installDebug` and UI interaction. Compilation was verified by the executing agent per SUMMARY self-checks.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| ANDROID-03 | 12-01-PLAN.md | Exercise catalog with search, detail view, anatomy region display, and custom exercise creation | PARTIAL — anatomy picker deferred | Search (Truth 1 VERIFIED), detail with muscle groups (Truth 3 VERIFIED), create form (Truth 4 VERIFIED); anatomy picker Canvas drawing explicitly deferred to Phase 14 per 12-01-PLAN.md notes |
| ANDROID-04 | 12-02-PLAN.md | Template management with list, editor, exercise picker, drag reorder — identical to iOS | PARTIAL — drag reorder deferred | Template CRUD (Truths 6-13 VERIFIED except partial reorder); reorder uses move-up/down buttons not drag-and-drop; 12-02 plan explicitly documents this decision |

**Orphaned requirements check:** No additional requirements map to Phase 12 in REQUIREMENTS-v1.5.md beyond ANDROID-03 and ANDROID-04.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| TemplateListScreen.kt | 63, 71 | `TODO: Workout history / Settings — Phase 14` in TopAppBar icon handlers | Info | Navigation icon buttons are present but non-functional; Phase 14 scope as per plan. Does not block core template CRUD functionality. |
| TemplateListScreen.kt | 95-100 | `WorkoutEmptyStateScreen` called without `innerPadding` from Scaffold | Warning | When templates list is empty, the empty state fills the Scaffold content area without the TopAppBar's `innerPadding` applied. The content may render behind the TopAppBar on some devices. The non-empty `LazyColumn` branch correctly applies `padding(innerPadding)`. |

**Classification:**
- `TODO` items on Phase 14 stub buttons: Info — does not block Phase 12 goal
- Missing `innerPadding` on empty state: Warning — potential visual overlap with TopAppBar; does not prevent template creation but may cause minor layout issue

---

### Human Verification Required

#### 1. Anatomy Picker — ANDROID-03 UAT Criterion Scope Decision

**Test:** Review REQUIREMENTS-v1.5.md ANDROID-03 UAT criteria against Phase 12 deliverables
**Expected:** Project owner accepts Phase 12 as satisfying ANDROID-03 with anatomy picker deferred to Phase 14, OR formally updates ANDROID-03 to split the anatomy picker into a separate requirement covered by Phase 14
**Why human:** ANDROID-03 UAT reads: "anatomy picker renders front/back body with touch regions". Phase 12-01-PLAN.md notes explicitly state "Anatomy picker (Canvas body drawing) is Phase 14 scope." The Phase 14 ROADMAP confirms ANDROID-09 covers anatomy Canvas rendering. A human must decide whether ANDROID-03 is considered satisfied at Phase 12 or held open pending Phase 14.

#### 2. Drag Reorder — ANDROID-04 UAT Criterion Acceptance

**Test:** Review TemplateEditorScreen move-up/down button reorder against ANDROID-04 UAT
**Expected:** Project owner accepts move-up/down IconButtons as meeting ANDROID-04 for the prototype, OR confirms drag-and-drop must be delivered before ANDROID-04 can be signed off
**Why human:** ANDROID-04 UAT reads: "reorder exercises with drag". The 12-02-PLAN.md explicitly chose move-up/down buttons over drag-and-drop ("Compose LazyColumn lacks built-in drag reorder; avoids external reorderable library dependency for prototype scope"). This is a functional equivalent for prototype use but does not match the UAT verbatim.

---

### Gaps Summary

No blocking gaps found. All 7 screen files exist, are substantive (78–356 lines each), and are fully wired into the workout tab NavHost. All ViewModel connections trace to live Room DAO queries. The 4 documented commits (1c43122, f9dd83d, 7a31fe1, 49f6f6c) exist in git history.

Two items require human sign-off before ANDROID-03 and ANDROID-04 can be formally marked satisfied:

1. **Anatomy picker** — deferred to Phase 14 by plan design, but present in ANDROID-03 UAT. Needs scope decision.
2. **Drag reorder** — implemented as move-up/down buttons per plan decision, but ANDROID-04 UAT specifies drag. Needs acceptance confirmation.

One warning-level anti-pattern exists: `WorkoutEmptyStateScreen` in `TemplateListScreen` is called without applying Scaffold's `innerPadding`, which could cause content overlap with the TopAppBar on device. This is cosmetic and does not block functionality.

---

_Verified: 2026-03-31T18:00:00Z_
_Verifier: Claude (gsd-verifier)_

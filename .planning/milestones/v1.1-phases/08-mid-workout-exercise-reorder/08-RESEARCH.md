# Phase 8: Mid-Workout Exercise Reorder - Research

**Researched:** 2026-03-29
**Domain:** Workout session reorder (KMP ViewModel + Room migration + SwiftUI drag UI)
**Confidence:** HIGH

## Summary

Phase 8 adds two features to the active workout flow: (1) drag-reorder of pending exercises and (2) skip-exercise. The user decided to use in-memory list reorder (D-01) rather than the firmware's `exerciseOrder[]` indirection array, which simplifies the Kotlin implementation considerably. The exercises list within `WorkoutSessionState.Active` is reordered directly via `removeAt`/`add`, and only items after `currentExerciseIndex` can move.

Crash recovery requires persisting the current exercise order to Room. The user decided (D-07) to add an `exerciseOrder` text column to `active_sessions` storing a comma-separated list of exercise indices. This requires a Room schema migration from version 3 to 4. The existing codebase already has a v2-to-v3 AutoMigration precedent that can be followed exactly.

Skip exercise (D-05) is straightforward: advance `currentExerciseIndex` by 1. The skipped exercise retains 0 completed sets and is naturally excluded from saved history by the existing `saveReviewedWorkout()` filter. The SwiftUI side reuses the established `.onMove` pattern from `TemplateEditorView.swift` and extends the existing `ExerciseOverviewSheet` with reorder capability.

**Primary recommendation:** Implement in three layers: (1) Room migration + DAO updates, (2) ViewModel reorder/skip methods + crash recovery integration, (3) SwiftUI reorder UI on the exercise overview sheet.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use in-memory list reorder (swap items in the `exercises` list within `WorkoutSessionState.Active`) rather than the firmware's `exerciseOrder[]` indirection array.
- **D-02:** Only exercises after `currentExerciseIndex` (pending exercises) can be reordered. Completed exercises and the current exercise are locked in place.
- **D-03:** Use SwiftUI `.onMove` modifier with an `EditButton` pattern, matching the existing TemplateEditorView drag-reorder pattern.
- **D-04:** Reorder UI is accessed from the workout session screen. The exercise list visually distinguishes completed (greyed/checked), current (highlighted), and pending (draggable) exercises.
- **D-05:** Skip advances the cursor to the next exercise (`currentExerciseIndex + 1`). Skipped exercise retains 0 completed sets.
- **D-06:** Skip is available as an action button or menu item during the active workout -- not restricted to a context menu.
- **D-07:** Persist exercise order to Room via `exerciseOrder` text column on `active_sessions` storing comma-separated exercise indices. Room migration 3->4 required.
- **D-08:** On resume after crash, `resumeWorkout()` reads persisted exercise order and reconstructs the `exercises` list in that order. Pre-migration sessions fall back to template order.

### Claude's Discretion
- Visual styling of the reorder screen (follow existing workout screen patterns)
- Whether to use a sheet or inline section for the exercise reorder UI
- Animation details for drag-and-drop reorder
- Whether skip confirmation is needed (firmware skips immediately with no confirmation)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FLOW-03 | User can reorder pending exercises mid-workout via drag gesture | In-memory list reorder (D-01) + SwiftUI `.onMove` (D-03) on ExerciseOverviewSheet; only pending exercises movable (D-02) |
| FLOW-04 | Exercise reorder preserves completed set data and crash recovery integrity | Room migration 3->4 adds `exerciseOrder` column (D-07); `resumeWorkout()` reads persisted order (D-08); `exerciseIndex` in `active_session_sets` refers to template-original index, not position |
| FLOW-07 | User can skip current exercise and move to the next one | `skipExercise()` advances `currentExerciseIndex + 1` (D-05); standalone button (D-06); skipped exercises naturally excluded from saved history by existing `mapIndexedNotNull` filter |
</phase_requirements>

## Architecture Patterns

### Current Codebase Architecture (Reference)
```
shared/src/commonMain/kotlin/com/pumpernickel/
├── data/db/
│   ├── ActiveSessionEntity.kt         # MODIFY: add exerciseOrder column
│   ├── ActiveSessionSetEntity.kt      # NO CHANGE: exerciseIndex stays as template-original index
│   ├── WorkoutSessionDao.kt           # MODIFY: add updateExerciseOrder query
│   └── AppDatabase.kt                 # MODIFY: version 3->4, add AutoMigration
├── data/repository/
│   └── WorkoutRepository.kt           # MODIFY: add updateExerciseOrder(), update getActiveSession()
├── domain/model/
│   └── WorkoutSession.kt              # NO CHANGE
└── presentation/workout/
    └── WorkoutSessionViewModel.kt     # MODIFY: add reorderExercise(), skipExercise(), update resumeWorkout()

iosApp/iosApp/Views/Workout/
├── WorkoutSessionView.swift           # MODIFY: add skip button, wire reorder sheet
└── ExerciseOverviewSheet.swift        # MODIFY: add .onMove drag reorder with completed/current/pending sections
```

### Pattern 1: In-Memory List Reorder
**What:** Reorder exercises directly in the `exercises: List<SessionExercise>` within `WorkoutSessionState.Active` using `removeAt`/`add`.
**When to use:** When the reordered list IS the source of truth (as decided in D-01).
**Example:**
```kotlin
// Source: TemplateEditorViewModel.moveExercise() (existing pattern)
fun reorderExercise(from: Int, to: Int) {
    val active = _sessionState.value as? WorkoutSessionState.Active ?: return
    val pendingStart = active.currentExerciseIndex + 1
    // Convert from/to from pending-relative to absolute indices
    val absFrom = pendingStart + from
    val absTo = pendingStart + to
    if (absFrom < pendingStart || absFrom >= active.exercises.size) return
    if (absTo < pendingStart || absTo >= active.exercises.size) return

    val list = active.exercises.toMutableList()
    val item = list.removeAt(absFrom)
    list.add(absTo, item)

    _sessionState.value = active.copy(exercises = list)
    // Persist new order for crash recovery
    persistExerciseOrder(list)
}
```

### Pattern 2: Skip Exercise via Cursor Advance
**What:** Skip jumps the cursor forward without completing any sets.
**When to use:** When user wants to defer or skip the current exercise.
**Example:**
```kotlin
// Source: jumpToExercise() existing pattern + computeNextCursor() logic
fun skipExercise() {
    val active = _sessionState.value as? WorkoutSessionState.Active ?: return
    val nextIndex = active.currentExerciseIndex + 1
    if (nextIndex >= active.exercises.size) return  // Can't skip last exercise

    timerJob?.cancel()
    val nextExercise = active.exercises[nextIndex]
    val firstIncompleteSet = nextExercise.sets.indexOfFirst { !it.isCompleted }
        .let { if (it == -1) 0 else it }

    _sessionState.value = active.copy(
        currentExerciseIndex = nextIndex,
        currentSetIndex = firstIncompleteSet,
        restState = RestState.NotResting
    )
    _preFill.value = computePreFill(nextExercise, firstIncompleteSet)
    // Update Room cursor
    viewModelScope.launch {
        workoutRepository.updateCursor(nextIndex, firstIncompleteSet)
    }
}
```

### Pattern 3: Room AutoMigration with New Column
**What:** Add `exerciseOrder` TEXT column to `active_sessions` with a default value so AutoMigration handles it.
**When to use:** Adding a non-nullable column with a sensible default to an existing table.
**Example:**
```kotlin
// ActiveSessionEntity.kt - add column with @ColumnInfo defaultValue
@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val lastUpdatedMillis: Long,
    @ColumnInfo(defaultValue = "")  // Empty string = use template order on resume
    val exerciseOrder: String = ""  // "0,1,3,2,4" comma-separated
)

// AppDatabase.kt - version bump + AutoMigration
@Database(
    entities = [/* ... all entities ... */],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
```

### Pattern 4: Exercise Order Persistence (CSV String)
**What:** Store exercise order as comma-separated string in `active_sessions.exerciseOrder`.
**When to use:** After any reorder operation, to enable crash recovery.
**Example:**
```kotlin
// Persist order after reorder
private fun persistExerciseOrder(exercises: List<SessionExercise>) {
    viewModelScope.launch {
        // Map current exercise list to their original template indices
        // The exerciseOrder string represents the order of exercises by their
        // position in the original template
        val orderString = exercises.indices.joinToString(",")
        workoutRepository.updateExerciseOrder(orderString)
    }
}

// Resume: apply persisted order
fun resumeWorkout() {
    // ... existing code to build exercises from template ...
    val orderString = activeSession.exerciseOrder
    val orderedExercises = if (orderString.isNotEmpty()) {
        val indices = orderString.split(",").mapNotNull { it.toIntOrNull() }
        indices.mapNotNull { idx -> exercises.getOrNull(idx) }
    } else {
        exercises  // Fallback: template order (pre-migration sessions)
    }
    // ... overlay completed sets using exerciseId matching ...
}
```

### Anti-Patterns to Avoid
- **Using exerciseIndex position as identity:** After reorder, the `exerciseIndex` stored in `active_session_sets` refers to the template-original position, not the current list position. On resume, match completed sets by `exerciseId` (the string identifier), not by positional index. This is a critical correctness issue.
- **Reordering completed exercises:** D-02 explicitly locks completed and current exercises. Any reorder must restrict indices to `currentExerciseIndex + 1 ..< exercises.size`.
- **Forgetting to persist order on reorder:** Every call to `reorderExercise()` must persist the new order to Room before returning. If the app crashes between reorder and persist, the order is lost.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag-reorder gesture | Custom drag gesture recognizer | SwiftUI `.onMove` on ForEach in List | `.onMove` handles long-press, drag animation, insertion indicator, and scroll-during-drag automatically |
| List item move | Manual swap + index tracking | `Array.move(fromOffsets:toOffset:)` | SwiftUI's IndexSet-based move callback handles multi-selection edge cases |
| Schema migration | Manual SQL `ALTER TABLE` | Room `@AutoMigration` with `@ColumnInfo(defaultValue)` | AutoMigration generates correct ALTER TABLE ADD COLUMN with DEFAULT; tested by Room's migration verification |

## Common Pitfalls

### Pitfall 1: ExerciseIndex Identity After Reorder
**What goes wrong:** After reordering exercises, completed sets stored in `active_session_sets` with `exerciseIndex = 2` no longer correspond to the exercise at position 2 in the reordered list.
**Why it happens:** The `exerciseIndex` in Room was written when the exercise was at its original template position. Reordering changes list positions but not stored indices.
**How to avoid:** On resume, do NOT use positional index to overlay completed sets. Instead, use `exerciseId` (the string exercise identifier) to match completed sets to their exercises regardless of order. The `exerciseOrder` string tells you the display order; `exerciseId` on each `SessionExercise` tells you which completed sets belong to it.
**Warning signs:** After crash recovery, completed sets appear on the wrong exercises.

### Pitfall 2: SwiftUI onMove Index Space Mismatch
**What goes wrong:** SwiftUI `.onMove` provides indices relative to the ForEach's data source. If the ForEach only contains pending exercises (a slice of the full list), the indices need translation to the full exercise list before calling the ViewModel.
**Why it happens:** The reorder sheet shows all exercises but only pending ones have `.onMove`. The indices from `.onMove` are relative to the pending sublist.
**How to avoid:** Compute the offset (`currentExerciseIndex + 1`) and add it to the from/to indices before passing to the ViewModel's `reorderExercise()`.
**Warning signs:** Reorder moves the wrong exercise or crashes with index out of bounds.

### Pitfall 3: Skip on Last Exercise
**What goes wrong:** Attempting to skip when on the last exercise would advance `currentExerciseIndex` past the end of the list.
**Why it happens:** No bounds check before advancing.
**How to avoid:** Guard: `if (nextIndex >= active.exercises.size) return`. Disable the skip button in UI when on the last exercise. Firmware handles this the same way (no-op on last exercise, per `testReorder_SkipLastIsNoop`).
**Warning signs:** Array index out of bounds crash.

### Pitfall 4: Reorder Persisted but Cursor Not Updated
**What goes wrong:** After reorder, `currentExerciseIndex` in Room still points to the old position. If the reorder moved exercises around the cursor boundary, the cursor could point to a wrong exercise after crash recovery.
**Why it happens:** Reorder only changes pending exercises (after cursor), so `currentExerciseIndex` should remain unchanged. But the `exerciseOrder` string must be consistent with the cursor.
**How to avoid:** D-01 states that after reorder, `currentExerciseIndex` stays unchanged. Verify this invariant: only items at indices > currentExerciseIndex can move. The cursor index into the exercises list remains valid because items before it (completed + current) are not moved.
**Warning signs:** After crash + resume, cursor points to wrong exercise.

### Pitfall 5: Room AutoMigration Requires @ColumnInfo(defaultValue)
**What goes wrong:** AutoMigration fails at runtime with "NOT NULL constraint" error.
**Why it happens:** Adding a NOT NULL column without a default value means existing rows have no value for the new column. Room AutoMigration requires `@ColumnInfo(defaultValue = "...")` for non-nullable new columns.
**How to avoid:** Always pair the new entity field with `@ColumnInfo(defaultValue = "")` and a Kotlin default value `= ""`. The existing codebase v2->v3 AutoMigration succeeded because the migration was for adding entire new tables, not columns. This v3->v4 migration adds a column to an existing table, so the defaultValue annotation is mandatory.
**Warning signs:** Build succeeds but app crashes on first launch after update with migration error.

## Code Examples

### Room Entity Change (ActiveSessionEntity.kt)
```kotlin
// Source: Existing codebase pattern + Room @ColumnInfo docs
import androidx.room.ColumnInfo

@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val id: Long = 1,
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val lastUpdatedMillis: Long,
    @ColumnInfo(defaultValue = "")
    val exerciseOrder: String = ""  // comma-separated indices: "0,1,3,2,4"
)
```

### DAO Update Query (WorkoutSessionDao.kt)
```kotlin
// Source: Existing updateCursor pattern in WorkoutSessionDao
@Query("UPDATE active_sessions SET exerciseOrder = :order, lastUpdatedMillis = :updatedAt WHERE id = 1")
suspend fun updateExerciseOrder(order: String, updatedAt: Long)
```

### AppDatabase Migration (AppDatabase.kt)
```kotlin
@Database(
    entities = [/* all existing entities */],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun completedWorkoutDao(): CompletedWorkoutDao
}
```

### Repository Updates (WorkoutRepository.kt)
```kotlin
// Interface addition
suspend fun updateExerciseOrder(order: String)

// ActiveSessionData addition - add exerciseOrder field
data class ActiveSessionData(
    val templateId: Long,
    val templateName: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val startTimeMillis: Long,
    val completedSets: List<ActiveSessionSetData>,
    val exerciseOrder: String = ""  // new field
)

// Implementation
override suspend fun updateExerciseOrder(order: String) {
    workoutSessionDao.updateExerciseOrder(
        order = order,
        updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
    )
}

// Update getActiveSession to include exerciseOrder
override suspend fun getActiveSession(): ActiveSessionData? {
    val session = workoutSessionDao.getActiveSession() ?: return null
    val sets = workoutSessionDao.getSessionSets()
    return ActiveSessionData(
        // ... existing fields ...
        exerciseOrder = session.exerciseOrder
    )
}
```

### SwiftUI Reorder Sheet Pattern
```swift
// Source: TemplateEditorView.swift .onMove pattern + ExerciseOverviewSheet.swift
struct ExerciseOverviewSheet: View {
    let exercises: [SessionExercise]
    let currentExerciseIndex: Int32
    var onSelect: (Int32) -> Void
    var onMove: (Int, Int) -> Void   // NEW: from/to in pending-relative indices
    var onSkip: () -> Void           // NEW: skip current exercise

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                // Completed exercises section (non-movable)
                if currentExerciseIndex > 0 {
                    Section("Completed") {
                        ForEach(0..<Int(currentExerciseIndex), id: \.self) { index in
                            exerciseRow(exercise: exercises[index], index: index, style: .completed)
                        }
                    }
                }

                // Current exercise (non-movable)
                Section("Current") {
                    exerciseRow(
                        exercise: exercises[Int(currentExerciseIndex)],
                        index: Int(currentExerciseIndex),
                        style: .current
                    )
                }

                // Pending exercises (movable)
                let pendingStart = Int(currentExerciseIndex) + 1
                if pendingStart < exercises.count {
                    Section("Up Next") {
                        ForEach(Array(exercises[pendingStart...].enumerated()), id: \.offset) { relIdx, exercise in
                            exerciseRow(exercise: exercise, index: pendingStart + relIdx, style: .pending)
                        }
                        .onMove { source, destination in
                            if let from = source.first {
                                onMove(from, destination)
                            }
                        }
                    }
                }
            }
            .environment(\.editMode, .constant(.active))  // Always show drag handles
            .navigationTitle("Exercise Order")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
```

### ViewModel Skip Exercise
```kotlin
fun skipExercise() {
    timerJob?.cancel()
    val active = _sessionState.value as? WorkoutSessionState.Active ?: return
    val nextIndex = active.currentExerciseIndex + 1
    if (nextIndex >= active.exercises.size) return  // Last exercise: no-op

    val nextExercise = active.exercises[nextIndex]
    val firstIncompleteSet = nextExercise.sets
        .indexOfFirst { !it.isCompleted }
        .let { if (it == -1) 0 else it }

    _sessionState.value = active.copy(
        currentExerciseIndex = nextIndex,
        currentSetIndex = firstIncompleteSet,
        restState = RestState.NotResting
    )
    _preFill.value = computePreFill(nextExercise, firstIncompleteSet)

    viewModelScope.launch {
        workoutRepository.updateCursor(nextIndex, firstIncompleteSet)
    }
}
```

## Critical Design Decision: ExerciseIndex Semantics

The `active_session_sets.exerciseIndex` currently stores the **positional index** of the exercise in the template-order list. After reorder, the exercises list changes positions, but `exerciseIndex` in Room still refers to the original template position.

**Solution (from CONTEXT.md D-08):** On resume, the `exerciseOrder` string tells us the display order. For example, if template has exercises `[A, B, C, D]` and user reorders to `[A, C, B, D]`, the `exerciseOrder` is `"0,2,1,3"`. Completed sets for exercise B are stored with `exerciseIndex = 1` (B's original template position). When rebuilding:

1. Build exercises from template (original order: `[A, B, C, D]`)
2. Read `exerciseOrder = "0,2,1,3"` and reorder: `[A, C, B, D]`
3. Overlay completed sets: match `exerciseIndex` to template-original index, not display position

This means `exerciseIndex` in `active_session_sets` must continue to refer to the **template-original** index, even after reorder. When `completeSet()` persists to Room, it must store the **template-original** index of the exercise, not its current display position.

**Implementation approach:** Store a mapping from display position to template-original index. When the exercises list is `[A, C, B, D]` (reordered from `[A, B, C, D]`), the current `exerciseIndex` of 2 (display position of B) must be persisted as template-original index 1. This can be derived from the `exerciseOrder` string: `exerciseOrder[displayIndex]` gives the template-original index.

**Simpler alternative that the codebase should use:** Since D-01 says we reorder the actual list (not using indirection), we need to track each exercise's original template index. Add a field to the in-memory exercise tracking, or use `exerciseId` (the string identifier) to match completed sets on resume instead of positional index. Using `exerciseId` is more robust and avoids the index translation entirely.

**Recommended approach:** On resume, match completed sets to exercises by `exerciseId` rather than by `exerciseIndex` positional matching. This requires changing the `resumeWorkout()` overlay logic from:
```kotlin
// CURRENT: matches by positional index (breaks after reorder)
.filter { it.exerciseIndex == exIdx }
```
to:
```kotlin
// NEW: matches by exerciseId (works regardless of order)
.filter { it.exerciseId == exercise.exerciseId }
```

This requires adding `exerciseId` to `ActiveSessionSetEntity`. However, this is a second column addition in the same migration, which is feasible with AutoMigration. Alternatively, we can keep using positional indices if we store the original template index alongside each exercise in the reordered list.

**Simplest correct approach:** Keep `exerciseIndex` as-is (template-original position). When saving a completed set after reorder, look up the exercise's original template index from the `exerciseOrder` string or from a maintained mapping. On resume, build from template, then reorder per `exerciseOrder`, then overlay by matching `exerciseIndex` to template-original positions (which is what the current code already does before reorder is introduced -- it builds exercises from template in order, so `exIdx` matches template position).

The key insight: `resumeWorkout()` currently builds exercises from template in template order, then overlays completed sets by `exIdx`. After this phase, it must:
1. Build exercises from template in template order
2. Overlay completed sets by `exIdx` (same as before -- `exIdx` is template-original index)
3. THEN reorder the list according to `exerciseOrder`

This ordering means completed sets are overlaid BEFORE reorder, so `exerciseIndex` still refers to template position. This is the cleanest approach and requires no changes to `ActiveSessionSetEntity`.

## Open Questions

1. **completeSet() exerciseIndex after reorder**
   - What we know: After reorder, `active.currentExerciseIndex` refers to the display position, but `saveCompletedSet()` stores this as `exerciseIndex` in Room. After reorder, the exercise at display position 2 might have template-original index 3.
   - What's unclear: How to resolve the mismatch between display position and template-original index when persisting completed sets.
   - Recommendation: Maintain a `templateOriginalIndices: List<Int>` alongside the exercises list. When starting a workout, this is `[0, 1, 2, ...]`. When reordering, the indices list reorders in parallel. When saving a completed set, use `templateOriginalIndices[currentExerciseIndex]` instead of `currentExerciseIndex` directly. Alternatively, store `exerciseId` in `active_session_sets` (requires migration change). The `templateOriginalIndices` approach avoids schema changes beyond the already-planned `exerciseOrder` column.

2. **exerciseOrder string format**
   - What we know: D-07 specifies comma-separated exercise indices (e.g., "0,1,3,2,4").
   - What's unclear: Whether these are template-original indices or display-order indices.
   - Recommendation: Store as template-original indices in display order. So "0,2,1,3" means "first show template exercise 0, then 2, then 1, then 3." This matches the firmware `exerciseOrder[]` semantics and is what `resumeWorkout()` needs to reconstruct the display order.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Firmware `exerciseOrder[]` indirection | Direct list reorder (D-01) | Phase 8 decision | Simpler code, no translation layer needed |
| No crash recovery for order | `exerciseOrder` column in Room | Phase 8 (new) | Crash recovery preserves reordered sequence |
| No skip exercise | `skipExercise()` method | Phase 8 (new) | Users can defer exercises during workout |

## Project Constraints (from CLAUDE.md)

- **Tech stack:** Kotlin Multiplatform + Compose Multiplatform (all business logic in KMP shared module)
- **Platform focus:** iOS first (SwiftUI for platform UI)
- **Storage:** Room KMP for structured data (currently v3, migrating to v4)
- **Architecture:** MVVM with ViewModel exposing StateFlow, SwiftUI observing via NativeCoroutinesState asyncSequence
- **State pattern:** Single sealed class `WorkoutSessionState` (Idle/Active/Reviewing/Finished)
- **Observation convention:** Use `*Flow` suffix for `@NativeCoroutinesState` asyncSequence observation
- **KMP sealed class interop:** Use `WorkoutSessionState.X` dot syntax in Swift
- **Existing patterns to follow:** TemplateEditorView `.onMove` for drag reorder, jumpToExercise() for cursor movement, AutoMigration for schema changes
- **GSD workflow:** All changes through GSD commands

## Sources

### Primary (HIGH confidence)
- Existing codebase: `WorkoutSessionViewModel.kt` -- current ViewModel architecture, sealed state pattern, cursor management
- Existing codebase: `ActiveSessionEntity.kt`, `WorkoutSessionDao.kt` -- current Room schema and DAO patterns
- Existing codebase: `AppDatabase.kt` -- AutoMigration v2->v3 precedent
- Existing codebase: `TemplateEditorView.swift` (line 57-59) -- `.onMove` + `editMode(.constant(.active))` pattern
- Existing codebase: `ExerciseOverviewSheet.swift` -- current exercise list sheet structure
- Existing codebase: `WorkoutRepository.kt` -- repository pattern, `ActiveSessionData` domain model
- Firmware reference: `WorkoutExerciseListState.cpp` -- cursor-based exercise list, completed/current/pending visual hierarchy
- Firmware reference: `WorkoutExerciseMoveState.cpp` -- swap-based reorder, cancel/confirm pattern
- Firmware reference: `TestReorder.cpp` -- edge cases (skip last = no-op, partial progress preserved, save compaction)
- Room schema JSON: `shared/schemas/.../3.json` -- current `active_sessions` table structure

### Secondary (MEDIUM confidence)
- [Room migration documentation](https://developer.android.com/training/data-storage/room/migrating-db-versions) -- AutoMigration with @ColumnInfo(defaultValue) for adding columns
- [SwiftUI .onMove reference](https://sarunw.com/posts/swiftui-list-onmove/) -- ForEach .onMove requires List container, edit mode for drag handles

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all technologies already in use in codebase, no new dependencies
- Architecture: HIGH -- follows existing ViewModel/Repository/DAO patterns exactly
- Pitfalls: HIGH -- exerciseIndex semantics after reorder is the main risk, thoroughly analyzed with firmware reference

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (stable -- no external dependency changes)

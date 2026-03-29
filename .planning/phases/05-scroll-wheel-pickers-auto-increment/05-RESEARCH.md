# Phase 5: Scroll Wheel Pickers & Auto-Increment - Research

**Researched:** 2026-03-29
**Domain:** SwiftUI wheel pickers, KMP ViewModel auto-increment logic, weight unit conversion
**Confidence:** HIGH

## Summary

This phase replaces the existing text field inputs (reps + weight) on the workout set entry screen with native iOS scroll wheel pickers and adds firmware-style auto-increment logic. The current implementation in `WorkoutSessionView.swift` uses `TextField` with `.numberPad`/`.decimalPad` keyboards and `@State` string bindings (`repsInput`, `weightInput`). The target implementation uses `Picker(.wheel)` with pre-computed value arrays.

The firmware reference (`WorkoutSetEntryState.cpp`) provides a clear behavioral spec: set 1 pre-fills from template targets, set 2+ pre-fills from previous set's actuals, reps range 1-50, weight range 0-2000 (kgX10) in 25-step increments (2.5 kg), and reps minimum of 1. The auto-increment logic is a ~15-line change in the KMP ViewModel (`WorkoutSessionViewModel.kt`), while the picker replacement is a SwiftUI-only change. No Room schema migration is needed.

**Primary recommendation:** Implement auto-increment in the KMP ViewModel (expose pre-fill values as part of session state), then replace SwiftUI TextFields with side-by-side `Picker(.wheel)` using the `UIPickerView.intrinsicContentSize` override + `.clipped()` pattern to prevent touch area overlap. Use integer-based picker tags (kgX10 for weight, plain Int for reps) to avoid floating-point issues. Add `WeightUnit`-aware display formatting and lbs picker value generation in Swift.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENTRY-01 | Reps via iOS scroll wheel picker (0-50, step 1) | SwiftUI `Picker(.wheel)` with `ForEach(0...50)`. Touch overlap fix via `UIPickerView.intrinsicContentSize` extension. |
| ENTRY-02 | Weight via iOS scroll wheel picker (0-1000, step 2.5kg) | `ForEach` over kgX10 array `stride(from: 0, through: 10000, by: 25)` with display formatting. 401 values total. |
| ENTRY-03 | Pickers display correctly in kg and lbs modes | Generate separate value arrays per unit. KG: kgX10 stride 25 (2.5kg). LBS: lbsX10 stride 50 (5 lbs). Store as kgX10 always; convert on display/selection. |
| ENTRY-04 | Set 2+ auto-fills from previous set's actuals | ViewModel `completeSet()` already advances cursor. Add pre-fill logic: if `setIndex > 0`, read previous set's `actualReps`/`actualWeightKgX10` instead of template targets. |
| ENTRY-05 | Set 1 pre-fills with template targets | Already partially implemented via `prefillInputs()` in SwiftUI. Move canonical pre-fill to ViewModel; picker `selection` binds to ViewModel-provided defaults. |
| ENTRY-06 | Cannot complete set with 0 reps | `Button("Complete Set").disabled(selectedReps == 0)` in SwiftUI. Optional: also enforce in ViewModel `completeSet()` with early return guard. |
</phase_requirements>

## Standard Stack

No new libraries are needed. This phase uses only existing dependencies.

### Core (already in project)
| Library | Version | Purpose | Role in This Phase |
|---------|---------|---------|-------------------|
| SwiftUI | iOS 17+ | UI framework | `Picker(.wheel)` component, `.disabled()` modifier |
| KMP Shared | existing | ViewModel + domain | Auto-increment logic, pre-fill value computation |
| KMPNativeCoroutinesAsync | existing | Flow observation | Observing pre-fill state from ViewModel |

### No New Dependencies
This phase is purely a UI replacement (SwiftUI) + logic enhancement (ViewModel). No new Gradle/SPM dependencies.

## Architecture Patterns

### Current Architecture (to modify)

```
WorkoutSessionView.swift
  |-- @State repsInput: String        // TextField binding (REPLACE)
  |-- @State weightInput: String      // TextField binding (REPLACE)
  |-- setInputSection()               // TextFields + Complete button (REPLACE)
  |-- prefillInputs()                 // String formatting (REPLACE)
  |-- observeSessionState()           // Updates inputs on cursor change (MODIFY)
  |
WorkoutSessionViewModel.kt
  |-- completeSet(reps, weightKgX10)  // Receives parsed values (KEEP)
  |-- startWorkout()                  // Sets template targets (MODIFY for pre-fill)
  |-- computeNextCursor()             // Advances cursor (KEEP)
```

### Target Architecture

```
WorkoutSessionView.swift
  |-- @State selectedReps: Int        // Picker binding (NEW)
  |-- @State selectedWeightKgX10: Int // Picker binding (NEW)
  |-- setInputSection()               // Wheel pickers + Complete button (REWRITTEN)
  |-- repsPickerValues: [Int]         // Static: 0...50 (NEW)
  |-- weightPickerValues(unit:)       // Computed per unit (NEW)
  |-- UIPickerView extension          // intrinsicContentSize override (NEW)
  |
WorkoutSessionViewModel.kt
  |-- currentPreFill: StateFlow<PreFill> // Exposes (reps, weightKgX10) for current set (NEW)
  |-- completeSet(reps, weightKgX10)     // Unchanged
  |-- computePreFill()                   // Auto-increment logic (NEW)
```

### Pattern 1: Side-by-Side Wheel Pickers with Touch Fix

**What:** Two `Picker(.wheel)` components in an HStack with the UIPickerView intrinsicContentSize override to prevent touch area overlap.

**When to use:** Whenever placing 2+ wheel pickers side by side in SwiftUI.

**Example:**
```swift
// Source: swiftuirecipes.com/blog/multi-column-wheel-picker-in-swiftui
// MUST be declared at file scope (outside any struct) to take effect globally

extension UIPickerView {
    open override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 150)
    }
}
```

```swift
// Inside the view
HStack(spacing: 0) {
    // Reps picker
    VStack(spacing: 4) {
        Text("Reps")
            .font(.caption)
            .foregroundColor(.secondary)
        Picker("Reps", selection: $selectedReps) {
            ForEach(0...50, id: \.self) { value in
                Text("\(value)").tag(value)
            }
        }
        .pickerStyle(.wheel)
        .frame(width: geometry.size.width / 2)
        .clipped()
    }

    // Weight picker
    VStack(spacing: 4) {
        Text("Weight (\(weightUnit.label))")
            .font(.caption)
            .foregroundColor(.secondary)
        Picker("Weight", selection: $selectedWeightKgX10) {
            ForEach(weightValues, id: \.self) { kgX10 in
                Text(weightUnit.formatWeight(kgX10: Int32(kgX10)))
                    .tag(kgX10)
            }
        }
        .pickerStyle(.wheel)
        .frame(width: geometry.size.width / 2)
        .clipped()
    }
}
```

### Pattern 2: Auto-Increment Pre-Fill in ViewModel (Firmware Parity)

**What:** ViewModel computes pre-fill values for the current set based on firmware logic: set 0 uses template targets, set 1+ uses previous set's actuals.

**When to use:** Every time the cursor advances to a new set.

**Example:**
```kotlin
// In WorkoutSessionViewModel.kt

data class SetPreFill(val reps: Int, val weightKgX10: Int)

private fun computePreFill(
    exercise: SessionExercise,
    setIndex: Int
): SetPreFill {
    if (setIndex > 0) {
        // Auto-increment: use previous set's ACTUALS (ENTRY-04)
        val prevSet = exercise.sets[setIndex - 1]
        if (prevSet.isCompleted && prevSet.actualReps != null && prevSet.actualWeightKgX10 != null) {
            return SetPreFill(
                reps = prevSet.actualReps,
                weightKgX10 = prevSet.actualWeightKgX10
            )
        }
    }
    // Set 1 or fallback: use template targets (ENTRY-05)
    return SetPreFill(
        reps = exercise.targetReps,
        weightKgX10 = exercise.targetWeightKgX10
    )
}
```

### Pattern 3: Weight Value Array Generation (kg vs lbs)

**What:** Generate picker value arrays based on current weight unit. Internal storage is always kgX10. Display and picker tags differ by unit.

**When to use:** Generating the `ForEach` data for the weight picker.

**Key insight:** The picker `tag` must ALWAYS be kgX10 (the storage format). Only the `Text` label changes per unit. This way, `selectedWeightKgX10` is always in the correct format for `completeSet()`.

**Example:**
```swift
// Weight values in kgX10 (always stored this way)
// 0, 25, 50, 75, ... 10000  (= 0.0, 2.5, 5.0, 7.5, ... 1000.0 kg)
let weightValuesKgX10: [Int] = Array(stride(from: 0, through: 10000, by: 25))

// Display depends on unit:
ForEach(weightValuesKgX10, id: \.self) { kgX10 in
    Text(weightUnit.formatWeight(kgX10: Int32(kgX10)))
        .tag(kgX10)  // Tag is ALWAYS kgX10
}
```

**For lbs mode:** The same kgX10 array is used, but displayed as lbs equivalents. Steps of 2.5 kg = ~5.5 lbs, which produces non-round lbs values. This matches firmware behavior (firmware stores kgX10 and only converts for display). If the user wants round lbs steps (e.g., 5 lbs), that would require a different value array -- but the requirement says "step 2.5kg" so the kgX10-based array is correct.

### Anti-Patterns to Avoid

- **Using Double/Float for picker tags:** Floating-point comparison in SwiftUI picker tags causes selection failures. Always use Int (kgX10).
- **Computing pre-fill in SwiftUI only:** The current `prefillInputs()` is SwiftUI-side and uses string parsing. Move to ViewModel for single source of truth, testability, and KMP consistency.
- **Using `onChange(of: exercise.exerciseName)` for pre-fill triggers:** The current code uses exercise name change as a proxy for exercise change. Use explicit cursor/state observation instead.
- **Separate lbs picker value array:** Don't generate a separate lbs value array. Use the same kgX10 array with lbs formatting. This ensures stored values are always valid kgX10 increments.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Side-by-side wheel pickers | Custom UIViewRepresentable with UIPickerView | SwiftUI `Picker(.wheel)` + `UIPickerView.intrinsicContentSize` extension + `.clipped()` | Native SwiftUI API works with the one-line extension fix. UIViewRepresentable adds unnecessary complexity. |
| Weight conversion for display | Custom conversion in each view | Existing `WeightUnit.formatWeight(kgX10:)` (already in codebase) | Already implemented with integer-only math. Reuse everywhere. |
| Pre-fill logic | SwiftUI-side string parsing | ViewModel `computePreFill()` returning `(Int, Int)` tuple | Single source of truth, testable, matches firmware pattern. |

## Common Pitfalls

### Pitfall 1: Wheel Picker Touch Area Overlap
**What goes wrong:** Two side-by-side `Picker(.wheel)` in an HStack have overlapping touch areas. Scrolling the left picker spins the right one.
**Why it happens:** `UIPickerView` (underlying SwiftUI Picker) has an intrinsicContentSize wider than its visible frame. The touch area extends beyond the visible bounds.
**How to avoid:** Add `UIPickerView.intrinsicContentSize` extension at file scope, set width to `UIView.noIntrinsicMetric`. Apply `.frame(width:)` and `.clipped()` to each picker. Wrap in `GeometryReader` for dynamic sizing.
**Warning signs:** Scrolling one picker affects the other; picker selection doesn't match visual position.

### Pitfall 2: Picker Selection Mismatch with Non-Hashable Tags
**What goes wrong:** `Picker` selection doesn't update, or always shows the first item.
**Why it happens:** The `tag` type must exactly match the `selection` binding type. If `selection` is `Int` but tags are `Int32` (from KMP), SwiftUI silently ignores the binding.
**How to avoid:** Ensure `@State var selectedReps: Int` and `.tag(value)` where `value` is `Int`. Convert KMP `Int32` to Swift `Int` at the boundary.
**Warning signs:** Picker appears to scroll but `selectedReps` stays at initial value.

### Pitfall 3: Weight Picker Performance with 401 Values
**What goes wrong:** `Picker(.wheel)` with 401 items (0 to 10000 by 25) may have initial rendering lag.
**Why it happens:** SwiftUI creates all 401 Text views eagerly for wheel pickers.
**How to avoid:** 401 values is within acceptable range for wheel pickers (iOS DatePicker handles more). Pre-compute the array as a static `let` to avoid regeneration. If performance issues appear, consider reducing range to 0-500 kg (201 values) or using lazy value generation.
**Warning signs:** Visible lag when the picker first appears; dropped frames during scroll.

### Pitfall 4: Pre-Fill Race Condition on Exercise Change
**What goes wrong:** When the cursor advances from the last set of exercise A to set 0 of exercise B, the pre-fill briefly shows exercise A's last set values before updating to exercise B's template targets.
**Why it happens:** SwiftUI observes the state change and the pre-fill update as separate events if not batched.
**How to avoid:** Compute pre-fill as part of the cursor advance in `completeSet()`, not as a separate observation. Include pre-fill values in the state object that SwiftUI observes (e.g., add `preFillReps`/`preFillWeightKgX10` to `WorkoutSessionState.Active`, or use a dedicated `SetPreFill` StateFlow updated atomically with cursor).
**Warning signs:** Picker briefly flashes old values when switching exercises.

### Pitfall 5: Lbs Picker Shows Non-Round Values
**What goes wrong:** In lbs mode, picker shows "5.5 lbs", "11.0 lbs", etc. instead of round 5-lb increments.
**Why it happens:** 2.5 kg = 5.5115 lbs. The kgX10 step of 25 doesn't map to round lbs values.
**How to avoid:** This is correct behavior per requirements (ENTRY-02 specifies "step 2.5kg", ENTRY-03 says display in lbs). The firmware also uses kgX10 storage with lbs display. Document this in UI so users understand the increments are kg-based. Alternatively, if round lbs steps are desired, generate a separate lbs-native array when unit is LBS (e.g., stride 5 lbs = stride 22.68 kgX10, rounded to nearest 25) -- but this deviates from firmware behavior.
**Warning signs:** User confusion about non-round lbs values; this is a UX decision, not a bug.

### Pitfall 6: Edit Set Sheet Still Uses TextFields
**What goes wrong:** The edit set sheet (for modifying completed sets) still uses TextFields after the main input is changed to pickers.
**Why it happens:** The edit sheet is a separate view section that's easy to miss during the picker migration.
**How to avoid:** Apply the same picker pattern to `editSetSheet`. The edit sheet should use wheel pickers with the same value arrays, pre-filled with the set's current actual values.
**Warning signs:** Inconsistent input methods between primary entry and edit sheet.

## Code Examples

### Example 1: Complete Set Input Section Replacement

```swift
// Source: Codebase analysis + SwiftUI Picker documentation

// File-scope extension (before any struct declaration)
extension UIPickerView {
    open override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 150)
    }
}

// Inside WorkoutSessionView
private let repsRange = Array(0...50)
private let weightValuesKgX10 = Array(stride(from: 0, through: 10000, by: 25))

private func setInputSection(exercise: SessionExercise, setIdx: Int) -> some View {
    VStack(spacing: 16) {
        GeometryReader { geometry in
            HStack(spacing: 0) {
                // Reps picker (ENTRY-01)
                VStack(spacing: 4) {
                    Text("Reps")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Picker("Reps", selection: $selectedReps) {
                        ForEach(repsRange, id: \.self) { value in
                            Text("\(value)").tag(value)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(width: geometry.size.width / 2)
                    .clipped()
                }

                // Weight picker (ENTRY-02)
                VStack(spacing: 4) {
                    Text("Weight (\(weightUnit.label))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Picker("Weight", selection: $selectedWeightKgX10) {
                        ForEach(weightValuesKgX10, id: \.self) { kgX10 in
                            Text(weightUnit.formatWeight(kgX10: Int32(kgX10)))
                                .tag(kgX10)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(width: geometry.size.width / 2)
                    .clipped()
                }
            }
        }
        .frame(height: 150)

        // Complete Set button (ENTRY-06: disabled when reps == 0)
        Button("Complete Set") {
            viewModel.completeSet(
                reps: Int32(selectedReps),
                weightKgX10: Int32(selectedWeightKgX10)
            )
        }
        .font(.body.weight(.semibold))
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
        .frame(height: 48)
        .background(selectedReps == 0
            ? Color.gray
            : Color(red: 0.4, green: 0.733, blue: 0.416))
        .cornerRadius(12)
        .padding(.horizontal, 32)
        .disabled(selectedReps == 0)  // ENTRY-06
    }
}
```

### Example 2: Auto-Increment ViewModel Logic

```kotlin
// Source: Firmware WorkoutSetEntryState.cpp onEnter() lines 86-96

// In WorkoutSessionViewModel.kt - new pre-fill computation
private fun computePreFill(
    exercise: SessionExercise,
    setIndex: Int
): Pair<Int, Int> {  // (reps, weightKgX10)
    return if (setIndex > 0) {
        val prevSet = exercise.sets[setIndex - 1]
        if (prevSet.isCompleted && prevSet.actualReps != null && prevSet.actualWeightKgX10 != null) {
            // ENTRY-04: Auto-fill from previous set's actuals
            Pair(prevSet.actualReps, prevSet.actualWeightKgX10)
        } else {
            // Fallback to template targets
            Pair(exercise.targetReps, exercise.targetWeightKgX10)
        }
    } else {
        // ENTRY-05: Set 1 uses template targets
        Pair(exercise.targetReps, exercise.targetWeightKgX10)
    }
}
```

### Example 3: Pre-Fill State Observation in SwiftUI

```swift
// In observeSessionState(), when cursor changes:
if let active = newState as? WorkoutSessionState.Active {
    let exIdx = Int(active.currentExerciseIndex)
    let setIdx = Int(active.currentSetIndex)
    if exIdx < active.exercises.count {
        let exercise = active.exercises[exIdx]
        // Auto-increment: check previous set's actuals (ENTRY-04)
        if setIdx > 0, setIdx - 1 < exercise.sets.count {
            let prevSet = exercise.sets[setIdx - 1]
            if prevSet.isCompleted,
               let prevReps = prevSet.actualReps,
               let prevWeight = prevSet.actualWeightKgX10 {
                selectedReps = Int(prevReps.int32Value)
                selectedWeightKgX10 = Int(prevWeight.int32Value)
            }
        } else {
            // Set 1: template targets (ENTRY-05)
            let currentSet = exercise.sets[setIdx]
            selectedReps = Int(currentSet.targetReps)
            selectedWeightKgX10 = Int(currentSet.targetWeightKgX10)
        }
    }
}
```

## State of the Art

| Old Approach (current) | New Approach (this phase) | Impact |
|------------------------|--------------------------|--------|
| `TextField` + `.numberPad` for reps | `Picker(.wheel)` for reps | No keyboard dismissal needed, faster input, iOS-native feel |
| `TextField` + `.decimalPad` for weight | `Picker(.wheel)` for weight | Eliminates parsing errors, constrained to valid values, 2.5 kg steps enforced |
| String-based pre-fill in SwiftUI (`prefillInputs()`) | ViewModel-computed pre-fill with auto-increment | Single source of truth, firmware parity, testable |
| No input validation (any string accepted) | `selectedReps == 0` disables button | Prevents invalid 0-rep sets |

## Open Questions

1. **Lbs step size preference**
   - What we know: Requirements say "step 2.5kg". Firmware uses kgX10 storage.
   - What's unclear: Should lbs mode show round 5-lb increments (user-friendly) or exact kg-to-lbs conversions (firmware-faithful)?
   - Recommendation: Start with firmware-faithful (kgX10 array, lbs display). If user feedback suggests round lbs steps are needed, add as a follow-up. This avoids introducing a separate value array and rounding logic.

2. **Weight picker snap to nearest valid kgX10**
   - What we know: When switching from lbs to kg, the selected weight might not be exactly on a 2.5kg boundary if it was originally entered in lbs.
   - What's unclear: Does the existing codebase ever produce non-2.5kg-aligned kgX10 values?
   - Recommendation: Since the picker constrains values to exact kgX10 steps, this is only an issue for existing data. Current TextField input can produce any kgX10 value (e.g., 33 = 3.3 kg). The picker should snap to nearest valid step. Add a rounding helper: `fun snapToStep(kgX10: Int): Int = (kgX10 / 25) * 25`.

3. **Picker height and number of visible rows**
   - What we know: Default `Picker(.wheel)` height is ~150pt showing ~5 rows.
   - What's unclear: Is 150pt the right height for the workout screen layout alongside the header, completed sets, and finish button?
   - Recommendation: Start with 150pt (default). Adjust based on visual testing. The height can be tuned via `.frame(height:)` on the GeometryReader.

## Sources

### Primary (HIGH confidence)
- Firmware reference: `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/states/WorkoutSetEntryState.cpp` - Auto-increment logic (lines 86-96), picker ranges, weight step size
- Codebase: `WorkoutSessionView.swift` - Current TextField implementation to replace
- Codebase: `WorkoutSessionViewModel.kt` - Current completeSet/cursor logic
- Codebase: `WeightUnit.kt` - Existing kgX10 integer math, formatWeight()
- [Apple Picker(.wheel) documentation](https://developer.apple.com/documentation/swiftui/pickerstyle/wheel) - Wheel picker style API

### Secondary (MEDIUM confidence)
- [Multi Column Wheel Picker in SwiftUI](https://swiftuirecipes.com/blog/multi-column-wheel-picker-in-swiftui) - UIPickerView.intrinsicContentSize extension pattern, GeometryReader approach
- [Apple Developer Forums: Picker overlapping](https://developer.apple.com/forums/thread/690791) - Touch overlap problem documentation
- [Apple Developer Forums: Side by side Picker wheels](https://developer.apple.com/forums/thread/690610) - iOS 15 regression, clipped() workaround
- [SwiftUI ForEach stride for decimal increments](https://copyprogramming.com/howto/swiftui-array-creation-using-foreach-and-stride-for-decimal-increments) - Stride-based value array generation

### Tertiary (LOW confidence)
- None. All findings verified against firmware source and Apple documentation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new dependencies, uses existing SwiftUI Picker API
- Architecture: HIGH - Direct firmware port with clear behavioral spec (WorkoutSetEntryState.cpp)
- Pitfalls: HIGH - Touch overlap issue well-documented across multiple Apple Developer Forums threads; fix verified by community
- Auto-increment logic: HIGH - Direct translation from firmware C++ to Kotlin (10 lines)
- Lbs display behavior: MEDIUM - Requirements clear on step size (2.5kg) but lbs UX preference ambiguous

**Research date:** 2026-03-29
**Valid until:** 2026-04-28 (stable APIs, no moving targets)

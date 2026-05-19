---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
fixed_at: 2026-05-19T00:00:00Z
review_path: .planning/phases/23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set/23-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 23: Code Review Fix Report

**Fixed at:** 2026-05-19
**Source review:** `.planning/phases/23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set/23-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (1 Critical, 3 Warning)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### CR-01: System prompt example contradicts the setsPerExercise hard rule

**Files modified:** `shared/src/commonMain/resources/workout-system-prompt.md`
**Commit:** cd2ac5c
**Applied fix:** Changed the first exercise in the JSON example from `"targetSets": 4` to `"targetSets": 3` so both exercises show the same value. Replaced the rule text with the strengthened form: `MUST be exactly the value of setsPerExercise from the user message for every exercise in every template. Do not vary it across exercises.`

---

### WR-01: validateResponse does not check per-template exercise count against form.exerciseCount

**Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt`
**Commit:** 68dea10
**Applied fix:** Replaced `if (t.exercises.isEmpty()) throw AiError.SchemaInvalid("Template has no exercises")` with `if (t.exercises.size != form.exerciseCount) throw AiError.SchemaInvalid("Template '${t.name}': expected ${form.exerciseCount} exercises, got ${t.exercises.size}")`. This catches both zero-exercise responses and responses with the wrong count.

---

### WR-02: AiGenerationMiniBar renders blank content during exit animation

**Files modified:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiGenerationMiniBar.kt`
**Commit:** 7eda48f
**Applied fix:** Added `var lastVisibleState by remember { mutableStateOf<AiGenerationState>(AiGenerationState.Idle) }` and `if (visible) lastVisibleState = genState` before `AnimatedVisibility`. Updated both the `activeType` derivation and the `when` branch inside `Surface` to read from `lastVisibleState` instead of the live `genState`. Added `mutableStateOf`, `remember`, and `setValue` imports.

---

### WR-03: StagedTemplate.description is silently discarded in commit()

**Files modified:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt`, `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiResponseDto.kt`, `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt`, `shared/src/commonMain/resources/workout-system-prompt.md`, `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiPreviewSheet.kt`, `iosApp/iosApp/Views/AI/AIPreviewSheet.swift`
**Commit:** f435619
**Applied fix:** `WorkoutTemplate` DB entity has no `description` column and adding one would require a Room migration. Chose the "make the contract honest" path: removed `description` from `StagedTemplate` data class, from the `WorkoutAiTemplate` serializable DTO, from the system prompt JSON example, from `resolvePreview()` in `WorkoutAiUseCase`, and from both preview UIs (Android `AiPreviewSheet.kt` and iOS `AIPreviewSheet.swift`). Description can be re-introduced end-to-end (DB column + migration + all layers) in a future phase when it is genuinely needed.

---

_Fixed: 2026-05-19_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_

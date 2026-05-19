---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
reviewed: 2026-05-19T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
  - shared/src/commonMain/resources/workout-system-prompt.md
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiGenerationMiniBar.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
findings:
  critical: 1
  warning: 3
  info: 3
  total: 7
status: issues_found
---

# Phase 23: Code Review Report

**Reviewed:** 2026-05-19
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 23 delivers the `setsPerExercise` stepper across the shared KMP domain/ViewModel/UseCase, updates the system prompt, adds `AiGenerationMiniBar`, extends `AiWorkoutGenScreen` with the new stepper, and wires the mini-bar into `MainScreen`'s bottom bar.

The data model and ViewModel changes are largely sound. `setsPerExercise` propagates correctly from `WorkoutAiForm` through `WorkoutAiUiState.Form` and into `buildUserMessage`. The `reset()` guard from D-21-02 correctly extends coverage to `Preview` and `Error` states. MiniBar navigation correctly targets the right `NavController` per AI type.

One critical bug is present: the system prompt example directly contradicts the new hard rule for `setsPerExercise`, which will cause the LLM to produce varying `targetSets` values instead of the uniform value the user configured — defeating the purpose of the stepper. Three warnings cover a missing exercise-count validator, a blank-during-exit-animation defect in `AiGenerationMiniBar`, and silent discard of LLM-generated template descriptions. Three info items cover style and technical-debt concerns.

No security vulnerabilities or data-loss risks were found.

---

## Critical Issues

### CR-01: System prompt example contradicts the setsPerExercise hard rule

**File:** `shared/src/commonMain/resources/workout-system-prompt.md:19-33` and `46`

**Issue:** Line 46 states: "`targetSets`: use setsPerExercise from user message **exactly**." However the JSON example immediately above (lines 19-33) shows two exercises in the same template with `"targetSets": 4` and `"targetSets": 3` respectively — two different values. LLMs weight concrete JSON examples more heavily than prose rules. The contradictory example will reliably cause the model to produce varying `targetSets` values across exercises rather than the uniform value the user configured via the stepper, making the setsPerExercise feature effectively non-functional.

**Fix:** Update the example so both exercises show the same `targetSets` value (matching the expected default of 3), and strengthen the rule text:

```markdown
## Hard rules

- `targetSets`: MUST be exactly the value of `setsPerExercise` from the user message for
  **every** exercise in **every** template. Do not vary it across exercises.
  `targetReps` in 8..12. `restPeriodSec` in 60..120.
```

And in the JSON example:

```json
{
  "exerciseName": "Barbell Bench Press - Medium Grip",
  "targetSets": 3,
  "targetReps": 8,
  "restPeriodSec": 120,
  "note": "Schulterblätter zusammenziehen."
},
{
  "exerciseName": "Dumbbell Shoulder Press",
  "targetSets": 3,
  "targetReps": 10,
  "restPeriodSec": 90,
  "note": null
}
```

Both exercises must show the same value. Any value other than the runtime `setsPerExercise` in the example teaches the model to deviate.

---

## Warnings

### WR-01: validateResponse does not check per-template exercise count against form.exerciseCount

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:254-256`

**Issue:** The system prompt instructs the LLM that `exercises.length MUST equal exerciseCount from the user message exactly`. However `validateResponse` only asserts `t.exercises.isEmpty()` — it does not verify `t.exercises.size == form.exerciseCount`. If the LLM returns fewer or more exercises than requested (e.g., returns 4 when the user asked for 6), the malformed workout is silently accepted and saved. The user gets a result that does not match their input.

**Fix:**

```kotlin
// In validateResponse, replace:
if (t.exercises.isEmpty()) throw AiError.SchemaInvalid("Template has no exercises")

// With:
if (t.exercises.size != form.exerciseCount) {
    throw AiError.SchemaInvalid(
        "Template '${t.name}': expected ${form.exerciseCount} exercises, " +
        "got ${t.exercises.size}"
    )
}
```

---

### WR-02: AiGenerationMiniBar renders blank content during exit animation

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiGenerationMiniBar.kt:61-81`

**Issue:** `genState` is read via `collectAsState()` directly inside the `AnimatedVisibility` content lambda. When the generation state transitions from `Success` or `Generating` to `Idle` (e.g., after `save()` or `cancel()`), `AnimatedVisibility` begins its 300 ms exit animation (`slideOutVertically + fadeOut`). During that animation the composable is still in the tree but `genState` is now `Idle`, so `when (val s = genState)` falls through to `else -> {}` and renders nothing. The user sees a blank bar sliding and fading out instead of the last meaningful label.

**Fix:** Snapshot the last visible state so exit animation has content:

```kotlin
@Composable
fun AiGenerationMiniBar(
    onTap: (AiType) -> Unit,
    generationManager: AiGenerationManager = koinInject()
) {
    val genState by generationManager.state.collectAsState()
    val visible = genState is AiGenerationState.Generating || genState is AiGenerationState.Success

    // Hold the last active state so exit animation renders meaningful content.
    var lastVisibleState by remember { mutableStateOf<AiGenerationState>(AiGenerationState.Idle) }
    if (visible) lastVisibleState = genState

    val activeType: AiType? = when (val s = lastVisibleState) {
        is AiGenerationState.Generating -> s.type
        is AiGenerationState.Success -> s.type
        else -> null
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(animationSpec = tween(300)),
        exit  = slideOutVertically { it } + fadeOut(animationSpec = tween(300))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = activeType != null) { activeType?.let(onTap) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            when (val s = lastVisibleState) {
                is AiGenerationState.Generating -> MiniBarGenerating(s.type)
                is AiGenerationState.Success    -> MiniBarSuccess(s.type)
                else -> {}
            }
        }
    }
}
```

---

### WR-03: StagedTemplate.description is silently discarded in commit()

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:99-101`

**Issue:** The LLM generates a `description` field for each template (e.g., "Push-Fokus mit Schwerpunkt Volumen."), which is correctly parsed into `StagedTemplate.description` and displayed in the preview sheet. When `commit()` calls `templateRepository.createTemplate(name = template.name, source = "AI")`, the `description` argument is absent. The description is never persisted — it is silently dropped at save time. The system prompt, the DTO, and the preview all carry the description, but the saved template does not.

**Fix:** Either extend `TemplateRepository.createTemplate` to accept and persist an optional `description` field, or — if template descriptions are not yet supported in the data model — remove `description` from `StagedTemplate`, from the `WorkoutAiTemplate` DTO, and from the system prompt so the contract is honest end-to-end. Silent discard is the worst of the three options.

---

## Info

### IN-01: _uiState initial value does not explicitly set setsPerExercise (style divergence from defaultForm)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt:70-76`

**Issue:** The inline `_uiState` initialization constructs `WorkoutAiUiState.Form` without specifying `setsPerExercise`, relying on the `= 3` default in the data class. The `defaultForm` at lines 97-102 explicitly sets `setsPerExercise = 3`. Both produce the same value today, but there are now two "initial form" construction sites. If the default in `WorkoutAiUiState.Form` is ever changed, the inline initialization silently diverges.

**Fix:** Explicitly pass `setsPerExercise = 3` in the inline initialization, or move `defaultForm` above `_uiState` and initialize `_uiState` from it:

```kotlin
private val _uiState = MutableStateFlow<WorkoutAiUiState>(
    WorkoutAiUiState.Form(
        targetMuscles = emptyList(),
        exerciseCount = 5,
        splitStyle = WorkoutAiSplit.NONE,
        setsPerExercise = 3  // explicit — matches defaultForm
    )
)
```

---

### IN-02: validateResponse validates primaryMuscles but silently ignores invalid secondaryMuscles

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:264-273` and `287`

**Issue:** `validateResponse` iterates `inline.primaryMuscles` and throws `AiError.SchemaInvalid` for any unknown `MuscleGroup` string. `secondaryMuscles` are not validated at all. In `resolvePreview()`, `secondaryMuscles` uses `mapNotNull` to silently drop unrecognised values. An LLM that writes `"upper back"` instead of `"lats"` in `secondaryMuscles` produces an exercise with a missing muscle group — no error, no log. This is an inconsistent validation strategy within the same function.

**Fix:** Either add an analogous validation loop for `secondaryMuscles` in `validateResponse`, or document explicitly that `secondaryMuscles` validation is intentionally lenient (and remove the strict validation for `primaryMuscles` to be consistent). Mixing strict and lenient validation on sibling fields in the same DTO is confusing.

---

### IN-03: Locale hardcoded to "de" — the AiPromptCatalog substitution mechanism is never exercised

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:50`

**Issue:** `promptCatalog.workoutSystemPrompt()` is called without a `locale` argument, always falling back to `DEFAULT_LOCALE = "de"`. The `{locale}` token substitution system in `AiPromptCatalog` exists but is never used by any call site. If the app is extended to non-German locales, the system prompt (and all LLM-generated content — names, descriptions, instructions) will remain German. The mechanism is built; it simply is not wired.

**Fix:** Pass locale explicitly or read it from `SettingsRepository`. At minimum, mark the `locale` parameter call site with a comment so the omission is visible rather than accidental:

```kotlin
// D-23: locale hardcoded to "de" — wire from SettingsRepository when i18n is added.
val systemPrompt = promptCatalog.workoutSystemPrompt(locale = "de")
```

---

_Reviewed: 2026-05-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

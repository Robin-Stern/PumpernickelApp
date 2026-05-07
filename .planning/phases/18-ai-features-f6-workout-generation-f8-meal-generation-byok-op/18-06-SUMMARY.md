---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "06"
subsystem: workout-ai-use-case-and-vm
tags: [viewmodel, koin, di, domain, usecase, ai, workout-generation, kmp, byok]
dependency_graph:
  requires:
    - 18-02 (OpenAICompatibleClient + ChatRequest/ResponseFormat DTOs)
    - 18-03 (AiPromptCatalog + workoutSystemPrompt)
    - 18-04 (SecureKeyStore expect/actual)
    - 18-05 (AiModule, SettingsRepository AI fields, AiSettingsViewModel)
  provides:
    - WorkoutAiPreview.kt: staging data classes (WorkoutAiPreview, StagedTemplate, StagedTemplateExercise, StagedExercise, WorkoutAiForm, WorkoutAiSplit)
    - WorkoutAiUseCase: invoke (preview only) + commit (transactional DB write)
    - WorkoutAiViewModel: sealed WorkoutAiUiState + 5 actions
    - WorkoutAiKoinHelper (iosMain): iOS Koin factory
    - AiModule: WorkoutAiUseCase + WorkoutAiViewModel bindings appended
    - TemplateRepository.createTemplate: extended with source: String? = null
  affects:
    - Plan 07: Android workout AI screen uses WorkoutAiViewModel + WorkoutAiUiState
    - Plan 08: RecipeAiUseCase follows the same invoke/commit pattern
    - Plan 10: iOS AI workout view uses WorkoutAiKoinHelper
tech_stack:
  added: []
  patterns:
    - WorkoutAiUseCase follows invoke=preview-only / commit=transactional DB write pattern (D-18-12)
    - json_schema primary call with single retry as json_object fallback (D-18-14)
    - Sealed UiState pattern: 6 states covering full F6 flow lifecycle
    - Cancel stores Job reference + calls .cancel(); CancellationException caught in generate() returns to Form without error UI (D-18-16)
    - MutableStateFlow<WorkoutAiUiState> with @NativeCoroutinesState annotation (KMPNativeCoroutines convention)
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt
decisions:
  - "WorkoutAiUseCase.invoke never writes to DB; all staging is in-memory WorkoutAiPreview (D-18-12)"
  - "Retry fallback fires only for SchemaInvalid or AuthOrQuota(400/422) — not for Timeout/Network/Provider (no benefit from retry on those)"
  - "lookupMuscles helper returns emptyList — TemplateRepository re-fetches muscles on read so the write-time value is not load-bearing"
  - "existingExercises.take(80) caps prompt size; muscle-group-targeted filtering deferred to a future plan"
  - "WorkoutAiSplit.templateCount encodes split → template count mapping directly on the enum (NONE=1, PPL=3, UL=2, FB=1)"
metrics:
  duration: "~10 min"
  completed: "2026-05-07"
  tasks: 2
  files_created: 4
  files_modified: 2
---

# Phase 18 Plan 06: Workout AI Use Case and VM — Summary

**One-liner:** WorkoutAiUseCase orchestrates prompt→json_schema LLM call→validate→resolve→in-memory preview (no DB writes), with single retry fallback to json_object; WorkoutAiViewModel exposes 6-state sealed UiState and 5 actions; AiModule extended; iOS KoinHelper added.

## Files Created / Modified

### Created

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` — staging data classes: `WorkoutAiPreview`, `StagedTemplate`, `StagedTemplateExercise`, `StagedExercise`, `WorkoutAiForm`, `WorkoutAiSplit` enum (NONE/PPL/UL/FULL_BODY with templateCount)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` — use case: `invoke(form)` builds user message from form + existing exercise catalog, calls LLM with `response_format=json_schema`, validates response app-side, resolves exercise names case-insensitively, returns `WorkoutAiPreview` (no DB writes); `commit(preview)` persists inline exercises with `source="AI"`, then persists templates with `source="AI"` + TemplateExercise rows
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` — ViewModel with `MutableStateFlow<WorkoutAiUiState>`, init check for NoKey, `generate()`/`cancel()`/`save()`/`discardPreview()`/`retryFromError()` actions
- `shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt` — canonical 9-line iOS Koin factory for Plan 10

### Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — appended `single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }` and `viewModel { WorkoutAiViewModel(get(), get()) }`; added 5 imports
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` — extended interface `createTemplate(name: String, source: String? = null): Long` and impl to pass `source` to `WorkoutTemplateEntity`; all existing call sites unaffected (default null)

## WorkoutAiUseCase: invoke vs commit Responsibilities

| Method | DB writes | Purpose |
|--------|-----------|---------|
| `invoke(form)` | NONE | Builds prompt → calls LLM → validates schema → resolves exercise refs → returns `WorkoutAiPreview` (in-memory staging only) |
| `commit(preview)` | YES (exercises + templates + TemplateExercise rows) | Persists `StagedExercise` items as `Exercise(source="AI")`, then each `StagedTemplate` as `WorkoutTemplate(source="AI")` + its TemplateExercise rows; returns new template IDs |

## WorkoutAiUiState Sealed State Diagram

```
                    [init: readApiKey == null]
                          |
                          v
                       NoKey
                          |
              [user goes to AI Settings, sets key]
                          |
                          v
   +--> Form(targetMuscles, exerciseCount, splitStyle)
   |       |
   |    [generate() called, muscles non-empty]
   |       v
   |    Generating(skeletonRowCount)
   |       |              |
   |    [success]      [cancel()]
   |       |              |
   |       v              |
   |    Preview   --------+
   |    (preview,  [returns to Form, no error UI]
   |     origForm)
   |       |
   |    [save()]     [discardPreview()]
   |       |              |
   |       v              |
   |    Saved(ids)  +-----+
   |                |
   |    [any LLM/DB error in generate() or save()]
   |       v
   +--  Error(aiError, origForm)
            |
         [retryFromError()]
            |
            v
          Form (restored from originatingForm)
```

## TemplateRepository.createTemplate Signature Change

```kotlin
// Before (Plan 05 and earlier):
suspend fun createTemplate(name: String): Long

// After (Plan 06):
suspend fun createTemplate(name: String, source: String? = null): Long
```

All existing call sites (`TemplateEditorViewModel.save()`, etc.) continue working because `source` defaults to `null`. `WorkoutAiUseCase.commit()` passes `source = "AI"`.

## Security Mitigations Implemented (from threat model)

| Threat ID | Mitigation |
|-----------|-----------|
| T-18-06-01 | `validateResponse()`: rejects templates with blank names, empty exercise lists, targetSets outside 1..10, targetReps outside 1..50, restPeriodSec outside 0..600, blank exerciseName; rejects inline exercises with blank name, empty primaryMuscles, unknown MuscleGroup dbNames, empty instructions |
| T-18-06-02 | Use case never logs request body; no API key held in class field |
| T-18-06-03 | Retry is silent (one auto-retry per D-18-14); SchemaInvalid surfaces only after retry exhausted |
| T-18-06-04 | Defended in Plan 02 (64KB cap in OpenAICompatibleClient) |
| T-18-06-05 | `resolvePreview()` uses case-insensitive trimmed key lookup; if inline.name matches an existing exercise by name, the existing id wins — no duplicate exercise row created |
| T-18-06-06 | No delete API in WorkoutAiViewModel or WorkoutAiUseCase; out-of-scope per D-AI-02 |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — `WorkoutAiPreview` is pure staging, not rendered UI. The stub tracking requirement (hardcoded empty values flowing to UI) does not apply to this plan since no UI layer is created. The `lookupMuscles` helper returning `emptyList()` is intentional and documented — TemplateRepository re-resolves muscle data on read, so the write-time value is not load-bearing. This is documented in the plan's action notes and inline in the code.

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>`. No new network endpoints, auth paths, or schema changes beyond what was already planned (TemplateRepository.createTemplate source field was planned in Plan 01's schema migration).

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` — FOUND
- `shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — modified (FOUND)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` — modified (FOUND)
- Commit e524837 (Task 1: WorkoutAiPreview + WorkoutAiUseCase + TemplateRepository) — FOUND
- Commit f9f047a (Task 2: WorkoutAiViewModel + AiModule + WorkoutAiKoinHelper) — FOUND

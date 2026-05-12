---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "03"
subsystem: ai-prompts-and-schemas
tags: [prompts, serialization, schemas, byok, kmp-resources]
dependency_graph:
  requires:
    - AiChatDto @Serializable wire types (data/api) — Plan 02
    - OpenAICompatibleClient chatCompletion — Plan 02
    - readResourceFile expect/actual (Platform.kt) — pre-existing
  provides:
    - workout-system-prompt.md (flat resource, {locale}, Exercise schema embedded)
    - recipe-system-prompt.md (flat resource, {locale}, inline-Food schema embedded)
    - AiPromptCatalog.workoutSystemPrompt(locale) + recipeSystemPrompt(locale)
    - WorkoutAiResponse / WorkoutAiTemplate / WorkoutAiTemplateExercise / WorkoutAiInlineExercise
    - RecipeAiResponse / RecipeAiIngredient / RecipeAiInlineFood
  affects:
    - Plans 06 (WorkoutAiUseCase) and 08 (RecipeAiUseCase) depend on these schemas for deserialization and validation
tech_stack:
  added: []
  patterns:
    - Flat resource naming (no subdir) — required for NSBundle.pathForResource(name, ext) on iOS
    - {locale} placeholder substituted at call time, not build time
    - @Serializable data classes with nullable refusal field for LLM refusal passthrough
    - All macro/muscle validations deferred to downstream plans (Plan 06, 08)
key_files:
  created:
    - shared/src/commonMain/resources/workout-system-prompt.md
    - shared/src/commonMain/resources/recipe-system-prompt.md
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt
  modified:
    - iosApp/iosApp.xcodeproj/project.pbxproj (added both .md files to PBXBuildFile, PBXFileReference, PBXGroup, PBXResourcesBuildPhase)
decisions:
  - "iOS pbxproj updated to include both .md prompt files — same pattern as free_exercise_db.json (A11801/B11801 for workout, A11802/B11802 for recipe)"
  - "AiPromptCatalog is a plain class (not a singleton or Koin module entry) — call sites instantiate or inject as needed in downstream plans"
  - "recipe-system-prompt.md already included in pbxproj in Task 1 commit (both files added together to avoid partial state)"
metrics:
  duration: "3m 57s"
  completed: "2026-05-07"
  tasks: 3
  files_created: 5
  files_modified: 1
---

# Phase 18 Plan 03: Prompts and Schemas — Summary

**One-liner:** Versioned workout and recipe system prompts (flat .md resources, {locale} substitution) plus @Serializable response-schema Kotlin models for both AI flows, with iOS Xcode bundle inclusion.

## Files Created / Modified

Five new files and one modified Xcode project entry implement the AI prompt infrastructure:

- `workout-system-prompt.md` (95 lines) — English system prompt with refusal policy, anti-injury heuristics, Exercise catalog policy (D-18-09), inline-Exercise schema, and JSON response shape
- `recipe-system-prompt.md` (66 lines) — nutrition expert prompt with macro-targeting policy, refusal policy, inline-Food schema, and JSON response shape
- `WorkoutAiSchema.kt` — 4 @Serializable data classes: `WorkoutAiResponse`, `WorkoutAiTemplate`, `WorkoutAiTemplateExercise`, `WorkoutAiInlineExercise`
- `RecipeAiSchema.kt` — 3 @Serializable data classes: `RecipeAiResponse`, `RecipeAiIngredient`, `RecipeAiInlineFood`
- `AiPromptCatalog.kt` — `workoutSystemPrompt(locale)` and `recipeSystemPrompt(locale)` backed by `readResourceFile` expect/actual

## AiPromptCatalog API Surface

```kotlin
class AiPromptCatalog {
    fun workoutSystemPrompt(locale: String = DEFAULT_LOCALE): String
    fun recipeSystemPrompt(locale: String = DEFAULT_LOCALE): String

    companion object {
        const val DEFAULT_LOCALE: String = "de"
    }
}
```

Reads `.md` files at AI call time; no caching. `DEFAULT_LOCALE = "de"` per D-18-15.

## iOS Xcode Bundle Inclusion

Both `.md` prompt files were added to `iosApp/iosApp.xcodeproj/project.pbxproj` following the exact same pattern as `free_exercise_db.json`:

| File | PBXFileReference UUID | PBXBuildFile UUID |
|------|-----------------------|-------------------|
| `workout-system-prompt.md` | `B11801` | `A11801` |
| `recipe-system-prompt.md` | `B11802` | `A11802` |

Path format: `../shared/src/commonMain/resources/<filename>` with `sourceTree = SOURCE_ROOT`. Both are included in the `PBXResourcesBuildPhase` (F10003) alongside `free_exercise_db.json`.

## Prompt File Line Counts

| File | Lines | {locale} occurrences |
|------|-------|---------------------|
| `workout-system-prompt.md` | 95 | 7 |
| `recipe-system-prompt.md` | 66 | 5 |

Both well exceed the 30-line minimum.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Worktree Path Note

Files were initially created to the main repo path instead of the worktree path due to the working directory being the main repo. Corrected by copying to the worktree and cleaning up the main repo before committing to the worktree branch. No functional impact.

## Known Stubs

None — the prompt `.md` files are complete and the schema models are complete. Downstream semantic validation (MuscleGroup enum membership, macro non-negative, sugar <= carbs) is intentionally deferred to Plans 06 and 08 as documented in the plan.

## Threat Flags

No new threat surface beyond what is documented in the plan's threat model. The prompt files ship inside the signed iOS app bundle (T-18-03-01 mitigated by signing). Schema validation is deferred to Plans 06/08 (T-18-03-03 mitigation continuity).

## Self-Check: PASSED

- `shared/src/commonMain/resources/workout-system-prompt.md` — FOUND (95 lines)
- `shared/src/commonMain/resources/recipe-system-prompt.md` — FOUND (66 lines)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` — FOUND
- Commit 4b570e7 (Task 1: workout prompt + schema + pbxproj) — FOUND
- Commit e353d74 (Task 2: recipe prompt + schema) — FOUND
- Commit 1958d3d (Task 3: AiPromptCatalog) — FOUND

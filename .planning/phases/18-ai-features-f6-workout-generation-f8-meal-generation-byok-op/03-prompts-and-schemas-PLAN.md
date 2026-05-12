---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 03
type: execute
wave: 2
depends_on: []
files_modified:
  - shared/src/commonMain/resources/workout-system-prompt.md
  - shared/src/commonMain/resources/recipe-system-prompt.md
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt
autonomous: true
requirements:
  - REQ-AI-02
  - REQ-AI-03
  - REQ-AI-05
user_setup: []

must_haves:
  truths:
    - "A workout-system prompt file exists in source control with a {locale} placeholder and embeds the Exercise schema (D-18-09 — LLM authoring rights)"
    - "A recipe-system prompt file exists in source control with a {locale} placeholder and embeds the inline Food + ingredient schema (D-18-10)"
    - "WorkoutAiSchema models the response: array of templates each with name, exercises[] (sets, reps, restSeconds, primaryMuscles), allowing inline new-Exercise emission"
    - "RecipeAiSchema models the response: name, ingredients[] each with inline Food + amountGrams, optional steps"
    - "AiPromptCatalog loads .md files via the existing readResourceFile expect/actual and substitutes {locale}"
    - "Schemas serialise to JsonElement so they can ride in ChatRequest.responseFormat.jsonSchema.schema"
  artifacts:
    - path: "shared/src/commonMain/resources/workout-system-prompt.md"
      provides: "Versioned workout system prompt with embedded Exercise schema and {locale} placeholder"
      contains: "{locale}"
      min_lines: 30
    - path: "shared/src/commonMain/resources/recipe-system-prompt.md"
      provides: "Versioned recipe system prompt with embedded inline-Food schema and {locale} placeholder"
      contains: "{locale}"
      min_lines: 30
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt"
      provides: "workoutSystemPrompt(locale) and recipeSystemPrompt(locale) loaders"
      contains: "class AiPromptCatalog"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt"
      provides: "WorkoutAiResponse + WorkoutAiTemplate + WorkoutAiTemplateExercise + WorkoutAiInlineExercise"
      contains: "@Serializable"
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt"
      provides: "RecipeAiResponse + RecipeAiIngredient + RecipeAiInlineFood"
      contains: "@Serializable"
  key_links:
    - from: "AiPromptCatalog"
      to: "readResourceFile"
      via: "shared expect/actual loader"
      pattern: "readResourceFile\\(\"(workout|recipe)-system-prompt\\.md\""
    - from: "WorkoutAiSchema"
      to: "MuscleGroup enum (validated app-side)"
      via: "primaryMuscles List<String>"
      pattern: "primaryMuscles"
    - from: "RecipeAiSchema"
      to: "Food per-100g shape"
      via: "RecipeAiInlineFood mirrors Food.kt fields"
      pattern: "RecipeAiInlineFood"
---

<objective>
Ship the versioned system prompts and the response-schema Kotlin models for both AI flows.

D-18-15 anchors prompts in source control as `.md` files at `shared/src/commonMain/resources/` (flat — `workout-system-prompt.md` and `recipe-system-prompt.md`) so they read and review as plain English with a `{locale}` placeholder substituted at call time. **Resource files MUST be flat-named (no subdirectory) because `Platform.ios.kt`'s `readResourceFile` calls `NSBundle.pathForResource(name, ext)` — that overload does not interpret `/` in the resource name as a subdirectory and would throw "Resource file not found" at first AI call.** This mirrors the existing `free_exercise_db.json` precedent. D-18-09 / D-18-10 require the prompts to embed the full `Exercise` and `Food` schemas so the LLM can emit new entries when it needs an exercise or food not in the catalog. The Kotlin schema models in `WorkoutAiSchema.kt` and `RecipeAiSchema.kt` mirror the wire shape the prompt asks the LLM to emit; they ride inside `ChatRequest.responseFormat.jsonSchema.schema` (defined in Plan 02) and are app-side validated before any DB write per REQ-AI-07.

`AiPromptCatalog.kt` reuses the existing `readResourceFile(...)` `expect/actual` (already used to load `free_exercise_db.json`) — no new platform code is needed.

Purpose: Implement REQ-AI-02 (workout schema), REQ-AI-03 (versioned workout prompt), REQ-AI-05 (recipe prompt + schema). Closes D-18-09 (Exercise authoring rights), D-18-10 (inline-Food shape), D-18-15 (English prompts + locale placeholder).
Output: Two prompt `.md` files + `AiPromptCatalog.kt` + `WorkoutAiSchema.kt` + `RecipeAiSchema.kt`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/FoodUnit.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt

<interfaces>
Existing readResourceFile expect/actual (commonMain/Platform.kt — already wired for both Android and iOS via the free_exercise_db.json precedent):
```
expect fun readResourceFile(fileName: String): String
```

MuscleGroup.kt enum dbName values that primaryMuscles MUST be drawn from (the system prompt enumerates these so the model picks valid ones):
chest, shoulders, biceps, triceps, forearms, traps, lats, neck, quadriceps, hamstrings, glutes, calves, adductors, abdominals, obliques, lower back

FoodUnit.kt enum values (LLM constrained to these):
GRAM, MILLILITER

Existing Exercise.kt schema fields the LLM must populate when emitting a NEW exercise (D-18-09):
- id (String — LLM emits a UUID-like string; app generates if missing)
- name (String, non-blank)
- force (String? — "push" | "pull" | "static" | null)
- level (String — "beginner" | "intermediate" | "expert")
- mechanic (String? — "compound" | "isolation" | null)
- equipment (String? — free-form; see Exercise table for distinct values)
- category (String — non-blank, e.g. "strength")
- instructions (List<String> — at least 1 step)
- images (List<String> — usually empty for AI-emitted exercises)
- isCustom (Boolean — true for AI emissions)
- primaryMuscles (List<MuscleGroup> as dbName strings)
- secondaryMuscles (List<MuscleGroup> as dbName strings, may be empty)

TemplateExercise (Phase 18 generation target — flat fields the LLM emits per exercise within a template):
- exerciseName (String — must match an existing exercise's name OR be the inline-emitted new exercise's name)
- targetSets (Int — typical 1..5)
- targetReps (Int — typical 1..30)
- restPeriodSec (Int — typical 30..240)

Existing Food.kt schema fields the LLM must populate (D-18-10):
- id (String — LLM emits UUID-like; app regenerates if missing)
- name (String, non-blank)
- calories (Double, per 100g/100ml)
- protein (Double, per 100g/100ml)
- fat (Double, per 100g/100ml)
- carbohydrates (Double, per 100g/100ml)
- sugar (Double, per 100g/100ml; sugar <= carbohydrates required)
- unit (FoodUnit — "GRAM" | "MILLILITER")

RecipeIngredient: { food: { ...Food schema }, amountGrams: Double } per D-18-10.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Write the workout-system-prompt.md + WorkoutAiSchema.kt</name>
  <files>
    shared/src/commonMain/resources/workout-system-prompt.md,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
  </read_first>
  <action>
**File 1: shared/src/commonMain/resources/workout-system-prompt.md** (create flat at the resources root — NO subdirectory; iOS `pathForResource(name, ext)` cannot resolve a `/` in the resource name). The prompt is English (D-18-15) but instructs the model to emit user-visible strings (template name, exercise notes) in `{locale}`. Embed the Exercise schema verbatim so the model knows how to emit new entries (D-18-09). Specify the JSON response shape exactly so it matches `WorkoutAiSchema.kt`.

Suggested content (adjust wording but preserve structure and fields):

```markdown
# Workout Generation System Prompt v1

You are a fitness expert generating workout templates for a personal training
app. The user has selected a set of target muscles and an exercise count;
optionally a split style (Push/Pull/Legs, Upper/Lower, Full Body). You must
respond ONLY with JSON matching the schema below — no prose, no markdown.

Respond with all user-visible text (template name, exercise notes) in
{locale}, matching the app's UI language. Field NAMES (the JSON keys) stay
in English exactly as the schema specifies.

## Refusal policy
If the user request is unrelated to fitness or includes content that could be
harmful (e.g. injury-aggravating prescriptions for stated injuries, eating-
disorder triggers), respond with a single object: `{ "refusal": "<short
explanation in {locale}>" }`. Otherwise return the regular response.

## Anti-injury heuristics
- Default targetSets in 3..4 unless the user count or split implies otherwise.
- Default targetReps in 8..12 (hypertrophy range) unless user signals strength
  (1..5) or endurance (15..20).
- Default restPeriodSec in 60..120 (compound) or 45..90 (isolation).
- Never prescribe more than 8 working sets for a single muscle group inside
  one template.
- For a "split" request, distribute volume across templates so the same muscle
  is not targeted on consecutive templates.

## Exercise catalog policy (D-18-09 — LLM authoring rights)
The app will pass a list of available exercises in the user message under the
key `existingExercises` (each item: `{ name, primaryMuscles, equipment }`).

- Prefer to reference exercises BY NAME from `existingExercises`. The app
  matches case-insensitively and trims whitespace.
- If you NEED an exercise that is not in `existingExercises`, emit a complete
  inline new exercise under `inlineNewExercises` AND reference it by the same
  `name` from inside the template's `exercises` list. The app persists new
  exercises before linking them.
- DO NOT invent low-quality exercises. Only emit a new exercise when no
  reasonable existing exercise covers the target movement.

## Inline-Exercise schema (matches the app's Exercise model)
Each `inlineNewExercises[i]` MUST have:
- `name`: string, non-empty, in {locale}.
- `primaryMuscles`: array of strings; each value MUST be one of:
  chest, shoulders, biceps, triceps, forearms, traps, lats, neck,
  quadriceps, hamstrings, glutes, calves, adductors, abdominals,
  obliques, lower back
- `secondaryMuscles`: array of strings (same enum, may be empty).
- `equipment`: one of "barbell", "dumbbell", "machine", "cable",
  "bodyweight", "kettlebell", "band", or null.
- `force`: "push" | "pull" | "static" | null
- `mechanic`: "compound" | "isolation" | null
- `level`: "beginner" | "intermediate" | "expert"
- `category`: "strength" | "cardio" | "stretching" | "powerlifting" | "olympic weightlifting" | "strongman" | "plyometrics"
- `instructions`: array of strings, at least 1, each step in {locale}.

## Response shape (you MUST emit this exactly)
```json
{
  "templates": [
    {
      "name": "string in {locale}",
      "description": "string in {locale} or null",
      "exercises": [
        {
          "exerciseName": "must match existingExercises[i].name OR inlineNewExercises[i].name",
          "targetSets": 3,
          "targetReps": 10,
          "restPeriodSec": 90,
          "note": "optional string in {locale} or null"
        }
      ]
    }
  ],
  "inlineNewExercises": [
    {
      "name": "...", "primaryMuscles": ["..."], "secondaryMuscles": [],
      "equipment": "...", "force": "...", "mechanic": "...",
      "level": "...", "category": "...", "instructions": ["..."]
    }
  ]
}
```

When the user did NOT select a split (single template), `templates` MUST have
length exactly 1. When a split is selected (PPL = 3 templates, Upper-Lower =
2, Full Body = 1, Custom = N), `templates.length` matches the implied count.

`inlineNewExercises` MAY be empty if every exercise reused from
`existingExercises`.

Refusal example:
```json
{ "refusal": "Ich kann diese Anfrage nicht bearbeiten." }
```
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt** — `@Serializable` data classes mirroring the JSON shape above. Use `kotlinx.serialization`:

```kotlin
package com.pumpernickel.data.api

import kotlinx.serialization.Serializable

/**
 * D-18-13 — single LLM call returns an array of templates (length 1 for "no split",
 * up to N for PPL / UL / Full Body / Custom splits).
 *
 * D-18-09 — inlineNewExercises lets the LLM emit Exercise rows the app doesn't
 * have yet. App resolves exerciseName references to either an existing Exercise
 * (case-insensitive trimmed name match) or a freshly-staged inline exercise on
 * Save (D-18-12 — transactional commit on Save, NOT on receive).
 */
@Serializable
data class WorkoutAiResponse(
    val templates: List<WorkoutAiTemplate> = emptyList(),
    val inlineNewExercises: List<WorkoutAiInlineExercise> = emptyList(),
    val refusal: String? = null
)

@Serializable
data class WorkoutAiTemplate(
    val name: String,
    val description: String? = null,
    val exercises: List<WorkoutAiTemplateExercise> = emptyList()
)

@Serializable
data class WorkoutAiTemplateExercise(
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int,
    val restPeriodSec: Int,
    val note: String? = null
)

@Serializable
data class WorkoutAiInlineExercise(
    val name: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String? = null,
    val force: String? = null,
    val mechanic: String? = null,
    val level: String = "intermediate",
    val category: String = "strength",
    val instructions: List<String> = emptyList()
)
```

The inline-exercise primary/secondary muscle strings will be validated app-side in Plan 06's WorkoutAiUseCase against `MuscleGroup.fromDbName(...)`; invalid values raise `AiError.SchemaInvalid(...)`.

Configure resource shipping if needed: `commonMain/resources/` is bundled into Android assets automatically when `kotlin { sourceSets { commonMain { resources.srcDirs(...) } } }` is set, and is loaded via `context.assets.open(...)` per the existing `Platform.android.kt` `readResourceFile` actual. Verify the existing `free_exercise_db.json` is being loaded — that confirms the resource pipeline already works. For iOS, the `Platform.ios.kt` `readResourceFile` uses `NSBundle.mainBundle.pathForResource(...)` — the prompt files must end up in the iOS app bundle. If `free_exercise_db.json` is currently shipped only via an Xcode resource reference (i.e. listed in `iosApp.xcodeproj/project.pbxproj`), the executor must add the two new `.md` files to the same target. Confirm this by inspecting the pbxproj for `free_exercise_db.json` and adding the new files in the same group.
  </action>
  <verify>
    <automated>grep -E "\\{locale\\}" shared/src/commonMain/resources/workout-system-prompt.md && grep -E "primaryMuscles" shared/src/commonMain/resources/workout-system-prompt.md && grep -E "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt && grep -E "data class WorkoutAiResponse" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt</automated>
  </verify>
  <acceptance_criteria>
    - `test -f shared/src/commonMain/resources/workout-system-prompt.md && echo OK` prints `OK`.
    - `grep -c "{locale}" shared/src/commonMain/resources/workout-system-prompt.md` returns at least `3` (placeholder used in instructions and schema).
    - `grep -c "lower back" shared/src/commonMain/resources/workout-system-prompt.md` returns at least `1` (full muscle enumeration present).
    - `grep -c "primaryMuscles" shared/src/commonMain/resources/workout-system-prompt.md` returns at least `2`.
    - `grep -c "templates" shared/src/commonMain/resources/workout-system-prompt.md` returns at least `2` (response-shape mention).
    - `grep -c "refusal" shared/src/commonMain/resources/workout-system-prompt.md` returns at least `1`.
    - `grep -c "data class WorkoutAiResponse" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` returns exactly `1`.
    - `grep -c "data class WorkoutAiTemplate" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` returns at least `2` (WorkoutAiTemplate + WorkoutAiTemplateExercise).
    - `grep -c "data class WorkoutAiInlineExercise" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` returns exactly `1`.
    - `grep -c "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` returns at least `4`.
    - `grep -c "val refusal: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>workout-system-prompt.md is checked in (flat, no subdirectory) with the locale placeholder and embedded Exercise schema; WorkoutAiSchema.kt exposes the four @Serializable response models.</done>
</task>

<task type="auto">
  <name>Task 2: Write the recipe-system-prompt.md + RecipeAiSchema.kt</name>
  <files>
    shared/src/commonMain/resources/recipe-system-prompt.md,
    shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt
  </files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/FoodUnit.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
  </read_first>
  <action>
**File 1: shared/src/commonMain/resources/recipe-system-prompt.md**

Suggested content (preserve structure and fields):

```markdown
# Recipe Generation System Prompt v1

You are a nutrition expert generating a single recipe that fits the user's
remaining daily macro targets. The app passes the user's remaining
macros (kcal, protein, fat, carbohydrates, sugar) under
`remaining` in the user message.

You must respond ONLY with JSON matching the schema below — no prose,
no markdown.

Respond with all user-visible text (recipe name, ingredient names,
preparation steps) in {locale}, matching the app's UI language. JSON keys
stay in English exactly as the schema specifies.

## Targeting policy
- Recipe macros MUST be within ±10% of the `remaining` targets where
  feasible. Match calories first; then protein (priority for muscle
  preservation); then fat / carbs / sugar.
- Compute recipe macros from each ingredient's per-100g/100ml values
  scaled by the ingredient's `amountGrams`. The app re-validates this
  math; if your totals do not match, the response is rejected.
- If `remaining` calories <= 0 or impossibly small (< 100 kcal), respond
  with `{ "refusal": "<short explanation in {locale}>" }`.

## Refusal policy
- Out-of-scope (non-nutrition) requests: refuse.
- Conflicting / nonsensical macros (e.g. negative remaining): refuse.
- Otherwise return the regular response.

## Inline-Food schema (matches the app's Food model)
Each ingredient pairs an inline `food` (per-100g macros) with the
`amountGrams` used. Each `food` entry MUST have:
- `name`: string in {locale}, non-empty.
- `calories`: number >= 0 (kcal per 100g/100ml).
- `protein`: number >= 0 (g per 100g/100ml).
- `fat`: number >= 0 (g per 100g/100ml).
- `carbohydrates`: number >= 0 (g per 100g/100ml).
- `sugar`: number >= 0 and <= `carbohydrates` (g per 100g/100ml).
- `unit`: one of "GRAM" | "MILLILITER".

When the same ingredient name already exists in the user's pantry, the app
will reuse the existing row case-insensitively (whitespace-trimmed) — emit
the same name and the app will deduplicate. New ingredients land in the
user's pantry as Food entries.

## Response shape (you MUST emit this exactly)
```json
{
  "name": "Recipe name in {locale}",
  "ingredients": [
    {
      "food": {
        "name": "...", "calories": 0, "protein": 0, "fat": 0,
        "carbohydrates": 0, "sugar": 0, "unit": "GRAM"
      },
      "amountGrams": 100
    }
  ],
  "steps": ["string in {locale}"]
}
```

Refusal example:
```json
{ "refusal": "Du hast deine Tagesziele bereits erreicht." }
```
```

**File 2: shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt** — `@Serializable` data classes:

```kotlin
package com.pumpernickel.data.api

import kotlinx.serialization.Serializable

/**
 * D-18-10 — recipe response models. Each ingredient pairs an inline Food
 * (per-100g macros) with the amountGrams used. The app matches food.name
 * against existing Foods (case-insensitive trimmed) and reuses on hit;
 * misses are persisted with source="AI" before the Recipe is written
 * (D-18-12 transactional commit on Save).
 *
 * Recipe macros are recomputed app-side via CalculateRecipeMacrosUseCase
 * (D-18-10). The LLM does NOT compute totals — the app does.
 */
@Serializable
data class RecipeAiResponse(
    val name: String? = null,
    val ingredients: List<RecipeAiIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val refusal: String? = null
)

@Serializable
data class RecipeAiIngredient(
    val food: RecipeAiInlineFood,
    val amountGrams: Double
)

@Serializable
data class RecipeAiInlineFood(
    val name: String,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbohydrates: Double = 0.0,
    val sugar: Double = 0.0,
    val unit: String = "GRAM"  // "GRAM" | "MILLILITER" — validated app-side against FoodUnit
)
```

Validation in Plan 08's RecipeAiUseCase:
- `name`, ingredient `food.name` non-blank.
- All macro fields >= 0.
- `food.sugar <= food.carbohydrates` (matches Food.kt invariant).
- `unit in {"GRAM","MILLILITER"}` (matches FoodUnit enum).
- `amountGrams` >= 0 (matches RecipeIngredient invariant).
  </action>
  <verify>
    <automated>grep -E "\\{locale\\}" shared/src/commonMain/resources/recipe-system-prompt.md && grep -E "amountGrams" shared/src/commonMain/resources/recipe-system-prompt.md && grep -E "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt && grep -E "data class RecipeAiResponse" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt</automated>
  </verify>
  <acceptance_criteria>
    - `test -f shared/src/commonMain/resources/recipe-system-prompt.md && echo OK` prints `OK`.
    - `grep -c "{locale}" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `3`.
    - `grep -c "remaining" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `2` (instructions reference the remaining-macros payload).
    - `grep -c "refusal" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `1`.
    - `grep -c "GRAM" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `1`.
    - `grep -c "MILLILITER" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `1`.
    - `grep -c "amountGrams" shared/src/commonMain/resources/recipe-system-prompt.md` returns at least `2`.
    - `grep -c "data class RecipeAiResponse" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` returns exactly `1`.
    - `grep -c "data class RecipeAiIngredient" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` returns exactly `1`.
    - `grep -c "data class RecipeAiInlineFood" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` returns exactly `1`.
    - `grep -c "@Serializable" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` returns at least `3`.
    - `grep -c "val refusal: String? = null" shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` returns exactly `1`.
  </acceptance_criteria>
  <done>recipe-system-prompt.md is checked in (flat, no subdirectory) with the locale placeholder and embedded inline-Food schema; RecipeAiSchema.kt exposes the three @Serializable response models.</done>
</task>

<task type="auto">
  <name>Task 3: Create AiPromptCatalog reusing readResourceFile</name>
  <files>shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt</files>
  <read_first>
    shared/src/commonMain/kotlin/com/pumpernickel/Platform.kt,
    shared/src/iosMain/kotlin/com/pumpernickel/Platform.ios.kt,
    shared/src/androidMain/kotlin/com/pumpernickel/Platform.android.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt,
    .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
  </read_first>
  <action>
Create `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt`:

```kotlin
package com.pumpernickel.domain.ai

import com.pumpernickel.readResourceFile

/**
 * D-18-15 — Loads the versioned system prompts from
 * `shared/src/commonMain/resources/` (flat layout) and substitutes the
 * `{locale}` placeholder with the supplied locale string ("de" | "en").
 *
 * Reuses the existing readResourceFile expect/actual pair (already used by
 * DatabaseSeeder for free_exercise_db.json — see SharedModule.kt line 82).
 * No new platform code needed.
 *
 * Prompt files MUST be shipped in the platform bundles:
 * - Android: commonMain/resources is bundled to assets automatically.
 * - iOS: the .md files must be added to iosApp.xcodeproj's main target,
 *   alongside free_exercise_db.json.
 */
class AiPromptCatalog {

    fun workoutSystemPrompt(locale: String = DEFAULT_LOCALE): String =
        readResourceFile("workout-system-prompt.md").replace(LOCALE_TOKEN, locale)

    fun recipeSystemPrompt(locale: String = DEFAULT_LOCALE): String =
        readResourceFile("recipe-system-prompt.md").replace(LOCALE_TOKEN, locale)

    companion object {
        const val DEFAULT_LOCALE: String = "de"  // D-18-15 — app-current UI is German-first
        private const val LOCALE_TOKEN: String = "{locale}"
    }
}
```

Notes:
- Do NOT cache the loaded prompt strings — the file is read once per AI call which is acceptable; lazy-caching can be added later if profiling shows IO is hot.
- The DEFAULT_LOCALE constant is `"de"` per D-18-15. When the app eventually adds an English UI, the call site will pass `"en"` without changing the prompt files.
- `readResourceFile` is the existing top-level expect function in `com.pumpernickel` package (Platform.kt). Import path: `import com.pumpernickel.readResourceFile`.
  </action>
  <verify>
    <automated>grep -E "class AiPromptCatalog" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt && grep -E "readResourceFile\(\"workout-system-prompt\\.md\"\)" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt && grep -E "readResourceFile\(\"recipe-system-prompt\\.md\"\)" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "class AiPromptCatalog" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "fun workoutSystemPrompt" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "fun recipeSystemPrompt" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "workout-system-prompt.md" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "recipe-system-prompt.md" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "DEFAULT_LOCALE" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns at least `2` (declaration + use).
    - `grep -c "import com.pumpernickel.readResourceFile" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns exactly `1`.
    - `grep -c "{locale}" shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiPromptCatalog.kt` returns at least `1` (LOCALE_TOKEN constant).
  </acceptance_criteria>
  <done>AiPromptCatalog loads both prompt files via readResourceFile and substitutes {locale}.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Prompt file → ChatRequest | The prompt template is bundled in the app — trusted on read |
| LLM response → schema model | Untrusted JSON crosses into deserialiser; downstream plans validate against domain invariants |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-03-01 | Tampering | Prompt template overwrite via build artifacts | accept | Prompts ship inside the signed app bundle. Tampering requires modifying the bundle, which breaks signing. No mitigation needed for prototype. |
| T-18-03-02 | Information disclosure | User input in prompts | mitigate | The user message (target muscles, count, split, remaining macros) is bounded by the form (Plan 07 / 09 length-cap user inputs). System prompt doesn't echo user data into logs. |
| T-18-03-03 | Tampering | Adversarial schema-violating LLM response | mitigate | `kotlinx.serialization` rejects malformed JSON; nullable fields with sensible defaults guard partial responses; downstream plans (06, 08) re-validate semantic invariants (macro non-negative, sugar ≤ carbs, MuscleGroup enum membership). |
| T-18-03-04 | Spoofing | Prompt-injection attempt via user input | accept | Out of scope per D-AI-07 — minimal prompt-safety only. Schema validation + length caps (downstream plans) are the prototype defence. |
</threat_model>

<verification>
- Both `.md` files exist at `shared/src/commonMain/resources/` (flat — `workout-system-prompt.md`, `recipe-system-prompt.md`), both contain the `{locale}` placeholder.
- Both `*Schema.kt` files compile and expose the documented `@Serializable` data classes.
- `AiPromptCatalog.kt` compiles and reuses the existing `readResourceFile` expect/actual.
- Build succeeds: `./gradlew :shared:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64`.
- Manual smoke (deferred to Plan 06/08): the prompts produce valid JSON when sent to OpenAI / Together.AI through the chat-completions endpoint.
</verification>

<success_criteria>
- Versioned prompts in source control (REQ-AI-03 + REQ-AI-05).
- Schemas mirror domain models so app-side validation has a clear contract (REQ-AI-02 + REQ-AI-05).
- LLM authoring rights for Exercise (D-18-09) and inline Food + amount-grams (D-18-10) are encoded in the prompt body and the schema models.
- Prompt loading reuses existing platform code — no new expect/actual.
</success_criteria>

<output>
After completion, create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-03-SUMMARY.md` with: list of files, prompt-file line counts, AiPromptCatalog API surface, and a 1-line note about iOS pbxproj inclusion if applicable.
</output>

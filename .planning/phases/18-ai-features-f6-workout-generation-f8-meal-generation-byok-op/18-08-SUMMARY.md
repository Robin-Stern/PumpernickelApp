---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "08"
subsystem: recipe-ai-domain
tags: [recipe-ai, use-case, viewmodel, koin, kmp, di, f8-meal-generation, byok, staging, macros]
dependency_graph:
  requires:
    - 18-02 (OpenAICompatibleClient, AiError sealed class)
    - 18-03 (RecipeAiSchema: RecipeAiResponse/RecipeAiIngredient/RecipeAiInlineFood, AiPromptCatalog)
    - 18-04 (SecureKeyStore expect/actual, SettingsRepository AI fields)
    - 18-05 (AiModule + AiSettingsViewModel base)
    - 18-01 (Recipe.kt + Food.kt with source field)
  provides:
    - RecipeAiPreview staging types (domain/ai)
    - RemainingMacros with isExhausted gate
    - MacrosFitIndicator with ±10% fitsAll
    - RecipeAiUseCase: computeRemaining / invoke / commit
    - RecipeAiViewModel: 8 sealed states + 6 actions
    - AiModule: RecipeAiUseCase single + RecipeAiViewModel viewModel bindings
    - RecipeAiKoinHelper (iosMain) for iOS UI integration
  affects:
    - Plan 09: Android RecipeAi screen uses AiMealGenRoute + RecipeAiViewModel
    - Plan 10: iOS RecipeAi view uses RecipeAiKoinHelper
    - Plan 06: parallel wave-4 plan adds WorkoutAiViewModel to same AiModule file (merge isolated by comment block)
tech_stack:
  added: []
  patterns:
    - RecipeAiUseCase mirrors WorkoutAiUseCase pattern: json_schema primary → json_object retry-once on SchemaInvalid/400/422
    - Preview staging: all DB writes deferred until commit() — transactional Foods-then-Recipe
    - computeFits uses pseudo-ids (_staged_N) for in-memory CalculateRecipeMacrosUseCase call — never reach DB
    - RecipeAiViewModel mirrors AiSettingsViewModel StateFlow pattern with @NativeCoroutinesState
    - AiModule additions wrapped in Recipe AI comment block for clean parallel-worktree merge
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
decisions:
  - "fitsAll indicator is non-blocking: out-of-tolerance recipes are shown to the user as a warning (not auto-rejected); UI layer can surface the indicator (REQ-AI-04 UAT says 'within ±10%' descriptively)"
  - "AiModule additions isolated in Recipe AI comment block (between '--- Recipe AI (F8) ---' markers) so wave-merge with Plan 06 WorkoutAiViewModel additions is unambiguous"
  - "RecipeAiUseCase.computeRemaining uses settingsRepository.nutritionGoals.first() — Flow — not a suspend direct call; LoadConsumptionsForDateUseCase.invoke is suspend returning List<ConsumptionEntry> directly (confirmed in source)"
  - "Loading state added to RecipeAiUiState (not in plan template) to cover the transient init period before key check + remaining check complete — prevents UI flicker"
metrics:
  duration: "~8m"
  completed: "2026-05-08"
  tasks: 2
  files_created: 4
  files_modified: 1
---

# Phase 18 Plan 08: Recipe AI Use Case and VM — Summary

**One-liner:** F8 Recipe AI domain layer — RecipeAiPreview staging types, RecipeAiUseCase orchestrating remaining-macro gate → LLM call → schema validation → Food deduplication → MacrosFitIndicator, plus RecipeAiViewModel 8-state machine, AiModule Koin bindings, and iOS KoinHelper.

## Files Created / Modified

### Created

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` — 5 data classes: `RecipeAiPreview`, `StagedRecipe`, `StagedRecipeIngredient`, `StagedFood`, `MacrosFitIndicator`, plus `RemainingMacros` with `isExhausted` (kcal <= 100 gate)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` — use case with 3 public entry points: `computeRemaining`, `invoke`, `commit`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` — ViewModel with 8 sealed `RecipeAiUiState` branches and 6 actions
- `shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt` — canonical 9-line iOS factory

### Modified

- `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — appended `single { RecipeAiUseCase(...) }` and `viewModel { RecipeAiViewModel(...) }` inside an isolated Recipe AI comment block

## RecipeAiUseCase Responsibilities

| Entry Point | Responsibility |
|-------------|----------------|
| `computeRemaining()` | Read today's ConsumptionEntries → calculateDailyMacros → subtract from NutritionGoals → return RemainingMacros (isExhausted if kcal <= 100) |
| `invoke(remaining)` | Build prompt → call LLM with json_schema → retry-once with json_object on SchemaInvalid/400/422 → validateResponse (negative macros, sugar>carbs, unknown unit, refusal) → resolvePreview (Food dedup, staged new foods, computeFits) |
| `commit(preview)` | Persist each StagedFood with source="AI" → map ingredients to resolved foodIds → save Recipe with source="AI" (transactional, no DB writes before this call) |

## RecipeAiUiState Branches (8 states)

| State | When |
|-------|------|
| `Loading` | Transient init before key check + remaining compute |
| `NoKey` | `secureKeyStore.readApiKey() == null` at init |
| `RemainingExhausted` | `remaining.isExhausted` (kcal <= 100) — D-18-04 |
| `Form` | Normal state; user taps "Generieren" |
| `Generating` | In-flight LLM call; Cancel button visible |
| `Preview` | Valid `RecipeAiPreview` returned; preview sheet shown |
| `Error` | Any `AiError` from invoke/save; `originatingRemaining` for retry |
| `Saved` | `commit()` succeeded |

## RecipeAiViewModel Actions

- `refreshRemaining()` — re-reads remaining (from Form, after Saved, or from error with null originatingRemaining)
- `generate()` — transitions Form → Generating, fires useCase.invoke()
- `cancel()` — cancels generationJob, returns to Form
- `save()` — transitions Preview → Saved via useCase.commit()
- `discardPreview()` — returns Preview → Form
- `retryFromError()` — returns Error → Form (or refreshRemaining if no originatingRemaining)

## AiModule Final Shape (this worktree)

```kotlin
val aiModule = module {
    single { AiPromptCatalog() }
    single { OpenAICompatibleClient(client = get(), keyProvider = { get<SecureKeyStore>().readApiKey() }) }
    viewModel { AiSettingsViewModel(get(), get()) }
    // --- Recipe AI (F8) — Plan 08 ---
    single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RecipeAiViewModel(get(), get()) }
    // --- End Recipe AI (Plan 08) ---
}
```

Note: `viewModel { WorkoutAiViewModel(...) }` from Plan 06 is in Plan 06's parallel worktree; the orchestrator wave-merge will combine both plan's additions without conflict due to the isolated comment blocks.

## Security Mitigations Implemented

| Threat ID | Mitigation |
|-----------|-----------|
| T-18-08-01 | `validateResponse()` rejects sugar > carbohydrates with SchemaInvalid |
| T-18-08-02 | `validateResponse()` rejects all negative macro values |
| T-18-08-03 | `validateResponse()` rejects unit not in {GRAM, MILLILITER} |
| T-18-08-04 | `resolvePreview()` case-insensitive trimmed name match deduplicates against existing Foods — LLM cannot inject duplicate Food rows |
| T-18-08-05 | Accepted — remaining macros are user-local data; no PII leakage risk |
| T-18-08-06 | 64KB response cap enforced by Plan 02's OpenAICompatibleClient before deserialization |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Added `Loading` state to RecipeAiUiState**
- **Found during:** Task 2 implementation
- **Issue:** The plan's sealed class definition has no `Loading` state. The VM's `init {}` block executes `secureKeyStore.readApiKey()` and `computeRemaining()` asynchronously in a coroutine — without a Loading state the UI would start in an undefined state (the plan's `Form` or `NoKey` aren't set yet), causing a brief incorrect display.
- **Fix:** Added `object Loading : RecipeAiUiState()` as the initial MutableStateFlow value. The init coroutine transitions out of it immediately into NoKey, Form, or RemainingExhausted. This is standard ViewModel pattern (AiSettingsViewModel's own `_apiKeyConfigured` has an analogous init coroutine).
- **Files modified:** `RecipeAiViewModel.kt`
- **Commit:** 2c0341f

None other — plan executed as written.

## Known Stubs

None — all data sources are wired to real repositories. No hardcoded empty values flow to UI rendering. The `recipeAiSchema()` returns a minimal `{type: object}` shell (consistent with WorkoutAiUseCase pattern from Plan 06) which is sufficient for the json_schema request; a richer schema is a future enhancement.

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>`. No new network endpoints, auth paths, file access patterns, or schema changes introduced at trust boundaries.

## Self-Check: PASSED

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiPreview.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` — FOUND
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt` — FOUND
- `shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt` — FOUND
- Commit d3ec329 (Task 1: RecipeAiPreview + RecipeAiUseCase) — FOUND
- Commit 2c0341f (Task 2: RecipeAiViewModel + AiModule + KoinHelper) — FOUND

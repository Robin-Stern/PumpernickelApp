---
phase: 260510-w9h
plan: 01
subsystem: ai/provider-defaults
tags: [ai, defaults, provider-presets, error-messages, copy]
dependency_graph:
  requires:
    - evals/MULTI-MODEL-RESULTS.md (multi-model eval evidence)
  provides:
    - "Together preset default model = google/gemma-4-31B-it"
    - "Empty-content / reasoning-exhaustion error copy steers users to google/gemma-4-31B-it"
  affects:
    - AI Settings screen (Together preset row)
    - Recipe generation error-handling surface
    - Workout generation error-handling surface
tech_stack:
  added: []
  patterns:
    - "Map<String, Pair<String, String>> PROVIDER_DEFAULTS lookup (unchanged)"
key_files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
decisions:
  - "Swap Together default + recommended-model error copy to google/gemma-4-31B-it based on multi-model eval evidence (gpt-oss-20b 0/4 vs gemma-4-31B-it 12/12 stdev 0 on Recipe + Workout suites)"
  - "Preserve 'Nicht-Reasoning-Modell wie ...' German phrasing in two error-message sites — gemma-4-31B-it is a pure instruct (non-reasoning) model, so the class-of-model hint stays accurate"
  - "Leave openrouter ':free' preset (openai/gpt-oss-20b:free) untouched — out of scope per plan constraints"
  - "Leave OpenAI default (gpt-4o-mini) untouched — out of scope per plan constraints"
metrics:
  duration: "~3min"
  tasks_total: 2
  tasks_completed: 2
  files_modified: 3
  commits: 2
  completed_date: "2026-05-10T21:18:34Z"
---

# Phase 260510-w9h Plan 01: App defaults für AI-Provider von openai/gpt-oss-20b auf google/gemma-4-31B-it Summary

**One-liner:** Swap Together provider preset default + six empty-content / reasoning-exhaustion error-message recommendations from `openai/gpt-oss-20b` to `google/gemma-4-31B-it`, based on multi-model eval evidence that gpt-oss-20b scores 0/4 on Recipe while gemma-4-31B-it scores 12/12 stdev 0 across both suites.

## What Changed

### Files Touched (3)

| File | Lines | Change |
|------|-------|--------|
| `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt` | 87 | Together preset default model: `openai/gpt-oss-20b` -> `google/gemma-4-31B-it` |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` | 204, 226, 230 | Empty-content / reasoning-exhaustion error copy recommends `google/gemma-4-31B-it` |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` | 237, 262, 266 | Same: empty-content / reasoning-exhaustion error copy recommends `google/gemma-4-31B-it` |

Total: 7 string sites updated (1 default + 6 error-message sites). Base URLs, surrounding German prose, and all other PROVIDER_DEFAULTS rows unchanged.

### Intentionally Left Alone (per plan constraints)

- `AiSettingsViewModel.kt` line 86 — OpenAI default `gpt-4o-mini` (out of scope).
- `AiSettingsViewModel.kt` line 88 — Openrouter `:free` preset `openai/gpt-oss-20b:free` (out of scope).
- `AiSettingsViewModel.kt` line 89 — Groq `llama-3.3-70b-versatile` (out of scope).
- `AiSettingsViewModel.kt` line 90 — Custom empty default (out of scope).
- "Nicht-Reasoning-Modell wie ..." phrasing in RecipeAiUseCase.kt line 226 + WorkoutAiUseCase.kt line 262 — preserved verbatim; gemma-4-31B-it is a pure instruct model so the class-of-model hint remains accurate (only the identifier swapped).

## Tasks Completed

### Task 1: Swap Together preset default model

**Commit:** `ef5a08e` — `chore(260510-w9h): swap Together preset default to google/gemma-4-31B-it`

- Edited `AiSettingsViewModel.kt` line 87 in PROVIDER_DEFAULTS.
- Verification: `grep -nE 'together.*together\.ai.*gemma-4-31B-it'` matched line 87; `openai/gpt-oss-20b` as a standalone token (without `:free`) no longer present.

### Task 2: Update error-message model recommendations in Recipe + Workout use cases

**Commit:** `b643711` — `chore(260510-w9h): point empty-content error copy to google/gemma-4-31B-it`

- Edited 3 sites in `RecipeAiUseCase.kt` (lines 204, 226, 230).
- Edited 3 sites in `WorkoutAiUseCase.kt` (lines 237, 262, 266).
- Verification: each file contains exactly 3 occurrences of `google/gemma-4-31B-it` and 0 occurrences of `openai/gpt-oss-20b`. "Nicht-Reasoning-Modell wie google/gemma-4-31B-it" present in both.

## Eval Evidence Backing the Swap

Per `evals/MULTI-MODEL-RESULTS.md` (commit `e479aae`):

- `openai/gpt-oss-20b` scored **0/4 on Recipe**: reasoning trace consumes `max_tokens=4096`, so the model never emits JSON. Empty-content error path is hit deterministically.
- `google/gemma-4-31B-it` scored **12/12 stdev 0** across both Recipe and Workout suites — the only model in the eval matrix that landed perfect on both.

The defaults were steering users to a model that empirically does not work for our JSON-mode workloads; the recommendation now points to the model that does.

## Decisions Made

1. **Default model selection by eval-driven evidence, not vendor familiarity.** Together's `openai/gpt-oss-20b` was a sensible early default by reputation, but the multi-model eval matrix found it unusable for our JSON-schema workloads. `google/gemma-4-31B-it` was selected because it is the only model that scored perfect (12/12 stdev 0) across both Recipe and Workout suites in the eval matrix.
2. **Preserve the "Nicht-Reasoning-Modell wie ..." nuance.** `gemma-4-31B-it` is a pure instruct (non-reasoning) model, so the class-of-model hint in error copy stays accurate. The error message now informs users not just which model name to switch to, but also which class of model (non-reasoning) avoids the failure mode they just hit.
3. **Scope limited to the 3 Kotlin files in plan `<files_modified>`.** The iOS `AISettingsView.swift` quick-pick suggestion list still references `openai/gpt-oss-20b` in three places (lines 347/355/362) but was intentionally left untouched — see Deferred Items below.

## Deviations from Plan

None. Plan executed exactly as written.

### Auto-fixed Issues

None.

### Auth Gates Encountered

None.

## Deferred Items

Logged to `.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/deferred-items.md`:

- **iOS quick-pick suggestion list still references `openai/gpt-oss-20b`** at three sites in `iosApp/iosApp/Views/AI/AISettingsView.swift` (lines 347, 355, 362). Out of scope for this plan — the file is not in `<files_modified>` and updating the model id without re-reviewing the editorial notes ("Kostenlos · empfohlen", "Stark, mittlere Latenz") could make the copy misleading. Recommended follow-up: separate quick task with editorial review of each suggestion entry.

## Plan-vs-Reality Verification (Done Criteria)

- AiSettingsViewModel.kt line 87 maps the `together` provider to `google/gemma-4-31B-it`. **PASS**
- RecipeAiUseCase.kt contains exactly 3 occurrences of `google/gemma-4-31B-it` and 0 of `openai/gpt-oss-20b`. **PASS**
- WorkoutAiUseCase.kt contains exactly 3 occurrences of `google/gemma-4-31B-it` and 0 of `openai/gpt-oss-20b`. **PASS**
- "Nicht-Reasoning-Modell wie google/gemma-4-31B-it" phrasing preserved verbatim in both use case files. **PASS**
- OpenAI preset on line 86 (`gpt-4o-mini`) unchanged. **PASS**
- Openrouter preset on line 88 (`openai/gpt-oss-20b:free`) unchanged. **PASS**
- No other files modified; no logic or formatting changes anywhere. **PASS**

## Self-Check: PASSED

- FOUND: `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt`
- FOUND: `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt`
- FOUND: `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt`
- FOUND: `.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/260510-w9h-SUMMARY.md`
- FOUND: `.planning/quick/260510-w9h-app-defaults-f-r-ai-provider-von-openai-/deferred-items.md`
- FOUND: commit `ef5a08e` (Task 1 — Together preset default)
- FOUND: commit `b643711` (Task 2 — Recipe + Workout error copy)

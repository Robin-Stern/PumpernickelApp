---
phase: 260512-mr1-diagnose-logging-f-r-off-suche
plan: 01
subsystem: diagnostics
tags: [diagnostics, logging, openfoodfacts, ios, temporary]
dependency_graph:
  requires: []
  provides:
    - "[OFF] runtime markers in OpenFoodFactsApi.searchByName"
    - "[SearchUC] runtime markers in SearchFoodsRemoteUseCase.invoke"
    - "[FoodVM] runtime markers in FoodEntryViewModel debounce-collect"
  affects:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/FoodEntryViewModel.kt
tech_stack:
  added: []
  patterns: ["println diagnostic markers (temporary)"]
key_files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/FoodEntryViewModel.kt
decisions:
  - "Used `println` (not a logger) — matches plan and is the cheapest way to surface output in Xcode console for both iOS Native and Simulator."
  - "No variable-name substitutions needed: `responseText`, `results`, `result.foods`, and `result.message` were already the actual identifiers used in the source files."
  - "Wrapped each `when`-arm body in `{}` in FoodEntryViewModel to host both the println and the `_uiState.update { … }` call inline. No control-flow change."
metrics:
  duration_minutes: 2
  completed_date: 2026-05-12
requirements: [DIAG-OFF-SEARCH]
---

# Phase 260512-mr1 Plan 01: Diagnose-Logging für OFF-Suche Summary

Inserted 12 temporary `println` diagnostic markers across 3 shared-common Kotlin files (`OpenFoodFactsApi`, `SearchFoodsRemoteUseCase`, `FoodEntryViewModel`) instrumenting the entire OFF search pipeline — debounce trigger → use-case invocation → HTTP call → response receipt → HTML detection → JSON decode → mapping → Result-branch dispatch — to identify where the "0 results, nothing happens" failure occurs on iOS.

## Marker Placement

### File 1 — `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt`

4 `[OFF]` markers inside `searchByName(query, pageSize)`:

| # | Line | Placement | Statement |
|---|------|-----------|-----------|
| 1 | 22 | Function entry, before `client.get(...)` | `println("[OFF] searchByName query='$query' pageSize=$pageSize")` |
| 2 | 32 | Immediately after `.bodyAsText()` assigns `responseText` | `println("[OFF] response received bytes=${responseText.length} preview='${responseText.take(120)}'")` |
| 3 | 34 | Inside HTML-detection `if`-block, before `throw IllegalStateException` | `println("[OFF] HTML detected — throwing IllegalStateException")` |
| 4 | 37 | Immediately before `return json.decodeFromString(responseText)` | `println("[OFF] decoding JSON…")` |

### File 2 — `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt`

4 `[SearchUC]` markers inside `invoke(query)`:

| # | Line | Placement | Statement |
|---|------|-----------|-----------|
| 1 | 24 | Function entry, before `try` | `println("[SearchUC] invoke query='$query'")` |
| 2 | 27 | Inside `try`, immediately after `val response = api.searchByName(query)` | `println("[SearchUC] api returned count=${response.count} products=${response.products.size}")` |
| 3 | 44 | Immediately after `val results = response.products.mapNotNull { … }` | `println("[SearchUC] mapped results=${results.size}")` |
| 4 | 47 | Catch block, first line before message inspection | `println("[SearchUC] caught ${e::class.simpleName}: ${e.message}")` |

### File 3 — `shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/FoodEntryViewModel.kt`

4 `[FoodVM]` markers inside the debounce-collect lambda in `init`:

| # | Line | Placement | Statement |
|---|------|-----------|-----------|
| 1 | 100 | First line of `.collect { query -> ... }` lambda, before length check | `println("[FoodVM] debounce fired query='$query' length=${query.length}")` |
| 2 | 105 | Success arm, before `_uiState.update { … }` | `println("[FoodVM] -> Success size=${result.foods.size}")` |
| 3 | 109 | Empty arm, before `_uiState.update { … }` | `println("[FoodVM] -> Empty")` |
| 4 | 113 | Error arm, before `_uiState.update { … }` | `println("[FoodVM] -> Error: ${result.message}")` |

Note: each `when`-arm body was wrapped in `{}` so the println and the `_uiState.update` call could both live inline. No semantic change — the arms still produce the same `Unit` result.

## Variable-Name Substitutions

None. All placeholder identifiers in the plan matched the source files verbatim:

- `responseText` — actual local var in `OpenFoodFactsApi.searchByName`
- `results` — actual local var in `SearchFoodsRemoteUseCase.invoke`
- `result.foods` — actual property on `SearchFoodsRemoteUseCase.Result.Success`
- `result.message` — actual property on `SearchFoodsRemoteUseCase.Result.Error`

## Build Result

`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` → **exit 0**.

Only pre-existing warnings emitted (suspend-exposed-to-ObjC, `expect/actual classes in Beta`, biometric/SecureKeyStore redundant-cast warnings in `iosMain`). None of them originate from this plan's changes. Per the SCOPE BOUNDARY rule, these pre-existing warnings are out of scope and were not touched.

## Verification

- `grep -c "\[OFF\]" …/OpenFoodFactsApi.kt` → **4** ✓
- `grep -c "\[SearchUC\]" …/SearchFoodsRemoteUseCase.kt` → **4** ✓
- `grep -c "\[FoodVM\]" …/FoodEntryViewModel.kt` → **4** ✓
- iOS simulator framework link → **exit 0** ✓
- `git diff HEAD~1 HEAD --name-only` → exactly the 3 expected files ✓
- No changes to DTOs, UI layers, Ktor setup, Android, evals, or `iosApp/` ✓

## Commit

- **SHA:** `58a690f67ff38752d3dde97e60fc522896b5b868` (`58a690f`)
- **Message:** `chore(diag): add println markers to OFF search pipeline (temporary)`
- **Stats:** 3 files changed, 18 insertions(+), 3 deletions(-)

## Deviations from Plan

None — plan executed exactly as written.

## Reminder — Temporary Instrumentation

These 12 `println` markers are **temporary** debugging aids. Once the next manual iOS run reveals where the chain breaks (e.g. `[FoodVM] debounce fired` never appears → debounce never fires; `[OFF] response received` shows `bytes=0` → network failure; `[SearchUC] mapped results=0` while `products=N>0` → mapping/DTO mismatch), schedule a follow-up commit to remove them. Suggested follow-up message:

```
revert(diag): remove temporary println markers from OFF search pipeline
```

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/FoodEntryViewModel.kt`: FOUND
- Commit `58a690f`: FOUND
- All grep counts == 4
- iOS framework link exit 0

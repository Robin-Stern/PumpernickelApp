---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 05
subsystem: nutrition / barcode-lookup
tags: [bug-fix, B2, D-21-03, openfoodfacts, barcode, ui-hint]
requires:
  - 21-03 (Wave B — clean OFF baseline; sets the search/adapter context this plan traces through)
provides:
  - "Honest UI hint when a barcode scan yields zero macros (OFF empty + fallback miss)"
  - "Documented D-21-03 root-cause for B2 in code"
affects:
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt
tech-stack:
  added: []
  patterns: ["bug trace-then-fix via temporary println instrumentation; ViewModel guard for honest user feedback"]
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt
decisions:
  - "Honored D-21-03: traced the barcode dataflow first; identified ViewModel's silent persistence of zero-macro Food as root cause via static analysis (UAT still pending)."
  - "Persist the Food anyway when macros are zero, instead of refusing — name and barcode are preserved so the user can manually edit macros from the foods list."
  - "Zero-macro guard uses kcal < 1.0 AND protein/carbs/fat <= 0.0 (no single field is enough; water is the only legitimate zero-kcal product and is rarely barcoded as an ingredient)."
metrics:
  duration_minutes: 6
  completed_date: 2026-05-19
  tasks_completed: 2  # Task 3 is a human-verify checkpoint, not a code task
  tasks_total: 3
---

# Phase 21 Plan 05: B2 — Barcode-Scan zeigt alle Nährwerte = 0 Summary

**One-liner:** Static-analysis-identified D-21-03 root-cause for B2: ViewModel silently persisted zero-macro Foods when both OFF and the LookupBarcodeUseCase fallback table had no data. Fixed by adding a `hasNoMacros` guard in `RecipeCreationViewModel.OnBarcodeScanned` that surfaces a clear UI hint while still persisting the Food (with name + barcode) so the user can edit it manually.

## What Was Built

### Task 1 — Trace logs at each barcode dataflow handoff (committed)

Added `println("[B2] …")` instrumentation at four points along the barcode flow so the user could read off the [B2]-tagged lines during the iOS-simulator UAT and pinpoint which layer drops the macros:

1. **`OpenFoodFactsAdapter.lookupBarcode`** — logged raw `OpenFoodFactsResponse` nutriments (energyKcal100g, proteins100g, carbohydrates100g, fat100g) BEFORE null-coalescing, then the post-coalesced `RemoteBarcodeProduct` (kcal, protein, carbs, fat, sugar). Two trace lines per call. Rejection branch (status != 1 OR blank name) also logged.
2. **`LookupBarcodeUseCase`** — logged the `FoundRemote` result AFTER fallback-table resolution, including the `fromFallback` flag and the internal `allZeroOFF` boolean. Catch block also logged exception class + message.
3. **`RecipeCreationViewModel.OnBarcodeScanned`** — logged the incoming barcode, the per-branch outcome (FoundLocally / FoundRemote / NotFound / Error), and the in-VM `Food` constructed in the FoundRemote branch BEFORE `repository.saveFood`.
4. **`FoodRepositoryImpl.saveFood`** — logged the `Food` payload (id, name, kcal, protein, carbs, fat, sugar, barcode) immediately BEFORE the `dao.insertFood` call so a sqlite/DB-Browser dump can be diffed against the in-memory state.

Acceptance: `grep -rcE '\[B2\]' shared/src/commonMain/kotlin/com/pumpernickel/` yielded 12 (≥5 required). The `D-21-03 root-cause:` comment was placed near `OnBarcodeScanned` in `RecipeCreationViewModel.kt`.

Commit: `3164d42` — `test(21-05): add [B2] trace logs at each barcode dataflow handoff`.

### Task 2 — Layer-specific fix (committed)

Static analysis verdict before UAT (recorded inline as the `D-21-03 root-cause:` comment): **ViewModel + UseCase**. Evidence chain:

- `OpenFoodFactsAdapter.lookupBarcode` null-coalesces every macro to `0.0` when the OFF response field is null. By design — not a bug.
- `LookupBarcodeUseCase` already has a `FALLBACKS` table that triggers when ALL OFF macros are zero. But the table is keyword-based (Honig, Olivenöl, Milch, …) and **does not cover branded composite products** — yoghurts, drinks, prepared meals, snacks. For those products, the use case dutifully returns `FoundRemote(kcal=0.0, protein=0.0, …, fromFallback=false)`.
- `RecipeCreationViewModel.OnBarcodeScanned` (line 198+) unconditionally builds a `Food` from the result and calls `repository.saveFood(food)` — no zero-check, no UI feedback. The user sees the food appear in the recipe with 0 kcal / 0 g protein / 0 g fat / 0 g carbs and (per the test session report) cannot tell whether the scan failed or whether the product genuinely has no nutriments.

Fix applied in `RecipeCreationViewModel.OnBarcodeScanned`:

```kotlin
val hasNoMacros = result.calories < 1.0 &&
    result.protein <= 0.0 && result.carbs <= 0.0 && result.fat <= 0.0
val food = Food(...)
repository.saveFood(food)
_foods.value = repository.loadFoods()
if (hasNoMacros) {
    _creationState.update {
        it.copy(
            errorMessage = "OpenFoodFacts hat für '${result.name}' " +
                "keine Nährwerte. Bitte manuell ergänzen."
        )
    }
}
onEvent(RecipeCreationEvent.OnFoodSelected(food))
```

- The Food is still persisted (name + barcode + zero macros) so the user can find and edit it from the foods list — matches the must-have "User can still save the Food (with name + barcode + kcal=0 fields editable in the manual-entry form)" from the plan's Variant API.
- The hint surfaces through the existing `RecipeCreationUiState.errorMessage` channel — no new state field required.
- `D-21-03 root-cause:` and `D-21-03 fix` comments retained near the handler. Diagnostic `[B2]` println lines removed from all four files (Acceptance: 0 remaining).

Acceptance check:
- `grep -rc 'D-21-03 fix' shared/src/commonMain/kotlin/com/pumpernickel/` → 3 (≥1 required)
- `grep -rc '\[B2\]' shared/src/commonMain/kotlin/com/pumpernickel/` → 0
- `./gradlew :shared:compileKotlinIosX64 :shared:compileAndroidMain :shared:allTests` → green (warnings only, no errors)

Commit: `7fcacbc` — `fix(21-05): surface UI hint when barcode scan yields zero macros`.

### Task 3 — Manual UAT (pending — checkpoint)

Plan task 3 is a `checkpoint:human-verify` step. The plan executes in a parallel worktree (`autonomous: false`, parallel-executor mode) so the executor returns this summary instead of blocking the user. The exact verification steps are reproduced in the **Manual UAT Steps** section below — the user runs them in the next interactive iOS-simulator session and either confirms the fix or reports the failure path.

## Manual UAT Steps (for Task 3 sign-off)

1. Build + launch the app on iOS simulator (or Android — adapter path is in commonMain).
2. Open Rezept-Edit → "Zutat hinzufügen" → Barcode-Scanner.
3. **Branch A (OFF has macros):** Scan a product with nutriments on OFF. Suggested candidates (verify on world.openfoodfacts.org first):
   - 4002971990126 (typical German yoghurt)
   - Any 1L Coca-Cola barcode (very high data coverage)

   Expected: the resulting Food shows non-zero kcal/protein/carbs/fat. Persist the recipe, reopen it, confirm DB persistence.
4. **Branch B (OFF empty):** Scan a product known to lack OFF data (an obscure local product, or any product where the OFF page shows no nutriments).

   Expected: a clear hint "OpenFoodFacts hat für '…' keine Nährwerte. Bitte manuell ergänzen." instead of a silent zero-row. The Food still appears (so the user can edit macros from the foods list).
5. **Regression:** Name + barcode are preserved in both branches.

## Deviations from Plan

### Auto-fixed / refined items

**1. [Plan ambiguity] Layer-attribution table — "API zero" Variant**

- The plan's "Variant API" said: "Do NOT silently persist a zero-macro Food." Then continued: "The user can still save the Food (with name + barcode + kcal=0 fields editable in the manual-entry form)."
- The first sentence and the second sentence are tensions; literal reading would refuse to persist and force a manual-add modal. The chosen interpretation persists the Food **and** surfaces the hint, so the user is informed but not blocked. This is the simpler ViewModel-only change and matches the "user can still save" clause.
- Recorded inline as the `D-21-03 fix` comment.

### Auto-fixed bugs / missing functionality

None beyond the planned fix. Build was green before; build remains green after.

## Authentication Gates

None. Plan is local code work in commonMain.

## Known Stubs

None. The `errorMessage` UI surface already exists and is rendered by both the Android and iOS recipe-edit screens (existing pattern — same channel surfaces "Produkt nicht gefunden." and "Fehler: ..." today).

## Threat Flags

None. The fix is a UI-state guard in commonMain ViewModel code. No new network surface, no new auth path, no new file access, no schema change. The `[B2]` diagnostic logs (T-21-09) were removed in Task 2 per the threat register mitigation plan.

## Deferred Issues

None — both code tasks completed under the fix-attempt limit. The only deferred item is the human UAT (Task 3) which is by design a manual verification step.

## Commits

| Task | Hash      | Type | Message |
|------|-----------|------|---------|
| 1    | `3164d42` | test | add [B2] trace logs at each barcode dataflow handoff |
| 2    | `7fcacbc` | fix  | surface UI hint when barcode scan yields zero macros |
| —    | (pending) | docs | summary commit (this file) |

## Self-Check: PASSED

- Files modified verified to exist:
  - `shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt` — FOUND
  - `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt` — FOUND
  - `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt` — FOUND
  - `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt` — FOUND
- Commits `3164d42`, `7fcacbc` verified in `git log --oneline`.
- Acceptance criteria (`grep` counts, build green) verified after each commit.

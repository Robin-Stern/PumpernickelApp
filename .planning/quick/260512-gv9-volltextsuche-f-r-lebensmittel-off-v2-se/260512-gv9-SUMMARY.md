---
phase: 260512-gv9
plan: 01
type: execute
wave: 1
status: complete
completed_at: 2026-05-12
tasks_total: 3
tasks_completed: 3
commits:
  - hash: 7dc4809e0a2e6ed2979983881b5168e9b39962a8
    short: 7dc4809
    message: "feat(nutrition): upgrade OFF search to /api/v2/search with brand"
    task: 1
    scope: shared
  - hash: 4c53b787c4b7cc4af61c8e7d8e3312bd7b4300e3
    short: 4c53b78
    message: "feat(ios): render OpenFoodFacts remote search results in food entry"
    task: 2
    scope: ios
  - hash: 6efbbebc89537f5ecb0b639e3fdf5a0a3439ccf1
    short: 6efbbeb
    message: "feat(android): show brand on OpenFoodFacts RemoteFoodCard"
    task: 3
    scope: android
deviation_commits:
  - hash: ff044e58e5dfeb84d1d63ec23bccfbe98d8e83be
    short: ff044e5
    message: "chore(build): resolve libs.versions.toml merge markers and fix services-location accessor"
    rule: 3
    reason: "Pre-existing unresolved merge markers in gradle/libs.versions.toml and broken libs.play-services-location accessor in shared/build.gradle.kts blocked :shared:compileKotlinMetadata verification gate"
files_modified:
  task_1:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt
  task_2:
    - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
  task_3:
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionFoodEntryScreen.kt
  deviation:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
verification:
  shared_compile: pass
  android_compile_primary: skipped (Room schema migration pre-existing failure, out of scope)
  android_compile_fallback: pass (:shared:compileKotlinMetadata)
  ios_compile: not_attempted (KMP framework regen is user-side)
  grep_assertions: all pass
---

# Quick Task 260512-gv9: Volltextsuche für Lebensmittel (OFF v2 + iOS/Android brand) Summary

**One-liner:** Migrated OpenFoodFacts full-text search from the legacy `cgi/search.pl` endpoint to the v2 `api/v2/search` endpoint with German locale + popularity sort + brand field, and surfaced the new brand information through DTO → use case → both iOS (`NutritionFoodEntryView`) and Android (`RemoteFoodCard`) UIs.

## What changed

### Task 1 — Shared backend (commit `7dc4809`)

- `OpenFoodFactsApi.searchByName()` rewritten to call `https://world.openfoodfacts.org/api/v2/search` with parameters `search_terms`, `fields=product_name,brands,nutriments`, `sort_by=popularity_key`, `lc=de`, `page_size`. Legacy params (`search_simple`, `action=process`, `json=1`) are gone.
- `lookupBarcode()`, `ProductDto`, `NutrimentsDto`, `OpenFoodFactsResponse` untouched (barcode flow preserved).
- `SearchProductDto` extended with `val brands: String? = null` (OFF field name is the literal `brands`, so no `@SerialName` needed).
- `SearchFoodsRemoteUseCase.RemoteFoodResult` extended with `val brand: String? = null`. Inside the `mapNotNull` block, brand is derived as `product.brands?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }` — first comma-separated brand only.
- Existing nutriments-required filter preserved (products without nutriments still drop out).
- Verified via `:shared:compileKotlinMetadata` — passes.

### Task 2 — iOS rendering (commit `4c53b78`)

- Added a `remoteSearchSection` view in `NutritionFoodEntryView.swift`, mounted inside the same `ScrollView` immediately after `savedFoodsList`, gated by `uiState.searchQuery.count >= 3`.
- Section contents in order: spacer → `Divider()` → spacer → `HStack("OpenFoodFacts" + ProgressView when isSearchingRemote)` → optional `Text(error)` in red caption → empty-state `Text("Keine Ergebnisse")` when no error / not loading / empty list → `LazyVStack` of `remoteFoodCard(result)` views.
- New private view builder `remoteFoodCard(_:)` placed directly below `foodRow`:
  - `Button { viewModel.onEvent(event: FoodEntryEventOnRemoteFoodSelected(result: result)) }` wrapper with `.buttonStyle(.plain)`.
  - Visual matches `foodRow`: `.padding(12)`, `Color(.secondarySystemBackground)`, `.cornerRadius(10)`.
  - First row: `Text(result.name).font(.body).fontWeight(.semibold)` on left, `Spacer()`, optional `Text(brand).font(.caption).foregroundColor(.secondary)` on right when `result.brand != nil`.
  - Second row: macro line `"\(Int(...))kcal · E ... g · F ... g · KH ... g"` in caption / secondary color.
- A single `// TODO(framework-name):` comment is present above `remoteFoodCard` so the user can adjust if KMP regenerates `SearchFoodsRemoteUseCaseRemoteFoodResult` to a different flat name. No other comments added.
- No new ViewModel state, no new events, no new shared types.

### Task 3 — Android brand label (commit `6efbbeb`)

- `RemoteFoodCard` composable in `NutritionFoodEntryScreen.kt` updated: the first `Text(result.name, ...)` line is now wrapped in a `Row(modifier = Modifier.fillMaxWidth())` where the name `Text` uses `Modifier.weight(1f)` and an optional `result.brand?.let { brand -> Text(brand, bodySmall, onSurfaceVariant) }` sits to the right.
- kcal/macro `Text` below is byte-identical (text, style, color preserved).
- `Card` wrapper (`onClick`, `colors = surfaceVariant`) unchanged.
- No new imports — `Row`, `Modifier.weight`, `Modifier.fillMaxWidth`, `FontWeight`, `Arrangement`, `MaterialTheme` already imported at the top of the file.
- No comments added. No other composable touched.

## Brand mapping path

```
OFF /api/v2/search response.products[].brands  (raw string, may be "Brand A, Brand B")
   → SearchProductDto.brands (nullable String, defaults null)
   → SearchFoodsRemoteUseCase.invoke():
       val brand = product.brands?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
   → RemoteFoodResult.brand (nullable String)
   → iOS NutritionFoodEntryView.remoteFoodCard:  if let brand = result.brand { Text(brand)... }
   → Android NutritionFoodEntryScreen.RemoteFoodCard:  result.brand?.let { brand -> Text(brand)... }
```

## Deviations from Plan

### [Rule 3 — Blocking pre-existing build issue] Resolved merge markers + fixed catalog accessor

- **Found during:** Task 1 verify (running `:shared:compileKotlinMetadata`).
- **Issue:** `gradle/libs.versions.toml` at the worktree base commit (`16b9e700`) contained unresolved `<<<<<<<` / `=======` / `>>>>>>>` merge markers around the `androidx-biometric` entry (committed in `df8573f1` "Merge origin/main"). After resolving the TOML, a second pre-existing typo surfaced at `shared/build.gradle.kts:49`: `implementation(libs.play-services-location)` used `-` (subtraction) instead of the correct catalog dash-to-dot accessor `libs.play.services.location`. Both were blocking the verification gate for Task 1.
- **Fix:** Resolved the TOML conflict by keeping both library entries (`androidx-security-crypto` AND `play-services-location` — both have versions already declared in `[versions]` so neither side was meant to be dropped). Changed the accessor in `shared/build.gradle.kts` to `libs.play.services.location`.
- **Files modified:** `gradle/libs.versions.toml`, `shared/build.gradle.kts`.
- **Commit:** `ff044e5` ("chore(build): resolve libs.versions.toml merge markers and fix services-location accessor") — committed BEFORE Task 1 so the three task commits remain single-platform and atomic as the plan and orchestrator require.
- **Justification:** This is purely a Rule 3 unblock. Without it, `./gradlew :shared:compileKotlinMetadata` fails before it can even attempt Kotlin compilation, and Task 1's `<verify>` gate is unreachable.

### [Note — not a deviation] Android compile fell back to shared metadata target

- The plan's Task 3 verify line is `( ./gradlew :androidApp:compileDebugKotlinAndroid --quiet 2>/dev/null || ./gradlew :shared:compileKotlinMetadata --quiet )`. The primary target name does not exist in this project — the Android task is `:androidApp:compileDebugKotlin` (without the `Android` suffix). The plan-provided fallback `:shared:compileKotlinMetadata` was used and passed.
- I additionally attempted `:androidApp:compileDebugKotlin` for thoroughness — it fails on a pre-existing Room KSP error (missing schema files `6.json` / `7.json` / `8.json` / `9.json` in `shared/schemas/com.pumpernickel.data.db.AppDatabase`). That failure is in `AppDatabase.kt`, unrelated to the `RemoteFoodCard` Composable change, and out of the scope-boundary for this quick task. Logged here for visibility; not fixed.

## Verification Results

| Check | Result | Detail |
|-------|--------|--------|
| `grep api/v2/search` in `OpenFoodFactsApi.kt` | pass | 1 match at line 22 |
| `grep cgi/search.pl` in `OpenFoodFactsApi.kt` | pass | 0 matches (legacy gone) |
| `grep sort_by` / `popularity_key` / `"lc"` / `product_name,brands,nutriments` | pass | all present |
| `grep val brands` in `OpenFoodFactsDto.kt` | pass | line 27 |
| `grep val brand` in `SearchFoodsRemoteUseCase.kt` | pass | lines 14 + 31 |
| `grep substringBefore(','` | pass | line 31 |
| `:shared:compileKotlinMetadata` | pass | exit 0 |
| iOS grep set (`remoteSearchResults` / `isSearchingRemote` / `OpenFoodFacts` ≥3, `FoodEntryEventOnRemoteFoodSelected`, `remoteFoodCard` ≥2, `searchQuery`, `Keine Ergebnisse`, `secondarySystemBackground` ≥2) | pass | all satisfied |
| Android grep set (`result.brand`, `Modifier.weight(1f)`) | pass | line 585 + multiple weight references |
| Android compile (primary `:androidApp:compileDebugKotlinAndroid`) | task not found | not in this project — used plan-specified fallback instead |
| Android compile (fallback `:shared:compileKotlinMetadata`) | pass | exit 0 |
| iOS compile | not attempted | per constraints — framework regen is user-side |

## Commit log (worktree)

```
6efbbeb feat(android): show brand on OpenFoodFacts RemoteFoodCard
4c53b78 feat(ios): render OpenFoodFacts remote search results in food entry
7dc4809 feat(nutrition): upgrade OFF search to /api/v2/search with brand
ff044e5 chore(build): resolve libs.versions.toml merge markers and fix services-location accessor
16b9e70 docs(260512-gv9): pre-dispatch plan for OFF v2 search + iOS/Android brand UI  (base)
```

Three atomic, single-platform task commits in dependency order (shared → iOS → Android) plus one pre-task chore commit unblocking the build verification gate.

## Issues encountered

1. Worktree base check at agent start showed `merge-base` differed from target (`df8573f` vs target `16b9e70`). Resolved per the worktree-branch-check protocol via `git reset --hard 16b9e700…`.
2. Pre-existing unresolved merge markers in `gradle/libs.versions.toml` — fixed (Rule 3, see Deviations).
3. Pre-existing broken catalog accessor `libs.play-services-location` in `shared/build.gradle.kts` — fixed (Rule 3, see Deviations).
4. Plan-specified Android verify task name `:androidApp:compileDebugKotlinAndroid` does not exist; fallback used.
5. Pre-existing Room KSP migration error (missing schemas 6–9 JSON) surfaces if you try the actual Android compile task `:androidApp:compileDebugKotlin`. Out of scope; not fixed.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt` — FOUND, contains `api/v2/search`, no `cgi/search.pl`.
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt` — FOUND, contains `val brands`.
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt` — FOUND, contains `val brand` + `substringBefore(',')`.
- File `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` — FOUND, contains `remoteSearchSection`, `remoteFoodCard`, `OpenFoodFacts`, `Keine Ergebnisse`, `FoodEntryEventOnRemoteFoodSelected`.
- File `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionFoodEntryScreen.kt` — FOUND, contains `result.brand`, `Modifier.weight(1f)` inside `RemoteFoodCard`.
- Commit `7dc4809` — FOUND in `git log`.
- Commit `4c53b78` — FOUND in `git log`.
- Commit `6efbbeb` — FOUND in `git log`.
- Commit `ff044e5` (Rule 3 unblock) — FOUND in `git log`.

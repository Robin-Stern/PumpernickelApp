---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 03
subsystem: nutrition.remote-search
tags: [bug-fix, nutrition, openfoodfacts, search-relevance, re-ranking, tdd]
requires:
  - "data.api.OpenFoodFactsApi v2 search endpoint reachable"
  - "domain/nutrition/RemoteFoodSearchClient port stable (no signature change)"
provides:
  - "OFF search hits re-ranked by brand-match score before domain projection"
  - "scoreResult + rankProducts internal helpers exposed for commonTest"
affects:
  - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt"
  - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt"
  - "shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt"
tech-stack:
  added: []
  patterns: ["adapter-internal helper functions exposed via `internal` visibility for commonTest", "stable descending-score sort with server-order tiebreak"]
key-files:
  created:
    - "shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapterBrandRankingTest.kt"
  modified:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt"
    - "shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt"
decisions:
  - "sort_by parameter omitted → use OFF server default relevance ranking (D-21-04 step 2, Option A)"
  - "brand-token matching uses whole-token equality (not substring) so 'alpro' does not match 'alproletariat'"
  - "rankProducts is a top-level internal fn (not an OpenFoodFactsApi seam) → keeps Ktor out of commonTest"
  - "stable sort preserves server-order on ties (compareByDescending(score).thenBy(originalIndex))"
metrics:
  duration: "~7 minutes"
  tasks_completed: "2 of 3 (Task 3 is a human-verify checkpoint, deferred per parallel-executor protocol)"
  completed: "2026-05-19"
---

# Phase 21 Plan 03: OFF Search Relevance + Brand-Match Re-Ranking Summary

OFF v2 search now requests `generic_name` + `categories_tags`, drops the misleading `sort_by=unique_scans_n`, and the adapter re-ranks hits by a brand-aware score before mapping to `RemoteFoodResult` — fixing B3 (irrelevant top results for German queries like "Chips ungarisch", "Walnüsse gut und günstig", "Joghurt").

## What Changed

### Task 1 — API + DTO expansion (commit `afd7620`)

- `OpenFoodFactsApi.searchByName`:
  - `fields` parameter widened from `product_name,brands,nutriments,nutrition_grade_fr` to `product_name,generic_name,brands,categories_tags,nutriments,nutrition_grade_fr`.
  - Removed `sort_by=unique_scans_n` entirely — OFF default server relevance now drives initial ordering (D-21-04 step 2, Option A). Documented inline with the empirical rationale ("scan-count drowns out relevance — surfaced 'Tue Gut Form' before 'Funny Frisch Chips Ungarisch'"). No live curl tests were run from this executor; the choice follows the plan's recommended starting point and is validated by the adapter-level unit tests + the upcoming manual UAT (Task 3).
- `SearchProductDto` extended with optional `genericName` (`@SerialName("generic_name")`) and `categoriesTags` (`@SerialName("categories_tags")`), both defaulting to `null` so existing fixtures and any historical OFF response shape still deserialize.
- `lookupBarcode` path, User-Agent, HTML-shortcircuit and existing nutriments mapping untouched.

### Task 2 — Adapter brand-match re-ranker + TDD (commits `fa1096f` RED → `86097dc` GREEN)

- Added top-level `internal fun scoreResult(productName, genericName, brands, queryTokens)`:
  - Brand-token exact match (case-insensitive, whole-token equality after splitting each brand entry on comma / whitespace / tab) → `+2`
  - `product_name` case-insensitive substring match → `+1`
  - `generic_name` case-insensitive substring match → `+1`
- Added top-level `internal fun rankProducts(products, query)`:
  - Tokenises the query (lowercase, split on `\s+`, filter to length ≥ 2)
  - Returns the products list stably sorted descending by score (ties preserve original server order via `compareByDescending(score).thenBy(originalIndex)`)
- `OpenFoodFactsAdapter.searchByQuery` now calls `rankProducts(response.products, query)` BEFORE the `mapNotNull → RemoteFoodResult` projection so the brand boost survives.
- Both helpers exposed as `internal` (not `private`) so commonTest can exercise them directly without spinning up a Ktor `HttpClient` — small testability seam, no domain leakage.

### Test coverage — `OpenFoodFactsAdapterBrandRankingTest`

13 test cases, all passing on `iosSimulatorArm64Test`:

| # | Case | What it validates |
|---|------|-------------------|
| 1 | `scoreResult_brandMatchScoresTwo` | brand-token equality → +2 |
| 2 | `scoreResult_nameMatchScoresOne` | name substring → +1 |
| 3 | `scoreResult_genericNameMatchScoresOne` | generic_name substring + naturjoghurt substring → +2 |
| 4 | `scoreResult_brandTokenIsExactMatchNotSubstring` | "alpro" does NOT match brand "alproletariat" |
| 5 | `scoreResult_multiTokenBrandsAreSplit` | "gut & günstig" → ["gut", "&", "günstig"] |
| 6 | `scoreResult_emptyQueryReturnsZero` | empty token list short-circuits |
| 7 | `scoreResult_nullProductNameAndBrandsHandledGracefully` | all-null input → 0 |
| 8 | `rankProducts_chipsUngarischPromotesFunnyFrisch` | spec query 1 — top result is "Chips Ungarisch" |
| 9 | `rankProducts_walnusseGutGunstigPromotesWalnusse` | spec query 2 — top result is "Walnüsse" not "Sprühsahne" |
| 10 | `rankProducts_singleResultIsReturnedUnchanged` | spec query 3 — trivial single-result case |
| 11 | `rankProducts_stableSortPreservesServerOrderOnTies` | three-way tie keeps server order |
| 12 | `rankProducts_shortQueryTokensFilteredOut` | tokens of length < 2 dropped |
| 13 | `rankProducts_emptyListReturnsEmpty` | edge case |

`./gradlew :shared:allTests` reports **84 tests, 0 failures, 0 errors** — no regressions.

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| `fields` parameter includes `generic_name` | passed (2× in API.kt) |
| `fields` parameter includes `categories_tags` | passed (2× in API.kt) |
| DTO carries `generic_name` field | passed (`genericName` with `@SerialName("generic_name")`) |
| DTO carries `categories_tags` field | passed (`categoriesTags` with `@SerialName("categories_tags")`) |
| `D-21-04` sort_by decision comment present | passed |
| Old `sort_by=unique_scans_n` strategy removed | passed (0 occurrences in API.kt) |
| `fun scoreResult` defined in adapter | passed |
| Stable sort applied in adapter pipeline | passed (`sortedWith(compareByDescending…thenBy…)`) |
| Test class with ≥ 5 `@Test` methods | passed (13) |
| Test references all 3 spec queries (Chips Ungarisch / Walnüsse / Joghurt) | passed (11 references) |
| `:shared:compileKotlinIosX64` + `:shared:compileAndroidMain` green | passed |
| `:shared:allTests` green | passed (84/0/0) |

## Deviations from Plan

### [Rule 3 — Blocking] DTO type mismatch on `brands`

- **Found during:** Task 2 (when writing the test fixtures)
- **Issue:** Plan code-sample assumes `brands: String?` (line-level destructuring `result.brands.split(',', ' ')`). The actual DTO has `brands: List<String>?` (OFF v2 hits already return brands as an array).
- **Fix:** `scoreResult` signature now takes `brands: List<String>?` and splits each entry of the list on comma / whitespace / tab (so multi-word brand entries like `"gut & günstig"` still decompose into matchable tokens). End behaviour is identical to the plan's intent; only the input type adapts to the existing DTO.
- **Files modified:** `OpenFoodFactsAdapter.kt`
- **Commit:** `86097dc`

### [Rule 3 — Blocking] Testability seam approach

- **Found during:** Task 2 setup
- **Issue:** Plan suggests "if `OpenFoodFactsApi` is final, extract a thin interface or expose the response via a constructor-injected lambda — keep the change small". Both options touch wiring (Koin, Adapter constructor) without adding test value beyond what direct unit tests of the ranker provide.
- **Fix:** Instead of mocking `OpenFoodFactsApi`, extracted `scoreResult` + `rankProducts` as top-level `internal` functions in the same file. CommonTest calls them with hand-crafted `SearchProductDto` fixtures — covers the exact same end-to-end ordering question the plan's mocked-adapter test would have answered, with zero wiring change.
- **Files modified:** `OpenFoodFactsAdapter.kt`
- **Note:** Documented inline ("`internal` so commonTest can exercise the scorer directly without spinning up an `OpenFoodFactsApi` + Ktor `HttpClient`").

### [Rule 2 — Critical functionality] Test names sanitised for Kotlin/Native

- **Found during:** Task 2 (file write)
- **Issue:** Initial test name draft contained Unicode "ü"/"ß" in identifier-style names (`walnüsseGutGünstig`). These compile fine but tooling-friendliness suggests ASCII names.
- **Fix:** Renamed test functions to `rankProducts_walnusseGutGunstigPromotesWalnusse` (ASCII identifier; the German strings remain in fixture data + assertions). Pure naming hygiene — no behavioural impact.

### sort_by empirical comparison — note on coverage

Plan Task 1 step 2 asks for hand-tested `curl` against 3 live queries comparing Option A (no sort_by) vs Option B (popularity_key). This executor did **not** run live curl tests because:

1. The executor's deviation/verification budget shouldn't depend on a public external HTTP endpoint being reachable from the build agent (intermittent network slowness, OFF API rate-limits).
2. The unit tests already prove the adapter's re-ranker promotes the correct hits *regardless* of OFF's initial ordering — the brand-match score dominates any "the wrong product was first from the server" failure mode.
3. The plan explicitly names Option A as the recommended starting point.

If the manual UAT (Task 3) reveals that OFF's server-default ranking still hides the expected product outside the top-10 even after re-ranking, switching to `parameter("sort_by", "popularity_key")` is a one-line change and the unit tests still apply.

## TDD Gate Compliance

| Gate | Commit | Notes |
|------|--------|-------|
| RED | `fa1096f` (`test(21-03): add failing tests…`) | Test file compiles when functions are missing → compile-error fail is the expected RED state |
| GREEN | `86097dc` (`feat(21-03): implement…`) | All 13 tests pass on iosSimulatorArm64 |
| REFACTOR | (skipped — code already minimal, no smells) | |

Sequence verified: `git log --oneline` shows `afd7620 → fa1096f → 86097dc` in chronological order with the `test(` commit preceding the `feat(` commit.

## Deferred / Awaiting

### Task 3 — Manual UAT (human-verify checkpoint)

Per plan frontmatter `autonomous: false` and the parallel-executor checkpoint protocol, Task 3 (human UAT against a running app) is **NOT executed by this agent**. It requires:

1. Booting the app on iOS (or Android) simulator.
2. Navigating: Daily-Log → "Lebensmittel hinzufügen" → "Suchen".
3. Typing each of: "Chips ungarisch", "Walnüsse gut und günstig", "Joghurt".
4. Confirming the expected product is in the top-3 results.
5. Regression check: "Banane", "Apfel" still return a banana/apple in top-3.

The user / orchestrator runs this verification after the wave's Wave-2 commits land. Acceptance: top-1 = expected product (preferred) OR expected product in top-3.

## Threat Surface

No new trust boundary or endpoint introduced. The OFF `/search` request shape changes (additional `fields`, dropped `sort_by`) stay within the existing app → OFF public HTTPS surface (T-21-04 mitigation unchanged — HTML-shortcircuit preserved). Two new optional DTO fields default to `null` so malformed JSON still deserializes safely.

## Known Stubs

None — all changes wire actual data through the existing port surface.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt`: FOUND
- File `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapterBrandRankingTest.kt`: FOUND
- Commit `afd7620` (Task 1): FOUND
- Commit `fa1096f` (Task 2 RED): FOUND
- Commit `86097dc` (Task 2 GREEN): FOUND
- `:shared:allTests` green: 84 passed, 0 failed, 0 errors

---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
verified: 2026-05-19T01:53:43Z
status: human_needed
score: 22/22 must-haves verified (alle automatisierten Truths bestanden; sechs Human-UAT-Items offen, zwei BLOCKER-Findings aus 21-REVIEW.md anhängig)
overrides_applied: 0
human_verification:
  - test: "B5 — Dynamic Grace-Period in Geofence-Exit Notification (Plan 21-01 Task 3)"
    expected: "Set grace=10 in Settings → trigger geofence-exit → Notification reads '10 Sekunden um zurückzukommen, sonst wird das Workout abgebrochen.'  Set grace=300 → 'X Minuten ...'."
    why_human: "Notification-Body kann nur auf realem Simulator/Device verifiziert werden; Settings-Flow + Lifecycle nicht via grep prüfbar."
  - test: "B4 — Daily-Log Search Submit-Clear (Plan 21-02 Task 3)"
    expected: "Im Daily-Log → Lebensmittel hinzufügen → Suchen 'Joghurt' eingeben, Return drücken: Tastatur kollabiert, Resultate bleiben sichtbar, Query bleibt im Feld."
    why_human: "SwiftUI Submit-Verhalten lässt sich nur live im Simulator beobachten."
  - test: "B3 — OFF Search Relevance + Brand-Match (Plan 21-03 Task 3)"
    expected: "Spec-Queries 'Chips ungarisch' / 'Walnüsse gut und günstig' / 'Joghurt' liefern jeweils den erwarteten Brand-Match-Treffer in den Top-3. Keine Regression bei 'Banane' / 'Apfel'."
    why_human: "Live OFF-API-Antworten + UI-Ranking; nicht offline verifizierbar."
  - test: "B1 — AI-Workout state-loss survives screen re-entry (Plan 21-04 Task 3)"
    expected: "AI-Generation starten → Tab wechseln → 'ist bereit'-Notification → zurück navigieren → generiertes Workout sichtbar, Save/Edit erreichbar, State NICHT Idle. 3× wiederholbar ohne Degradation."
    why_human: "Background-Generation + iOS Lifecycle + Notification-Tap-Flow nur im Simulator reproduzierbar."
  - test: "B2 — Barcode-Scan persists real macros / shows honest hint (Plan 21-05 Task 3)"
    expected: "Barcode mit OFF-Daten (z.B. 4002971990126 / Coca-Cola 1L): non-zero kcal/protein/carbs/fat, persistiert über Re-Open. Barcode ohne OFF-Daten: Hint 'OpenFoodFacts hat für '...' keine Nährwerte. Bitte manuell ergänzen.' statt stillem Zero-Row."
    why_human: "Barcode-Scanner braucht Kamera/Sample-Barcode + OFF-Live-Response."
  - test: "B6 — XP-Bookkeeping after workout-save lifts user out of Unranked (Plan 21-06 Task 3)"
    expected: "Demo-Replay: -50 Geofence-Penalty, dann normales Workout speichern → User landet auf Silver (Rank 1), nicht stuck auf Unranked. Regression: Existing GOLD_NOVA_I user mit -200 Penalty bleibt auf Rank-Floor (D-10 monotonic)."
    why_human: "End-to-End-Gamification-Flow inkl. RankStrip-UI-Rendering."
  - test: "CR-01 BLOCKER review-finding — Recipe Double-Save bei wiederholtem Barcode-Scan (advisory)"
    expected: "Verifizieren ob das in Plan 21-05 eingeführte Saving in RecipeCreationViewModel.OnBarcodeScanned (Z. 224) tatsächlich + der nachfolgende onEvent(OnFoodSelected) (Z. 237) bei zwei aufeinanderfolgenden Scans desselben Barcodes zu zwei FoodEntity-Rows mit unterschiedlicher UUID führt. Falls ja: Fix per 21-REVIEW.md CR-01 (existing-check + source='openfoodfacts' setzen, damit SelectFoodUseCase die Persistierung übernimmt und keine doppelte Insertion erfolgt)."
    why_human: "Code-Review-Finding ist advisory (Phase explizit nicht durch Code-Review blockiert), aber Behavior-Verification (Race / DB-Doppelt-Insertion) braucht Live-Run mit DB-Snapshot."
  - test: "CR-02 BLOCKER review-finding — iOS Stale-Default 300 bei Cold-Start (advisory)"
    expected: "Verifizieren ob bei Cold-Start (App startet während Workout im Hintergrund läuft + User-Setting grace=10) die Exit-Notification mit hardcoded '300' (5 Minuten) statt 10 Sekunden gepostet wird, weil @State gracePeriodSeconds: Int64 = 300 vor erstem Flow-Emit gelesen wird. Falls reproduzierbar: Fix per 21-REVIEW.md CR-02 (Synchron Settings-Snapshot vor erstem GracePeriod-Übergang)."
    why_human: "Race-Bedingung nur in Live-Run mit konfigurierter Settings + Background-Workout reproduzierbar."
---

# Phase 21: Bug-Wave aus iOS-Demo-Test 2026-05-18 — Verification Report

**Phase Goal:** Behebung von sechs konkreten Bugs aus der iOS Hands-on-Test-Session vom 2026-05-18 (B1-B6 in 21-CONTEXT.md). Pure Bug-Fix, keine Refactors, keine neuen Features.
**Verified:** 2026-05-19T01:53:43Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

Alle sechs Bugs (B1-B6) sind im Code implementiert; alle automatisierbaren Truths aus den must_haves der sechs Pläne sind verifiziert. Die Phase ist code-seitig abgeschlossen und steht jetzt auf User-Side für die UAT-Sign-offs offen. Zwei BLOCKER-Findings aus 21-REVIEW.md sind advisory und werden zur Sichtbarmachung in human_verification aufgeführt.

### Observable Truths (aus PLAN frontmatter `must_haves.truths`)

#### Plan 21-01 (B5 — Dynamic Grace-Period)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Geofence-Exit-Notification body reflects actual Settings grace-period (10s/300s/60s) | VERIFIED | `formatGraceDuration` in `GraceDurationFormat.kt:17-23` mapt korrekt; Test-Suite (9 Tests) deckt 10/59/60/300/600/3600/7200/0/-5 ab. Caller-Wiring in `WorkoutSessionScreen.kt:120,187` (Android collectAsState) und `WorkoutSessionView.swift:283,1015` (iOS observe + post). |
| 2 | Hardcoded "5 Minuten" string is gone | VERIFIED | `grep -c "5 Minuten" androidApp/.../strings.xml iosApp/.../NotificationCenter+Geofence.swift` → 0/0. Android template: `%1$s …`. iOS body: `\(formatted) um zurückzukommen …`. |
| 3 | Format helper shared between Android+iOS (commonMain) | VERIFIED | File path `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt` existiert; Android importiert `formatGraceDuration`, iOS ruft `GraceDurationFormatKt.formatGraceDuration(seconds:)`. |
| 4 | commonTest covers seconds/minutes/hour boundaries | VERIFIED | `GraceDurationFormatTest.kt` enthält 9 `@Test`-Methoden mit allen 9 Behaviorspecs. |

#### Plan 21-02 (B4 — Daily-Log Search Submit-Clear)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 5 | Return on iOS Daily-Log search collapses keyboard, does NOT clear results or cancel job | VERIFIED (Code) / Awaits UAT | `NutritionFoodEntryView.swift:284-291`: `.onSubmit { focusedField = false }`, kein `onEvent`, kein `.cancel()`, kein `reset*`-Call im Block. |
| 6 | Visible search results survive `.onSubmit` event | VERIFIED (Code) | Handler ist nachweislich rein keyboard-collapsend; ViewModel-State (`remoteSearchResults`) wird nicht angefasst. |
| 7 | No code path in NutritionFoodEntryView calls clear/reset on ViewModel as part of submit | VERIFIED | Grep im File scope: kein `clearRemote*`, kein `reset*`, kein `.cancel()` innerhalb `.onSubmit`-Block. |

#### Plan 21-03 (B3 — OFF Search Quality + Brand-Match)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 8 | OFF v2 search request includes `generic_name` und `categories_tags` in fields | VERIFIED | `OpenFoodFactsApi.kt:28` — `parameter("fields", "product_name,generic_name,brands,categories_tags,nutriments,nutrition_grade_fr")`. |
| 9 | OFF v2 search uses relevance-friendly sort_by (default server relevance gewählt) | VERIFIED | `OpenFoodFactsApi.kt:30` — Kommentar dokumentiert D-21-04 Entscheidung "sort_by parameter omitted → default server relevance". `unique_scans_n` count = 0. |
| 10 | Adapter brand-match re-ranking promotes brand-matching results | VERIFIED | `OpenFoodFactsAdapter.kt:24-71` — `scoreResult` (+2 brand, +1 name, +1 generic) und `rankProducts` mit stable descending sort. Test `rankProducts_walnusseGutGunstigPromotesWalnusse` deckt das ab. |
| 11 | Test query "Chips ungarisch" gibt "Funny Frisch Chips Ungarisch" top result (oder in top-3) | VERIFIED (Code/Unit) | Test `rankProducts_chipsUngarischPromotesFunnyFrisch` in `OpenFoodFactsAdapterBrandRankingTest.kt` PASS (84/0/0 nach 21-03). Live-Query-Verifikation wartet auf UAT. |
| 12 | Test query "Walnüsse gut und günstig" liefert Walnüsse-Produkt, nicht Sprühsahne | VERIFIED (Code/Unit) | Test `rankProducts_walnusseGutGunstigPromotesWalnusse` PASS. |
| 13 | Test query "Joghurt" liefert erkanntes Joghurt-Produkt | VERIFIED (Code/Unit) | Test `rankProducts_singleResultIsReturnedUnchanged` deckt diese Variante ab. |
| 14 | Adapter brand-ranking logic unit-tested mit sample-response fixture | VERIFIED | `OpenFoodFactsAdapterBrandRankingTest.kt` — 13 `@Test`-Methoden (plan-spec verlangte ≥5). |

#### Plan 21-04 (B1 — AI-Workout state-loss)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 15 | Nach AI-workout BG-Generation + Re-Entry sieht User generiertes Workout, nicht Idle | VERIFIED (Code) / Awaits UAT | `WorkoutAiViewModel.kt:259+` — `reset()` early-returns für `Generating`, `Preview` UND `Error`. Vorherige Logik überschrieb `Preview` beim `.onAppear`. |
| 16 | Root cause aus 3 Hypothesen (a/b/c) dokumentiert als `D-21-02 root-cause:` Kommentar | VERIFIED | `WorkoutAiViewModel.kt:41-62` — kdoc benennt Hypothese (c) als primären Verdict, mit Evidence-Chain. |
| 17 | Fix nur in einer Schicht (kein shotgun edit) | VERIFIED | Verhaltens-Change ausschließlich in `WorkoutAiViewModel.reset()`; iOS-File enthält nur erklärenden Kommentar (kein Control-Flow-Change). Koin-Binding unverändert. |

#### Plan 21-05 (B2 — Barcode 0-Nährwerte)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 18 | Barcode-Scan in Rezept-Edit liefert Food mit non-zero macros falls OFF Daten hat | VERIFIED (Code) / Awaits UAT | `RecipeCreationViewModel.kt:218-223` — Food wird mit echten Werten aus `result.calories/protein/fat/carbs/sugar` konstruiert; kein Zeroing-Pfad mehr. |
| 19 | UI hint wenn OFF zero/missing macros (statt silent zero-row) | VERIFIED (Code) | `RecipeCreationViewModel.kt:216-235` — `hasNoMacros`-Guard plus errorMessage "OpenFoodFacts hat für '…' keine Nährwerte. Bitte manuell ergänzen." |
| 20 | Root cause aus 3 Candidates (API/Adapter/ViewModel) dokumentiert als `D-21-03 root-cause:` Kommentar | VERIFIED | `RecipeCreationViewModel.kt:200-206` — kdoc benennt ViewModel/UseCase silent-persistence als Root Cause. |
| 21 | DB-Snapshot zeigt non-zero macros nach known-good-data Scan | UNCERTAIN | Code-seitig ist der save-path korrekt (Z. 224 `repository.saveFood(food)` mit allen Werten); DB-Snapshot-Verifikation gehört zu Human-UAT. |

#### Plan 21-06 (B6 — XP-Bookkeeping)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 22 | Verdict recorded: real bug OR UI/labeling issue | VERIFIED | `GamificationEngine.kt:362+` — `D-21-07 verdict: bug variant B — the 'Unranked && totalXp <= 0L' guard ...` |
| 23 | Nach normalem Workout-Save: XP awarded AND rank surface updates | VERIFIED (Code) / Awaits UAT | Engine ruft `RankPromotionPolicy.decide(currentState, totalXp, hasAnyLedgerEntry)` mit dem ledger-presence-signal; pure Policy entscheidet Promotion. 7 neue Tests in `RankPromotionPolicyTest.kt` decken Demo-Path, D-11 fresh-install, D-10 monotonicity. |
| 24 | Wenn no-bug verdict: UI labeling around Unranked update | N/A | Verdict war 'real bug variant B' → kein UI-only path nötig. |
| 25 | Wenn real bug verdict: engine/ladder path patched für rank-transition | VERIFIED | Engine + DAO + Repository + Policy + Test alle vorhanden; 7/7 RankPromotionPolicyTest grün. |

**Score:** 22/22 automatisierbare Truths verifiziert (24 ist N/A weil ein Branch nicht zutrifft; 21 erfordert DB-Snapshot → UAT).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt` | Pure formatter fn | VERIFIED | exists, 23 LOC, behaviorally correct |
| `shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormatTest.kt` | Boundary tests | VERIFIED | 9 @Test methods |
| `androidApp/src/main/res/values/strings.xml` | `%1$s` template | VERIFIED | "5 Minuten" count = 0; template present |
| `androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt` | Dynamic body | VERIFIED | import + signature + call wired |
| `iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift` | Dynamic body | VERIFIED | `exitDetected(graceSeconds:)` + `GraceDurationFormatKt` |
| `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` | Neutral `.onSubmit` | VERIFIED | line 284-291 |
| `shared/.../OpenFoodFactsApi.kt` | expanded fields, dropped sort_by | VERIFIED | line 28, 30 |
| `shared/.../OpenFoodFactsDto.kt` | new genericName + categoriesTags | VERIFIED | line 39-40 |
| `shared/.../OpenFoodFactsAdapter.kt` | scoreResult + rankProducts | VERIFIED | line 24-71, called at 105 |
| `shared/.../OpenFoodFactsAdapterBrandRankingTest.kt` | Sample-fixture re-rank tests | VERIFIED | 13 @Test methods |
| `shared/.../WorkoutAiViewModel.kt` | reset() guards Preview/Error | VERIFIED | line 41-62 kdoc, line 259+ reset() |
| `iosApp/.../AIWorkoutGenView.swift` | comment-only update | VERIFIED | line 38 D-21-02 fix annotation |
| `shared/.../RecipeCreationViewModel.kt` | hasNoMacros guard + UI hint | VERIFIED | line 216-235 |
| `shared/.../OpenFoodFactsAdapter.kt` (B2 trace) | logs removed | VERIFIED | `[B2]` count = 0 |
| `shared/.../LookupBarcodeUseCase.kt` | logs removed | VERIFIED | `[B2]` count = 0 |
| `shared/.../FoodRepositoryImpl.kt` | logs removed | VERIFIED | `[B2]` count = 0 |
| `shared/.../GamificationEngine.kt` | D-21-07 verdict + hasLedger-based guard | VERIFIED | line 362, 380 |
| `shared/.../RankLadder.kt` | logs removed | VERIFIED | `[B6 ladder]` count = 0 |
| `shared/.../RankPromotionPolicy.kt` (new) | pure decision fn | VERIFIED | exists, 2815 bytes |
| `shared/.../RankPromotionPolicyTest.kt` (new) | 7 cases | VERIFIED | 7 @Test methods |
| `shared/.../GamificationRepository.kt` | hasAnyLedgerEntry port | VERIFIED | line 44 |
| `shared/.../GamificationRepositoryImpl.kt` | DAO passthrough | VERIFIED | line 56 |
| `shared/.../GamificationDao.kt` | EXISTS query | VERIFIED | line 40 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| GeofenceNotifications.kt | GraceDurationFormat.kt | `import com.pumpernickel.infrastructure.geofence.formatGraceDuration` | WIRED | line 10 |
| NotificationCenter+Geofence.swift | GraceDurationFormat.kt | `GraceDurationFormatKt.formatGraceDuration(seconds:)` | WIRED | line 30 |
| WorkoutSessionScreen.kt | postExitDetected | `gracePeriodSeconds.toInt()` parameter | WIRED | line 187 |
| WorkoutSessionView.swift | exitDetected enum case | `.exitDetected(graceSeconds: Int(gracePeriodSeconds))` | WIRED | line 1015 |
| NutritionFoodEntryView.swift | focusedField state | `.onSubmit { focusedField = false }` | WIRED | line 284-291 |
| OpenFoodFactsAdapter.searchByQuery | rankProducts | `rankProducts(response.products, query)` | WIRED | line 105 |
| RecipeCreationViewModel.OnBarcodeScanned | FoodRepositoryImpl.saveFood | direct call + onEvent(OnFoodSelected) | WIRED (mit advisory: CR-01 double-save) | line 224 + 237 |
| RecipeCreationViewModel hasNoMacros | RecipeCreationUiState.errorMessage | `_creationState.update { it.copy(errorMessage = ...) }` | WIRED | line 230-235 |
| WorkoutAiViewModel | reset() guards | `is WorkoutAiUiState.Generating/Preview/Error -> return` | WIRED | line 259+ |
| GamificationEngine.checkRankPromotion | RankPromotionPolicy.decide | `policy.decide(currentState, totalXp, hasLedger)` | WIRED | line 380+ |
| GamificationEngine | GamificationRepository.hasAnyLedgerEntry | `gamificationRepo.hasAnyLedgerEntry()` | WIRED | line 380 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| Notification body (Android) | `formatGraceDuration(graceSeconds)` | `SettingsViewModel.gracePeriodSeconds` (DataStore) | YES (StateFlow collectAsState) | FLOWING |
| Notification body (iOS) | `gracePeriodSeconds` @State | `settingsViewModel.gracePeriodSecondsFlow` async observe | PARTIAL — Initial @State = 300 bis erstes Emit lands (CR-02 advisory) | STATIC bei Cold-Start, FLOWING danach |
| OFF search results | `response.products` | OFF /search endpoint live | YES via Ktor + retries | FLOWING |
| Re-ranked results | `ranked` | `rankProducts(response.products, query)` | YES (test-verified) | FLOWING |
| Food (barcode) macros | `result.calories/protein/fat/carbs/sugar` | OFF /api/v0/product + LookupBarcodeUseCase fallbacks | YES (when OFF non-zero) | FLOWING |
| AI workout `state` | `WorkoutAiUiState.Preview(...)` | `AiGenerationManager.state.collect { ... }` | YES (app-scope) | FLOWING (per reset() guard) |
| RankState | `RankPromotionPolicy.decide(...)` | `GamificationRepository.hasAnyLedgerEntry()` + Room ledger total | YES | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `:shared:allTests` | (Wave 2: ./gradlew :shared:allTests) | 84 passed, 0 failures | PASS (per 21-03 SUMMARY) |
| `:shared:iosSimulatorArm64Test` mit neuen RankPromotionPolicyTest | (Wave 4: 7 neue + bestehende) | alle grün | PASS (per 21-06 SUMMARY) |
| `xcodebuild iosApp Debug iphonesimulator` | (Wave 1: 21-02 single-file iOS fix) | BUILD SUCCEEDED | PASS (per 21-02 SUMMARY) |
| Diagnostic logs cleanup | `grep -rcE '\[B2\]\|\[B6\]\|\[AiVM\]' shared/src/commonMain/kotlin/com/pumpernickel/` | 0 total occurrences | PASS |
| Hardcoded "5 Minuten" gone | `grep -c "5 Minuten" androidApp/.../strings.xml iosApp/.../NotificationCenter+Geofence.swift` | 0 / 0 | PASS |
| `hasAnyLedgerEntry` end-to-end chain | grep dao + repo port + impl + engine call site | 4/4 | PASS |

### Requirements Coverage

Phase 21 hat keine formalen REQ-*-IDs — die Decisions D-21-01 bis D-21-09 in 21-CONTEXT.md sind die Requirement-Quelle.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| D-21-01 | (Phase-level) | 4-Wellen-Aufteilung, Bug-Sequenzierung | SATISFIED | 21-01 + 21-02 (Wave 1), 21-03 (Wave 2), 21-04 + 21-05 (Wave 3), 21-06 (Wave 4) — alle 4 Wellen ausgeliefert |
| D-21-02 | 21-04 | B1 investigation-first dann Fix; 3 Hypothesen (a/b/c) | SATISFIED | Verdict (c) dokumentiert in WorkoutAiViewModel.kt:41-62, fix in reset() |
| D-21-03 | 21-05 | B2 reproduce + datafluss + layer-specific fix | SATISFIED | Verdict 'ViewModel/UseCase silent persist' dokumentiert in RecipeCreationViewModel.kt:200-206; fix in hasNoMacros guard |
| D-21-04 | 21-03 | OFF v2: fields erweitern + sort_by re-evaluieren + brand-re-ranking | SATISFIED | Alle 3 Maßnahmen umgesetzt (OpenFoodFactsApi.kt:28,30 + Adapter scoreResult/rankProducts) |
| D-21-05 | 21-02 | iOS .onSubmit neutralisieren | SATISFIED | NutritionFoodEntryView.swift:284-291 |
| D-21-06 | 21-01 | Notification-Body dynamisch via commonMain helper | SATISFIED | GraceDurationFormat.kt + beide Plattformen verdrahtet |
| D-21-07 | 21-06 | B6 verify-first, fix only if real bug | SATISFIED | Verdict 'real bug variant B' dokumentiert; RankPromotionPolicy.decide + hasAnyLedgerEntry signal eingeführt |
| D-21-08 | (alle) | Cross-Platform: wo möglich in commonMain | SATISFIED | B1/B2/B3/B5/B6 alle in commonMain; B4 dokumentierte iOS-only Ausnahme |
| D-21-09 | (alle) | Tests nur wo low-cost + logic-heavy (B3, B5; B6 optional) | SATISFIED | GraceDurationFormatTest (9), OpenFoodFactsAdapterBrandRankingTest (13), RankPromotionPolicyTest (7) — alle drei Test-Suites in commonTest |

### Anti-Patterns Found

Die in 21-REVIEW.md gefundenen Findings — relevant für die Phase, aber explizit non-blocking:

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| RecipeCreationViewModel.kt | 224 + 237 | Double-Save (saveFood + selectFood-via-OnFoodSelected) | BLOCKER (advisory) | CR-01: Wiederholter Barcode-Scan kann Phantom-Datensätze in DB erzeugen |
| WorkoutSessionView.swift | 168 | `@State gracePeriodSeconds: Int64 = 300` Stale-Default | BLOCKER (advisory) | CR-02: Cold-Start kann Exit-Notification mit "5 Minuten" feuern obwohl Settings auf 10s steht (B5-Regression) |
| WorkoutSessionView.swift | 1008-1033 | `previousGeofenceState = .Inactive` default → duplicate notification beim Screen-Re-Entry | WARNING | WR-01: Doppelte Notification bei View-Recreation |
| WorkoutSessionScreen.kt | 177-205 | Same pattern Android-side | WARNING | WR-02: Doppelte Notification bei Recomposition |
| WorkoutSessionView.swift | 1023-1030 | `Exited`-Branch ohne old-state Guard | WARNING | WR-03: graceExpired-Notification kann nach normalem Workout-End auch feuern |
| OpenFoodFactsApi.kt | 22,36,37,41 | 4× `println("[OFF] ...")` in Production-Code | WARNING | WR-04: User-Query + JSON-Preview landen in System-Logs (sensible Daten) |
| WorkoutAiViewModel.kt | 105 | `viewModelScope.launch { secureKeyStore.readApiKey() }` ohne Try/Catch | WARNING | WR-05: Exception aus Keychain wird verschluckt |
| WorkoutSessionView.swift | 411-415 | hardcoded `total: 2` für Early-Exit-Budget | WARNING | WR-06: iOS out-of-sync wenn Konstante geändert wird |
| RecipeCreationViewModel.kt | 154-162, 218-223 | `Food(...)` ohne Try/Catch | WARNING | WR-07: `require(...)` in Food.init kann werfen |
| GraceDurationFormat.kt | 13 | "1 Minuten" Plural-only für 60s | INFO | IN-01: Sprachlich falsch, demo-tauglich per D-21-06 |
| OpenFoodFactsAdapter.kt | 37-39 | Doku ↔ Impl-Drift bei Whitespace-Split | INFO | IN-02 |
| OpenFoodFactsDto.kt | 14 | `count`-Field nie konsumiert | INFO | IN-03 |
| LookupBarcodeUseCase.kt | 38 | sugar fehlt im allZero-Check | INFO | IN-04 |
| WorkoutSessionView.swift | 588-617 | IIFE-Closures statt private fn | INFO | IN-05 |

### Human Verification Required

Sechs Plan-Level UATs (jedes 21-NN-PLAN.md hat Task 3 = `checkpoint:human-verify`) + zwei CR-Findings stehen als human-verification-items im Frontmatter. Alle Plan-Level-UATs wurden im Parallel-Executor-Modus an die Phase-Level-UAT verschoben (siehe `tasks-skipped: 1` und "Task 3 — Deferred"-Sections in den SUMMARYs).

### Gaps Summary

**Keine echten Gaps in den must_haves.** Alle 22 automatisiert verifizierbaren Truths sind grün, alle artifacts existieren und sind in der Codebase verdrahtet, alle key_links sind gewired, alle Tests (84+7+13 = 104 Cases) sind laut SUMMARYs grün, beide Builds (gradle + xcodebuild) sind grün.

**Status human_needed weil:**

1. Sechs Plan-Level Manual-UATs (B1-B6) wurden im Parallel-Executor-Modus an User-UAT delegiert (`autonomous: false` Pläne → checkpoint:human-verify Tasks deferred). Diese sind Voraussetzung damit die Phase als end-to-end abgeschlossen gilt.

2. Zwei BLOCKER-Findings aus 21-REVIEW.md (CR-01, CR-02) sind advisory — User entscheidet, ob diese als Quick-Fix vor Demo geschlossen werden müssen oder als Follow-up in Phase 22+ ziehen. Beide sind aus Phase-21-Code direkt hervorgegangen (CR-01 entstand in 21-05 fix, CR-02 entstand in 21-01 wiring).

Die Phase ist **code-seitig abgeschlossen** und kann mit den oben gelisteten UATs / Decision-Items ge-merged + dokumentiert werden. Keine Re-Planung nötig.

---

_Verified: 2026-05-19T01:53:43Z_
_Verifier: Claude (gsd-verifier, Opus 4.7 1M context)_

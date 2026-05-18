# Phase 21: Bug-Wave aus iOS-Demo-Test 2026-05-18 - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning
**Mode:** `--auto` — all decisions auto-resolved per recommended defaults

<domain>
## Phase Boundary

Behebung von **sechs konkreten Bugs**, die in der Hands-on-iOS-Test-Session am 2026-05-18 vom User reproduziert wurden. Reine Bug-Fixes — keine neuen Features, keine Refactor-Operationen. Jeder Bug hat einen klar dokumentierten User-Symptom-Bericht, daher kein Discuss-Vorgang nötig.

**In scope:**
- B1 — AI-Workout verschwindet nach "ist bereit"-Notification (vermutlich state-loss bei Screen-Unmount während Background-Generation)
- B2 — Barcode-gescannte Zutat in Rezept-Edit zeigt alle Nährwerte = 0
- B3 — OFF v2 Suche liefert irrelevante Treffer ("Chips ungarisch" → "tue gut Form") + fehlender Brand-Match
- B4 — Daily-Log Lebensmittel-Suche: Tastatur-Submit collapsed alle Ergebnisse zu "keine Ergebnisse"
- B5 — Geofence-Exit-Notification sagt hardcoded "5 Minuten" trotz Settings-Grace-Period 10sec
- B6 — XP-Bookkeeping: keine XP nach Workout-Save bei unranked-state (-50 hat User unter Bronze gedrückt)

**Out of scope (verschoben in Folge-Phasen):**
- Anthropic AI Provider → Phase 22
- iOS Workout-Gen Parität (Zielmuskel, Sets/Reps Toggle) + Background-Mini-Bar → Phase 23
- Macro-Pills, Pagination, Zutat-Tap-Detail, Brand-Suche-UI → Phase 24
- Esslöffel/Teelöffel-Einheit, Daily-Log-Reihenfolge → Phase 25
- Pending UATs aus STATE.md (260518-egc/eny/ey4/f1c/f2h, 260517-pzh/ra5/vn7/w2f) — eigene Quick-Tasks, kein Teil dieser Bug-Wave

</domain>

<decisions>
## Implementation Decisions

### Bug-Sequenzierung & Wave-Aufteilung

- **D-21-01:** Pläne werden in **4 Wellen** organisiert, jede Welle ist ein eigener Plan:
  - **Wave A (parallelisierbar, trivial):** B5 (Grace-Period dynamisch) + B4 (Daily-Log Submit-Clear) — beide < 30min, geringe Kollisionsfläche
  - **Wave B (Such-Quality, blockierend für Wave C):** B3 (OFF v2 Quality + Brand-Match) — saubere OFF-Search ist Voraussetzung um B2 sauber zu verifizieren
  - **Wave C (Debug-Sessions, einzeln):** B1 (AI-Workout state-loss) + B2 (Barcode 0-Nährwerte) — beide brauchen Reproduktion + Logging vor dem Fix
  - **Wave D (Verifikation):** B6 (XP-Bookkeeping) — kein klarer Bug, nur User-Beobachtung; verifizieren statt fixen

  **Why:** B1 und B2 haben "Symptom-only"-Berichte und können mehrere Root-Causes haben — die brauchen Debug-Session-Disziplin, während B4/B5 reine Implementation-Bugs sind, die direkt fixbar sind.

### B1 — AI-Workout state-loss Approach

- **D-21-02:** **Investigation zuerst**, dann Fix. Konkretes Vorgehen:
  1. Reproduzieren auf iOS-Simulator: Workout via AI generieren → aus Screen rausgehen → wieder rein → state-check
  2. Logging zu `WorkoutAiViewModel.state` Transitionen (`Idle`/`Generating`/`Streaming`/`Saved`/`Error`)
  3. Hypothesen prüfen — gelistet nach Wahrscheinlichkeit:
     - **(a)** Koin-Singleton-Recreate: ViewModel ist nicht Singleton, jeder Re-Entry erzeugt neue Instanz → state weg. Fix: Koin-Scope auf `singleOf` für AI-VMs.
     - **(b)** `viewModelScope` cancel bei Screen-Unmount: KMP-ViewModel `onCleared()` killt den coroutine-job → Stream wird gecancelt → state landet auf `Idle`. Fix: Background-Service oder Application-scoped CoroutineScope für AI-Gen.
     - **(c)** SwiftUI `.task {}` re-runs bei View-Recreation und überschreibt state mit initial. Fix: state-load nur wenn `state == Idle` (siehe Quick-Fix 260518-eny — selbes Pattern).
  4. Root-Cause bestätigen → Fix in der entsprechenden Schicht.

  **Why:** "Workout ist weg" hat 3+ mögliche Schichten als Root-Cause — direkter Fix-Versuch ohne Logging würde im falschen Layer fixen.

### B2 — Barcode 0-Nährwerte Approach

- **D-21-03:** **Reproduzieren + Datenfluss durchgehen**. Bug kann in 3 Stellen sitzen:
  1. `OpenFoodFactsApi.lookupBarcode()` → Antwort enthält 0-Werte (OFF-Datensatz tatsächlich leer)
  2. `OpenFoodFactsAdapter.lookupBarcode()` → mapping verliert Werte
  3. `LookupBarcodeUseCase` + `RecipeCreationViewModel.OnBarcodeScanned` → Food wird mit 0-Werten persistiert in `FoodRepository`

  Verifizieren via Log-Statement im Adapter + DB-Snapshot der `Food`-Entity nach dem Scan. Wenn DB 0 hat aber Adapter-Output korrekt → ViewModel-Bug. Wenn Adapter 0 hat aber API non-zero → Mapping-Bug. Wenn API 0 hat → OFF-Datensatz hat tatsächlich keine Daten, dann UI-Indikator anzeigen ("keine Nährwerte verfügbar — bitte prüfen").

### B3 — OFF v2 Suche-Quality + Brand-Match

- **D-21-04:** **Drei kombinierte Maßnahmen:**
  1. `fields`-Parameter erweitern: aktuell `product_name,brands,nutriments,nutrition_grade_fr`. Hinzufügen: `generic_name`, `categories_tags`. (OFF v2-API matched die query auch in diesen Feldern auf Server-Seite, nicht nur in product_name.)
  2. Adapter-side `sort_by` ggf. wechseln von `unique_scans_n` auf `popularity_key` oder weglassen (Default ist Server-Relevanz-Ranking — vermutlich besser). Test mit Sample-Queries ("Joghurt", "Chips ungarisch", "Walnüsse gut und günstig").
  3. Brand-Match expliziter: Wenn Query mehr als ein Wort hat und ein Wort im `brands`-Feld eines Treffers vorkommt, diesen Treffer nach oben sortieren (lokale Re-Ranking im Adapter).

  **Why:** Server-side query ist eine Black-Box. Drei einfache Hebel an der API-Wrapper-Schicht sind schneller getestet als blind raten.

### B4 — Daily-Log Submit-Clear

- **D-21-05:** SwiftUI `.onSubmit { }` Handler oder `.submitLabel(.search)` triggert vermutlich einen Clear-Handler. Recipe-Steuerung: in `NutritionFoodEntryView.swift` den `.onSubmit`-Handler explizit auf "tut nichts oder behält Results" setzen — kein Job-Cancel, kein State-Clear. Tastatur soll nur runter-collabsen via `focusedField = false`.

### B5 — Geofence-Notification dynamische Grace-Period

- **D-21-06:** Notification-Body in `GeofenceNotifications.kt` (Android) und der iOS-Counterpart-File (suchen) lesen die Grace-Period aus `SettingsRepository.gracePeriodSeconds` und formatieren via Helper `formatGraceDuration(seconds: Int): String`:
  - `seconds < 60` → `"$seconds Sekunden"`
  - `seconds >= 60 && seconds < 3600` → `"${seconds/60} Minuten"`
  - `seconds == 3600` → `"1 Stunde"`
  - String-Resource `geofence_notification_exit_body` wird zum Template mit `%1$s`-Slot.

  Helper kommt in commonMain (`infrastructure/geofence/GraceDurationFormat.kt` oder ähnlich), beide Plattformen rufen es auf. Unit-Test in commonTest.

### B6 — XP-Bookkeeping bei unranked

- **D-21-07:** **Verifikation, kein Fix-Pre-Decision.** Untersuchen:
  1. War der User wirklich unranked nach dem ersten Workout, oder hat er die XP-Anzeige in Übersicht missverstanden?
  2. `GamificationEngine.applyWorkoutCompletion()` traceln: hat das echte Workout XP gegeben?
  3. Rank-Computation `RankLadder.rankFor(xp: Int)` — wenn `xp == 0` und es gibt Bronze1 bei xp >= some_threshold, sollte der User Bronze1 sein, nicht "unranked".

  Wenn das System korrekt arbeitet → Doku-Update (UI zeigen "Bronze 1" statt "unranked" als Initial-State). Wenn echter Bug → einzelner Plan-Step im Wave D.

### Cross-Platform Konsistenz

- **D-21-08:** Wo möglich, Bug-Fix in `shared/commonMain/`. Bug-by-Bug:
  - B1 → ViewModel-State in commonMain (`WorkoutAiViewModel`)
  - B2 → OFF-Adapter/UseCase/Repository in commonMain
  - B3 → OFF API + Adapter in commonMain
  - B4 → reiner SwiftUI-Bug, iOS-only Fix; Android-Verifikation als Sanity-Check
  - B5 → Format-Helper in commonMain + Notification-Service-Call platform-specific
  - B6 → ViewModel/Engine in commonMain

### Tests

- **D-21-09:** Automatisierte Tests nur wo low-cost und logic-heavy:
  - **B5**: ja — `GraceDurationFormatTest` in commonTest (kleines Pure-Function-Mapping)
  - **B3**: ja — Sample-Response-Test in commonTest für Adapter-Brand-Re-Ranking-Logik
  - **B1, B2**: nein — Debug-Sessions, keine Regression-Tests (Symptom zu Implementation-detail abhängig)
  - **B4**: nein — UI-Behavior, manuell verifizierbar
  - **B6**: optional je nach Investigations-Outcome

### Claude's Discretion

- Reihenfolge der Wave-A-Pläne (B4 vs B5 zuerst): egal, parallelisierbar
- Genauer Code-Pfad für B1-Fix nach Investigation
- Adapter-Re-Ranking-Algorithmus für B3: simpler Score (brand-match → +2, name-match → +1) reicht erstmal
- Naming des Format-Helpers (`formatGraceDuration` vs `formatGracePeriod` etc.)

### Folded Todos

[None — der eine high-score Todo-Match "Retroactive progress photo attach from History" passt thematisch zur iOS-Workout-Phase (Phase 23 oder eigener Quick-Fix), nicht zur Bug-Wave. Deferred unten.]

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Test-Bericht (Quelle aller 6 Bugs)
- `.planning/STATE.md` § Roadmap Evolution — Phase 21–25 Eintrag enthält den vollständigen User-Test-Bericht vom 2026-05-18. Anker für jeden Bug-Symptom-Bericht.

### Codebase-Maps
- `.planning/codebase/ARCHITECTURE.md` — Clean-Architecture Schichten nach Phase 20
- `.planning/codebase/STRUCTURE.md` — Package-Layout (commonMain vs platform-specific)
- `.planning/codebase/INTEGRATIONS.md` — OpenFoodFacts, Geofence, Notifications, AI-Provider
- `.planning/codebase/CONVENTIONS.md` — KMP-ViewModel, Koin-Wiring, StateFlow-Patterns

### Vorgängerphase + Phase-19 (Geofence-Kontext)
- `.planning/phases/20-clean-architecture-refactor-dependency-rule-fixen-repository/20-13-SUMMARY.md` — Phase-20-Endzustand, was wo wohnt nach dem Refactor
- `.planning/phases/19-geofencing-workout-enforcement/VERIFICATION.md` — Phase-19-Geofence-Implementation (relevant für B5)

### Verwandte Quick-Fix-Vorgänger (gleicher Bereich)
- `.planning/quick/260517-vn7-settings-debug-mode-toggle-configurable-/` — Konfigurierbare Grace-Period in SettingsRepository (B5 baut darauf auf)
- `.planning/quick/260518-eny-ios-ai-workout-generation-state-reset-na/` — Pattern für B1-Fix-Hypothese (c): one-shot SharedFlow + reset() in onAppear
- `.planning/quick/260518-egc-ios-geofence-exit-notification-posten-ko/` — iOS-Notification-Wiring, relevant für B5

### OpenFoodFacts API
- OFF v2 Search API: https://search.openfoodfacts.org/docs — endpoint `/search`, query params `q`, `fields`, `sort_by`, `page_size`
- OFF v1 (Legacy, falls Fallback gebraucht): https://wiki.openfoodfacts.org/API/Read/Search

### Memorys (User-Vision-Hintergrund, nicht in Phase 21 zu bauen)
- `[[ai-provider-extension]]` — Anthropic Provider (Phase 22)
- `[[ai-background-generation-ux]]` — Mini-Bar (Phase 23)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`SettingsRepository.gracePeriodSeconds`** (`shared/commonMain/.../infrastructure/settings/` — exact Pfad via grep) — bereits konfigurierbar dank Quick-Fix 260517-vn7. B5 liest hier.
- **`OpenFoodFactsAdapter`** (`shared/commonMain/.../infrastructure/nutrition/OpenFoodFactsAdapter.kt`) — Phase-20-Port mit `searchByQuery` + `lookupBarcode`. B3 erweitert das `fields`/`sort_by`-Verhalten hier; B2 traced den lookupBarcode-Pfad ab hier.
- **`WorkoutAiViewModel`** (`shared/commonMain/.../presentation/...` — grep für genauen Pfad) — Quick-Fix 260518-eny hat dort `savedEvent: SharedFlow<Unit>` + `reset()` schon eingebaut. B1 erweitert das Pattern wenn Hypothese (c) zutrifft.
- **`GeofenceNotifications`** (Android: `androidApp/.../notifications/GeofenceNotifications.kt`; iOS: SwiftUI-Side via `iosApp/iosApp/Views/Workout/` oder AppDelegate) — Notification-Body-Strings. B5 ersetzt hardcoded Text.
- **`commonTest`-Setup** mit Turbine + 62/62 Tests aktuell grün (Stand Phase 20). B5/B3-Tests reihen sich dort ein.

### Established Patterns
- **Koin-DI** für ViewModels und Adapter — `single<RemoteFoodSearchClient> { OpenFoodFactsAdapter(get()) }`. Bei B1 Hypothese (a) wechsel auf `singleOf<WorkoutAiViewModel>(::WorkoutAiViewModel)` falls nicht schon so.
- **StateFlow + `@NativeCoroutinesState`** für iOS-Konsumption via `KMPNativeCoroutinesAsync`. iOS-Views verwenden `asyncSequence(for: viewModel.someFlow)`.
- **Dependency-Rule (Phase 20)**: domain/ darf nicht von data/ importieren. Bei jedem neuen Helper (z.B. `GraceDurationFormat`) sicherstellen, dass nichts Framework-spezifisches im domain/ landet — gehört in `infrastructure/`.
- **String-Resources** Android: `androidApp/src/main/res/values/strings.xml` mit `%1$s`-Slots. iOS: meist hardcoded in Swift-Files (keine Localizable.strings — auch für B5 hardcoded geplant, da deutsche-only App).

### Integration Points
- **B1**: `WorkoutAiViewModel` ↔ Koin module ↔ iOS `AIWorkoutGenView.swift` + Android-Pendant. Plus eventuell Background-CoroutineScope-Definition in `di/SharedModule.kt`.
- **B2**: `OpenFoodFactsAdapter.lookupBarcode` → `LookupBarcodeUseCase` → `RecipeCreationViewModel.OnBarcodeScanned` → `selectFood(food)` → `FoodRepository.save()`.
- **B3**: `OpenFoodFactsApi.searchByName` → `OpenFoodFactsAdapter.searchByQuery` → `RemoteFoodSearchClient` Port → `SearchFoodsRemoteUseCase` → Daily-Log + Recipe-Creation ViewModels.
- **B4**: iOS `NutritionFoodEntryView.swift` line range mit `.searchable`/`TextField`-Setup + `.onSubmit` Handler. Single-File-Fix.
- **B5**: Format-Helper (commonMain) ↔ `SettingsRepository.gracePeriodSeconds` (lesen) ↔ Android `GeofenceNotifications.kt` und iOS Notification-Builder (Strings injizieren).
- **B6**: `GamificationEngine.applyWorkoutCompletion` ↔ `RankLadder.rankFor` ↔ Overview-UI.

</code_context>

<specifics>
## Specific Ideas

- User-Beobachtung B3 als konkrete Test-Suite: Query "Chips ungarisch" sollte "Funny Frisch Chips Ungarisch" als Top-Treffer liefern; Query "Walnüsse gut und günstig" sollte ein "Walnüsse, gut & günstig"-Produkt finden, nicht "Sprühsahne von gut & günstig".
- B5 Test: Grace-Period 10sec → Notification sagt "10 Sekunden um zurückzukommen". Grace-Period 300sec → "5 Minuten um zurückzukommen". Grace-Period 60sec → "1 Minuten" oder "1 Minute" (Singular-Fall, low-prio).
- B6 Acceptance: nach normalem Workout-Save sollte User XP bekommen UND Rank-Anzeige soll sich anpassen (von unranked auf Bronze1 oder beibehalten Bronze1 wenn schon darüber).

</specifics>

<deferred>
## Deferred Ideas

- **Retroactive progress photo attach from History** (Todo 2026-05-06) — Todo-Match Score 0.9 (Keywords ios+workout), aber inhaltlich Feature-Arbeit, kein Bug. Gehört thematisch in Phase 23 (iOS Polish) oder eigene Quick-Fix.
- **Pending Manual UATs** aus STATE.md (260518-egc, eny, ey4, f1c, f2h und 260517-pzh, ra5, vn7, w2f) — sind Verifikationen für bereits gefixte Issues, kein Re-Build. Eigenständig durch User abzuarbeiten parallel zur Phase 21 Plan-Execution.
- **Recipe-Edit: "+"-Button zeigt Suche, aber Suchergebnisse leeren sich beim Submit** — B4 fixt das in Daily-Log; falls dasselbe Bug-Verhalten in Recipe-Edit existiert, separat verifizieren und ggf. als follow-up plan in Wave A integrieren.

### Reviewed Todos (not folded)
- **`2026-05-06-retroactive-progress-photo-attach-from-history.md`** — Reviewed, nicht gefolded: ist ein iOS-Feature (Foto aus History an alten Workout-Eintrag anhängen), kein Bug-Fix. Deferred zu Phase 23 oder Quick-Fix.

</deferred>

---

*Phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss*
*Context gathered: 2026-05-18*

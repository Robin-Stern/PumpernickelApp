---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss
reviewed: 2026-05-19T00:00:00Z
depth: standard
files_reviewed: 25
files_reviewed_list:
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/notifications/GeofenceNotifications.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
  - androidApp/src/main/res/values/strings.xml
  - iosApp/iosApp/Utilities/NotificationCenter+Geofence.swift
  - iosApp/iosApp/Views/AI/AIWorkoutGenView.swift
  - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
  - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/GamificationDao.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicy.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicyTest.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormatTest.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapterBrandRankingTest.kt
findings:
  critical: 2
  warning: 7
  info: 5
  total: 14
status: issues_found
---

# Phase 21: Code Review Report

**Reviewed:** 2026-05-19
**Depth:** standard
**Files Reviewed:** 25 (24 Source + 1 Resources)
**Status:** issues_found

## Summary

Die sechs Bug-Fixes der Phase 21 (B1–B6) sind durchgängig sauber implementiert: dynamische Grace-Period-Formatierung mit Pure-Helper + Unit-Tests (B5), neutraler `.onSubmit`-Handler auf der OFF-Suche (B4), OFF-v2 Suche mit erweiterten Feldern + lokaler Brand-Re-Ranker (B3), UI-Hint bei zero-macro Barcode-Lookups (B2), bewachtes `reset()` in `WorkoutAiViewModel` (B1), und eine pure `RankPromotionPolicy` mit dediziertem Ledger-Empty-Check (B6).

Trotz der sauberen Phase-Arbeit habe ich **zwei BLOCKER** identifiziert, die direkt aus den Phase-21-Änderungen folgen, sowie **mehrere WARNINGS** in angrenzendem Code, der für die Bug-Fixes verändert wurde:

1. **Race / Duplicate-Save bei B2** — Der Fix speichert die Food zweimal (einmal explizit, einmal indirekt über `OnFoodSelected`), wodurch bei wiederholtem Scan ein Phantom-Datensatz entstehen kann.
2. **iOS Grace-Period Default-Value Race** — Beim Cold-Start kann die Exit-Notification mit dem Stale-Default `300` (statt User-Setting) gefeuert werden, falls Settings-Flow nicht vor dem ersten Grace-Übergang emittiert hat.

Außerdem fallen **alle Geofence-Status-Beobachter (iOS+Android)** auf eine "duplicate notification beim Re-Entry"-Falle herein: der `previousGeofenceState`-Default ist `Inactive`, wodurch das erste Re-Subscriben einer aktiven `GracePeriod`/`Exited`-State erneut die Notification feuert.

## Critical Issues

### CR-01: Duplicate `saveFood` + Phantom-Food bei wiederholtem Scan (B2)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt:224,237`
**Issue:**
Im Zweig `LookupBarcodeUseCase.Result.FoundRemote` (Zeile 210–238) wird `repository.saveFood(food)` direkt aufgerufen (Zeile 224) **und** anschließend `onEvent(RecipeCreationEvent.OnFoodSelected(food))` (Zeile 237).

`OnFoodSelected` ruft `selectFood(food)` → `SelectFoodUseCase` auf. `SelectFoodUseCase.invoke` speichert die Food zwar nur, wenn `food.source == "openfoodfacts"` (Zeile 8 in `SelectFoodUseCase.kt`), aber im B2-Pfad wird **bewusst kein `source = "openfoodfacts"` gesetzt** (Zeile 218–223 — anders als `OnRemoteFoodSelected` in derselben VM, Zeile 161). Resultat: 

- Erster Save passiert via direktem `repository.saveFood(food)` (Zeile 224). Die `Food`-Instanz hat eine bei `Food(...)` neu zufällig generierte `id` (Default `Uuid.random().toString()`).
- Beim erneuten Scan desselben Barcodes generiert die nächste `Food(...)`-Konstruktion eine **neue UUID** — `FoodEntity` wird per `id` insertiert (`@Insert` ohne `OnConflictStrategy.IGNORE` für die Barcode-Spalte), also entstehen zwei Foods mit gleichem `barcode` aber unterschiedlicher `id` in der DB.
- Daraus folgt: keine Idempotenz bei wiederholtem Scan; `loadFoods().firstOrNull { it.barcode == barcode }` in `LookupBarcodeUseCase` (Zeile 22) liefert nach dem zweiten Scan einen der beiden Datensätze, die UI-Konsistenz ist nicht mehr garantiert.

Zusätzliches Risiko: Wenn `Food(...)` (Zeile 218–223) wegen `require(sugar <= carbohydrates)` oder `require(name.isNotBlank())` (siehe `Food.init`) wirft, läuft der Code in eine `IllegalArgumentException`, die nicht gefangen wird — der Coroutine-Job crasht ohne UI-Hint. Adapter und UseCase clampen Sugar zwar an Carbs, aber der Phase-21-Fix kommt ohne Defensive direkt nach einem unsicheren OFF-Output.

**Fix:**
```kotlin
is LookupBarcodeUseCase.Result.FoundRemote -> {
    // Vor dem Save: check ob barcode bereits lokal vorhanden ist.
    val existing = repository.loadFoods().firstOrNull { it.barcode == event.barcode }
    if (existing != null) {
        onEvent(RecipeCreationEvent.OnFoodSelected(existing))
        return@launch
    }
    val hasNoMacros = result.calories < 1.0 && result.protein <= 0.0 &&
        result.carbs <= 0.0 && result.fat <= 0.0
    val food = try {
        Food(
            name = result.name, calories = result.calories,
            protein = result.protein, fat = result.fat,
            carbohydrates = result.carbs, sugar = result.sugar,
            barcode = event.barcode,
            source = "openfoodfacts"   // ⬅ aktiviert die SelectFoodUseCase-Persistierung
        )
    } catch (e: IllegalArgumentException) {
        _creationState.update { it.copy(errorMessage = "Ungültige Nährwerte von OpenFoodFacts: ${e.message}") }
        return@launch
    }
    // Save erfolgt jetzt EINMAL via OnFoodSelected → SelectFoodUseCase.
    if (hasNoMacros) {
        _creationState.update {
            it.copy(errorMessage = "OpenFoodFacts hat für '${result.name}' keine Nährwerte. Bitte manuell ergänzen.")
        }
    }
    onEvent(RecipeCreationEvent.OnFoodSelected(food))
}
```

### CR-02: iOS Notification feuert mit Stale-Default-GracePeriod beim Cold-Start

**File:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:168,1015`
**Issue:**
`@State private var gracePeriodSeconds: Int64 = 300` (Zeile 168) ist der Initial-Wert. Der Settings-Flow (`observeGracePeriodSeconds`, Zeile 1069–1077) läuft in derselben `TaskGroup` wie `observeGeofenceState`. Es gibt **keine Garantie**, dass `gracePeriodSeconds` aus dem Flow gesetzt ist, bevor `handleGeofenceStateChange` zum ersten Mal `GracePeriod` sieht:

- Wenn der User App in `.away`-State landet (Workout läuft, App im Hintergrund), iOS einen Geofence-Exit reportet, der Native-Bridge die `GracePeriod` setzt, und der User-Setting bereits auf 10 Sekunden steht, würde die Notification mit `300` ("5 Minuten um zurückzukommen") gesendet — direktes B5-Regressions-Symptom.
- Die `D-21-06`-Doku behauptet "the configured grace-period via shared Kotlin helper", aber der gelesene Wert ist der lokale `@State`, nicht der Flow-Wert.

Auf Android ist das Problem entschärft: `WorkoutSessionScreen.kt` Zeile 119–120 verwendet `settingsViewModelRoot.gracePeriodSeconds.collectAsState()`, was synchron den aktuellen StateFlow-Value liefert. Auf iOS gibt es kein synchrones Pendant; die `for try await`-Schleife in `observeGracePeriodSeconds` startet erst, wenn die Task gescheduled ist.

Konsekutive Bugs: Selbe Race-Bedingung gilt für den `Exited`-Branch in Zeile 1023–1030, der die `penalty` mit `loggedSets`/`planned` aus `sessionState` rechnet — diese können beim Cold-Resume auch noch nicht aktuell sein, wenn die TaskGroup gerade erst startet.

**Fix:**
Synchron beim ersten Eintritt in `GracePeriod` den aktuellen Settings-Wert lesen statt auf den Flow-Cache zu verlassen. Entweder via blocking-suspend-call auf einen Settings-Snapshot, oder durch `await` auf den ersten Flow-Emit am Start der Task:
```swift
.task {
    // Vor allem anderen: Synchron den Settings-Snapshot pullen.
    await observeGracePeriodSeconds_BootstrapOnce()   // wartet auf erstes Emit
    await withTaskGroup(of: Void.self) { group in
        group.addTask { await observeGeofenceState() }
        group.addTask { await observeGracePeriodSeconds() }  // kontinuierlich
        // …
    }
}

private func observeGracePeriodSeconds_BootstrapOnce() async {
    for try? await value in asyncSequence(for: settingsViewModel.gracePeriodSecondsFlow).prefix(1) {
        self.gracePeriodSeconds = value.int64Value
        return
    }
}
```
Oder simpler: Lazy-load via `KoinHelper.shared.getSettingsRepository().gracePeriodSecondsNow()` (synchron suspend) im Moment, wo die Notification gebaut wird.

## Warnings

### WR-01: Geofence-Notification feuert beim Screen-Re-Entry erneut (iOS)

**File:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:1008-1033`
**Issue:**
`handleGeofenceStateChange(old:, new:)` wird in `observeGeofenceState` für jedes Flow-Emit aufgerufen. Bei einer SwiftUI-View-Recreation startet `.task {}` neu, der `@State var geofenceState` ist initial `GeofenceUiState.Inactive.shared` (Zeile 148). Wenn das Workout zu diesem Zeitpunkt bereits in `GracePeriod` ist (App war im Hintergrund, kommt jetzt zurück), liefert der Flow als ersten Wert `GracePeriod`. Logic:
```swift
case is GeofenceUiState.GracePeriod:
    if !(old is GeofenceUiState.GracePeriod) {  // old == .Inactive → branch FIRES
        center.postGeofenceNotification(.exitDetected(graceSeconds: …))
    }
```
Resultat: doppelte Exit-Notification, obwohl der State nicht wirklich gewechselt hat. Selbes Schema für `.Exited` (Zeile 1023) — der Branch hat keinen `wasInExited`-Guard, also feuert jeder Re-Entry den `graceExpired`-Push erneut.

**Fix:**
- `geofenceState`-Default auf `nil` (Optional) setzen und beim ersten Emit den Übergang `nil → X` als "Bootstrap" behandeln (kein Notification-Fire).
- Oder einen Persistenz-Flag (z.B. `@AppStorage` mit `lastNotifiedGeofenceState`) ergänzen, der den letzten gefeuerten Event-Typ persistiert, so dass derselbe Übergang nicht zweimal in einer Session gepostet wird.

### WR-02: Geofence-Notification feuert beim Screen-Re-Entry erneut (Android)

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt:177-205`
**Issue:**
Identische Bug-Klasse wie WR-01. `LaunchedEffect(Unit)` (Zeile 177) launcht eine Coroutine, die `previousGeofenceState: GeofenceUiState = GeofenceUiState.Inactive` (Zeile 178) als lokalen Loop-State hält. Bei Recomposition mit neuem Key (oder nach `Activity`-Recreate, was Compose nicht zwangsläufig durchhält) startet die Coroutine neu mit `Inactive` als previous — feuert dann die `postExitDetected`-Notification, wenn der ViewModel bereits `GracePeriod` reportet. 

Doppelte Notifications sind subjektiv schlimmer auf Android, weil `NotificationManagerCompat.notify(id, …)` mit konstanter id (Zeile 39: `NOTIFICATION_ID_BASE + 1`) den Notification-Eintrag zwar überschreibt, aber den Sound erneut abspielt.

**Fix:**
- `previousGeofenceState` aus `rememberSaveable` heraus initialisieren, oder explizit aus `viewModel.geofenceState.value` initialisieren statt Hardcode `Inactive`.
- Alternativ: Notification-Side-Effect in den `ViewModel` ziehen statt im `Composable` zu reagieren — der ViewModel kennt seinen eigenen letzten State und kann es selbst guarden.

### WR-03: `Exited`-Notification feuert auch nach normalem Workout-Save (iOS)

**File:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:1023-1030`
**Issue:**
Im `case is GeofenceUiState.Exited`-Branch wird ohne `old`-Check immer die `graceExpired`-Notification mit XP-Penalty gepostet. Wenn der User normal über "Workout beenden" → `enterReview()` → `saveReviewedWorkout()` läuft und der Geofence-Service danach in `Exited` übergeht (Stop des aktiven Geofence kann je nach Phase-19-Implementierung als `Exited`-Emission ankommen), bekommt der User eine "XP abgezogen"-Notification, obwohl er regulär gespeichert hat. Worth-Checking, ob das in Phase-19-Tests bereits ausgeschlossen ist; Android hat denselben Code-Pfad (`WorkoutSessionScreen.kt:194-201`) und denselben fehlenden Guard.

**Fix:**
```swift
case is GeofenceUiState.Exited:
    if !(old is GeofenceUiState.Exited) {  // guard wie bei GracePeriod
        let active = sessionState as? WorkoutSessionState.Active
        // … nur posten wenn sessionState nicht Reviewing/Finished ist
        if active != nil {
            center.postGeofenceNotification(.graceExpired(loggedSets: logged, penaltyXp: penalty))
        }
    }
```

### WR-04: Debug-Logging mit `println` in Production-Code

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt:22,36,37,41`
**Issue:**
Vier `println`-Statements in `searchByName`:
```kotlin
println("[OFF] searchByName query='$query' pageSize=$pageSize")
println("[OFF] response received bytes=${responseText.length} preview='${responseText.take(120)}'")
println("[OFF] HTML detected — throwing IllegalStateException")
println("[OFF] decoding JSON…")
```
`println` schreibt auf Android in `System.out` (geht im LogCat verloren) und auf iOS in `NSLog`-äquivalent (sichtbar in Console.app). Die Logs enthalten User-Query-Daten und API-Response-Snippets — kein direktes Secret-Leak, aber sensible Daten in System-Logs. Außerdem werden 120 Zeichen JSON-Preview im Log gespeichert.

**Fix:**
Logging-Framework verwenden (z.B. Kermit), oder `println`-Statements entfernen. Mindestens auf `if (DEBUG) println(…)` guarden — aktuell läuft das auch in Release-Builds.

### WR-05: `secureKeyStore.readApiKey()` Result-Ignored (B1-VM)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt:105`
**Issue:**
```kotlin
init {
    viewModelScope.launch { secureKeyStore.readApiKey() }
```
Der Return-Value wird verworfen. Wenn `readApiKey()` (suspend) wirft (z.B. iOS-Keychain-Crash bei Cold-Start), schluckt der Launch die Exception in `CoroutineExceptionHandler` (oder crasht den `viewModelScope` je nach Setup). Der Kommentar sagt "read updates the flow", aber ohne Try/Catch ist die Fehlerquelle unerklärbar.

**Fix:**
```kotlin
viewModelScope.launch {
    try {
        secureKeyStore.readApiKey()
    } catch (t: Throwable) {
        // Keychain miss is fine — ApiKeyState.configured stays false.
    }
}
```

### WR-06: `WorkoutSessionView` iOS hardcoded `total: 2` für Early-Exit-Budget

**File:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:411-415`
**Issue:**
```swift
earlyExitDialogConfig = EarlyExitDialogConfig(
    total: 2,
    remaining: Int(budget?.remaining ?? 0),
    penaltyXp: penalty
)
```
`total: 2` ist hardcoded. Die Konstante `EarlyExitTracker.EARLY_EXIT_BUDGET_PER_MONTH` ist im shared Code definiert und Android (`WorkoutSessionScreen.kt:297`) verwendet sie korrekt. Wenn der Wert geändert wird, läuft iOS aus dem Sync. **Pre-existing**, nicht Phase-21-Regression, aber gefährliche Inkonsistenz, weil B5/B6 auf Phase-19-Logik aufbauen.

**Fix:**
```swift
total: Int(EarlyExitTracker.companion.EARLY_EXIT_BUDGET_PER_MONTH)
```

### WR-07: `Food`-Constructor wirft, kein Try/Catch bei `OnRemoteFoodSelected` & `OnBarcodeScanned`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt:154-162,218-223`
**Issue:**
`Food.init` (Food.kt:23-31) hat `require(...)`-Checks für nicht-negative Werte, `sugar <= carbohydrates` und `name.isNotBlank()`. Beim `OnRemoteFoodSelected`-Branch (Zeile 153–176) wird `Food(name = event.result.name, …)` ohne Try/Catch konstruiert. Wenn OFF eine Edge-Case-Antwort liefert (z.B. sugar > carbs, was der Adapter clampt, aber bei Schema-Wechsel ungeklammt bleiben könnte), wirft der Constructor.

`OnBarcodeScanned`-Pfad (siehe CR-01) hat dasselbe Problem.

**Fix:**
Defensive Konstruktion via Helper:
```kotlin
private fun safeFoodFromRemote(result: RemoteFoodResult): Food? = runCatching {
    Food(
        name = result.name, calories = result.calories,
        protein = result.protein, fat = result.fat,
        carbohydrates = result.carbs, sugar = minOf(result.sugar, result.carbs),
        source = "openfoodfacts"
    )
}.getOrNull()
```

## Info

### IN-01: `GraceDurationFormat`: "1 Minuten" Singular-Fall ist absichtlich plural

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GraceDurationFormat.kt:13`
**Issue:**
Per CONTEXT.md D-21-06 ist das Verhalten "60s → '1 Minuten' (plural)" absichtlich ("singular ist low-prio"). Test `sixtySecondsIsOneMinute` (GraceDurationFormatTest.kt:18) lockt das fest. Demo-tauglich, aber sprachlich falsch — wenn die App über University-Demo hinaus geht, ist das ein bekannter Defekt.

**Fix:**
```kotlin
seconds == 60 -> "1 Minute"
seconds < 3600 -> "${seconds / 60} Minuten"
```

### IN-02: `OpenFoodFactsAdapter.scoreResult` Doku ↔ Implementation Mismatch

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt:37-39`
**Issue:**
Doku sagt "Split each entry on comma and whitespace, lowercase, drop blanks". Implementierung: `it.split(',', ' ', '\t')`. Splittet nicht auf Unicode-Whitespace (z.B. NBSP, Zeilenumbruch). OFF-Brand-Strings können theoretisch beliebige Whitespace-Klassen enthalten.

**Fix:**
```kotlin
.flatMap { it.split(Regex("[,\\s]+")) }
```

### IN-03: `OpenFoodFactsSearchResponse.count` wird nie konsumiert

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsDto.kt:14`, `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt:43`
**Issue:**
`OpenFoodFactsSearchResponse.count` wird gesetzt aus `v2.count`, aber kein Downstream-Konsument liest es. UI macht weder Pagination noch Hit-Count-Anzeige. Dead-Field. Kein Bug, aber Cruft.

**Fix:**
Entfernen, oder konsumieren (z.B. UI-Hint "von X Treffern werden 20 angezeigt").

### IN-04: `allZero`-Check in `LookupBarcodeUseCase` ignoriert `sugar`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt:38`
**Issue:**
```kotlin
val allZero = cal <= 0.0 && prot <= 0.0 && fat <= 0.0 && carbs <= 0.0
```
`sugar` fehlt im Check. Theoretischer Edge-Case: OFF gibt 0 für alle Makros zurück, aber sugar > 0 — dann wird der Fallback nicht aktiviert, obwohl das Produkt offensichtlich missing-data ist. Praktisch unwahrscheinlich, weil sugar Teilmenge von carbs sein muss.

**Fix:** Sugar in den Check aufnehmen oder als Kommentar dokumentieren, warum es weggelassen wird.

### IN-05: `WorkoutSessionView.swift` doppelter Code-Pfad für `prevText`/`pbText`-Closures

**File:** `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift:588-617`
**Issue:**
`let prevText: String? = { … }()` und `let pbText: String? = { … }()` als IIFE-Closures sind ungewöhnlich in SwiftUI. Closures werden bei jedem `headerSection`-Render evaluiert. Kein Bug, aber lesbarer als private `func computePrevText() -> String?`.

---

_Reviewed: 2026-05-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

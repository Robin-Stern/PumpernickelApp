---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
reviewed: 2026-05-19T00:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
  - shared/src/commonMain/resources/workout-system-prompt.md
  - shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiGenerationMiniBar.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt
findings:
  critical: 3
  warning: 4
  info: 2
  total: 9
status: issues_found
---

# Phase 23: Code Review Report

**Reviewed:** 2026-05-19
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Zusammenfassung

Geprüft wurden die KI-Workout-Generierung (Domain, ViewModel, UseCase), der System-Prompt, die Datenbankdefinition sowie die Android-UI-Komponenten MiniBar, AiWorkoutGenScreen und MainScreen. Drei Befunde erfordern eine Korrektur vor dem Merge: ein hängendes Bottom-Sheet-Problem, ein UnsafeCast mit Absturzpotenzial und ein echter Race-Condition-Bug im AiGenerationManager. Die Warnings betreffen fehlende Validierung, eine verlorene Datenbeschreibung und ein implizit hartcodiertes Locale-Verhalten.

---

## Critical Issues

### CR-01: ModalBottomSheet mit leerem `onDismissRequest` erzeugt dauerhaft hängendes UI

**File:** `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt:313`

**Issue:** `WorkoutPreviewSheet` übergibt eine leere Lambda an `onDismissRequest`. In Material3 löst eine Wisch-nach-unten-Geste auf dem Sheet den `onDismissRequest`-Callback aus und entfernt das Sheet aus der Komposition. Da der ViewModel-Zustand weiterhin `WorkoutAiUiState.Preview` ist, wird das Sheet beim nächsten Recompose sofort wieder eingeblendet. Das Resultat für den Nutzer: Das Sheet verschwindet kurz und erscheint sofort wieder — ein nicht behebbares UI-Blockierer-Szenario bis Speichern oder Verwerfen geklickt wird. Das entspricht nicht dem Verhalten von iOS `interactiveDismissDisabled`, sondern einem defekten Swipe-Loop.

**Fix:** Entweder `confirmValueChange` im SheetState konfigurieren, um Swipe-Dismiss zu sperren, oder `onDismissRequest` an `viewModel::discardPreview` delegieren, damit Sheet-State und VM-State synchron bleiben:

```kotlin
// Option A — Swipe-Dismiss dauerhaft sperren (spiegelt iOS-Verhalten)
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { it != SheetValue.Hidden }
)
ModalBottomSheet(
    onDismissRequest = { /* gesperrt via confirmValueChange */ },
    sheetState = sheetState
) { ... }

// Option B — Swipe-Dismiss als Discard behandeln
ModalBottomSheet(
    onDismissRequest = onDiscard,
    sheetState = sheetState
) { ... }
```

---

### CR-02: Unsicherer Cast `genState.preview as WorkoutAiPreview` — potenzielle ClassCastException

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt:148`

**Issue:** `AiGenerationState.Success.preview` ist als `Any` typisiert (weil `AiGenerationState` sowohl Workout als auch Recipe-Previews verwaltet). Zeile 148 nutzt einen harten Cast `as WorkoutAiPreview`. Alle drei anderen Zugriffe auf `genState.originatingData` verwenden korrekt `as?` (Zeilen 141, 149, 163). Sollte durch einen Programmierfehler oder zukünftige Erweiterung ein `RecipeAiPreview` in `Success.preview` landen, während `type == AiType.WORKOUT`, stürzt die App mit `ClassCastException` ab — ohne UI-Fehlermeldung, da der Fehler außerhalb eines `try/catch`-Blocks liegt.

**Fix:** Sicheren Cast verwenden und bei `null` einen Fehlerzustand setzen:

```kotlin
is AiGenerationState.Success -> {
    if (genState.type == AiType.WORKOUT) {
        val preview = genState.preview as? WorkoutAiPreview
        if (preview == null) {
            _uiState.value = WorkoutAiUiState.Error(
                AiError.SchemaInvalid("Unexpected preview type"),
                originatingForm = (current as? WorkoutAiUiState.Preview)?.originatingForm ?: defaultForm
            )
            return@collect
        }
        val form = genState.originatingData as? WorkoutAiForm
        _uiState.value = WorkoutAiUiState.Preview(
            preview = preview,
            originatingForm = ...
        )
    }
}
```

---

### CR-03: Race Condition in `AiGenerationManager.startWorkoutGeneration()` / `startRecipeGeneration()`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt:43-48` und `78-82`

**Issue:** Das Check-then-Act-Muster ist nicht atomar:

```kotlin
if (_state.value is AiGenerationState.Generating) return false  // (1)
_state.value = AiGenerationState.Generating(...)                 // (2)
currentJob = scope.launch { ... }                                 // (3)
```

`AiGenerationManager` läuft auf `Dispatchers.Default` (Scope-Definition Zeile 30), der ein Thread-Pool ist. Wenn `startWorkoutGeneration` und `startRecipeGeneration` nahezu gleichzeitig von verschiedenen Threads aufgerufen werden (z.B. Tap-Event + Notification-Callback), können beide die Prüfung in (1) passieren, bevor einer von ihnen in (2) schreibt. Das führt zu zwei gleichzeitig laufenden LLM-Requests, zwei konkurrierenden `_state`-Schreibern und einem orphaned `currentJob` (wird von dem zweiten `launch`-Aufruf überschrieben — der erste Job läuft weiter, ist aber nicht mehr abbbrechbar via `clear()`).

**Fix:** Zugriff auf `_state` und `currentJob` in einem `synchronized`-Block oder mit einem Mutex kapseln:

```kotlin
private val mutex = Mutex()

suspend fun startWorkoutGeneration(form: WorkoutAiForm): Boolean = mutex.withLock {
    if (_state.value is AiGenerationState.Generating) return false
    _state.value = AiGenerationState.Generating(AiType.WORKOUT, form)
    currentJob = scope.launch { ... }
    true
}
```

Alternativ: Die `start*`-Funktionen als `suspend`-Funktionen deklarieren, die über den `scope`-Dispatcher dispatchen, sodass alle Zustandsänderungen sequenziell ablaufen.

---

## Warnings

### WR-01: `StagedTemplate.description` wird in `commit()` nicht persistiert

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:99-101`

**Issue:** Die KI liefert für jedes Template ein `description`-Feld (z.B. "Push-Fokus mit Schwerpunkt Volumen."), das auch korrekt in `StagedTemplate.description` gespeichert wird. In `commit()` wird `templateRepository.createTemplate(name = template.name, source = "AI")` aufgerufen — `description` wird nicht übergeben. `TemplateRepository.createTemplate` akzeptiert kein `description`-Argument, das `WorkoutTemplate`-Domain-Modell besitzt kein solches Feld. Die vom LLM generierte Beschreibung geht damit stillschweigend verloren und wird nie angezeigt oder gespeichert.

**Fix:** Kurzfristig: Entweder `description` aus dem Interface entfernen und das System-Prompt aktualisieren (wenn Beschreibungen nicht genutzt werden sollen), oder das `WorkoutTemplate`-Model, das DB-Entity, die Migration und `TemplateRepository` erweitern. Das stille Verwerfen ist schlechter als eine der beiden expliziten Entscheidungen.

---

### WR-02: `validateResponse` prüft `secondaryMuscles` nicht auf gültige `MuscleGroup`-Werte

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:264-273`

**Issue:** Für `inlineNewExercises` werden `primaryMuscles`-Strings gegen `MuscleGroup.fromDbName()` validiert. `secondaryMuscles` werden hingegen nicht validiert. In `resolvePreview()` (Zeile 287) werden ungültige `secondaryMuscles`-Strings via `mapNotNull` still gefiltert. Das bedeutet: Ein LLM, das z.B. `"upper back"` statt `"lats"` in `secondaryMuscles` schreibt, produziert ein `StagedExercise` ohne den erwarteten Muskel — ohne Fehlermeldung. Das ist inkonsistentes Validierungsverhalten.

**Fix:** `secondaryMuscles` in `validateResponse` analog zu `primaryMuscles` prüfen oder — wenn stilles Ignorieren akzeptiert ist — den Kommentar explizit dokumentieren und die `primaryMuscles`-Logik konsistent gestalten (ebenfalls `mapNotNull` statt Fehler werfen):

```kotlin
for (mg in inline.secondaryMuscles) {
    if (MuscleGroup.fromDbName(mg) == null) {
        // Log oder ignorieren — hier explizit entscheiden statt still verwerfen
    }
}
```

---

### WR-03: `exerciseCount` pro Template wird nicht gegen `form.exerciseCount` validiert

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:254-264`

**Issue:** `validateResponse` prüft, dass `response.templates.size == form.splitStyle.templateCount` und dass jedes Template mindestens eine Übung hat (`exercises.isEmpty()`). Es wird jedoch nicht geprüft, ob `t.exercises.size == form.exerciseCount`. Das LLM kann ein Template mit 2 statt 5 Übungen zurückgeben; der Code akzeptiert das kommentarlos. Der User sieht dann ein Workout, das nicht seinen Eingaben entspricht.

**Fix:**

```kotlin
for (t in response.templates) {
    if (t.exercises.size != form.exerciseCount) {
        throw AiError.SchemaInvalid(
            "Template '${t.name}': expected ${form.exerciseCount} exercises, got ${t.exercises.size}"
        )
    }
    ...
}
```

---

### WR-04: `AiGenerationManager` schreibt rohe Exception-Messages in Benachrichtigungen

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt:65` und `99`

**Issue:** Fehlerbenachrichtigungen enthalten `e.message` direkt:

```kotlin
notificationService.showNotification(
    "Fehler bei der Generierung",
    "Das Workout konnte nicht erstellt werden: ${e.message}"
)
```

`e.message` kann interne technische Details enthalten (Stack-Trace-Fragmente, Provider-URLs, API-Key-Statusbeschreibungen aus Ktor-Exceptions). Diese werden als Android-Systembenachrichtigung angezeigt — für den User lesbar, für Screenshots und Logs potenziell sensibel (z.B. könnte ein Ktor `ClientRequestException` die komplette Request-URL inklusive Auth-Header-Details in der Message haben).

**Fix:** `e.message` durch nutzerfreundliche, klassifizierte Fehlertexte ersetzen. `AiError.fromThrowable()` ist bereits vorhanden — diesen nutzen:

```kotlin
val aiError = if (e is AiError) e else AiError.fromThrowable(e)
val userMessage = when (aiError) {
    is AiError.AuthOrQuota -> "API-Schlüssel ungültig oder Kontingent aufgebraucht."
    is AiError.Network -> "Netzwerkfehler. Bitte Verbindung prüfen."
    AiError.Timeout -> "Zeitüberschreitung beim KI-Aufruf."
    else -> "Das Workout konnte nicht erstellt werden."
}
notificationService.showNotification("Fehler bei der Generierung", userMessage)
```

---

## Info

### IN-01: `workoutAiSchemaJson()` liefert nur `{"type":"object"}` — kein nutzbares Schema

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:331`

**Issue:** Der JSON-Schema-String für die `response_format=json_schema`-API-Anfrage ist `{"type":"object"}` — vollständig trivial. Er beschränkt die Antwort nur auf ein JSON-Objekt, gibt aber keinerlei Feldstruktur vor. Die eigentliche Validierung liegt in `validateResponse()` und im System-Prompt. Das ist funktional korrekt für Provider, die nur `json_object`-Mode unterstützen, bietet aber keine strukturierten Parsing-Vorteile bei Providern, die echtes `json_schema` unterstützen (z.B. OpenAI). Das wirft die Frage auf, ob das `callWithJsonSchema`-Fallback überhaupt einen Vorteil gegenüber `callWithJsonObject` hat.

**Fix:** Entweder ein vollständiges Schema für `WorkoutAiResponse` hinterlegen (bessere Schemakonformität bei OpenAI), oder die Methode auf `callWithJsonObject` vereinheitlichen und den Dead-Code-Pfad entfernen. Keine unmittelbare Fehlerursache, aber technische Schuld.

---

### IN-02: Locale-Übergabe in `WorkoutAiUseCase.invoke()` fehlt — nur Default "de" wird genutzt

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:50`

**Issue:** `promptCatalog.workoutSystemPrompt()` wird ohne `locale`-Argument aufgerufen, was auf den Default `"de"` zurückfällt. Das `{locale}`-Substitutionssystem im `AiPromptCatalog` ist damit für die Workout-Generierung komplett außer Betrieb. Zukünftige Internationalisierung (EN, FR, etc.) erfordert eine Änderung an `WorkoutAiUseCase.invoke()`, was aktuell nicht erkennbar ist. `invoke()` hätte einen `locale: String`-Parameter oder sollte ihn aus `SettingsRepository` lesen — konsistent mit dem per-Provider-Muster (Zeilen 45-49).

**Fix:** `locale` als Parameter hinzufügen oder aus dem `SettingsRepository` lesen:

```kotlin
val locale = settingsRepository.appLocale.first()  // falls vorhanden, sonst:
val systemPrompt = promptCatalog.workoutSystemPrompt(locale = "de")  // explizit dokumentieren
```

---

_Reviewed: 2026-05-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

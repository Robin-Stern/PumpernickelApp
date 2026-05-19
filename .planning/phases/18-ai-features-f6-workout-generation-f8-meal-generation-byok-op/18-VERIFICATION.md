---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
verified: 2026-05-19T12:00:00Z
status: human_needed
score: 11/13 must-haves verified
overrides_applied: 0
human_verification:
  - test: "F6 Workout-Generation Endtoend-Flow auf Android"
    expected: "Generieren-Button öffnet Skelett, Vorschau-Sheet erscheint mit Template, Alle speichern persistiert es in TemplateList, Abbrechen kehrt zur Form zurück"
    why_human: "Erfordert echten LLM-API-Aufruf und Live-Android-Build; Netzwerk-Timeouts, tatsächliche LLM-Antwort und NavController-Pop-Verhalten sind nicht rein per grep verifizierbar"
  - test: "F8 Meal-Generation Endtoend-Flow auf Android"
    expected: "Restliche Makros werden berechnet, Generieren ruft LLM auf, Preview-Sheet zeigt Zutaten + Schritte + Makro-Fit-Indikator, Speichern persistiert Rezept in Rezeptliste"
    why_human: "Erfordert echten LLM-Aufruf, befüllte ConsumptionEntry-Daten des Tages und Live-Android-Build"
  - test: "BYOK Settings — API-Schlüssel wird nicht im Klartext gespeichert"
    expected: "Kein Schlüsselwert in logcat, DataStore, SharedPreferences-Plaintext oder Crashberichten sichtbar; adb pull und grep auf Datenspeicher liefern keinen Klartext-Schlüssel"
    why_human: "Sicherheitseigenschaft erfordert manuelle Inspektion des Gerätespeichers"
---

# Phase 18: Verifikationsbericht — AI Features F6 + F8 (BYOK)

**Phasenziel:** Zwei KI-gestützte Generierungsflows mit Bring-Your-Own-Key OpenAI-kompatiblem HTTPS-Transport ausliefern. (1) Workout AI (F6): Formular → LLM → WorkoutTemplate-Repository. (2) Meal AI (F8): verbleibende Tagesmakros → LLM → Recipe-Repository. Beides auf Android mit BYOK-Einstellungsscreen und konsistenter Fehler-UX.
**Verifiziert:** 2026-05-19T12:00:00Z
**Status:** human_needed
**Re-Verifikation:** Nein — initiale Verifikation

---

## Zielerreichung

### Observable Truths (aus Plan-Frontmatter + ROADMAP.md Anforderungen)

| #  | Truth | Status | Evidenz |
|----|-------|--------|---------|
| 1  | AiWorkoutGenScreen rendert alle 6 WorkoutAiUiState-Branches (NoKey / Form / Generating / Preview / Error / Saved) mit deutschem UI-Copy | VERIFIED | `AiWorkoutGenScreen.kt` 539 Zeilen; grep zeigt alle 6 Branches in `when(state)` bestätigt |
| 2  | Sparkles-Button (AutoAwesome) in TemplateListScreen TopAppBar navigiert zu AiWorkoutGenRoute | VERIFIED | `TemplateListScreen.kt` Zeile 82: `IconButton(onClick = { navController.navigate(AiWorkoutGenRoute) })` + `AutoAwesome` icon |
| 3  | AiWorkoutGenRoute ist in MainScreen Workout-Tab NavHost registriert | VERIFIED | `MainScreen.kt` Zeile 178-179: `composable<AiWorkoutGenRoute> { AiWorkoutGenScreen(navController = workoutNavController) }` |
| 4  | Alle 5 AiError-Klassen haben eigenständigen deutschen Copy + korrekten Action-Button | VERIFIED | `describeError()` in `AiWorkoutGenScreen.kt` Zeilen 496-528: Timeout, Network, AuthOrQuota, Provider, SchemaInvalid je mit distinctem Title + Body + Action; `ErrorBody` ist `internal` und wird in `AiMealGenScreen` korrekt mitverwendet |
| 5  | AiMealGenScreen rendert alle 8 RecipeAiUiState-Branches (Loading / NoKey / RemainingExhausted / Form / Generating / Preview / Error / Saved) | VERIFIED | `AiMealGenScreen.kt` 260 Zeilen; alle 8 Branches in `when(uiState)` Zeilen 84-130 bestätigt |
| 6  | Sparkles-Button in NutritionDailyLogScreen TopAppBar navigiert zu AiMealGenRoute | VERIFIED | `NutritionDailyLogScreen.kt` Zeile 119-120: `IconButton(onClick = { navController.navigate(AiMealGenRoute) })` + `AutoAwesome` icon |
| 7  | AiMealGenRoute ist in MainScreen Nutrition-Tab NavHost registriert | VERIFIED | `MainScreen.kt` Zeile 256-257: `composable<AiMealGenRoute> { AiMealGenScreen(navController = nutritionNavController) }` |
| 8  | AiPreviewSheet (sealed AiPreviewContent) implementiert Workout- und Recipe-Branch | VERIFIED | `AiPreviewSheet.kt` 352 Zeilen; `sealed interface AiPreviewContent` mit `data class Workout` und `data class Recipe`; `workoutBody()` + `recipeBody()` beide implementiert; Zutaten, Schritte, FitsIndicatorCard mit 5 Makro-Deltas vorhanden |
| 9  | RemainingExhausted zeigt erklärenden Leerstand (REQ-AI-04 UAT #3) | VERIFIED | `AiMealGenScreen.kt` Zeile 229: `"Du hast deine Tagesziele bereits erreicht"` + verbleibende kcal angezeigt; isExhausted-Gate in `RecipeAiPreview.kt` Zeile 60: `val isExhausted: Boolean get() = kcal <= 100.0` |
| 10 | BYOK Settings: API-Schlüssel in EncryptedSharedPreferences (Android) / Keychain (iOS); kein StateFlow exponiert den Rohschlüssel | VERIFIED | `SecureKeyStore.android.kt`: `EncryptedSharedPreferences.create(...)` mit AES256; `SecureKeyStore.ios.kt`: `SecItemAdd/Update/CopyMatching/Delete`; `AiSettingsViewModel` exponiert nur `apiKeyConfigured: StateFlow<Boolean>`, niemals den Rohwert |
| 11 | WorkoutAiUseCase.invoke() schreibt niemals in die DB; commit() ist transaktional | VERIFIED | `WorkoutAiUseCase.kt` Zeile 80: `D-18-12 — Save: persist new exercises with source="AI" first...`; invoke()-Methode hat keine DB-Writes; commit() persistiert Exercises dann Templates mit `source = "AI"` |
| 12 | REQ-AI-07: Ungültige LLM-Responses werden rejected und nie in die DB geschrieben | UNCERTAIN | `validateResponse()` in beiden UseCases wirft `AiError.SchemaInvalid` vor jedem DB-Write; `workoutAiSchemaJson()` und `recipeAiSchemaJson()` geben jedoch nur `{"type":"object"}` zurück — ein minimales Schema, das keine Felder wie `name`, `exercises` etc. erzwingt. Die SUMMARY dokumentiert dies als bewusste Entscheidung. App-seitige Validierung in `validateResponse()` ist vollständig; strukturelles LLM-Enforcement ist jedoch minimal. REQ-AI-02 UAT ("schema validated against actual generations from ≥2 providers") kann nicht rein per grep verifiziert werden. |
| 13 | F6+F8 Endtoend-Flows funktionieren auf Android mit echtem LLM-Aufruf | UNCERTAIN | Alle Code-Pfade sind verdrahtet und substantiell; menschliche Endtoend-Verifikation mit echtem API-Schlüssel steht aus (Human-Verify-Checkpoints wurden mit AUTO_MODE=true automatisch approved) |

**Score:** 11/13 Truths verifiziert

---

### Pflichtartefakte

| Artefakt | Erwartet | Status | Details |
|----------|----------|--------|---------|
| `androidApp/.../AiWorkoutGenScreen.kt` | F6-Compose-Screen | VERIFIED | 539 Zeilen, alle 6 States, 5 AiError-Klassen, deutsches Copy |
| `androidApp/.../AiPreviewSheet.kt` | Sealed AiPreviewContent + beide Branches | VERIFIED | 352 Zeilen, sealed interface, Workout + Recipe bodies voll implementiert |
| `androidApp/.../AiMealGenScreen.kt` | F8-Compose-Screen | VERIFIED | 260 Zeilen, alle 8 States, ErrorBody via shared internal composable |
| `shared/.../WorkoutAiPreview.kt` | Staging-Datenklassen | VERIFIED | 57 Zeilen, WorkoutAiPreview, StagedTemplate, StagedTemplateExercise |
| `shared/.../WorkoutAiUseCase.kt` | invoke+commit, Validation | VERIFIED | 324 Zeilen, validateResponse() vollständig, source="AI" in commit() |
| `shared/.../WorkoutAiViewModel.kt` | 6 UiState-Branches, 5 Actions | VERIFIED | 316 Zeilen, sealed class WorkoutAiUiState, alle Actions implementiert |
| `shared/.../RecipeAiPreview.kt` | Staging-Typen + RemainingMacros | VERIFIED | 61 Zeilen, isExhausted-Gate, MacrosFitIndicator |
| `shared/.../RecipeAiUseCase.kt` | computeRemaining+invoke+commit | VERIFIED | 341 Zeilen, validateResponse() vollständig, source="AI" in commit() |
| `shared/.../RecipeAiViewModel.kt` | 8 UiState-Branches, 6 Actions | VERIFIED | 216 Zeilen, alle 8 States, alle 6 Actions |
| `shared/.../AiModule.kt` | Koin-Bindings für alle VMs + UseCases | VERIFIED | WorkoutAiUseCase, RecipeAiUseCase, WorkoutAiViewModel, RecipeAiViewModel + AiSettingsViewModel alle registriert |
| `shared/.../SecureKeyStore.android.kt` | EncryptedSharedPreferences | VERIFIED | AES256_SIV + AES256_GCM bestätigt |
| `shared/.../SecureKeyStore.ios.kt` | Keychain via Security-Framework | VERIFIED | SecItemAdd/Update/CopyMatching/Delete bestätigt |
| `androidApp/.../AiSettingsScreen.kt` | BYOK-Settings-Screen | VERIFIED | PasswordVisualTransformation, HTTPS-Validierung, Schlüssel speichern/löschen, Modell-Feld |
| `shared/.../resources/workout-system-prompt.md` | Versionierter Systemprompt | VERIFIED | 107 Zeilen vorhanden |
| `shared/.../resources/recipe-system-prompt.md` | Versionierter Systemprompt | VERIFIED | 177 Zeilen vorhanden |

---

### Key Link Verifikation

| Von | Zu | Via | Status | Details |
|-----|-----|-----|--------|---------|
| TemplateListScreen TopAppBar | AiWorkoutGenRoute | `IconButton.onClick navigate` | VERIFIED | Zeile 82 in TemplateListScreen.kt |
| AiWorkoutGenScreen.NoKey-Branch | AiSettingsRoute | `onOpenSettings = { navController.navigate(AiSettingsRoute) }` | VERIFIED | Zeile 102 in AiWorkoutGenScreen.kt |
| AiWorkoutGenScreen.Error(AuthOrQuota) | AiSettingsRoute | `onOpenSettings` in ErrorBody | VERIFIED | Zeile 139 in AiWorkoutGenScreen.kt |
| MainScreen Workout-Tab NavHost | AiWorkoutGenScreen | `composable<AiWorkoutGenRoute>` | VERIFIED | Zeile 178-179 in MainScreen.kt |
| NutritionDailyLogScreen TopAppBar | AiMealGenRoute | `IconButton.onClick navigate` | VERIFIED | Zeile 119 in NutritionDailyLogScreen.kt |
| AiMealGenScreen.NoKey-Branch | AiSettingsRoute | `onOpenSettings` in NoKeyBody | VERIFIED | Zeile 92 in AiMealGenScreen.kt |
| MainScreen Nutrition-Tab NavHost | AiMealGenScreen | `composable<AiMealGenRoute>` | VERIFIED | Zeile 256-257 in MainScreen.kt |
| AiPreviewSheet.Recipe-Branch | RecipePreviewBody / recipeBody() | `when(content)` dispatch | VERIFIED | Zeile 71 in AiPreviewSheet.kt: `is AiPreviewContent.Recipe -> recipeBody(content.preview)` |
| AiPreviewSheet.FitsIndicator | deltaKcalPercent / MacroFitRow | `FitsIndicatorCard` | VERIFIED | Zeile 288+ in AiPreviewSheet.kt: 5 MacroFitRow-Aufrufe |
| WorkoutAiViewModel.save() | WorkoutAiUseCase.commit() | Coroutine-Aufruf | VERIFIED | WorkoutAiViewModel.kt Zeile 216: `val ids = useCase.commit(preview.preview)` |
| RecipeAiViewModel.generate() | RecipeAiUseCase.invoke() | Coroutine-Aufruf | VERIFIED | RecipeAiViewModel.kt Zeile 84: `useCase.invoke(form.remaining)` |
| AiSettingsRoute in Workout-Tab | AiSettingsScreen | `composable<AiSettingsRoute>` | VERIFIED | MainScreen.kt Zeile 175-176 |
| AiSettingsRoute in Nutrition-Tab | AiSettingsScreen | `composable<AiSettingsRoute>` | VERIFIED | MainScreen.kt Zeile 259-260 |

---

### Data-Flow-Trace (Level 4)

| Artefakt | Datenvariable | Quelle | Produziert Echte Daten | Status |
|----------|--------------|--------|----------------------|--------|
| AiWorkoutGenScreen | `uiState` | `WorkoutAiViewModel._uiState.asStateFlow()` | Ja — VM liest SecureKeyStore + LLM-Aufruf via WorkoutAiUseCase | FLOWING |
| AiMealGenScreen | `uiState` | `RecipeAiViewModel._uiState.asStateFlow()` | Ja — VM liest ConsumptionEntry + NutritionGoals + LLM-Aufruf via RecipeAiUseCase | FLOWING |
| AiPreviewSheet (Workout) | `preview.templates` | `WorkoutAiPreview` aus LLM-Antwort, nie aus DB | Ja — StagedTemplate-Liste aus LLM-JSON nach validateResponse() | FLOWING |
| AiPreviewSheet (Recipe) | `preview.recipe` + `preview.fitsIndicator` | `RecipeAiPreview` aus LLM-Antwort | Ja — StagedRecipe aus LLM-JSON nach validateResponse() + computeFits() | FLOWING |
| AiSettingsScreen | `providerPreset`, `baseUrl`, `model`, `apiKeyConfigured` | `AiSettingsViewModel` ← SecureKeyStore + SettingsRepository | Ja — alle 4 StateFlows von echten Datenspeichern | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: Nicht ausführbar ohne laufenden Android-Build und echten LLM-API-Schlüssel. Alle prüfbaren Verhaltenspfade wurden per statischer Code-Analyse abgedeckt. Endtoend-Verhalten in Human Verification Required dokumentiert.

---

### Anforderungsabdeckung

| Anforderung | Quell-Plan | Beschreibung | Status | Evidenz |
|------------|-----------|-------------|--------|---------|
| REQ-AI-01 | Plan 06, 07 | User kann WorkoutTemplate(s) aus Formular generieren | VERIFIED | WorkoutAiUseCase.invoke() + commit(); AiWorkoutGenScreen Form-Branch; splitStyle.templateCount-Mapping für Multi-Template |
| REQ-AI-02 | Plan 03, 06 | JSON-Schema regelt LLM-Workout-Output | PARTIAL | `WorkoutAiResponseDto.kt` serialisiert alle Felder; `validateResponse()` erzwingt vollständige Validierung app-seitig; `workoutAiSchemaJson()` gibt nur `{"type":"object"}` zurück — LLM-seitiges Schema-Enforcement minimal aber SUMMARY-dokumentiert als bewusste Entscheidung (D-18-14 Fallback-Strategie) |
| REQ-AI-03 | Plan 03 | Systemprompt versioniert in Repo | VERIFIED | `workout-system-prompt.md` (107 Zeilen) + `recipe-system-prompt.md` (177 Zeilen) in `commonMain/resources`; geladen via `AiPromptCatalog.workoutSystemPrompt()` / `.recipeSystemPrompt()` |
| REQ-AI-04 | Plan 08, 09 | User kann Rezept für verbleibende Tagesmakros generieren | VERIFIED | `RecipeAiUseCase.computeRemaining()` + `isExhausted`-Gate; `MacrosFitIndicator` mit ±10%-Farbkodierung; RemainingExhaustedBody für Gate-Erklärung; commit() persistiert via Recipe-Repository |
| REQ-AI-05 | Plan 03, 08 | Rezept-Systemprompt + JSON-Schema in Repo | PARTIAL | `recipe-system-prompt.md` vorhanden; `recipeAiSchemaJson()` gibt `{"type":"object"}` zurück — analog zu REQ-AI-02 |
| REQ-AI-06 | Plan 04, 05 | BYOK: OpenAI-kompatibler HTTPS-Anbieter, sicherer Key-Store | VERIFIED | EncryptedSharedPreferences (Android) + Keychain (iOS); `apiKeyConfigured: StateFlow<Boolean>` nie Rohschlüssel; HTTPS-Validierung in AiSettingsScreen + OpenAICompatibleClient |
| REQ-AI-07 | Plan 06, 08 | Schema-Validierung vor jedem DB-Write | VERIFIED | `validateResponse()` in WorkoutAiUseCase + RecipeAiUseCase wirft `AiError.SchemaInvalid` vor commit(); invoke() schreibt niemals in DB |
| REQ-AI-08 | Plan 02, 07, 09 | Timeout + Größenlimit + 4xx/5xx/Refusal Fehler-UX | VERIFIED | `OpenAICompatibleClient`: requestTimeoutMillis=600s, socketTimeoutMillis=120s, 64KB-Cap; `AiError.fromThrowable()` mapped Timeout/Network/ClientRequest/ServerResponse; `describeError()` in AiWorkoutGenScreen mit 5 eigenständigen deutschen Error-Messages |

---

### Anti-Pattern-Befunde

| Datei | Zeile | Pattern | Schwere | Auswirkung |
|-------|-------|---------|---------|------------|
| `AiWorkoutGenScreen.kt` | 284 | `ModalBottomSheet(onDismissRequest = { /* dismiss only via buttons */ })` — no-op | WARNUNG | Nutzer kann Sheet per Zurück-Geste oder Scrim-Tap nicht schließen; nur Button-Interaktion möglich. Im Code-Review-Bericht als CR-01 (Critical) dokumentiert |
| `AiMealGenScreen.kt` | 251 | `ModalBottomSheet(onDismissRequest = { /* dismiss only via buttons */ })` — no-op | WARNUNG | Identisches Problem wie AiWorkoutGenScreen (CR-01 im Review-Bericht) |
| `AiWorkoutGenScreen.kt` | 77-80 | `yield()` zwischen `_uiState.value = Saved` und `_uiState.value = defaultForm` — potenzielle Race-Condition | WARNUNG | Pop-Signal kann verpasst werden wenn Compose-Recomposition nicht vor `yield()`-Resume geplant wird (WR-01 im Review-Bericht) |
| `AiMealGenScreen.kt` | 57-59 | `LaunchedEffect(Unit)` für `viewModel.onAppearRefresh()` — feuert nur einmal | WARNUNG | Veraltete Makros bei Re-Navigation zum Screen (WR-02 im Review-Bericht) |

> Hinweis: Diese Anti-Patterns wurden bereits in `18-REVIEW.md` dokumentiert (reviewed: 2026-05-19T08:37:44Z). Die Review klassifiziert CR-01 als Critical. Da jedoch alle Geschäftslogik-Pfade verdrahtet und substantiell sind und kein Pfad zur völligen Funktionsunfähigkeit führt (Nutzer kann immer die Buttons verwenden), wird hier WARNUNG statt BLOCKER vergeben — die Endtoend-Funktionalität ist vorhanden, aber das UX-Verhalten bei Zurück-Geste ist fehlerhaft.

---

### Menschliche Verifikation erforderlich

#### 1. F6 Workout-Generation Endtoend auf Android

**Test:** Android-Build installieren (`./gradlew :androidApp:installDebug`). Gültigen OpenAI/Together-AI-Schlüssel in den KI-Einstellungen setzen. Workout-Tab → Sparkles-Icon. 2-3 Muskeln auswählen, Übungsanzahl=5, Split=Einzeln. „Generieren" tippen.
**Erwartet:** Skeleton mit 5 Platzhalter-Zeilen + Abbrechen-Button erscheint; nach ~10s Vorschau-Sheet mit Templatename + Übungslist + Sets×Reps×Pause; „Alle speichern" navigiert zurück zur TemplateList mit dem neuen Template. PPL-Split produziert 3 Templates.
**Warum menschlich:** Erfordert echten LLM-Aufruf, Live-Android-Build und NavController-Pop-Beobachtung.

#### 2. F8 Meal-Generation Endtoend auf Android

**Test:** Einige Mahlzeiten im Nutrition-Tab eintragen (sodass verbleibende kcal >100 aber <Tagesziel). Nutrition-Tab → Sparkles-Icon. 5 verbleibende Makros prüfen. „Restliche Makros füllen" tippen.
**Erwartet:** Skeleton erscheint, danach Vorschau-Sheet mit Rezeptname, Zutaten (Gramm-Angaben), Zubereitungsschritte und Makro-Fit-Indikator mit ±%-Deltas für alle 5 Makros. „Alle speichern" öffnet das Rezept in der Rezeptliste.
**Warum menschlich:** Erfordert befüllte Tagesdaten, echten LLM-Aufruf und Datenbankpersistenz-Verifikation.

#### 3. BYOK-Sicherheit — Schlüssel nicht im Klartext

**Test:** Schlüssel setzen, App schließen. Via `adb pull /data/data/com.pumpernickel/shared_prefs/ /tmp/prefs/` und grep auf Schlüsselwert. Alternativ: logcat auf Schlüsselwert filtern während Generierung.
**Erwartet:** Schlüssel nicht im Klartext in Shared Preferences, nicht in Logcat. EncryptedSharedPreferences verhindert Klartext-Lesbarkeit.
**Warum menschlich:** Sicherheitseigenschaft; erfordert Gerätedatei-Inspektion und Laufzeit-Loganalyse.

---

### Lücken-Zusammenfassung

Keine BLOCKER-Lücken. Alle Must-Haves der Phase sind entweder VERIFIED oder UNCERTAIN mit bekanntem und dokumentiertem Grund:

- **REQ-AI-02/REQ-AI-05 (JSON-Schema minimal):** `workoutAiSchemaJson()` und `recipeAiSchemaJson()` geben nur `{"type":"object"}` zurück. App-seitige Validierung (`validateResponse()`) ist vollständig und korrekt — das LLM erhält jedoch kein strukturiertes Schema. Dies ist eine bewusste, in Plan 06/08 SUMMARY dokumentierte Entscheidung (D-18-14: json_object-Fallback erfordert minimales Schema). REQ-AI-02 UAT-Kriterium "validated against ≥2 providers" kann nur durch Laufzeit-Tests bestätigt werden.

- **Human-Verify-Checkpoints wurden mit AUTO_MODE=true auto-approved:** Die Plans 05, 07 und 09 enthielten blocking `checkpoint:human-verify`-Tasks. Diese wurden laut SUMMARY.md automatisch genehmigt. Die eigentliche Nutzer-UAT steht für Android aus.

---

_Verifiziert: 2026-05-19T12:00:00Z_
_Verifier: Claude (gsd-verifier)_

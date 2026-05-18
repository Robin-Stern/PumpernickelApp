---
phase: 260518-eny
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - iosApp/iosApp/Views/AI/AIWorkoutGenView.swift
autonomous: false
requirements:
  - BUG-260518-eny-01
tags:
  - ios
  - ai
  - workout
  - state-machine

must_haves:
  truths:
    - "Nach dem Speichern eines generierten Workouts kann der iOS-User direkt im selben App-Run ein zweites Workout generieren (ohne App-Neustart)."
    - "Beim erneuten Öffnen des KI-Workout-Screens wird IMMER die Form-Ansicht gezeigt — kein grünes Check-Symbol und keine sofortige Auto-Dismiss-Schleife."
    - "Der Save-Erfolg navigiert weiterhin zurück zur Template-Liste (Bestehender UX-Flow bleibt erhalten)."
    - "Android-Verhalten bleibt unverändert (LaunchedEffect-basiertes pop funktioniert weiter, da die VM-Reset-Logik auch dort sauber durchläuft)."
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt"
      provides: "Reset-Methode + one-shot SavedEvent SharedFlow + Auto-Reset im save()-Pfad"
      contains: "fun reset()"
    - path: "iosApp/iosApp/Views/AI/AIWorkoutGenView.swift"
      provides: "savedEvent-Observer triggert dismiss(); onAppear ruft reset(); Saved-State-Branch entfernt (oder defensiv neutral gerendert)"
      contains: "viewModel.reset()"
  key_links:
    - from: "WorkoutAiViewModel.save()"
      to: "_savedEvent.tryEmit(Unit) + State zurück auf defaultForm"
      via: "Innerhalb des try-Erfolgspfades nach useCase.commit"
      pattern: "_savedEvent\\.tryEmit"
    - from: "AIWorkoutGenView .task"
      to: "viewModel.savedEvent -> dismiss()"
      via: "asyncSequence(for: viewModel.savedEvent)"
      pattern: "observeSavedEvent"
    - from: "AIWorkoutGenView .onAppear"
      to: "viewModel.reset()"
      via: "SwiftUI lifecycle on push"
      pattern: "viewModel\\.reset\\(\\)"
---

<objective>
Behebung iOS-only Bug: Nach dem Speichern eines KI-generierten Workouts ist der KI-Workout-Screen "verbrannt" — beim erneuten Aufrufen zeigt er nur das grüne Check-Symbol (`SavedBody`) und dispatcht sofort `dismiss()`. Effekt: User kann ohne App-Neustart kein zweites Workout generieren.

Purpose: AI-Workout-Generierung wird im selben App-Run wiederholt nutzbar — kritisch für iterative UX (User probiert mehrere Generierungen aus, vergleicht Ergebnisse).

Output:
- `WorkoutAiViewModel` exponiert one-shot `savedEvent: SharedFlow<Unit>` und eine `reset()`-Methode (analog zu `RecipeCreationViewModel.reset()`).
- `WorkoutAiViewModel.save()` emittiert das Event UND setzt den State zurück auf `defaultForm`, sodass der `Saved` UI-Branch nicht mehr persistent ist.
- `AIWorkoutGenView` observiert `savedEvent` für `dismiss()` und ruft `reset()` in `.onAppear` — dieselbe bewährte Pattern wie `NutritionRecipeCreationView`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md

# Quelldateien — VM + View
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt
@iosApp/iosApp/Views/AI/AIWorkoutGenView.swift

# Reference-Implementierung des "reset + savedEvent"-Patterns (Vorbild)
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt
@iosApp/iosApp/Views/Nutrition/NutritionRecipeCreationView.swift

# Android-Pendant für Cross-Platform-Verifikation (KEINE Änderung dort nötig)
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
</context>

<bug_analysis>
**Bug-Reproduktion (iOS, gemeldet):**
1. Templates -> sparkles-Icon -> AIWorkoutGenView push
2. Muskeln wählen, Generieren, Save in PreviewSheet
3. View pop'd zurück zur Template-Liste (korrekt)
4. Erneut sparkles -> AIWorkoutGenView push
5. **Bug:** Sofort grüner Check + pop zurück zur Template-Liste, ohne dass der User irgendetwas tun kann.

**Root Cause — empirische Evidenz aus der Codebase:**

Die `WorkoutAiViewModel`-Instanz, die `WorkoutAiKoinHelper().getWorkoutAiViewModel()` liefert, behält ihren `_uiState` zwischen Navigationen. Egal ob das aus Koin's `viewModel { }`-Binding ohne `ViewModelStoreOwner` auf iOS singleton-artiges Verhalten ist oder ob `AiGenerationManager` (echter `single`-Scope, siehe `AiModule.kt:47`) den State über seinen `state.collect`-Subscriber zurückspielt — Fakt ist: **`WorkoutAiUiState.Saved` (eine terminale `data class`) bleibt sichtbar, wenn der User erneut zum Screen navigiert.**

In `AIWorkoutGenView.swift:56-58`:
```swift
} else if state is WorkoutAiUiState.Saved {
    SavedBody().onAppear { dismiss() }
}
```
Der `Saved`-Branch nutzt `Saved` als persistente Terminal-Anzeige UND als Auto-Dismiss-Trigger. Wenn der State noch `Saved` ist, feuert `onAppear` sofort `dismiss()` und die View ist erneut verbrannt.

**Vergleich Android (warum dort kein Bug):** `AiWorkoutGenScreen.kt:77-81` nutzt `LaunchedEffect(uiState)` mit `popBackStack()`. Nach `popBackStack()` wird die NavBackStackEntry zerstört → `koinViewModel()` würde beim erneuten Push eine NEUE VM-Instanz scopen. Selbst wenn die VM-Singleton-Annahme auch dort gälte, würde `LaunchedEffect(uiState)` beim Re-Compose nur dann re-feuern, wenn `uiState` SICH ÄNDERT — eine bereits gesetzte `Saved`-Referenz triggert keinen erneuten Effekt. (Zur Sicherheit: dieselbe Reset-Logik in der VM macht Android idempotenter, aber es ist NICHT die Bug-Quelle.)

**Lösungs-Pattern (bewährt in `RecipeCreationViewModel`):**

`RecipeCreationViewModel.kt:67-69` + `:78-83`:
```kotlin
private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

fun reset() {
    viewModelScope.launch {
        _foods.value = repository.loadFoods()
        _creationState.value = RecipeCreationUiState(searchResults = recentFoods())
    }
}
```

`NutritionRecipeCreationView.swift:154-166`:
```swift
.task {
    await withTaskGroup(of: Void.self) { group in
        group.addTask { await observeCreationState() }
        group.addTask { await observeSavedEvent() }   // one-shot dismiss trigger
    }
}
.onAppear {
    if let recipe = editingRecipe { viewModel.loadRecipe(...) }
    else { viewModel.reset() }                          // immer frische Form
}
```

Beides muss `WorkoutAiViewModel` und `AIWorkoutGenView` analog adoptieren.
</bug_analysis>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: WorkoutAiViewModel — one-shot savedEvent + reset() + auto-rollback aus Saved</name>
  <files>shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt</files>
  <behavior>
    - Nach erfolgreicher `save()`-Operation: `_savedEvent.tryEmit(Unit)` UND `_uiState.value = defaultForm` (statt persistent `Saved`).
    - `reset()`-Funktion setzt `_uiState.value = defaultForm` und ruft `generationManager.clear()` defensiv (Idempotenz).
    - `savedEvent` ist `SharedFlow<Unit>` (replay=0, extraBufferCapacity=1) — exportiert via `@NativeCoroutines`.
    - Bestehende `WorkoutAiUiState.Saved`-Klasse bleibt erhalten (für mögliche zukünftige Use-Cases / Test-Kompatibilität), wird aber im normalen Save-Flow NICHT mehr emittiert. Alternativ: komplett entfernen — siehe Implementation-Hinweis.
    - Android-Kompatibilität: `AiWorkoutGenScreen.kt:77-81` reagiert weiterhin auf `LaunchedEffect(uiState)`. Da `save()` jetzt direkt auf `defaultForm` zurückspringt statt auf `Saved`, geht der bestehende Android-Pop verloren. **Deshalb muss Android-seitig ebenfalls auf `savedEvent` umgestellt werden** — siehe Task 2's Android-Hinweis (kein eigener Android-Task nötig wenn man `LaunchedEffect` durch `LaunchedEffect(Unit) { savedEvent.collect { popBackStack() } }` ersetzt; das gehört aber NICHT in den Scope dieses Bugfixes laut Constraints).
    - **Pragmatischer Kompromiss zur Vermeidung von Android-Regression:** `save()` emittiert `savedEvent`, setzt `_uiState.value = WorkoutAiUiState.Saved(ids)` (Android-Pop bleibt funktionsfähig) UND führt UNMITTELBAR DANACH in einem `viewModelScope.launch` einen kurzen yield + `_uiState.value = defaultForm` aus. Dieser Mikro-Delay (ein Coroutine-yield reicht aus, kein `delay()` mit Zeit) garantiert, dass beide Plattform-Observer `Saved` einmal sehen, der State danach aber wieder auf `Form` steht und beim erneuten Subscribe NICHT mehr `Saved` zeigt.
    - Alternative (sauberer aber Android-Touch): `save()` setzt nur `defaultForm`, `savedEvent` emittiert; Android-Screen wird in einem Mini-Edit auf `savedEvent.collect { popBackStack() }` umgebaut. Diese Variante ist explizit erlaubt durch Constraint: "If both Android + iOS share the ViewModel: the fix is in commonMain (state reset) + iOS-View".

    **Entscheidung (Claude's Discretion innerhalb dieses Plans):** Variante "Alternative sauber" — `save()` setzt direkt `defaultForm`, emittiert `savedEvent`. Android-Screen wird MIT angepasst (eine Zeile `LaunchedEffect` ersetzt). Das ist konsistenter und vermeidet die yield-Hack-Stelle.
  </behavior>
  <action>
    1. Imports ergänzen:
       - `kotlinx.coroutines.flow.MutableSharedFlow`
       - `kotlinx.coroutines.flow.SharedFlow`
       - `kotlinx.coroutines.flow.asSharedFlow`
       - `com.rickclephas.kmp.nativecoroutines.NativeCoroutines` (für one-shot Event-Export)

    2. Nach `streamingText`-Feld einen neuen one-shot Event Flow hinzufügen:
       ```kotlin
       private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

       @NativeCoroutines
       val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()
       ```

    3. `save()` umschreiben: nach erfolgreichem `useCase.commit(preview.preview)` NICHT mehr `WorkoutAiUiState.Saved(...)` setzen. Stattdessen:
       ```kotlin
       fun save() {
           val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
           viewModelScope.launch {
               try {
                   useCase.commit(preview.preview)               // Rückgabewert (ids) wird nicht mehr im UI-State benötigt
                   generationManager.clear()                      // Globalen Manager idle setzen
                   _uiState.value = defaultForm                   // VM-State zurück auf Form (kein Saved-Branch mehr aktiv)
                   _savedEvent.tryEmit(Unit)                      // One-shot Signal an iOS-View / Android-Screen
               } catch (ce: CancellationException) {
                   throw ce
               } catch (ai: AiError) {
                   _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = preview.originatingForm)
               } catch (t: Throwable) {
                   _uiState.value = WorkoutAiUiState.Error(
                       AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                       originatingForm = preview.originatingForm
                   )
               }
           }
       }
       ```

    4. Neue `reset()`-Methode hinzufügen (analog `RecipeCreationViewModel.reset()`):
       ```kotlin
       fun reset() {
           // Idempotent — vom iOS .onAppear bei jedem Push aufgerufen, um sicherzustellen,
           // dass ein "verbrannter" State (Saved / Error / Preview-Geister) sauber auf Form zurückgesetzt wird.
           // Bricht keine laufende Generierung ab: wenn aktuell Generating, NICHT eingreifen.
           if (_uiState.value is WorkoutAiUiState.Generating) return
           _uiState.value = defaultForm
       }
       ```
       **Wichtig:** `reset()` ruft NICHT `generationManager.clear()` auf — sonst würde ein laufender Background-Job (Phase 19 BackgroundTaskManager) abgebrochen, wenn der User während einer parallelen Generation den Screen erneut öffnet. Der `Generating`-Guard schützt davor.

    5. `WorkoutAiUiState.Saved` data class: **behalten** für API-Stabilität (Swift-Header exportiert sie bereits — Entfernen würde Framework-Header-Diff erzwingen). Sie wird nur nicht mehr emittiert. Kommentar hinzufügen:
       ```kotlin
       /**
        * Deprecated emission path — `save()` emittiert ab 260518-eny das one-shot `savedEvent`
        * und setzt den UI-State direkt zurück auf `Form`. Diese data class bleibt aus
        * Binary-Stabilitätsgründen erhalten (Shared-Framework Headers).
        */
       data class Saved(val templateIds: List<Long>) : WorkoutAiUiState()
       ```

    6. **Android-Begleitänderung (in dieser Datei nicht, aber in derselben Plan-Wave):** Aus den Constraints („If both Android + iOS share the ViewModel: the fix is in commonMain (state reset) + iOS-View") → Android-Screen ist NICHT Teil von `files_modified`. Da `save()` nicht mehr `Saved` setzt, würde der Android-`LaunchedEffect(uiState)` keinen Pop mehr machen. **Daher: Android-Pop muss in dieser Task ebenfalls fließend gemacht werden — am sichersten OHNE Android-Datei zu editieren, indem `save()` zusätzlich `WorkoutAiUiState.Saved` einmal emittiert und dann via `viewModelScope.launch { yield(); _uiState.value = defaultForm }` zurücksetzt.**

       **Finale Entscheidung (revidiert):** Wir wählen den minimal-invasiven Pfad — `save()` emittiert kurz `Saved` (Android-Pop bleibt erhalten), dann nach einem `yield()` zurück auf `defaultForm`. Plus `savedEvent` für iOS. Der Saved-State ist also nur für einen einzigen Coroutine-Tick sichtbar:

       ```kotlin
       fun save() {
           val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
           viewModelScope.launch {
               try {
                   val ids = useCase.commit(preview.preview)
                   generationManager.clear()
                   // Android: LaunchedEffect(uiState) sieht Saved -> popBackStack()
                   _uiState.value = WorkoutAiUiState.Saved(templateIds = ids)
                   // iOS: savedEvent triggert dismiss()
                   _savedEvent.tryEmit(Unit)
                   // Beide Plattformen: nach einem Tick zurück auf Form, damit Re-Entry sauber ist
                   kotlinx.coroutines.yield()
                   _uiState.value = defaultForm
               } catch (ce: CancellationException) {
                   throw ce
               } catch (ai: AiError) {
                   _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = preview.originatingForm)
               } catch (t: Throwable) {
                   _uiState.value = WorkoutAiUiState.Error(
                       AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                       originatingForm = preview.originatingForm
                   )
               }
           }
       }
       ```

       Imports: zusätzlich `kotlinx.coroutines.yield`.

    7. Keine Änderung am `init`-Block / `generationManager.state.collect`-Subscriber: dessen `Idle`-Branch greift nur wenn `current is Generating`, also harmlos in unserem Fall.

    Zusammenfassung der Änderung in dieser Datei: 1 Import-Block erweitert, 1 SharedFlow-Feld hinzugefügt (+ Property + NativeCoroutines-Export), `save()` erweitert um `_savedEvent.tryEmit` + `yield` + Rückfall auf `defaultForm`, neue `reset()`-Funktion, Doc-Kommentar an `Saved` data class.
  </action>
  <verify>
    <automated>cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp &amp;&amp; ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 --no-daemon 2>&amp;1 | tail -40</automated>
  </verify>
  <done>
    - `WorkoutAiViewModel.kt` kompiliert für iOS Simulator + iOS Arm64.
    - `grep -n "savedEvent\|fun reset" shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` zeigt: das neue Feld + Funktion + `tryEmit` im save-Pfad.
    - Bestehende Tests (falls vorhanden) für `WorkoutAiViewModel` brechen nicht: `./gradlew :shared:iosSimulatorArm64Test --tests "*WorkoutAi*"` läuft grün ODER (wenn keine Tests existieren) der Befehl meldet "no tests found".
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: AIWorkoutGenView — savedEvent observer + reset() in .onAppear + defensiver Saved-Branch</name>
  <files>iosApp/iosApp/Views/AI/AIWorkoutGenView.swift</files>
  <behavior>
    - `.task { ... }` observiert zusätzlich zur `uiStateFlow` und `streamingTextFlow` jetzt auch `savedEvent` — beim Empfang: `dismiss()`.
    - `.onAppear` ruft `viewModel.reset()` auf, damit ein evtl. „verbrannter" Saved/Preview-State sauber zurückspringt, bevor die View etwas zeichnet.
    - Der `Saved`-Branch im `content(for:)` rendert defensiv eine neutrale Ladeanzeige (`ProgressView()`), aber triggert NICHT mehr `dismiss()` (das macht jetzt `savedEvent`). Damit ist `Saved` als State idempotent harmlos, falls er kurz aufflackert.
    - Existierende `observeUiState()` und `observeStreaming()` bleiben unverändert.
  </behavior>
  <action>
    1. `.task { await observeUiState() }` und `.task { await observeStreaming() }` ersetzen durch einen einzigen `.task`-Block mit `withTaskGroup` (Pattern aus `NutritionRecipeCreationView.swift:154-159`):
       ```swift
       .task {
           await withTaskGroup(of: Void.self) { group in
               group.addTask { await observeUiState() }
               group.addTask { await observeStreaming() }
               group.addTask { await observeSavedEvent() }
           }
       }
       ```

    2. `.onAppear`-Modifier ergänzen (direkt nach `.task`):
       ```swift
       .onAppear {
           viewModel.reset()
       }
       ```

    3. Neue Observer-Funktion innerhalb `AIWorkoutGenView` ergänzen:
       ```swift
       private func observeSavedEvent() async {
           do {
               for try await _ in asyncSequence(for: viewModel.savedEvent) {
                   dismiss()
               }
           } catch {
               print("AIWorkoutGenView savedEvent observation error: \(error)")
           }
       }
       ```

    4. `content(for:)`: den `Saved`-Branch entschärfen — kein `dismiss()` mehr aus `onAppear`. Stattdessen eine neutrale ProgressView rendern (kurzes Aufflackern bei race conditions ist harmlos):
       ```swift
       } else if state is WorkoutAiUiState.Saved {
           // Saved ist seit 260518-eny ein transient state, der nur einen Coroutine-Tick lang
           // sichtbar ist (siehe WorkoutAiViewModel.save). Der eigentliche dismiss-Trigger
           // kommt aus dem one-shot savedEvent in observeSavedEvent(). Hier nur eine neutrale
           // ProgressView, damit nichts visuell „springt" — KEIN dismiss() in .onAppear!
           ProgressView()
               .frame(maxWidth: .infinity, maxHeight: .infinity)
       }
       ```

    5. `private let viewModel = WorkoutAiKoinHelper().getWorkoutAiViewModel()` bleibt unverändert — VM-Instanz wird weiterhin via Helper geholt; durch `reset()` in `.onAppear` ist die Singleton-Frage egal.
  </action>
  <verify>
    <automated>cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/iosApp &amp;&amp; xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO 2>&amp;1 | tail -60</automated>
  </verify>
  <done>
    - iOS-App kompiliert ohne Fehler (xcodebuild liefert `BUILD SUCCEEDED`).
    - `grep -n "observeSavedEvent\|viewModel.reset()" iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` zeigt beide Stellen vorhanden.
    - `grep -n "SavedBody().onAppear" iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` liefert KEIN Ergebnis mehr (alter Auto-Dismiss-Pfad entfernt).
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Manueller iOS-Smoke-Test — zweite Generierung in derselben Session</name>
  <what-built>
    Behebung des „verbrannten KI-Workout-Screen" Bugs durch Umstellung von `Saved`-Terminalstate auf one-shot `savedEvent` + idempotentes `reset()` auf View-Appearance.
  </what-built>
  <how-to-verify>
    Auf einem iOS-Simulator oder echtem Gerät:

    1. App starten, zum Workout-Tab navigieren.
    2. Sparkles-Icon (oben rechts in der Template-Liste) antippen → AIWorkoutGenView öffnet sich mit leerem Formular.
    3. 2-3 Zielmuskeln auswählen (z.B. Brust, Trizeps), Anzahl Übungen bei 5 lassen, „Generieren" antippen.
    4. Warten bis die Preview-Sheet erscheint, dort „Speichern" antippen.
    5. **Erwartung:** Sheet schließt, View pop'd zurück zur Template-Liste, neues Workout-Template ist sichtbar in der Liste.
    6. **Direkt erneut** Sparkles-Icon antippen → AIWorkoutGenView öffnet sich.
    7. **Kritischer Check:** Es wird das LEERE FORMULAR angezeigt, KEIN grüner Check, KEIN automatischer Pop zurück.
    8. Andere Muskeln wählen, erneut „Generieren" antippen → Generierung läuft normal → Save funktioniert erneut.
    9. Schritte 6-8 ein drittes Mal wiederholen, um sicherzustellen, dass es kein einmaliges Phänomen ist.

    **Zusätzlich auf Android verifizieren (Cross-Platform-Regression-Check):**
    10. Android-App starten, dieselben Schritte 2-8 durchführen.
    11. **Erwartung:** Android-Verhalten identisch — Save → pop → erneut Sparkles → Form sauber → zweite Generierung möglich. Wenn Android nach Save NICHT mehr popt: Regression durch Task 1's `save()`-Änderung; in dem Fall sofort melden.

    Wenn alles passt: "approved" + Notiz über Android-Verifikation.
  </how-to-verify>
  <resume-signal>Type "approved" or describe issues (insb. wenn Android jetzt nicht mehr popt, oder wenn iOS Auto-Dismiss anders fehlschlägt).</resume-signal>
</task>

</tasks>

<verification>
**Automatische Verifikation:**
- `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64` grün (Kotlin-Side).
- `xcodebuild ... build` grün (Swift-Side).
- `grep -rn "WorkoutAiUiState.Saved" iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` zeigt KEINE `onAppear { dismiss() }`-Stelle mehr.

**Manuelle Verifikation:** siehe Task 3.

**Regression-Risiko-Check:**
- `RecipeAiViewModel` hat dasselbe `Saved`-Terminal-State-Muster (`RecipeAiViewModel.kt:215 object Saved : RecipeAiUiState()`). Ein analoger Bug ist im AI-Rezept-Generator wahrscheinlich. **Aus dem Scope dieses Quick-Tasks explizit ausgenommen** (Bug-Report nur für Workout). Falls Task 3 den iOS-Recipe-Flow zufällig mit-testet und derselbe Bug auftaucht: separat als neuen `/gsd:quick` öffnen.
</verification>

<success_criteria>
1. iOS-User kann nach erfolgreichem Workout-Save direkt im selben App-Run ein zweites Workout generieren — kein App-Neustart nötig.
2. Beim erneuten Push der AIWorkoutGenView wird IMMER das leere Form angezeigt (oder das ausgewählte Form, falls eine Generierung gerade läuft — Generating-Guard in `reset()`).
3. Android-Verhalten unverändert: Save → popBackStack → Re-Entry zeigt Form.
4. Keine neue Compile-Warning, keine neue Test-Regression.
</success_criteria>

<output>
After completion, create `.planning/quick/260518-eny-ios-ai-workout-generation-state-reset-na/260518-eny-SUMMARY.md` mit:
- Root-Cause-Beschreibung (Saved-Terminal-State + Koin-Singleton-Verhalten auf iOS)
- Diff-Zusammenfassung (savedEvent + reset() + yield-Rollback in VM, observeSavedEvent + onAppear-reset in View)
- Test-Resultat (manuelle iOS- + Android-Verifikation)
- Hinweis auf potenziellen analogen Bug in `RecipeAiViewModel` (für späteren Quick-Task)
</output>

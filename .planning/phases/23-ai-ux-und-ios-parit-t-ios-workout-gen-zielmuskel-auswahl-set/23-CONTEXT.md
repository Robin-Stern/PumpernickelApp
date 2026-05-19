# Phase 23: AI UX und iOS-Parität - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Zwei unabhängige Verbesserungen der AI-UX auf beiden Plattformen:

1. **Sets-Stepper im Workout-Gen-Formular** — neues Pflichtfeld `setsPerExercise` (1–6, Default 3) wird in `WorkoutAiForm` + ViewModel + UI (iOS + Android) ergänzt und per Platzhalter an den System-Prompt übergeben.

2. **Background-Mini-Bar** — globaler, persistenter Typing-Indicator direkt über der Tab-Bar, der erscheint sobald eine AI-Generation läuft (egal auf welchem Screen). Tap navigiert zurück zum Gen-Screen. Nach Fertigstellung wechselt die Bar zu "Workout bereit — tippen zum Ansehen". Gilt für Workout-KI und Rezept-KI.

**In scope:**
- `WorkoutAiForm` um `setsPerExercise: Int` erweitern
- `WorkoutAiViewModel` + `WorkoutAiUiState.Form` entsprechend anpassen
- `workout-system-prompt.md` um `setsPerExercise`-Platzhalter ergänzen (minimale Änderung)
- Sets-Stepper UI: Android (`+/-` IconButtons wie `exerciseCount`), iOS (`Stepper`-Widget wie `exerciseCount`)
- Mini-Bar Infrastruktur: `AiGenerationManager.state` global observieren (in App-Root/MainScreen)
- Mini-Bar UI: Android (Composable direkt über `BottomNavigation`), iOS (View über `TabView`)
- Mini-Bar Zustand: Generating → Typing-Indicator + Label; Success → "Workout bereit"; Error → nicht anzeigen (User sieht Fehlermeldung im Screen)
- Mini-Bar Tap: Navigation zurück zum jeweils aktiven Gen-Screen (Workout oder Rezept)
- Mini-Bar gilt für beide: Workout-KI und Rezept-KI (Label unterscheidet sich)

**Out of scope:**
- Reps-Stepper (bewusst nicht: nur Sets)
- Sets/Reps-Presets (Hypertrophie/Kraft/Endurance) — bewusst nicht
- Per-Task-Defaults oder Modell-Picker im Gen-Screen — bleibt Settings-only (aus Phase 22)
- Prompt-Rewrite oder Qualitätsoptimierung für Anthropic
- Erweiterte Mini-Bar mit Streaming-Text direkt in der Bar
- Error-Zustand in der Mini-Bar (kein roter Fehler-Banner)
- iOS SwiftUI-spezifische Implementations-Details (werden in 23-IOS-HANDOFF.md festgehalten, User implementiert iOS selbst)

</domain>

<decisions>
## Implementation Decisions

### Sets-Stepper

- **D-23-01:** `WorkoutAiForm` bekommt ein neues Feld `setsPerExercise: Int`, Range 1–6, Default 3. Das Feld wird in `WorkoutAiUiState.Form` (und `copy()`-Aufrufen im ViewModel) mitgeführt.

  **Why:** User will nur die Satzanzahl steuern, Reps bleiben LLM-Entscheidung. Sets haben direkten Trainingsvolumen-Einfluss und sind der häufigste Anpassungswunsch.

- **D-23-02:** Der `setsPerExercise`-Wert wird per Platzhalter in `workout-system-prompt.md` an das LLM übergeben. Bestehender Prompt-Block (Constraint-Sektion) bekommt eine Zeile `setsPerExercise: ${form.setsPerExercise}` — analog zur bestehenden `exerciseCount`-Interpolation in `WorkoutAiUseCase.buildPrompt()`.

  **Why:** Platzhalter-Ansatz hält den Prompt unverändert bis auf einen Eintrag — kein Rewrite, kein Regressions-Risiko.

- **D-23-03:** UI-Widget: Android nutzt dieselben `+/-` IconButtons wie der bestehende `exerciseCount`-Stepper in `AiWorkoutGenScreen.kt`. iOS nutzt SwiftUI `Stepper`-Widget wie der bestehende Stepper für die Übungsanzahl.

### Background-Mini-Bar

- **D-23-04:** Die Mini-Bar beobachtet `AiGenerationManager.state` (bereits singleton, Koin-injected). Sie erscheint wenn `state is AiGenerationState.Generating` oder `state is AiGenerationState.Success`. Sie verschwindet wenn User Preview bestätigt/verwirft (→ `AiGenerationState.Idle`).

  **Why:** `AiGenerationManager` ist der einzige singleton State-Holder der Generation-übergreifend überlebt. Kein neues State-Management nötig.

- **D-23-05:** **Position:** direkt über der `BottomNavigation` (Android) / `TabView` (iOS). Persistent, kein Floating-Overlay. Höhe ca. 44–48dp/pt.

- **D-23-06:** **Inhalt bei Generating-State:** Animierter 3-Punkte-Typing-Indicator (links) + Label `"KI generiert Workout…"` bzw. `"KI generiert Mahlzeit…"` (je nach `AiType`). Kein Streaming-Text, kein Prozent.

- **D-23-07:** **Inhalt bei Success-State:** Icon (Checkmark) + Label `"Workout bereit — tippen zum Ansehen"` bzw. `"Mahlzeit bereit — tippen zum Ansehen"`. Bar bleibt bis User die Preview öffnet und bestätigt/verwirft.

- **D-23-08:** **Tap-Verhalten:** Navigation zum aktiven Gen-Screen (Workout-Tab → `AiWorkoutGenScreen` / Nutrition-Tab → `AiMealGenScreen`). Auf iOS analog via NavigationPath oder Callback nach oben.

- **D-23-09:** **Hintergrund-Generation:** User kann den Gen-Screen verlassen, während KI arbeitet. `AiGenerationManager` läuft weiter (application-scoped CoroutineScope). Mini-Bar hält den Zustand. Lokale Notification (bestehendes Verhalten) bleibt erhalten, ergänzt die Mini-Bar.

- **D-23-10:** **Error-Zustand:** Mini-Bar verschwindet bei Fehler — kein Fehler-Banner in der Bar. User sieht Fehlermeldung wenn er zurück zum Gen-Screen navigiert.

### iOS-Parität

- **D-23-11:** iOS hat bereits alle Form-Felder (Zielmuskeln, Übungsanzahl, Split-Picker, Cancel-Button während Generation). Die einzige echte Lücke ist der neue Sets-Stepper (D-23-01/03). Kein weiterer iOS-Only-Fix in dieser Phase.

- **D-23-12:** iOS-spezifische Implementierung (Mini-Bar in SwiftUI, Sets-Stepper im `FormBody`) wird in `23-IOS-HANDOFF.md` spezifiziert — User implementiert iOS-Seite selbst. Android + shared KMP ist Agent-Aufgabe.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Bestehende AI-Architektur (Pflichtlektüre)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt` — Singleton State-Holder; Mini-Bar observiert `state: StateFlow<AiGenerationState>`
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` — Form-State-Machine; `WorkoutAiUiState.Form` + `onExerciseCountChanged` als Muster für neuen `onSetsPerExerciseChanged`
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiPreview.kt` — `WorkoutAiForm` data class; hier `setsPerExercise` ergänzen
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` — `buildPrompt()`-Methode; hier Platzhalter für `setsPerExercise` einfügen (Zeile ~160–170)
- `shared/src/commonMain/resources/workout-system-prompt.md` — System-Prompt; Constraint-Sektion bekommt `setsPerExercise`-Zeile

### UI-Referenz (Platform-spezifisch)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt` — `WorkoutFormBody` mit `exerciseCount`-Stepper als Muster; Mini-Bar direkt über `BottomNavigation`
- `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` — `FormBody` mit `Stepper` für `exerciseCount` als Muster; Mini-Bar über `TabView` (User-Seite)

### Navigation / Routing (für Mini-Bar Tap)
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — Routen für Tap-Navigation

### Phase 22 Kontext (Voraussetzungen)
- `.planning/phases/22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o/22-CONTEXT.md` — Multi-Provider-Architektur; `AiGenerationManager` ist nach Phase 22 provider-agnostisch

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AiGenerationManager.state: StateFlow<AiGenerationState>` — bereits singleton, beobachtbar von App-Root. States: `Idle`, `Generating(type, form)`, `Success(type, preview, form)`, `Error(type, error)`.
- `AiGenerationManager.streamingText: StateFlow<StreamingText>` — Streaming-Daten, nicht direkt für Mini-Bar benötigt.
- `exerciseCount`-Stepper in Android (`WorkoutFormBody`) — `+/-` IconButton-Pattern direkt kopierbar für Sets-Stepper.
- `Stepper(value: Binding<Int32>, in:)` in iOS `FormBody` — direkt kopierbar für Sets-Stepper.

### Established Patterns
- `WorkoutAiForm` ist ein plain data class ohne Logik — Feld einfach ergänzen, alle Kopier-Stellen im ViewModel werden Compile-Error-getrieben gefunden.
- `buildPrompt()` in `WorkoutAiUseCase` interpoliert Form-Felder direkt als String. Muster: `"exerciseCount: ${form.exerciseCount}"`.
- Mini-Bar muss in `MainScreen` (Android) / `ContentView` (iOS) platziert werden, wo die `TabBar` gerendert wird.

### Integration Points
- `WorkoutAiForm` (shared) → `WorkoutAiUseCase` → System-Prompt → LLM
- `AiGenerationManager.state` → neue Mini-Bar-Composable (Android) / Mini-Bar-View (iOS)
- Mini-Bar Tap → `NavHostController.navigate()` (Android) / Callback-Kette in Swift (iOS)

</code_context>

<specifics>
## Specific Ideas

- Mini-Bar wie WhatsApp Typing-Indicator: animierte drei Punkte links, Text rechts. Kein Fortschritt in Prozent.
- Höhe der Bar ~44–48dp/pt, volle Breite, dezente Hintergrundfarbe (Surface-Variant o.ä.).
- Bei Success-State: Checkmark-Icon statt Dots, Text "Workout bereit — tippen zum Ansehen".
- iOS-Implementierung wird in `23-IOS-HANDOFF.md` dokumentiert (User schreibt SwiftUI selbst).

</specifics>

<deferred>
## Deferred Ideas

- **Reps-Stepper** — User will nur Sets. Reps bleiben LLM-Entscheidung (könnte Phase 24+ werden falls Feedback kommt).
- **Sets/Reps-Presets** (Hypertrophie/Kraft/Endurance) — interessanter Ansatz, aber mehr UX-Komplexität als Sets-Stepper.
- **Streaming-Text in der Mini-Bar** — zu viel Information für einen 44dp-Banner.
- **Fehler-Banner in der Mini-Bar** — bewusst weggelassen, Error-Screen ist ausreichend.

</deferred>

---

*Phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set*
*Context gathered: 2026-05-19*

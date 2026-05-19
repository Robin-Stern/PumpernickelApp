# Phase 23 — iOS Handoff Spec

**Created:** 2026-05-19
**Status:** SwiftUI-Implementation durch User (per MEMORY-Konvention + D-23-12)
**Agent-Scope:** Android + Shared KMP — siehe 23-01-SUMMARY.md + 23-02-SUMMARY.md
**iOS-Scope:** Diese Datei spezifiziert, was User auf der iOS-Seite implementieren muss

---

## Überblick

Zwei iOS-Änderungen, beide rein in SwiftUI:

1. **Sets-Stepper in `AIWorkoutGenView.swift`** — neues `Stepper`-Widget für `setsPerExercise`
   (Range 1–6, Default 3), Position zwischen "Anzahl Übungen" und "Aufteilung".
2. **Background-Mini-Bar in `MainTabView.swift`** — ein 44pt-Banner direkt *unter* den
   Tab-Inhalten (über dem nativen Tab-Bar-Bereich), der beobachtet `AiGenerationManager.state`
   und zeigt einen Typing-Indicator während der Generation, danach Checkmark +
   "tippen zum Ansehen"-Label nach Success.

Alle deutschen Strings sind 1:1 identisch zur Android-Implementation
(verifiziert gegen `23-UI-SPEC.md` Copywriting Contract).

---

## 1. Sets-Stepper (`AIWorkoutGenView.swift`)

### Shared Hook (aus Plan 23-01)

Nach Plan 23-01 sind im Shared-Modul verfügbar:

- `WorkoutAiUiState.Form.setsPerExercise: Int32` (Default 3, gültiger Range 1..6)
- `WorkoutAiViewModel.onSetsPerExerciseChanged(count: Int32)` — klemmt intern auf 1..6

Das `Form`-State-Objekt aus `viewModel`-Observation enthält bereits `setsPerExercise`.

### Bestehende Referenz im Projekt

`AIWorkoutGenView.swift` enthält bereits einen Stepper für `exerciseCount` (Zeilen 169–179).
Das neue `setsPerExercise`-Stepper folgt exakt demselben Pattern:

```swift
Section("Anzahl Übungen") {
    Stepper(
        value: Binding(
            get: { Int(state.exerciseCount) },
            set: { viewModel.onExerciseCountChanged(count: Int32($0)) }
        ),
        in: 1...12
    ) {
        Text("\(state.exerciseCount) Übungen")
    }
}
```

### SwiftUI-Snippet für `setsPerExercise`

Direkt **nach** dem `Section("Anzahl Übungen")` und **vor** dem `Section("Aufteilung")` einfügen:

```swift
// D-23-01: Anzahl Sätze Stepper (Range 1–6, Default 3 aus shared)
Section("Anzahl Sätze") {
    Stepper(
        value: Binding(
            get: { Int(form.setsPerExercise) },
            set: { newValue in
                viewModel.onSetsPerExerciseChanged(count: Int32(newValue))
            }
        ),
        in: 1...6
    ) {
        HStack {
            Text("Anzahl Sätze")
                .font(.body)
            Spacer()
            Text("\(form.setsPerExercise) Sätze")
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundColor(.secondary)
        }
    }
}
```

**Hinweis:** Der `Section`-Header `"Anzahl Sätze"` ist optional — die bestehenden Sections im
File nutzen bereits den Pattern. Wenn ein kürzerer Stil bevorzugt wird, kann der HStack-Inhalt
allein den Stepper-Label ersetzen (wie bei `exerciseCount` mit nur `Text("\(state.exerciseCount) Übungen")`).
Entscheidend ist nur das Binding gegen `viewModel.onSetsPerExerciseChanged` und `in: 1...6`.

### Copy (deutsch — exakt wie Android)

| Element | Copy |
|---------|------|
| Section-Header | `"Anzahl Sätze"` |
| Label im HStack | `"Anzahl Sätze"` |
| Value-Anzeige | `"\(form.setsPerExercise) Sätze"` (z.B. `"3 Sätze"`) |
| Range | `1...6` |
| Default | 3 (kommt aus shared `defaultForm` — kein manuelles Setzen nötig) |

### Position in der Form

Reihenfolge in `FormBody` von oben nach unten:

1. `Section("Zielmuskeln")` — Anatomy-Chips (bestehend)
2. `Section("Anzahl Übungen")` — `exerciseCount` Stepper (bestehend)
3. **`Section("Anzahl Sätze")` — `setsPerExercise` Stepper (NEU — D-23-01)**
4. `Section("Aufteilung")` — Split-Picker (bestehend)
5. `Section` — Generieren-Button (bestehend)

---

## 2. Background-Mini-Bar (`MainTabView.swift`)

### Shared Hook

`AiGenerationManager` ist ein Koin-Single — über einen neuen `AiGenerationKoinHelper` in Swift
erreichbar. Pattern identisch zu `WorkoutAiKoinHelper.kt`:

**Kotlin (neue Datei — muss Agent oder User anlegen):**
```kotlin
// shared/src/iosMain/kotlin/com/pumpernickel/di/AiGenerationKoinHelper.kt
package com.pumpernickel.di

import com.pumpernickel.domain.ai.AiGenerationManager
import org.koin.mp.KoinPlatform

class AiGenerationKoinHelper {
    fun getAiGenerationManager(): AiGenerationManager =
        KoinPlatform.getKoin().get()
}
```

**Hinweis:** Prüfe ob `AiGenerationKoinHelper` bereits existiert
(z.B. in `shared/src/iosMain/kotlin/com/pumpernickel/di/`). Falls nicht, ist obige
Kotlin-Datei anzulegen und in das Shared-Modul zu kompilieren.

Alternativ: `AiGenerationManager` direkt über `KoinHelper.shared` Bridge zugänglich machen —
falls die Bridge bereits alle Koin-Singles exponiert. Prüfe `KoinHelper.shared` in Xcode
auf vorhandene Methoden.

### Was zu beobachten ist

- `manager.state: StateFlow<AiGenerationState>` — via `asyncSequence(for:)` in Swift
- `AiGenerationState` ist sealed; in Swift (KMP-NativeCoroutines-Konvention) via
  `is`-Check erreichbar:
  - `AiGenerationState.Idle` — Bar verstecken
  - `AiGenerationState.Generating` — Bar zeigen, Typing-Dots + Label
  - `AiGenerationState.Success` — Bar zeigen, Checkmark + "bereit"-Label
  - `AiGenerationState.Error` — Bar verstecken (D-23-10)
- `AiType` Enum: `WORKOUT` vs. `RECIPE` (Swift-Naming: `.workout` / `.recipe`)

### Observation-Pattern

Projekte nutzen `asyncSequence(for:)` aus `KMPNativeCoroutinesAsync` — identisch zu
`AIWorkoutGenView.swift`. Beispiel-Pattern für State-Observation (analog zu
`PumpernickelApp.swift`-Pattern mit `viewModel.appThemeFlow`):

```swift
// In MainTabView oder einem eigenen ObservableObject

@State private var aiGenState: AiGenerationState = AiGenerationStateCompanion.shared.idle
// Oder nutze eine lazy-initialisierte Hilfsvariable für den initial-State

// Im .task { } Block:
private func observeAiGenerationState() async {
    let manager = AiGenerationKoinHelper().getAiGenerationManager()
    do {
        for try await s in asyncSequence(for: manager.state) {
            aiGenState = s
        }
    } catch {
        print("MainTabView AiGeneration observation error: \(error)")
    }
}
```

**Initialwert-Problem:** `AiGenerationState.Idle` ist ein Kotlin `object` — in Swift als
`AiGenerationStateIdle` zugänglich. Nutze einen optionalen State (`AiGenerationState?`) oder
initialisiere mit dem statischen Companion-Objekt, das Swift/KMP generiert. Wenn beides nicht
direkt zugreifbar ist, starte mit `nil` und rendere die Bar nur wenn non-nil und kein Idle.

### Integration in `MainTabView.swift`

Die bestehende `MainTabView.swift` nutzt `TabView` direkt. Die Mini-Bar kommt **darunter**,
über dem nativen iOS Tab-Bar. Der sicherste Ansatz: `ZStack` oder `VStack` wrapper um den
`TabView` mit dem Banner als `safeAreaInset`:

```swift
struct MainTabView: View {
    @State private var selectedTab = 0
    @State private var pendingUnlocks: [SharedUnlockEvent] = []
    @State private var aiGenState: AiGenerationState? = nil   // NEU D-23-04

    private let gamificationViewModel = GamificationUiKoinHelper().getGamificationViewModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            // ... bestehende Tab-Inhalte unverändert ...
        }
        .tint(.appAccent)
        // NEU D-23-05: Mini-Bar direkt über der Tab-Bar via safeAreaInset
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AiGenerationMiniBarView(
                state: aiGenState,
                onTap: { aiType in
                    // D-23-08: Tab-Switch ZUERST, dann Navigation
                    switch aiType {
                    case AiType.workout:
                        selectedTab = 0
                        // Navigation zur AIWorkoutGenView via bestehenden Toolbar-Link in Tab 0
                        // (kein zusätzlicher navController nötig — AIWorkoutGenView ist bereits
                        //  über ToolbarItem in TemplateListView erreichbar)
                    case AiType.recipe:
                        selectedTab = 2
                        // Navigation zur AIMealGenView analog
                    default:
                        break
                    }
                }
            )
        }
        // ... bestehende .fullScreenCover und .task Modifier ...
        .task { await observeUnlocks() }
        .task { await observeAiGenerationState() }  // NEU
    }

    private func observeAiGenerationState() async {
        let manager = AiGenerationKoinHelper().getAiGenerationManager()
        do {
            for try await s in asyncSequence(for: manager.state) {
                self.aiGenState = s
            }
        } catch {
            print("MainTabView AiGeneration observation error: \(error)")
        }
    }

    // ... observeUnlocks() unverändert ...
}
```

**Warum `safeAreaInset` statt `VStack`:**
`safeAreaInset(edge: .bottom)` ist der idiomatische iOS 15+-Weg, Content direkt über der
Tab-Bar zu platzieren. Der `TabView` rendert seinen Content automatisch mit dem nötigen
Bottom-Padding, sodass kein manuelles Padding-Berechnungen nötig ist. `VStack` würde die
Tab-Bar nach oben drücken und die gesamte Layout-Höhe verändern.

**Pitfall 2 Erinnerung:** `selectedTab = X` MUSS vor jeder Navigation-Aktion gesetzt werden.
iOS rendert nur den aktiven Tab — wenn die Navigation zuerst kommt, hat der falsche NavStack
den Push.

### `AiGenerationMiniBarView` View

```swift
struct AiGenerationMiniBarView: View {
    let state: AiGenerationState?
    let onTap: (AiType) -> Void

    private var visible: Bool {
        guard let s = state else { return false }
        return s is AiGenerationStateGenerating || s is AiGenerationStateSuccess
    }

    private var activeType: AiType? {
        if let g = state as? AiGenerationStateGenerating { return g.type }
        if let s = state as? AiGenerationStateSuccess { return s.type }
        return nil
    }

    var body: some View {
        if visible {
            HStack(spacing: 8) {
                barContent
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
            .background(Color(.secondarySystemBackground))
            .contentShape(Rectangle())
            .onTapGesture {
                if let type = activeType { onTap(type) }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .bottom).combined(with: .opacity),
                removal: .move(edge: .bottom).combined(with: .opacity)
            ))
            .animation(.easeInOut(duration: 0.3), value: visible)
        }
    }

    @ViewBuilder
    private var barContent: some View {
        if let g = state as? AiGenerationStateGenerating {
            TypingIndicatorDots()
                .accessibilityHidden(true)
            Text(g.type == AiType.workout
                 ? "KI generiert Workout…"
                 : "KI generiert Mahlzeit…")
                .font(.body)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel("KI-Generierung läuft")
        } else if let s = state as? AiGenerationStateSuccess {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.accentColor)
                .font(.system(size: 18))
                .accessibilityHidden(true)
            Text(s.type == AiType.workout
                 ? "Workout bereit — tippen zum Ansehen"
                 : "Mahlzeit bereit — tippen zum Ansehen")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel(s.type == AiType.workout
                                    ? "Workout bereit, tippen zum Ansehen"
                                    : "Mahlzeit bereit, tippen zum Ansehen")
        }
    }
}
```

### `TypingIndicatorDots` View

```swift
struct TypingIndicatorDots: View {
    @State private var animating = false

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color.secondary)
                    .frame(width: 6, height: 6)
                    .opacity(animating ? 1.0 : 0.3)
                    .animation(
                        .easeInOut(duration: 0.6)
                        .repeatForever(autoreverses: true)
                        .delay(Double(index) * 0.2),
                        value: animating
                    )
            }
        }
        .onAppear { animating = true }
        .onDisappear { animating = false }
    }
}
```

Alternativ für präzisere Steuerung: `withAnimation(.easeInOut(duration: 0.6).repeatForever())` via `DispatchQueue.main.asyncAfter` für den 200ms-Stagger — beide Ansätze sind akzeptabel.

### Copy (deutsch — exakt wie Android)

| State | Label | Accessibility |
|-------|-------|---------------|
| Generating (Workout) | `"KI generiert Workout…"` | `"KI-Generierung läuft"` |
| Generating (Recipe) | `"KI generiert Mahlzeit…"` | `"KI-Generierung läuft"` |
| Success (Workout) | `"Workout bereit — tippen zum Ansehen"` | `"Workout bereit, tippen zum Ansehen"` |
| Success (Recipe) | `"Mahlzeit bereit — tippen zum Ansehen"` | `"Mahlzeit bereit, tippen zum Ansehen"` |
| Idle / Error | (nicht sichtbar — D-23-10) | — |

### Visuelle Spezifikation

| Property | Value |
|----------|-------|
| Höhe | 44pt fix (`minHeight: 44, maxHeight: 44`) |
| Background | `Color(.secondarySystemBackground)` (entspricht Android `surfaceVariant`) |
| Padding horizontal | 16pt |
| Spacer zwischen Icon/Dots und Text | 8pt (`HStack(spacing: 8)`) |
| Dot-Größe | 6pt × 6pt, `Color.secondary` |
| Dot-Spacing | 4pt |
| Checkmark-Icon | `checkmark.circle.fill`, Tint `.accentColor`, 18pt |
| Generating-Text | `.body` Font, `.secondary` Color |
| Success-Text | `.subheadline` Font, `.semibold` Weight, `.primary` Color |
| Animation | 300ms ease-in-out slide+fade (insertion + removal) |

---

## 3. Pitfalls

### Pitfall 1: State bei `.idle` und `.error` — Bar muss verschwinden

Mini-Bar darf bei `AiGenerationState.Idle` und `AiGenerationState.Error` NICHT sichtbar sein
(D-23-10). Im Swift-Code: `visible`-Computed-Property prüft nur `Generating` + `Success` —
Error und Idle landen im `else`-Zweig (Bar nicht gerendert).

**Wichtig:** Wenn `state` optional ist (`AiGenerationState?`) — `nil` ebenfalls als
nicht-sichtbar behandeln. Kein Force-Unwrap.

### Pitfall 2: Tab-Switch vor Navigation

`selectedTab = X` MUSS vor jeglicher NavigationPath-Manipulation oder Coordinator-Aufruf.
`MainTabView` rendert den aktiven NavStack nur wenn der Tab selected ist — wenn Navigation
zuerst kommt, hat die falsche Tab-Sicht den Push.

### Pitfall 3: KMP-NativeCoroutines Sealed-Class-Casting

`AiGenerationState` ist Kotlin-sealed → in Swift Pattern-Match via `as?`:

```swift
if let g = state as? AiGenerationStateGenerating {
    // g.type, g.originatingData
}
if let s = state as? AiGenerationStateSuccess {
    // s.type, s.preview, s.originatingData
}
```

Die Kotlin-Klassennamen werden in Swift als `AiGenerationStateIdle`, `AiGenerationStateGenerating`,
`AiGenerationStateSuccess`, `AiGenerationStateError` exportiert (KMP-Standard-Naming bei sealed
classes in Objektform). Falls das Xcode-Autocomplete andere Namen zeigt — diese verwenden.

### Pitfall 4: `safeAreaInset` vs. `TabView` Insets

`safeAreaInset(edge: .bottom)` auf den `TabView` fügt die Mini-Bar *zwischen* Tab-Content und
Tab-Bar ein — iOS gibt dem Tab-Content automatisch zusätzliches Bottom-Padding, sodass Content
nicht hinter der Bar verschwindet. Falls visuell falsch: `spacing: 0` im `safeAreaInset`
sicherstellt kein Gap zwischen Bar und Tab-Bar.

### Pitfall 5: `AiGenerationKoinHelper` fehlt möglicherweise

Das Shared-Modul hat kein `AiGenerationKoinHelper.kt` (Stand Phase 23). Die Kotlin-Hilfsdatei
muss entweder vom User oder als Deviation in Plan 23-01 angelegt werden. Pattern ist identisch
zu `WorkoutAiKoinHelper.kt` — ein `KoinPlatform.getKoin().get<AiGenerationManager>()`.

---

## 4. Tab-Navigation beim Mini-Bar-Tap

### Workout-AI-Generation

Tab 0 ist der Workout-Tab. `AIWorkoutGenView` wird in `MainTabView` über einen `ToolbarItem`
(Sparkles-Button) in `TemplateListView` navigiert — kein direkter `NavigationLink` in
`MainTabView`.

**Optionen nach Mini-Bar-Tap (Workout):**

1. **Tab-Wechsel + programmatischer Push (empfohlen):** `selectedTab = 0` setzen, dann via
   `NavigationPath` oder einen `@Binding`-Flag in `TemplateListView` den AI-Screen pushen.
   Erfordert State-Lifting aus `MainTabView` nach unten.
2. **Tab-Wechsel only:** Nur `selectedTab = 0` setzen. User ist dann auf `TemplateListView`
   und muss selbst auf Sparkles tippen. Einfachste Option, funktional korrekt für D-23-08.
3. **Notification-Ansatz:** `NotificationCenter.default.post(name: .navigateToAiWorkout, ...)`,
   `TemplateListView` subscribt und triggert Navigation. Entkoppelt, kein State-Lifting.

Empfehlung: Option 2 für initialen Ship (Tab-Wechsel reicht), Option 1 oder 3 als Follow-up.

### Recipe-AI-Generation

Tab 2 ist der Nutrition-Tab. `AIMealGenView` liegt hinter Sparkles in `NutritionDailyLogView`.
Analog: `selectedTab = 2` setzen, programmatische Navigation optional.

---

## 5. Acceptance Criteria (für User-Implementation)

### Sets-Stepper

- [ ] In `AIWorkoutGenView.swift` erscheint zwischen "Anzahl Übungen" und "Aufteilung" ein
      `Stepper` mit Label `"Anzahl Sätze"`
- [ ] Stepper-Range ist `1...6`, Default 3 (kommt aus shared `Form.setsPerExercise`)
- [ ] Tap auf `+` ruft `viewModel.onSetsPerExerciseChanged(count: Int32(newValue))` auf
- [ ] Tap auf `−` analog
- [ ] Value-Anzeige zeigt `"\(form.setsPerExercise) Sätze"` (z.B. `"3 Sätze"`)

### Mini-Bar

- [ ] In `MainTabView.swift` ist eine `AiGenerationMiniBarView` über der Tab-Bar platziert
      (via `safeAreaInset(edge: .bottom)` oder äquivalent)
- [ ] Mini-Bar ist **nicht sichtbar** wenn `state` `.idle` oder `.error` ist
- [ ] Bei `.generating` (Workout): Bar zeigt animierte 3 Dots + `"KI generiert Workout…"`
- [ ] Bei `.generating` (Recipe): Bar zeigt Dots + `"KI generiert Mahlzeit…"`
- [ ] Bei `.success` (Workout): Bar zeigt Checkmark + `"Workout bereit — tippen zum Ansehen"`
- [ ] Bei `.success` (Recipe): Bar zeigt `"Mahlzeit bereit — tippen zum Ansehen"`
- [ ] Tap auf Mini-Bar wechselt `selectedTab` auf den richtigen Tab (0 = Workout, 2 = Recipe)
- [ ] Slide+Fade Animation 300ms beim Erscheinen / Verschwinden
- [ ] Höhe der Bar 44pt fix
- [ ] Accessibility-Label bei Generating: `"KI-Generierung läuft"`
- [ ] Accessibility-Label bei Success (Workout): `"Workout bereit, tippen zum Ansehen"`

---

## 6. Referenzen

- `23-CONTEXT.md` — Decisions D-23-01 bis D-23-12
- `23-UI-SPEC.md` — Copywriting Contract + Spacing/Typography/Color Tokens
- `23-PATTERNS.md` — Android-Implementation als Referenz (`AiGenerationMiniBar.kt` Composable)
- `23-01-SUMMARY.md` — Shared-KMP-Änderungen (was iOS via Bridge sieht: neue Felder + Handler)
- `23-02-SUMMARY.md` — Android-UI-Implementation (Referenz für SwiftUI-Spiegelung)
- `iosApp/iosApp/Views/AI/AIWorkoutGenView.swift` — bestehender Stepper-Pfad für `exerciseCount`
  als direktes Kopier-Muster (Zeilen 169–179)
- `iosApp/iosApp/Views/MainTabView.swift` — Root für Mini-Bar-Integration
- `iosApp/iosApp/PumpernickelApp.swift` — `asyncSequence`-Observation-Pattern (Zeilen 65–83)
- Phase 22 Handoff (`22-IOS-HANDOFF.md`) — Strukturvorlage + KMPNativeCoroutinesAsync-Pattern

---

*Phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set*
*Handoff to user: 2026-05-19*

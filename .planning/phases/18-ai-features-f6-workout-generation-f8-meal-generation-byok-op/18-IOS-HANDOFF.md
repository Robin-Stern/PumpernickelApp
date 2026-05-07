# Phase 18 — iOS Handoff: AI Features (F6 Workout Generation + F8 Meal Generation, BYOK)

**Audience:** the user (iOS-focused) hand-writing the SwiftUI surfaces.
**Scope:** 4 new SwiftUI views + 2 modifications. No Compose Multiplatform on iOS (D-17-18 / D-18 KMP-shared-VM convention).
**Convention:** mirror the structure of `17-IOS-HANDOFF.md` (Progress-pic feature) and the existing iOS surfaces under `iosApp/iosApp/Views/`. Kotlin/Koin side is shipped by Plans 18-05 through 18-09.

**D-17-18 reminder:** Compose Multiplatform is NOT used for the iOS UI in this phase. Plans 05/07/09 deliver Android composables; this doc specifies the SwiftUI counterparts.

---

## What you are building

| Status | File | Purpose |
|--------|------|---------|
| NEW | `iosApp/iosApp/Views/AI/AiSettingsView.swift` | Provider preset / API key / base URL / model — analog to Android `AiSettingsScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiWorkoutGenView.swift` | F6 workout-AI form, skeleton, error states — analog to `AiWorkoutGenScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiMealGenView.swift` | F8 meal-AI flow, RemainingExhausted state, error states — analog to `AiMealGenScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiPreviewSheet.swift` | Bottom sheet rendering both Workout and Recipe previews |
| MODIFY | `iosApp/iosApp/Views/Settings/SettingsView.swift` | Add a "KI" NavigationLink section → `AiSettingsView()` |
| MODIFY | `iosApp/iosApp/Views/MainTabView.swift` | Add a sparkles toolbar item to the Workout-tab and Nutrition-tab roots |

---

## pbxproj concerns

When you add the new Swift files, drag the `AI/` folder into Xcode's Project Navigator ("Add Files to iosApp..." with "Copy items if needed" off, target membership = `iosApp`). This is consistent with the Phase 15.1 / 17 convention — this phase does NOT add files to `project.pbxproj` programmatically from a GSD executor.

**CRITICAL — Add the prompt files to the iOS bundle.** The two system-prompt `.md` files shipped by Plan 03 MUST be in the iOS target's bundle resources or `readResourceFile(...)` will throw at runtime:

| File | Location |
|------|----------|
| `workout-system-prompt.md` | `shared/src/commonMain/resources/workout-system-prompt.md` |
| `recipe-system-prompt.md` | `shared/src/commonMain/resources/recipe-system-prompt.md` |

Both are already referenced in `iosApp.xcodeproj/project.pbxproj` (Plan 03 added them following the exact same pattern as `free_exercise_db.json` — PBXBuildFile UUIDs `A11801` / `A11802`, PBXFileReference UUIDs `B11801` / `B11802`, flat path `../shared/src/commonMain/resources/<filename>` with `sourceTree = SOURCE_ROOT`).

**Why flat naming matters:** `Platform.ios.kt`'s `readResourceFile` calls `NSBundle.pathForResource(name, ext)`, which does NOT interpret a `/` in the resource name as a subdirectory. Filenames must be flat (no subdir prefix). Both prompt files are flat as shipped — do not move them.

Verify the bundle entries are present before first build:

```bash
grep -c "workout-system-prompt.md" iosApp/iosApp.xcodeproj/project.pbxproj
grep -c "recipe-system-prompt.md" iosApp/iosApp.xcodeproj/project.pbxproj
```

Both should return `3` (one PBXFileReference + one PBXBuildFile + one reference in the PBXGroup or PBXResourcesBuildPhase).

---

## No new Info.plist permissions

- No camera, no biometric, no photo library, no contacts.
- HTTPS-only transport. ATS (App Transport Security) defaults reject `http://`. The Settings layer rejects non-HTTPS base URLs at the VM level (`baseUrlDraft.startsWith("https://")` in the Android counterpart; mirror this in SwiftUI). No ATS exception needed.

---

## Kotlin contracts (what the SwiftUI views consume)

All three KoinHelpers follow the exact same canonical 9-line shape as `AchievementGalleryKoinHelper`, `ProgressGalleryKoinHelper`, etc. They are **class-style** (not objects), so Swift call syntax is `ClassName().method()`.

### `AiSettingsView` — backed by `AiSettingsViewModel` via `AiSettingsKoinHelper`

**KoinHelper (Plan 05 — already shipped):**
```kotlin
class AiSettingsKoinHelper {
    fun getAiSettingsViewModel(): AiSettingsViewModel =
        KoinPlatform.getKoin().get()
}
```

**Swift acquisition:**
```swift
private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()
```

**Exposed flows (via `asyncSequence(for:)` per project convention):**

| Flow name | Kotlin type | Default | Description |
|-----------|-------------|---------|-------------|
| `viewModel.providerPresetFlow` | `StateFlow<String>` | `"openai"` | One of: `openai`, `together`, `openrouter`, `groq`, `custom` |
| `viewModel.baseUrlFlow` | `StateFlow<String>` | `"https://api.openai.com/v1"` | Pre-filled by preset; editable only when preset == `custom` |
| `viewModel.modelFlow` | `StateFlow<String>` | `"gpt-4o-mini"` | Pre-filled by preset; always editable |
| `viewModel.apiKeyConfiguredFlow` | `StateFlow<Boolean>` | `false` | `true` once a key is stored; raw key is NEVER exposed |

**Note on KMP-Native property names:** `@NativeCoroutinesState` annotates the Kotlin properties as `providerPreset`, `baseUrl`, `model`, `apiKeyConfigured`. KMPNativeCoroutines generates `providerPresetFlow`, `baseUrlFlow`, `modelFlow`, `apiKeyConfiguredFlow` (appends `Flow` suffix). Use the `Flow`-suffixed names in Swift.

**Action methods:**

| Method | Parameters | Effect |
|--------|------------|--------|
| `viewModel.setApiKey(value:)` | `String` | Writes to Keychain; sets `apiKeyConfigured = true` |
| `viewModel.clearApiKey()` | — | Deletes from Keychain; sets `apiKeyConfigured = false` |
| `viewModel.setProviderPreset(preset:)` | `String` — one of the 5 preset keys | Resets `baseUrl` + `model` to per-preset defaults (D-18-06) |
| `viewModel.setBaseUrl(url:)` | `String` | Writes to DataStore; validate `https://` prefix before calling |
| `viewModel.setModel(value:)` | `String` | Writes to DataStore |

**Provider preset defaults (D-18-06):**

| Preset key | Display label | Default base URL | Default model |
|------------|---------------|-----------------|---------------|
| `openai` | OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| `together` | Together.AI | `https://api.together.xyz/v1` | `meta-llama/Llama-3.3-70B-Instruct-Turbo` |
| `openrouter` | OpenRouter | `https://openrouter.ai/api/v1` | `meta-llama/llama-3.3-70b-instruct` |
| `groq` | Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` |
| `custom` | Benutzerdefiniert | `""` (user fills) | `""` (user fills) |

**UI structure (Form layout — all in German):**

```swift
NavigationStack {
    Form {
        Section("Anbieter") {
            Picker("Anbieter", selection: $providerPreset) {
                Text("OpenAI").tag("openai")
                Text("Together.AI").tag("together")
                Text("OpenRouter").tag("openrouter")
                Text("Groq").tag("groq")
                Text("Benutzerdefiniert").tag("custom")
            }
            .onChange(of: providerPreset) { _, newValue in
                viewModel.setProviderPreset(preset: newValue)
            }
        }

        Section("API-Schlüssel") {
            // SecureField / TextField toggle (show/hide)
            if showKey {
                TextField("API-Schlüssel", text: $keyDraft)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            } else {
                SecureField("API-Schlüssel", text: $keyDraft)
            }
            Button(showKey ? "Verbergen" : "Anzeigen") { showKey.toggle() }
                .buttonStyle(.borderless)

            Text(apiKeyConfigured ? "Gespeichert" : "Kein Schlüssel gespeichert")
                .font(.caption)
                .foregroundColor(.secondary)

            Button("Schlüssel speichern") {
                viewModel.setApiKey(value: keyDraft)
                keyDraft = ""        // T-18-05-05 — clear draft immediately
            }
            .disabled(keyDraft.isEmpty)

            Button("Schlüssel löschen", role: .destructive) {
                viewModel.clearApiKey()
            }
            .disabled(!apiKeyConfigured)
        }

        Section("Verbindung") {
            TextField("Basis-URL", text: $baseUrlDraft)
                .disabled(providerPreset != "custom")
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
            if baseUrlDraft.hasPrefix("http://") {
                Text("Nur HTTPS-URLs erlaubt.")
                    .font(.caption)
                    .foregroundColor(.red)
            }

            TextField("Modell", text: $modelDraft)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

            Button("URL und Modell speichern") {
                guard !baseUrlDraft.hasPrefix("http://") else { return }
                viewModel.setBaseUrl(url: baseUrlDraft)
                viewModel.setModel(value: modelDraft)
            }
            .disabled(baseUrlDraft.hasPrefix("http://"))
        }
    }
    .navigationTitle("KI-Einstellungen")
    .navigationBarTitleDisplayMode(.inline)
}
```

---

### `AiWorkoutGenView` — backed by `WorkoutAiViewModel` via `WorkoutAiKoinHelper`

**KoinHelper (Plan 06 — ships in Wave 4):**
```kotlin
class WorkoutAiKoinHelper {
    fun getWorkoutAiViewModel(): WorkoutAiViewModel =
        KoinPlatform.getKoin().get()
}
```

**Swift acquisition:**
```swift
private let viewModel = WorkoutAiKoinHelper().getWorkoutAiViewModel()
```

**Exposed flow:**

| Flow name | Kotlin type | Description |
|-----------|-------------|-------------|
| `viewModel.uiStateFlow` | `StateFlow<WorkoutAiUiState>` | Full state machine |

**Sealed UiState branches:**

KMP-Native flat-exports sealed subclasses. Verify in the generated `Shared.framework` headers — the naming convention is `Shared.WorkoutAiUiStateNoKey`, `Shared.WorkoutAiUiStateForm`, etc. (flat, not nested). Inspect with Xcode autocomplete after first build.

| Swift type | Kotlin source | Fields accessible on the Swift side |
|------------|---------------|--------------------------------------|
| `WorkoutAiUiStateNoKey` | `object NoKey` | — |
| `WorkoutAiUiStateForm` | `data class Form(...)` | `.targetMuscles`, `.exerciseCount`, `.splitStyle` |
| `WorkoutAiUiStateGenerating` | `data class Generating(...)` | `.skeletonRowCount: Int32` |
| `WorkoutAiUiStatePreview` | `data class Preview(...)` | `.preview: WorkoutAiPreview`, `.originatingForm` |
| `WorkoutAiUiStateError` | `data class Error(...)` | `.error: AiError`, `.originatingForm` |
| `WorkoutAiUiStateSaved` | `data class Saved(...)` | `.templateIds: [Int64]` (bridges as `NSArray<NSNumber>`) |

**Action methods:**

| Method | Parameters | Effect |
|--------|------------|--------|
| `viewModel.onMusclesChanged(muscles:)` | `[MuscleGroup]` (as KotlinLong array or list) | Updates form targetMuscles |
| `viewModel.onExerciseCountChanged(count:)` | `Int32` | Updates form exerciseCount (coerced 1..12) |
| `viewModel.onSplitStyleChanged(split:)` | `WorkoutAiSplit` | Updates form splitStyle |
| `viewModel.generate()` | — | Starts generation; transitions Form → Generating |
| `viewModel.cancel()` | — | Cancels in-flight job; transitions Generating → Form |
| `viewModel.save()` | — | Commits preview to DB; transitions Preview → Saved |
| `viewModel.discardPreview()` | — | Transitions Preview → Form (no DB write) |
| `viewModel.retryFromError()` | — | Transitions Error → Form (re-enables form for retry) |

**Split style options (WorkoutAiSplit enum — flat export from KMP):**

| Swift case | Kotlin name | Templates generated |
|------------|-------------|---------------------|
| `WorkoutAiSplitNone` | `NONE` | 1 |
| `WorkoutAiSplitPushPullLegs` | `PUSH_PULL_LEGS` | 3 |
| `WorkoutAiSplitUpperLower` | `UPPER_LOWER` | 2 |
| `WorkoutAiSplitFullBody` | `FULL_BODY` | 1 |

**UI structure per state (all German):**

- **`NoKey`** — centred VStack:
  - `Text("Du hast noch keinen API-Schlüssel konfiguriert.")`
  - `Button("KI-Einstellungen öffnen") { /* push AiSettingsView() */ }`

- **`Form`** — VStack / Form with:
  - Target muscle chip cluster (multi-select). Reuse the iOS `AnatomyPicker` / `AnatomyPickerSheet` from Phase 14. If that component isn't available as a standalone, use a `LazyHGrid` of toggleable chips using `MuscleGroup` values.
  - Exercise count picker (range 3–8 or 1–12, Stepper or segmented Picker).
  - Split style Picker (segmented or Menu): None / PPL / Upper-Lower / Full Body.
  - `Button("Generieren")` — `disabled` when `form.targetMuscles.isEmpty`.

- **`Generating`** — N skeleton rows (count = `state.skeletonRowCount`, static, no shimmer per D-18-16) + `Button("Abbrechen") { viewModel.cancel() }`.

- **`Preview`** — `.sheet(isPresented:)` presenting `AiPreviewSheet(content: .workout(state.preview))` with `onSaveAll: { viewModel.save() }` and `onDiscard: { viewModel.discardPreview() }`.

- **`Error`** — use the per-class error copy table (below). `retryFromError()` or `push AiSettingsView()` per the table.

- **`Saved`** — call `dismiss()` on appearance (view is done).

---

### `AiMealGenView` — backed by `RecipeAiViewModel` via `RecipeAiKoinHelper`

**KoinHelper (Plan 08 — ships in Wave 6):**
```kotlin
class RecipeAiKoinHelper {
    fun getRecipeAiViewModel(): RecipeAiViewModel =
        KoinPlatform.getKoin().get()
}
```

**Swift acquisition:**
```swift
private let viewModel = RecipeAiKoinHelper().getRecipeAiViewModel()
```

**Exposed flow:**

| Flow name | Kotlin type | Description |
|-----------|-------------|-------------|
| `viewModel.uiStateFlow` | `StateFlow<RecipeAiUiState>` | Full state machine |

**Sealed UiState branches:**

| Swift type | Kotlin source | Fields accessible |
|------------|---------------|-------------------|
| `RecipeAiUiStateLoading` | `object Loading` | — |
| `RecipeAiUiStateNoKey` | `object NoKey` | — |
| `RecipeAiUiStateRemainingExhausted` | `data class RemainingExhausted(...)` | `.remaining: RemainingMacros` |
| `RecipeAiUiStateForm` | `data class Form(...)` | `.remaining: RemainingMacros` |
| `RecipeAiUiStateGenerating` | `object Generating` | — |
| `RecipeAiUiStatePreview` | `data class Preview(...)` | `.preview: RecipeAiPreview`, `.originatingRemaining: RemainingMacros` |
| `RecipeAiUiStateError` | `data class Error(...)` | `.error: AiError`, `.originatingRemaining: RemainingMacros?` |
| `RecipeAiUiStateSaved` | `object Saved` | — |

**RemainingMacros fields (bridges from Kotlin data class):**

`.kcal`, `.protein`, `.fat`, `.carbs`, `.sugar` — all `Double`. `.isExhausted: Bool` (true when `kcal <= 100`).

**Action methods:**

| Method | Parameters | Effect |
|--------|------------|--------|
| `viewModel.refreshRemaining()` | — | Re-reads today's totals; transitions Loading/Error → Form or RemainingExhausted |
| `viewModel.generate()` | — | Starts recipe generation; transitions Form → Generating |
| `viewModel.cancel()` | — | Cancels in-flight job; transitions Generating → Form |
| `viewModel.save()` | — | Commits preview to DB; transitions Preview → Saved |
| `viewModel.discardPreview()` | — | Transitions Preview → Form |
| `viewModel.retryFromError()` | — | Transitions Error → Form (or re-runs refreshRemaining if error was from computeRemaining) |

**UI structure per state (all German):**

- **`Loading`** — centred `ProgressView()`.

- **`NoKey`** — centred VStack:
  - `Text("Du hast noch keinen API-Schlüssel konfiguriert.")`
  - `Button("KI-Einstellungen öffnen") { /* push AiSettingsView() */ }`

- **`RemainingExhausted(remaining)`** — centred VStack:
  - `Text("Du hast deine Tagesziele bereits erreicht.")`
  - `Text("Verbleibend: \(Int(state.remaining.kcal)) kcal").font(.caption).foregroundColor(.secondary)`
  - No generate button.

- **`Form(remaining)`** — VStack:
  - Macro summary card showing 5 rows (kcal, Protein, Fett, Kohlenhydrate, Zucker) with `Int(remaining.kcal)` etc.
  - `Button("Restliche Makros füllen") { viewModel.generate() }` — always enabled when in Form state.

- **`Generating`** — 5 static placeholder rows (shimmer optional; plain gray rectangles per D-18-16) + `Button("Abbrechen") { viewModel.cancel() }`.

- **`Preview(preview, originatingRemaining)`** — `.sheet` presenting `AiPreviewSheet(content: .recipe(state.preview))` with `onSaveAll: { viewModel.save() }` and `onDiscard: { viewModel.discardPreview() }`.

- **`Error(error, originatingRemaining)`** — per-class copy table (below).

- **`Saved`** — `dismiss()` on appearance.

---

### `AiPreviewSheet` — sealed `AiPreviewContent`

Two callers (F6 and F8). Swift enum bridges both:

```swift
enum AiPreviewContent {
    case workout(WorkoutAiPreview)
    case recipe(RecipeAiPreview)
}
```

**Initialise via:**
```swift
AiPreviewSheet(
    content: .workout(state.preview),    // from AiWorkoutGenView
    onSaveAll: { viewModel.save() },
    onDiscard: { viewModel.discardPreview() }
)
```

**WorkoutAiPreview fields (bridges from Plan 06 Kotlin data classes):**

- `.templates: [StagedTemplate]` — each with `.name: String`, `.description: String?`, `.exercises: [StagedTemplateExercise]`
- `StagedTemplateExercise`: `.exerciseName: String`, `.targetSets: Int32`, `.targetReps: Int32`, `.restPeriodSec: Int32`, `.note: String?`
- `.inlineNewExercises: [StagedExercise]` — each with `.name`, `.primaryMuscles`, etc.

**RecipeAiPreview fields (bridges from Plan 08 Kotlin data classes):**

- `.recipe: StagedRecipe` — `.name: String`, `.ingredients: [StagedRecipeIngredient]`, `.steps: [String]`
- `StagedRecipeIngredient`: `.foodName: String`, `.amountGrams: Double`
- `.inlineNewFoods: [StagedFood]` — each with `.name`, `.calories`, `.protein`, `.fat`, `.carbohydrates`, `.sugar`
- `.fitsIndicator: MacrosFitIndicator` — `.deltaKcalPercent`, `.deltaProteinPercent`, `.deltaFatPercent`, `.deltaCarbsPercent`, `.deltaSugarPercent` (all `Double`); `.fitsAll: Bool`

**Sheet layout:**

```swift
struct AiPreviewSheet: View {
    let content: AiPreviewContent
    let onSaveAll: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    switch content {
                    case .workout(let preview):
                        WorkoutPreviewBody(preview: preview)
                    case .recipe(let preview):
                        RecipePreviewBody(preview: preview)
                    }
                }
                .padding()
            }
            .navigationTitle("Vorschau")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Verwerfen", role: .destructive) { onDiscard() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Alle speichern") { onSaveAll() }
                        .bold()
                }
            }
        }
    }
}
```

**Workout preview body** — for each template: a section with the template name as header, listing exercise rows (`\(ex.targetSets)×\(ex.targetReps) — \(ex.exerciseName) — \(ex.restPeriodSec)s Pause`).

**Recipe preview body** — recipe name as title, ingredients list (`\(Int(ing.amountGrams))g \(ing.foodName)`), steps list, MacrosFitIndicator row per macro:

```swift
// Per macro row (5 rows):
HStack {
    Text("Kalorien")
    Spacer()
    Text("\(delta >= 0 ? "+" : "")\(String(format: "%.0f", delta))%")
        .foregroundColor(abs(delta) <= 10 ? .secondary : .orange)
}
// Summary line:
Text(preview.fitsIndicator.fitsAll ? "Passt zu deinen Zielen" : "Weicht von deinen Zielen ab")
    .foregroundColor(preview.fitsIndicator.fitsAll ? .accentColor : .orange)
```

---

## Per-class error copy (German — same as Android)

| `AiError` subclass | Title | Action button label | Action |
|--------------------|-------|---------------------|--------|
| `AiErrorTimeout` | "Generierung hat zu lange gedauert." | "Wiederholen" | `viewModel.retryFromError()` |
| `AiErrorNetwork` | "Keine Internetverbindung." | "Wiederholen" | `viewModel.retryFromError()` |
| `AiErrorAuthOrQuota` | "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht." | "KI-Einstellungen öffnen" | push `AiSettingsView()` |
| `AiErrorProvider` | "Der KI-Anbieter hat ein Problem." | "Wiederholen" | `viewModel.retryFromError()` |
| `AiErrorSchemaInvalid` | "Die KI-Antwort war nicht verwertbar." | "Wiederholen" | `viewModel.retryFromError()` |
| `AiErrorCancelled` | (never reached as Error — VM transitions to Form instead) | — | — |

**Swift pattern-matching AiError:** KMP-Native flat-exports sealed subclasses. Inspect `Shared.framework` headers after building for exact names. Likely: `AiError.Timeout`, `AiError.Network`, etc. (nested under `AiError`). Use `is` casting:

```swift
func errorTitle(for error: AiError) -> String {
    if error is AiError.Timeout    { return "Generierung hat zu lange gedauert." }
    if error is AiError.Network    { return "Keine Internetverbindung." }
    if error is AiError.AuthOrQuota { return "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht." }
    if error is AiError.Provider   { return "Der KI-Anbieter hat ein Problem." }
    return "Die KI-Antwort war nicht verwertbar."
}
```

---

## SettingsView modification

Add a new `Section("KI")` with one NavigationLink row, placed after the existing `Section("Gamification")` block:

```swift
Section("KI") {
    NavigationLink {
        AiSettingsView()
    } label: {
        Label("KI-Einstellungen", systemImage: "sparkles")
    }
}
```

`SettingsView.swift` currently has the `NavigationStack` inside it — `AiSettingsView` pushes naturally into that stack. No additional wiring needed.

---

## MainTabView modifications

The current `MainTabView.swift` already wraps each tab's root in a `NavigationStack`. Add a sparkles toolbar item to the **Workout tab** and the **Nutrition tab**:

**Workout tab** — currently:
```swift
NavigationStack {
    TemplateListView()
}
```

After modification:
```swift
NavigationStack {
    TemplateListView()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(destination: AiWorkoutGenView()) {
                    Image(systemName: "sparkles")
                }
            }
        }
}
```

**Nutrition tab** — currently:
```swift
NavigationStack {
    NutritionDailyLogView()
}
```

After modification:
```swift
NavigationStack {
    NutritionDailyLogView()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(destination: AiMealGenView()) {
                    Image(systemName: "sparkles")
                }
            }
        }
}
```

**Note:** if `TemplateListView` or `NutritionDailyLogView` already attach their own `.toolbar`, add the new `ToolbarItem` inside that existing toolbar block instead of adding a second `.toolbar` modifier to the parent — SwiftUI can merge them but a single toolbar per stack is cleaner.

---

## Critical security guardrails

1. **Never log the API key.** Zero `print(keyDraft)` or `print("key: \(key)")` patterns anywhere. The raw key value never reaches the SwiftUI side — only `apiKeyConfiguredFlow: Bool` does.

2. **Clear `keyDraft` immediately after save.** Right after calling `viewModel.setApiKey(value: keyDraft)`, set `keyDraft = ""`. This mirrors the Android `keyDraft = ""` pattern in `AiSettingsScreen.kt` (T-18-05-05).

3. **Never echo the key in error UIs.** The `AuthOrQuota` copy is generic ("ungültig oder Kontingent aufgebraucht") — do NOT include the key value in any displayed string.

4. **HTTPS-only base URL.** The base-URL `TextField` MUST check `baseUrlDraft.hasPrefix("https://")` before calling `viewModel.setBaseUrl(url:)`. Show an inline `Text("Nur HTTPS-URLs erlaubt.").foregroundColor(.red)` if the user types `http://`. Save button stays disabled until the prefix is valid. Mirrors the Android `T-18-05-03` mitigation.

5. **`keyDraft` in `@State` only.** Never store `keyDraft` in a persistent store, UserDefaults, or pass it into a ViewModel property other than through `setApiKey(value:)`.

---

## Already shipped (do NOT re-add)

- `expect class SecureKeyStore` + iOS Keychain actual — Plan 04. The user does NOT write Keychain code; the KMP shared layer handles it.
- `OpenAICompatibleClient` — Plan 02.
- Versioned prompt files — Plan 03. (**The user DOES add them to the iOS bundle via pbxproj — but Plan 03 already did this in the executor.** Verify rather than redo.)
- `AiSettingsKoinHelper` — Plan 05.
- `WorkoutAiKoinHelper` — Plan 06.
- `RecipeAiKoinHelper` — Plan 08.

---

## Reference — iOS entry-point for each surface

| iOS view | Android source reference | Kotlin VM source |
|----------|--------------------------|------------------|
| `AiSettingsView` | `androidApp/.../ui/screens/AiSettingsScreen.kt` | `shared/.../presentation/ai/AiSettingsViewModel.kt` |
| `AiWorkoutGenView` | `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` | `shared/.../presentation/ai/WorkoutAiViewModel.kt` |
| `AiMealGenView` | `androidApp/.../ui/screens/AiMealGenScreen.kt` | `shared/.../presentation/ai/RecipeAiViewModel.kt` |
| `AiPreviewSheet` | `androidApp/.../ui/screens/AiPreviewSheet.kt` | both VMs above |
| Sealed UiState branches | — | `WorkoutAiViewModel.kt` / `RecipeAiViewModel.kt` |
| Per-class error copy | `AiWorkoutGenScreen.kt` `ErrorBody` composable | `domain/ai/AiError.kt` |

---

## Acceptance checklist (UAT — user-driven)

When all of these tick, Phase 18 is done on iOS.

- [ ] Build: iOS target compiles clean with no new warnings.
- [ ] AI Settings round-trip: enter key, force-quit, relaunch → key persists, "Gespeichert" caption shows. Key field is blank on relaunch (draft cleared).
- [ ] Provider preset switch: choose Together.AI → base URL and model fields update to Together.AI defaults.
- [ ] Custom preset: choose Benutzerdefiniert → base URL field becomes editable; type `http://` → red warning appears, save button disabled; change to `https://` → warning disappears, save enabled.
- [ ] F6 NoKey: tap Workout sparkles button without a key → NoKey empty state with "KI-Einstellungen öffnen" button navigates to AI Settings.
- [ ] F6 happy path (None split): form → select 1+ muscle groups → tap Generieren → skeleton rows appear → preview sheet shows 1 template → tap "Alle speichern" → sheet dismisses → template appears in TemplateList.
- [ ] F6 happy path (PPL split): same but split = Push-Pull-Legs → preview shows 3 templates → save all.
- [ ] F6 cancel: tap Generieren → tap Abbrechen → skeleton disappears, form reappears — no error toast.
- [ ] F6 Discard: from preview sheet, tap "Verwerfen" → sheet dismisses, form reappears.
- [ ] F8 NoKey: tap Nutrition sparkles without a key → NoKey state.
- [ ] F8 RemainingExhausted: log enough food to exhaust daily kcal → tap sparkles → "Du hast deine Tagesziele bereits erreicht." shows.
- [ ] F8 happy path: log some food, tap sparkles → Form with remaining macros card → "Restliche Makros füllen" → generating → preview sheet → save → recipe appears in Recipe list.
- [ ] F8 MacrosFitIndicator: at least one delta percentage row is visible in the preview sheet.
- [ ] Error Timeout copy: all 5 AiError classes render the correct German copy and correct action button.
- [ ] No API key value visible in any error message or log output.

---

*Phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op*
*Handoff authored: Plan 18-10 executed 2026-05-08*

---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: 10
type: execute
wave: 8
depends_on: [05, 06, 07, 08, 09]
files_modified:
  - .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md
autonomous: true
requirements:
  - REQ-AI-01
  - REQ-AI-04
  - REQ-AI-06
  - REQ-AI-08
user_setup: []

must_haves:
  truths:
    - "An 18-IOS-HANDOFF.md doc exists specifying the SwiftUI surfaces the user will hand-write per D-17-18 / D-18 KMP-shared-VM convention"
    - "The doc enumerates all NEW SwiftUI files (AiSettingsView, AiWorkoutGenView, AiMealGenView, AiPreviewSheet) and all MODIFY surfaces (SettingsView, MainTabView)"
    - "The doc lists the Kotlin contract per VM (KoinHelper class name, exposed flow names, action method names)"
    - "The doc enumerates German UI copy for each error class so the iOS surfaces stay consistent with Android"
    - "The doc flags the prompt .md files as needing pbxproj target membership"
    - "The doc notes that no new Info.plist permissions are required (HTTPS only, no camera / biometric)"
  artifacts:
    - path: ".planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md"
      provides: "iOS SwiftUI implementation spec"
      contains: "18-IOS-HANDOFF"
      min_lines: 200
  key_links:
    - from: "18-IOS-HANDOFF.md"
      to: "AiSettingsKoinHelper / WorkoutAiKoinHelper / RecipeAiKoinHelper"
      via: "iOS factory contracts"
      pattern: "KoinHelper"
---

<objective>
Produce `18-IOS-HANDOFF.md` — the spec the user uses to hand-write the iOS SwiftUI surfaces. Mirrors the structure of `17-IOS-HANDOFF.md` and `15.1-IOS-HANDOFF.md` per the project's KMP-shared-VM + native-UI convention (D-17-18 — explicit SwiftUI on iOS, NOT Compose Multiplatform).

The doc covers:
1. **What to build** — 4 new SwiftUI views (AiSettingsView, AiWorkoutGenView, AiMealGenView, AiPreviewSheet) + 2 modifications (SettingsView gains an "AI" NavigationLink; MainTabView gains 2 toolbar items on Workout + Nutrition tabs).
2. **Kotlin contracts per VM** — KoinHelper class names, the StateFlow / SharedFlow names exported via @NativeCoroutinesState (`uiStateFlow`, etc.), the action method signatures, and the sealed UiState variants (with the `is`-castable type names per Phase 15 STATE.md export convention).
3. **UI copy table** — German strings for each AiError class, mirroring the Android implementations.
4. **pbxproj concerns** — the two prompt `.md` files (`workout-system-prompt.md`, `recipe-system-prompt.md`, both flat at `shared/src/commonMain/resources/`, NO subdirectory) MUST be added to the iOS target's bundle resources (executor inspects the existing `free_exercise_db.json` reference in pbxproj as a precedent). Files MUST be flat — `Platform.ios.kt`'s `pathForResource(name, ext)` does not interpret a `/` in the name as a subdirectory.
5. **Security guardrails** — never log the API key, never store it in @State, never echo it in error UIs.
6. **No new Info.plist permissions** — HTTPS-only transport, no camera, no biometric. The existing ATS default rejects HTTP — fine because Settings layer rejects non-HTTPS base URLs (Plan 05).

Purpose: Per-MEMORY.md "user is iOS-focused, not an Android dev" and "execute inline when source files exceed ~10K tokens" — the planner produces a SPEC, not the SwiftUI code itself. Implements the iOS half of REQ-AI-01 / REQ-AI-04 / REQ-AI-06 / REQ-AI-08. The user will hand-write the views following this spec.
Output: One markdown file under `.planning/phases/18-.../`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS-ai-features.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-CONTEXT.md
@.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-PATTERNS.md
@.planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md
@.planning/phases/15.1-ranks-achievements-browser/15.1-IOS-HANDOFF.md
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
@shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/AiSettingsKoinHelper.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/WorkoutAiKoinHelper.kt
@shared/src/iosMain/kotlin/com/pumpernickel/di/RecipeAiKoinHelper.kt
@iosApp/iosApp/Views/MainTabView.swift
@iosApp/iosApp/Views/Settings/SettingsView.swift
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Write 18-IOS-HANDOFF.md following the Phase 17 / 15.1 doc structure</name>
  <files>.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md</files>
  <read_first>
    .planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md,
    .planning/phases/15.1-ranks-achievements-browser/15.1-IOS-HANDOFF.md,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt,
    shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiWorkoutGenScreen.kt,
    androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiMealGenScreen.kt,
    iosApp/iosApp/Views/MainTabView.swift,
    iosApp/iosApp/Views/Settings/SettingsView.swift
  </read_first>
  <action>
Create `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md`.

Use this structure (follow exactly — match the Phase 17 doc's section ordering):

```markdown
# Phase 18: iOS Handoff — AI Features (F6 + F8 BYOK)

**Audience:** the user (iOS-focused) hand-writing the SwiftUI surfaces.
**Scope:** 4 new SwiftUI views + 2 modifications. No Compose Multiplatform on iOS (D-17-18 / D-18 KMP-shared-VM convention).
**Convention:** mirror the structure of `17-IOS-HANDOFF.md` (Progress-pic feature) and the existing iOS surfaces under `iosApp/iosApp/Views/`.

## What you are building

| Status | File | Purpose |
|--------|------|---------|
| NEW | `iosApp/iosApp/Views/AI/AiSettingsView.swift` | Provider preset / API key / base URL / model — analog to Android `AiSettingsScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiWorkoutGenView.swift` | F6 workout-AI form, skeleton, error states — analog to `AiWorkoutGenScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiMealGenView.swift` | F8 meal-AI flow, RemainingExhausted state, error states — analog to `AiMealGenScreen.kt` |
| NEW | `iosApp/iosApp/Views/AI/AiPreviewSheet.swift` | Bottom sheet rendering both Workout and Recipe previews — analog to `AiPreviewSheet.kt` |
| MODIFY | `iosApp/iosApp/Views/Settings/SettingsView.swift` | Add an "AI" NavigationLink → `AiSettingsView()` |
| MODIFY | `iosApp/iosApp/Views/MainTabView.swift` | Add a sparkles toolbar item to the Workout-tab and Nutrition-tab roots |

## pbxproj concerns

- Add the new SwiftUI files to the `iosApp` target via Xcode > Project Navigator > drag the AI/ folder in. Match the existing target membership (e.g. how `Views/Overview/ProgressGalleryView.swift` is registered in `iosApp.xcodeproj/project.pbxproj`).
- **Add the prompt files** `shared/src/commonMain/resources/workout-system-prompt.md` and `shared/src/commonMain/resources/recipe-system-prompt.md` (both flat — no subdirectory) to the `iosApp` target's bundle resources. Follow the precedent of `free_exercise_db.json`. Without this step, `readResourceFile("workout-system-prompt.md")` will throw at runtime on iOS. The flat naming is mandatory because `Platform.ios.kt` calls `NSBundle.pathForResource(name, ext)`, which does not interpret a `/` in the resource name as a subdirectory.

## No new Info.plist permissions

- No camera, no biometric, no photo library, no contacts. HTTPS-only transport.
- ATS (App Transport Security) defaults reject `http://`. The Settings layer rejects non-HTTPS base URLs (Plan 05 / T-18-04-04). No Info.plist exception needed.

## Kotlin contracts (what the SwiftUI views consume)

### `AiSettingsView` — backed by `AiSettingsViewModel` via `AiSettingsKoinHelper`

KoinHelper:
```kotlin
class AiSettingsKoinHelper {
    fun getAiSettingsViewModel(): AiSettingsViewModel = KoinPlatform.getKoin().get()
}
```

Swift acquisition:
```swift
private let viewModel = AiSettingsKoinHelper().getAiSettingsViewModel()
```

Exposed flows (read via `asyncSequence(for:)` per project convention):
- `viewModel.providerPresetFlow` (`StateFlow<String>`) — one of `openai`, `together`, `openrouter`, `groq`, `custom`.
- `viewModel.baseUrlFlow` (`StateFlow<String>`)
- `viewModel.modelFlow` (`StateFlow<String>`)
- `viewModel.apiKeyConfiguredFlow` (`StateFlow<Boolean>`)

Actions:
- `viewModel.setApiKey(value: String)`
- `viewModel.clearApiKey()`
- `viewModel.setProviderPreset(preset: String)` — auto-fills baseUrl + model
- `viewModel.setBaseUrl(url: String)` — VIEW MUST validate `https://` prefix
- `viewModel.setModel(value: String)`

UI elements (all in German):
- `Form { Section("Anbieter") { Picker } Section("API-Schlüssel") { SecureField + show toggle + Speichern + Löschen } Section("Basis-URL") { TextField, disabled unless preset == custom } Section("Modell") { TextField } }`
- API key field uses `SecureField`; show/hide toggle swaps between `SecureField` and `TextField`.
- After successful `setApiKey(...)`, clear the local @State binding to prevent the key sitting in memory.
- Show "Gespeichert" / "Kein Schlüssel gespeichert" caption from `apiKeyConfiguredFlow`.

### `AiWorkoutGenView` — backed by `WorkoutAiViewModel` via `WorkoutAiKoinHelper`

KoinHelper: `WorkoutAiKoinHelper().getWorkoutAiViewModel()`.

Exposed flow:
- `viewModel.uiStateFlow` (`StateFlow<WorkoutAiUiState>`) — sealed class.

Sealed UiState branches (`is`-castable Swift types — verify against generated header by inspecting `Shared.framework`'s headers; the Phase 15 STATE.md decision noted KMP-Native flat-export of nested sealed subclasses uses paths like `Shared.WorkoutAiUiState.Form`):
- `WorkoutAiUiStateNoKey` — no key configured.
- `WorkoutAiUiStateForm(targetMuscles, exerciseCount, splitStyle)`
- `WorkoutAiUiStateGenerating(skeletonRowCount)`
- `WorkoutAiUiStatePreview(preview, originatingForm)`
- `WorkoutAiUiStateError(error, originatingForm)` — `error: AiError` sealed.
- `WorkoutAiUiStateSaved(templateIds)`

Actions:
- `viewModel.onMusclesChanged(muscles: [MuscleGroup])`
- `viewModel.onExerciseCountChanged(count: Int32)`
- `viewModel.onSplitStyleChanged(split: WorkoutAiSplit)`
- `viewModel.generate()`
- `viewModel.cancel()`
- `viewModel.save()`
- `viewModel.discardPreview()`
- `viewModel.retryFromError()`

UI structure (all German):
- `NoKey` → centred VStack with "Du hast noch keinen API-Schlüssel" + button "AI-Einstellungen öffnen" → push `AiSettingsView()`.
- `Form` → VStack with: a multi-select MuscleGroup chip cluster (use `MuscleRegionPaths` if you want to mirror the Android anatomy view, or a SwiftUI `LazyHGrid` of toggleable chips — your call); a `Picker` or segmented `Picker` for exercise count (3..8); a segmented `Picker` for split style (None / PPL / Upper-Lower / Full Body); a `Button("Generieren")`, disabled when targetMuscles.isEmpty.
- `Generating` → 5-N skeleton placeholder rows (static — no shimmer per D-18-16) + `Button("Abbrechen")`.
- `Preview` → push `AiPreviewSheet` as `.sheet(isPresented:)` with `Workout(preview)` content.
- `Error(error)` → switch on `AiError` cases; render the per-class copy table (below) and the corresponding action button.
- `Saved` → `dismiss()` (or `presentationMode.wrappedValue.dismiss()`) on appearance.

### `AiMealGenView` — backed by `RecipeAiViewModel` via `RecipeAiKoinHelper`

KoinHelper: `RecipeAiKoinHelper().getRecipeAiViewModel()`.

Exposed flow:
- `viewModel.uiStateFlow` (`StateFlow<RecipeAiUiState>`) — sealed class.

Sealed branches:
- `RecipeAiUiStateLoading`
- `RecipeAiUiStateNoKey`
- `RecipeAiUiStateRemainingExhausted(remaining)`
- `RecipeAiUiStateForm(remaining)`
- `RecipeAiUiStateGenerating`
- `RecipeAiUiStatePreview(preview, originatingRemaining)`
- `RecipeAiUiStateError(error, originatingRemaining?)`
- `RecipeAiUiStateSaved`

Actions:
- `viewModel.refreshRemaining()`
- `viewModel.generate()`
- `viewModel.cancel()`
- `viewModel.save()`
- `viewModel.discardPreview()`
- `viewModel.retryFromError()`

UI structure:
- `Loading` → centred `ProgressView()`.
- `NoKey` / `Error` → mirror the AiWorkoutGenView shape with the same per-class error copy.
- `RemainingExhausted` → centred VStack: "Du hast deine Tagesziele bereits erreicht." + small subtitle showing `remaining.kcal.toInt()` kcal verbleibend.
- `Form` → VStack: a card listing the 5 remaining macros (kcal, protein, fat, carbs, sugar — use `Int(remaining.kcal)` etc.); `Button("Restliche Makros füllen")`.
- `Generating` → 5 placeholder rows + `Button("Abbrechen")`.
- `Preview` → `.sheet` with `AiPreviewSheet` content `Recipe(preview)`.
- `Saved` → dismiss.

### `AiPreviewSheet` — sealed `AiPreviewContent`

Two callers (F6 and F8). Mirror the Android sealed-content pattern:

```swift
enum AiPreviewContent {
    case workout(WorkoutAiPreview)
    case recipe(RecipeAiPreview)
}
```

The sheet renders:
- Header: "Vorschau"
- Body: per-content-type rendering (workout = list of templates; recipe = ingredients + steps + fit indicator).
- Footer: `Button("Alle speichern")` → `onSaveAll` callback; `Button("Verwerfen")` → `onDiscard` callback.

For Recipe content, render the `MacrosFitIndicator` deltas (deltaKcalPercent, deltaProteinPercent, etc.) as 5 right-aligned rows showing `+X%` / `-X%`. Use `.foregroundColor(fitsAll ? .accentColor : .orange)` for the summary label.

## Per-class error copy (German — same as Android)

| AiError | Title | Action button | Action behavior |
|---------|-------|---------------|-----------------|
| `Timeout` | "Generierung hat zu lange gedauert." | "Wiederholen" | call `viewModel.retryFromError()` |
| `Network` | "Keine Internetverbindung." | "Wiederholen" | call `viewModel.retryFromError()` |
| `AuthOrQuota` | "Dein API-Schlüssel ist ungültig oder das Kontingent ist aufgebraucht." | "AI-Einstellungen öffnen" | push `AiSettingsView()` |
| `Provider` | "Der KI-Anbieter hat ein Problem." | "Wiederholen" | call `viewModel.retryFromError()` |
| `SchemaInvalid` | "Die KI-Antwort war nicht verwertbar." | "Wiederholen" | call `viewModel.retryFromError()` |
| `Cancelled` | (never reached as Error — VM transitions back to Form) | — | — |

## SettingsView modification

Add a new `Section("AI")` with one row:

```swift
Section("KI") {
    NavigationLink {
        AiSettingsView()
    } label: {
        Label("KI-Einstellungen", systemImage: "sparkles")
    }
}
```

Place it after the existing `Section("Gamification")` block. The section title and label are German.

## MainTabView modifications

Add a sparkles toolbar item to two tabs:

**Workout tab** (currently wraps `TemplateListView()`):
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

**Nutrition tab** (currently wraps `NutritionDailyLogView()`):
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

Note: if `TemplateListView` or `NutritionDailyLogView` already attaches its own `.toolbar` modifier, add the new ToolbarItem to that existing toolbar instead of stacking a parent .toolbar — SwiftUI merges them but readability is better with a single toolbar per stack.

## Critical security guardrails

1. **Never log the API key.** No `print(viewModel.apiKey)` or `print("token: \\(token)")` patterns. The key value never even reaches the SwiftUI side — only `apiKeyConfiguredFlow: Boolean` does.
2. **Never store the key in `@State`.** The local draft `@State var keyDraft: String` is fine during entry, but immediately after `viewModel.setApiKey(keyDraft)`, set `keyDraft = ""`.
3. **Never echo the key in error UIs.** The AuthOrQuota copy is generic ("ungültig oder Kontingent aufgebraucht") — do NOT include the key value.
4. **HTTPS-only.** The base-URL TextField MUST validate `text.hasPrefix("https://")` before calling `viewModel.setBaseUrl(...)`. Show an inline warning if the user types `http://`.

## Already shipped (do NOT re-add)

- `expect class SecureKeyStore` + iOS Keychain actual — Plan 04. The user does not write Keychain code; the KMP shared layer handles it.
- `OpenAICompatibleClient` — Plan 02.
- Versioned prompt files — Plan 03 (the user does add them to the iOS target via pbxproj — see "pbxproj concerns" above).
- All three KoinHelpers — Plans 05/06/08.

## Acceptance after iOS implementation

UAT (user-driven, no Claude action):
1. AI Settings round-trip: enter key, force-quit, relaunch, key persists.
2. F6 happy path: form → generate → preview (1 template for None, 3 for PPL) → save → templates appear in TemplateList.
3. F8 happy path: log some food, sparkles → form shows remaining → generate → preview → save → recipe appears in RecipeList.
4. F8 RemainingExhausted: log enough food to drop remaining < 100 kcal → sparkles → empty state shows.
5. All five AiError classes display the correct copy + correct action button.
6. Cancel during Generating returns to Form (no error toast).
7. No crash on rapid back-navigation during in-flight generation.

## File / line references for the implementer

| Surface | Android source (reference) |
|---------|---------------------------|
| AiSettingsView | `androidApp/.../ui/screens/AiSettingsScreen.kt` |
| AiWorkoutGenView | `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` |
| AiMealGenView | `androidApp/.../ui/screens/AiMealGenScreen.kt` |
| AiPreviewSheet | `androidApp/.../ui/screens/AiPreviewSheet.kt` |
| Sealed UiState | `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/{WorkoutAi,RecipeAi}ViewModel.kt` |
| AiError → SwiftUI copy | `androidApp/.../ui/screens/AiWorkoutGenScreen.kt` `ErrorBody` Triple |
```

Word count target: ~250 lines. Follow the structure of `17-IOS-HANDOFF.md` precisely; do not invent new section names. The doc is consumed by the user as a hand-off spec; clarity beats completeness.
  </action>
  <verify>
    <automated>test -f .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md && grep -E "AiSettingsView|AiWorkoutGenView|AiMealGenView|AiPreviewSheet" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md && grep -E "AiSettingsKoinHelper|WorkoutAiKoinHelper|RecipeAiKoinHelper" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` exists.
    - `wc -l .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `200`.
    - `grep -c "AiSettingsView" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `4`.
    - `grep -c "AiWorkoutGenView" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `4`.
    - `grep -c "AiMealGenView" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `4`.
    - `grep -c "AiPreviewSheet" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `2`.
    - `grep -c "AiSettingsKoinHelper" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "WorkoutAiKoinHelper" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "RecipeAiKoinHelper" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "Generierung hat zu lange gedauert" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "Keine Internetverbindung" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "Du hast deine Tagesziele bereits erreicht" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "pbxproj" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `2`.
    - `grep -c "Info.plist" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1`.
    - `grep -c "free_exercise_db.json" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1` (precedent).
    - `grep -c "https://" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1` (HTTPS guardrail).
    - `grep -c "sparkles" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `2`.
    - `grep -c "Per-class error copy" .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` returns at least `1` (section heading).
  </acceptance_criteria>
  <done>18-IOS-HANDOFF.md exists with sections for what-to-build, Kotlin contracts per VM, error copy table, pbxproj concerns, and security guardrails — mirroring the Phase 17 doc structure.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Doc → user-implemented Swift | The doc is a SPEC; the user is responsible for adhering to it during implementation |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-18-10-01 | Repudiation | User implements iOS without reading the doc | accept | Out of scope — the GSD process relies on the user committing to follow handoff docs. Phase-17 precedent shows this works in practice. |
| T-18-10-02 | Information disclosure | Doc spec doesn't surface security guardrails | mitigate | Acceptance criteria require explicit "Critical security guardrails" section + grep of HTTPS / pbxproj / Info.plist tokens. |
</threat_model>

<verification>
- 18-IOS-HANDOFF.md exists with 200+ lines and all required sections.
- All 4 new SwiftUI files are spec'd with their KoinHelper, exposed flows, sealed-state branches, actions, and German UI copy.
- All 2 modifications (SettingsView + MainTabView) are spec'd.
- The pbxproj concern about prompt .md files is flagged.
- The HTTPS-only / no-permissions / never-log-key guardrails are explicit.
</verification>

<success_criteria>
- iOS implementer (the user) has a complete spec covering REQ-AI-01, REQ-AI-04, REQ-AI-06, REQ-AI-08 surfaces.
- All sealed state branches and per-class error copy are documented.
- Kotlin contracts (KoinHelpers + flow names + action methods) are exhaustive — no codebase exploration needed during iOS implementation.
- Bundle resource shipping (prompt .md files) is flagged as a manual pbxproj step.
</success_criteria>

<output>
After completion, the doc IS the SUMMARY for this plan; no separate `18-10-SUMMARY.md` needed unless the executor wants one. If created, the SUMMARY is one paragraph: "iOS handoff doc complete; covers 4 new SwiftUI views + 2 modifications; all KoinHelpers and per-class error copy documented; pbxproj concerns flagged."
</output>

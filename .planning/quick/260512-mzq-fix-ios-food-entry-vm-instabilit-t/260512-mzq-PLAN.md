---
phase: 260512-mzq
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
autonomous: true
requirements: [QUICK-260512-mzq]
must_haves:
  truths:
    - "NutritionFoodEntryView holds exactly one FoodEntryViewModel instance per SwiftUI view identity"
    - "Typing into the search TextField updates the same VM that the .task observer subscribes to"
    - "The debounce-collector receives the typed query string (e.g. 'hackfleisch') instead of repeated empty strings"
    - "Shared framework still links cleanly for iOS simulator (arm64)"
  artifacts:
    - path: "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift"
      provides: "Food-entry view with stable VM instance via @State"
      contains: "@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel"
  key_links:
    - from: "iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift"
      to: "FoodEntryViewModel (Koin factory)"
      via: "@State-owned single-init reference"
      pattern: "@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel"
---

<objective>
Fix the iOS Food-Entry VM-Instabilität bug where every parent re-render of `NutritionFoodEntryView` instantiated a fresh `FoodEntryViewModel` from the Koin `viewModel { … }` factory, causing the typed search string and the observer to bind to different VM instances.

Purpose: Restore the search → debounce → remote-fetch flow on iOS so typing "Hackfleisch" actually reaches the VM that the `.task` observer is collecting. Xcode console currently shows repeated `[FoodVM] debounce fired query='' length=0` — proof the typed text never lands in the observed VM state.

Output: One-line change in `NutritionFoodEntryView.swift` switching `private let viewModel = …` to `@State private var viewModel = …`, mirroring the already-correct pattern in `TemplateEditorView.swift:8`. SwiftUI's managed state storage will then hold the VM stable across re-renders for the lifetime of the view identity.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md

<interfaces>
<!-- Current (broken) declaration at NutritionFoodEntryView.swift:6 -->
```swift
import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct NutritionFoodEntryView: View {
    private let viewModel = KoinHelper.shared.getFoodEntryViewModel()
    // ^ `let` → recomputed each time the struct is reinitialized by SwiftUI's diff
    //   `getFoodEntryViewModel()` calls Koin which uses `viewModel { … }` registration
    //   On iOS there is no Android ViewModelProvider cache → factory returns a NEW instance every call
```

<!-- Target (fixed) declaration -->
```swift
struct NutritionFoodEntryView: View {
    @State private var viewModel = KoinHelper.shared.getFoodEntryViewModel()
    // ^ @State storage is keyed by view identity; the initializer runs exactly once per identity
    //   Same pattern already used correctly by TemplateEditorView.swift:8
```

<!-- Sibling reference (already correct) — do NOT modify -->
File: iosApp/iosApp/Views/Templates/TemplateEditorView.swift
Line 8: `@State private var viewModel = KoinHelper.shared.getTemplateEditorViewModel()`
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Switch viewModel declaration to @State in NutritionFoodEntryView</name>
  <files>iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift</files>
  <action>
Edit line 6 of `iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift`.

Change FROM:
```swift
    private let viewModel = KoinHelper.shared.getFoodEntryViewModel()
```

Change TO:
```swift
    @State private var viewModel = KoinHelper.shared.getFoodEntryViewModel()
```

That is the entire change. Touch nothing else in this file or anywhere else.

Do NOT modify:
- The `@State private var uiState = FoodEntryUiState(...)` block at lines 10–15
- Any other `@State` declarations or the `body`
- The ViewModel logic / debounce / Flow setup in shared code
- DI / Koin registration in `SharedModule.kt` (the `viewModel { … }` factory stays — the SwiftUI-side @State fixes the symptom without needing a DI refactor)
- `OpenFoodFacts` API or any UseCase
- The diagnostic `println` markers (`[OFF]`, `[SearchUC]`, `[FoodVM]`) — keep them in, they will be used to verify the fix landed (we should now see `[FoodVM] debounce fired query='hackfleisch' length=11` instead of repeated empty strings)
- Any other iOS view (TemplateEditorView, etc.)

Rationale: `FoodEntryViewModel` is registered as `viewModel { … }` in `SharedModule.kt`. On Android, the ViewModelProvider cache deduplicates `.get()` calls per ViewModelStoreOwner. On iOS there is no equivalent cache — Koin's `viewModel { … }` resolves like a factory: every call to `KoinHelper.shared.getFoodEntryViewModel()` builds a fresh instance. SwiftUI re-initializes the `NutritionFoodEntryView` struct on every parent re-render; with `private let`, the property initializer fires on each rebuild → new VM. The TextField binds to VM-A's `uiState`, but the `.task { for await … }` observer in the *previous* render closure was attached to VM-B's `_uiState` flow, so typed text never reaches the observed stream. `@State` parks the property in SwiftUI's managed storage, which is keyed by the view's identity — the initializer runs once per identity, so the same VM instance survives across re-renders and the observer + TextField finally share state. `TemplateEditorView.swift:8` already uses this pattern correctly.
  </action>
  <verify>
    <automated>cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp && test "$(grep -c '@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel' iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift)" = "1" && test "$(grep -c 'private let viewModel = KoinHelper.shared.getFoodEntryViewModel' iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift)" = "0" && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet</automated>
  </verify>
  <done>
- `grep -c "@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel" iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` returns `1`
- `grep -c "private let viewModel = KoinHelper.shared.getFoodEntryViewModel" iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` returns `0`
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --quiet` exits 0
- No other lines in the file changed (diff shows exactly one modified line with the `let` → `@State … var` swap)
  </done>
</task>

</tasks>

<verification>
After Task 1 lands:

1. Static checks (run by the automated verify above):
   - Exactly one `@State private var viewModel = KoinHelper.shared.getFoodEntryViewModel` in the file
   - Zero `private let viewModel = KoinHelper.shared.getFoodEntryViewModel` in the file
   - `:shared:linkDebugFrameworkIosSimulatorArm64` links cleanly

2. Diff sanity:
   - `git diff iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift` shows exactly one changed line (modulo whitespace)

3. Runtime behavior (manual, optional — diagnostic markers still in place):
   - Launch app on iOS simulator, navigate to Nutrition → Food Entry → Suchen tab
   - Type "Hackfleisch" into the search field
   - Xcode console should now show `[FoodVM] debounce fired query='hackfleisch' length=11` (instead of the prior `query='' length=0` repeats)
   - Search results from OFF should populate the list
</verification>

<success_criteria>
- One-line edit applied to `NutritionFoodEntryView.swift` (line 6: `private let` → `@State private var`)
- Automated grep + gradle link verification all pass
- No collateral changes anywhere else in the repo
- Commit message: `fix(ios): use @State for FoodEntryViewModel — Koin factory created new VM per render`
</success_criteria>

<output>
After completion, create `.planning/quick/260512-mzq-fix-ios-food-entry-vm-instabilit-t/260512-mzq-01-SUMMARY.md`
</output>

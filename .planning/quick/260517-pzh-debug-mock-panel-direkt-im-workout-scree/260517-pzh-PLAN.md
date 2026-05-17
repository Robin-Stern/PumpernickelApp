---
phase: quick-260517-pzh
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
autonomous: false
requirements: [DEBUG-MOCK-IN-WORKOUT]

must_haves:
  truths:
    - "In a DEBUG build of iOS, while a workout is active, a floating debug affordance is visible in WorkoutSessionView and does NOT cover the GeofenceStatusChip, ellipsis menu, close (X), or the Complete-Set button"
    - "In a DEBUG build of Android, while a workout is active, a floating debug affordance is visible in WorkoutSessionScreen and does NOT cover the GeofenceStatusChip, MoreVert menu, Close icon, or the Complete-Set button"
    - "Tapping the floating affordance opens a modal containing the SAME DebugGeofencePanel that Settings already shows (no UI duplication)"
    - "Inside the modal, Enter / Exit / Error buttons emit on DebugGeofenceProvider for the current active region id (same wiring as the Settings panel)"
    - "Triggering Exit from the in-workout panel causes the GeofenceStatusChip in the same screen to transition to GracePeriod with a countdown (verifies that the modal is presenting the live, real provider instance — not a phantom)"
    - "In a RELEASE build on both platforms, the floating affordance is absent and the source paths for it are not compiled in (compile-time gate, not runtime)"
    - "No code is duplicated between Settings panel and in-workout overlay — both call sites mount the exact same DebugGeofencePanel component"
  artifacts:
    - path: "iosApp/iosApp/Views/Workout/WorkoutSessionView.swift"
      provides: "Floating DEBUG button overlay on the active-workout SwiftUI view + sheet hosting DebugGeofencePanel"
      contains: "#if DEBUG"
    - path: "androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt"
      provides: "Floating Material 3 button + ModalBottomSheet hosting DebugGeofencePanel during active workout"
      contains: "BuildConfig.DEBUG"
  key_links:
    - from: "iosApp/iosApp/Views/Workout/WorkoutSessionView.swift floating button onTap"
      to: "iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift"
      via: ".sheet presenting DebugGeofencePanel"
      pattern: "DebugGeofencePanel\\(\\)"
    - from: "androidApp/.../WorkoutSessionScreen.kt floating button onClick"
      to: "androidApp/.../ui/components/DebugGeofencePanel.kt"
      via: "ModalBottomSheet hosting DebugGeofencePanel"
      pattern: "DebugGeofencePanel\\("
---

<objective>
Surface the existing DebugGeofencePanel (Enter/Exit/Error trigger buttons against DebugGeofenceProvider) **inside the active workout screen** on both iOS and Android, gated to DEBUG builds only.

**Why:** The user cannot navigate to Settings while mid-workout (workout session is a modal/fullscreen flow). Without an in-workout trigger, the Phase 19 Exit → GracePeriod → Auto-Abort → Notification flow cannot be UAT'd on Simulator/Emulator. The existing Settings panel from quick-260516-nfn is only reachable BEFORE starting a workout — useless for testing the runtime grace-period state machine.

**Output:**
- iOS: a small "🐛 DEBUG" floating pill overlay (ZStack overlay or `.overlay`) on `activeWorkoutView` in `WorkoutSessionView.swift`, opening a `.sheet` that renders the existing `DebugGeofencePanel` SwiftUI struct.
- Android: a small Material 3 floating button (Scaffold.floatingActionButton with `FloatingActionButton` or `SmallFloatingActionButton`) on the active-state branch of `WorkoutSessionScreen.kt`, opening a `ModalBottomSheet` that renders the existing `DebugGeofencePanel` Composable.
- Both gated by compile-time DEBUG flags (`#if DEBUG` on iOS, `if (BuildConfig.DEBUG)` on Android).
- Zero new business logic, zero duplicated UI code — both call sites mount the existing panel components verbatim.

**Hard constraints:**
- The floating affordance MUST NOT overlap or visually compete with: TopAppBar (close button, GeofenceStatusChip, MoreVert/ellipsis), the Complete-Set button (bottom of set-input section), the picker columns, the rest-timer view, or the completed-set rows.
- Recommended placement: **bottom-leading** (bottom-left) at ~16dp padding, OR top-leading just under the TopAppBar — both keep clear of the primary action zone (bottom-right "Complete Set" button) and the toolbar's trailing chip+menu cluster.
- The affordance MUST only appear when the workout is in the `Active` state (not Idle / Reviewing / Finished / loading) — otherwise the regionId display is meaningless and the user is in a different flow.
- DO NOT reimplement the trigger UI. Import and mount `DebugGeofencePanel` (Swift struct) / `DebugGeofencePanel` (Kotlin composable) from the existing files. If the existing components need a tiny prop tweak (e.g. an optional `modifier` or a "dismiss" callback for the modal), add it — but no copy-paste of the Enter/Exit/Error logic.

**Out of scope (deferred):**
- Auto-dismissing the modal after a trigger.
- Showing the panel in any state other than `Active`.
- Adding new trigger types beyond Enter/Exit/Error.
- Reworking the existing Settings panel placement.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/quick/260516-nfn-debug-gps-mock-f-r-phase-19-geofencing-b/260516-nfn-SUMMARY.md
@iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
@iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
@androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt

<interfaces>
<!-- Contracts the executor needs. Already in codebase — DO NOT re-explore. -->

iOS — `DebugGeofencePanel` (SwiftUI struct, file: iosApp/iosApp/Views/Settings/DebugGeofencePanel.swift):
```swift
#if DEBUG
struct DebugGeofencePanel: View {
    // Self-contained. Reads activeRegionId from
    // KoinHelper.shared.getDebugGeofenceProvider()?.lastRegisteredRegionId
    // on .onAppear. Renders a SwiftUI `Section("DEBUG — Geofence Mock")` with
    // three buttons: Enter / Exit / Error. Disabled when no active workout or
    // provider is not DebugGeofenceProvider.
    var body: some View { Section("DEBUG — Geofence Mock") { ... } }
}
#endif
```
Important: the panel's body is a `Section` — designed to be embedded in a `Form`. When presented in a `.sheet`, it MUST be wrapped in a `NavigationStack { Form { DebugGeofencePanel() } }` (or `List`) so the Section renders. A bare `DebugGeofencePanel()` in a sheet body will not render correctly because `Section` outside a container yields no visible chrome.

Android — `DebugGeofencePanel` (Compose composable, file: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt):
```kotlin
@Composable
fun DebugGeofencePanel(modifier: Modifier = Modifier)
// Self-contained. Uses koinInject() to resolve GeofenceProvider + WorkoutRepository,
// reads active region id from workoutRepository.getActiveSession() in LaunchedEffect(Unit).
// Renders a Column with the title text, region-id line, and Enter/Exit/Error buttons.
```
Safe to render directly inside a `ModalBottomSheet` content scope — no extra wrapper needed.

iOS — `WorkoutSessionView` body shape (file: iosApp/iosApp/Views/Workout/WorkoutSessionView.swift):
- Top-level `body` is a `Group { ... }` switching on sessionState — `Active` branch calls `activeWorkoutView(active)` (line ~185).
- `activeWorkoutView(_:)` returns a `ScrollView { VStack { ... } }` wrapped with `.navigationTitle / .toolbar / .sheet(isPresented: $showExerciseOverview)` / `.sheet(isPresented: $showEditSheet)` / `.sheet(isPresented: $showRationaleSheet)` / `.confirmationDialog(...)` etc.
- Existing pattern for modals: `@State private var showFoo = false` + `.sheet(isPresented: $showFoo) { ... }`.
- Existing pattern for an overlay: none — but `.overlay(alignment: .bottomLeading) { ... }` is the SwiftUI-native way to layer above the ScrollView without restructuring the VStack.

Android — `WorkoutSessionScreen` body shape (file: androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt):
- Top-level `WorkoutSessionScreen(...)` (line 98) does state setup and switches on `sessionState`. Active branch invokes `ActiveWorkoutContent(...)` at line 241.
- `ActiveWorkoutContent(...)` (line 426) uses `Scaffold(topBar = { TopAppBar(...) }) { innerPadding -> Column { ... } }` (Scaffold at line 466).
- Scaffold has a `floatingActionButton` slot — clean injection point that handles inset/padding automatically and does not require wrapping the content in a Box.
- Existing modal pattern: `ModalBottomSheet(onDismissRequest = ..., sheetState = rememberModalBottomSheetState()) { content }` — already in use elsewhere in this screen for the exercise overview and edit sheets.

Region-id source (verified in 260516-nfn-SUMMARY):
- iOS: `DebugGeofenceProvider.lastRegisteredRegionId` (synchronous).
- Android: `WorkoutRepository.getActiveSession()?.startTimeMillis` -> `"active-workout-$millis"`.
- The existing `DebugGeofencePanel` components already do this lookup — the executor does NOT need to read the region id in the workout view, only mount the panel.

Koin binding (already wired):
- DEBUG override loaded in `PumpernickelApplication.onCreate` (Android) and `AppDelegate.application(_:didFinishLaunchingWithOptions:)` (iOS).
- `GeofenceProvider` resolves to `DebugGeofenceProvider` in DEBUG, real iOS/Android actual in RELEASE.
- No additional Koin work needed in this plan.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: iOS — add floating "🐛 DEBUG" overlay button to active workout view, opens .sheet with DebugGeofencePanel</name>
  <files>
    - iosApp/iosApp/Views/Workout/WorkoutSessionView.swift (MODIFY — add @State for sheet, add .overlay on activeWorkoutView ZStack/.overlay, add .sheet presenting DebugGeofencePanel wrapped in NavigationStack { Form { ... } })
  </files>
  <action>
**EDIT A — Add the sheet-presentation state to `WorkoutSessionView`:**

Locate the existing `@State` declarations block (around lines 132–179, near `@State private var showSetInput: Bool = false`). Add ONE new state property:

```swift
    // DEBUG-only sheet trigger for in-workout geofence mock panel (quick-260517-pzh)
    #if DEBUG
    @State private var showDebugGeofenceSheet: Bool = false
    #endif
```

Place it adjacent to the other `@State` flags so the diff is local.

**EDIT B — Add a `.overlay` to the `activeWorkoutView` return value:**

Find the `activeWorkoutView(_ active:)` function (line ~293). Its return shape is `ScrollView { VStack { ... } } .navigationTitle(...) .toolbar { ... } .confirmationDialog(...) .alert(...) .sheet(isPresented: $showRationaleSheet, onDismiss: ...) { ... } .confirmationDialog(...) `.

AFTER the very last modifier on the function's returned view (after the closing `} message: { cfg in Text(cfg.message) }` block at the end of `activeWorkoutView`, before the function's closing brace), append:

```swift
        #if DEBUG
        .overlay(alignment: .bottomLeading) {
            Button(action: { showDebugGeofenceSheet = true }) {
                HStack(spacing: 4) {
                    Image(systemName: "ladybug.fill")
                    Text("DEBUG")
                        .font(.caption.weight(.bold))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.red.opacity(0.85))
                .foregroundColor(.white)
                .clipShape(Capsule())
                .shadow(radius: 3)
            }
            .padding(.leading, 16)
            .padding(.bottom, 16)
            .accessibilityLabel("Open debug geofence panel")
        }
        .sheet(isPresented: $showDebugGeofenceSheet) {
            NavigationStack {
                Form {
                    DebugGeofencePanel()
                }
                .navigationTitle("Debug — Geofence")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Done") { showDebugGeofenceSheet = false }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
        #endif
```

**Rationale for placement choices:**
- `alignment: .bottomLeading` — keeps the button clear of the bottom-right Complete-Set button (defined in `setInputSection`) and the top-right GeofenceStatusChip + menu in the toolbar.
- `Capsule()` shape + red — visually unmistakable as debug-only, will not be confused with primary app affordances.
- `NavigationStack { Form { DebugGeofencePanel() } }` — `DebugGeofencePanel`'s body is a SwiftUI `Section`, which only renders inside a `Form` / `List`. Bare `.sheet { DebugGeofencePanel() }` would show an empty sheet.
- `.presentationDetents([.medium, .large])` — keeps the sheet from blocking the full workout view; user can still glance at the GeofenceStatusChip up top to see the state transition.

**Verify no duplication:** the body of the sheet must call `DebugGeofencePanel()` directly. Do NOT re-declare Enter/Exit/Error buttons in `WorkoutSessionView.swift`.

**EDIT C — `#if DEBUG` placement audit:**

The `.overlay { ... }` and `.sheet { ... }` modifiers and the `@State` flag must ALL be inside `#if DEBUG / #endif` so release builds compile without referencing `showDebugGeofenceSheet`. The `DebugGeofencePanel` struct itself is already wrapped in `#if DEBUG` (existing file) — calling it from a `#if DEBUG`-only path keeps the release graph clean.

**Acceptance for Task 1:**
- `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` has exactly one `showDebugGeofenceSheet` state property, wrapped in `#if DEBUG`.
- The `.overlay(alignment: .bottomLeading) { ... }` block renders a Capsule-styled "DEBUG" button.
- The `.sheet(isPresented: $showDebugGeofenceSheet)` block presents `NavigationStack { Form { DebugGeofencePanel() } }`.
- Debug build compiles for iOS Simulator.
- Release build compiles for iOS (the `#if DEBUG` gating means none of the new code appears in the release binary).
- No references to `triggerEnter` / `triggerExit` / `triggerError` are added to `WorkoutSessionView.swift` — those live in `DebugGeofencePanel.swift` only.
  </action>
  <verify>
    <automated>
      # 1. iOS debug build compiles
      xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator' -workspace iosApp/iosApp.xcworkspace 2>&1 | tail -3 || \
        xcodebuild build -scheme iosApp -destination 'generic/platform=iOS Simulator' -project iosApp/iosApp.xcodeproj 2>&1 | tail -3
      # Expect: ** BUILD SUCCEEDED **

      # 2. State + overlay + sheet present and DEBUG-gated (strip leading // comments to avoid grep self-invalidation)
      grep -v '^[[:space:]]*//' iosApp/iosApp/Views/Workout/WorkoutSessionView.swift | grep -c "showDebugGeofenceSheet"
      # Expect: >=3 (state decl + overlay onTap + sheet binding)

      grep -v '^[[:space:]]*//' iosApp/iosApp/Views/Workout/WorkoutSessionView.swift | grep -c "DebugGeofencePanel()"
      # Expect: 1 (single mount inside the sheet)

      grep -v '^[[:space:]]*//' iosApp/iosApp/Views/Workout/WorkoutSessionView.swift | grep -c "#if DEBUG"
      # Expect: >=2 (state decl gate + overlay/sheet gate)

      # 3. No duplicated trigger logic in WorkoutSessionView
      grep -v '^[[:space:]]*//' iosApp/iosApp/Views/Workout/WorkoutSessionView.swift | grep -cE "triggerEnter|triggerExit|triggerError"
      # Expect: 0

      # 4. NavigationStack + Form wrapper present (otherwise Section won't render)
      awk '/showDebugGeofenceSheet/{f=NR} /NavigationStack/{if(NR-f<=15 && NR>f) print "NAV_OK"}' iosApp/iosApp/Views/Workout/WorkoutSessionView.swift
      # Expect: NAV_OK (at least once)
    </automated>
  </verify>
  <done>
    iOS debug build succeeds. WorkoutSessionView.swift contains a #if DEBUG-gated overlay button on the active workout view that opens a sheet containing DebugGeofencePanel inside NavigationStack { Form { ... } }. No trigger logic is duplicated in WorkoutSessionView.swift.
  </done>
</task>

<task type="auto">
  <name>Task 2: Android — add floatingActionButton to active-workout Scaffold, opens ModalBottomSheet with DebugGeofencePanel (BuildConfig.DEBUG-gated)</name>
  <files>
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt (MODIFY — inside ActiveWorkoutContent, add BuildConfig.DEBUG-gated `var showDebugSheet`, pass `floatingActionButton = { ... }` to Scaffold, render ModalBottomSheet that hosts DebugGeofencePanel)
  </files>
  <action>
**EDIT A — Add imports at top of WorkoutSessionScreen.kt:**

Add these imports (alphabetize into the existing import block):
```kotlin
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.SmallFloatingActionButton
import com.pumpernickel.android.BuildConfig
import com.pumpernickel.android.ui.components.DebugGeofencePanel
```
(If `Icons.Filled.BugReport` is not available in the bundled icons extension artifact, fall back to `Icons.Default.Build` or `Icons.Default.Settings` — verify by inspecting `material-icons-extended` artifact presence in `androidApp/build.gradle.kts`. Do NOT add a new dependency for this — pick whichever icon ships by default.)

**EDIT B — Add the sheet state inside `ActiveWorkoutContent`:**

Find `ActiveWorkoutContent` (line 426). Near the existing local state declarations (the `var showMenu by remember { mutableStateOf(false) }` line ~464), add:

```kotlin
    // DEBUG-only in-workout geofence trigger sheet (quick-260517-pzh)
    var showDebugSheet by remember { mutableStateOf(false) }
    val debugSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
```

Add the import for `rememberModalBottomSheetState` if not already present (it likely is, since other sheets in this file use it — verify before adding).

**EDIT C — Wire the floatingActionButton slot on the Scaffold:**

Find the `Scaffold(` invocation at line 466. Currently has only `topBar = { TopAppBar(...) }` and the content lambda. Insert `floatingActionButton` between `topBar = { ... }` and the content lambda:

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                // ... existing toolbar ...
            )
        },
        floatingActionButton = {
            if (BuildConfig.DEBUG) {
                SmallFloatingActionButton(
                    onClick = { showDebugSheet = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,   // or Icons.Default.Build fallback
                        contentDescription = "Open debug geofence panel"
                    )
                }
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Start
    ) { innerPadding ->
        // existing content body unchanged
```

Add the imports `import androidx.compose.material3.FabPosition` (if not already imported).

**Rationale:**
- `FabPosition.Start` — bottom-leading on LTR layouts; keeps the FAB clear of the Complete-Set button (which is full-width bottom in `SetInputSection`) and the GeofenceStatusChip / MoreVert in the TopAppBar. Wait — the Complete-Set button is a full-width Button inside the scrollable Column, not in the Scaffold bottomBar, so a bottom-leading FAB will overlay the bottom edge of the scroll content. This is acceptable for a DEBUG affordance (visible but only when the user scrolls to bottom; otherwise it floats above the rest-timer / picker area). If during manual UAT the FAB overlaps the Complete-Set CTA too aggressively, switch `FabPosition.Start` to wrapping the scaffold content in a Box and aligning the FAB to TopStart with extra top padding to clear the TopAppBar. **Default position: `FabPosition.Start`. Revisit only if UAT shows tap conflicts.**
- `SmallFloatingActionButton` — smaller visual footprint than the default `FloatingActionButton`; less likely to obscure content during a workout.
- `errorContainer` color — signals "debug/dangerous, not a primary action" without resorting to off-Material colors.

**EDIT D — Render the ModalBottomSheet hosting DebugGeofencePanel:**

INSIDE the Scaffold content lambda, AFTER the closing `}` of the existing `Column { ... }` but BEFORE the closing `}` of the content lambda, add:

```kotlin
        if (BuildConfig.DEBUG && showDebugSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDebugSheet = false },
                sheetState = debugSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Debug — Geofence",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    DebugGeofencePanel()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
```

**EDIT E — Audit:**

- Confirm `BuildConfig.DEBUG` is the ONLY gate — no env vars, no runtime flags.
- Confirm `DebugGeofencePanel()` is called exactly once (inside the ModalBottomSheet), not reimplemented.
- Confirm the FAB and the ModalBottomSheet block are both inside `ActiveWorkoutContent` — they MUST NOT render on the Reviewing, Finished, or Idle branches.

**Acceptance for Task 2:**
- `:androidApp:assembleDebug` succeeds.
- `:androidApp:assembleRelease` succeeds AND the FAB / ModalBottomSheet code paths are dead in release (BuildConfig.DEBUG = false at compile time means the `if` branches collapse — Kotlin won't actually strip the code, but the `if (BuildConfig.DEBUG)` guard renders the FAB invisible and the sheet unreachable; this is the same gating pattern used in the existing Settings panel wiring).
- `WorkoutSessionScreen.kt` has exactly one new `DebugGeofencePanel()` call site (inside the new ModalBottomSheet).
- No new business logic introduced — only mount + dismiss orchestration.
  </action>
  <verify>
    <automated>
      # 1. Builds succeed
      ./gradlew :androidApp:assembleDebug 2>&1 | tail -3
      # Expect: BUILD SUCCESSFUL
      ./gradlew :androidApp:assembleRelease 2>&1 | tail -3
      # Expect: BUILD SUCCESSFUL

      # 2. State, FAB, and sheet present; all behind BuildConfig.DEBUG (strip leading // comments)
      grep -v '^[[:space:]]*//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -c "showDebugSheet"
      # Expect: >=3 (decl + onClick set + if-guard read)

      grep -v '^[[:space:]]*//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -c "DebugGeofencePanel()"
      # Expect: 1

      grep -v '^[[:space:]]*//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -cE "BuildConfig\.DEBUG"
      # Expect: >=2 (FAB gate + sheet gate)

      # 3. No trigger logic duplicated
      grep -v '^[[:space:]]*//' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -cE "triggerEnter|triggerExit|triggerError"
      # Expect: 0

      # 4. FAB lives in ActiveWorkoutContent (not in the recap / finished branches)
      awk '/private fun ActiveWorkoutContent/,/^@Composable$/' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -c "SmallFloatingActionButton\|FloatingActionButton"
      # Expect: >=1

      awk '/private fun RecapContent/,/^@Composable$/' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -c "SmallFloatingActionButton\|FloatingActionButton\|DebugGeofencePanel"
      # Expect: 0 (no debug affordance on recap)

      awk '/private fun FinishedContent/,/^@Composable$/' androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt | grep -c "SmallFloatingActionButton\|FloatingActionButton\|DebugGeofencePanel"
      # Expect: 0
    </automated>
  </verify>
  <done>
    Both Android build flavors compile. WorkoutSessionScreen.kt has a BuildConfig.DEBUG-gated SmallFloatingActionButton in ActiveWorkoutContent's Scaffold and a ModalBottomSheet that mounts DebugGeofencePanel exactly once. No trigger logic is duplicated; no debug FAB appears on recap or finished screens.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Manual smoke test — open in-workout debug panel on iOS Simulator + Android Emulator, trigger Exit, observe chip → GracePeriod</name>
  <what-built>
    - iOS: a "🐛 DEBUG" capsule overlay appears bottom-leading on the active workout view in DEBUG builds. Tapping it presents a sheet with NavigationStack { Form { DebugGeofencePanel } } and a Done button. The same Enter/Exit/Error buttons that exist in Settings are now reachable mid-workout.
    - Android: a `SmallFloatingActionButton` (bug-icon, error-container color) appears bottom-leading on `ActiveWorkoutContent`. Tapping it presents a `ModalBottomSheet` containing the existing `DebugGeofencePanel` composable.
    - Both behind compile-time DEBUG gates (`#if DEBUG`, `BuildConfig.DEBUG`).
    - Only on the Active workout state — not on Reviewing, Finished, or Idle.
  </what-built>
  <how-to-verify>
**iOS (Simulator, DEBUG scheme):**
1. Build & run the iOS scheme on Simulator (DEBUG build config). Cold-start the app.
2. Start a workout (any template) and log one set so the geofence registers and the GeofenceStatusChip shows "In Zone".
3. Confirm a red "🐛 DEBUG" capsule is visible bottom-leading on the active workout view. It must NOT overlap:
   - The "Complete Set" button (full-width, bottom of the picker section)
   - The toolbar (chip + ellipsis at top-right, X at top-left)
   - The completed-set rows that scroll under it
   Tip: the overlay floats on top of the ScrollView, so as you scroll the content moves under it. That is intended.
4. Tap the capsule. A sheet should appear titled "Debug — Geofence" with the existing panel ("Region: active-workout-{millis}" + Enter / Exit / Error buttons).
5. Tap **Exit**. Close the sheet (Done). Expected: the GeofenceStatusChip in the toolbar transitions to **GracePeriod** with a countdown ticking down each second.
6. Tap the capsule again, tap **Enter**, close. Expected: chip returns to **InZone**, grace timer cancels.
7. Tap **Error** flow: capsule → sheet → Error → close. Expected: chip transitions to **Inactive**.
8. Build & run RELEASE config. Start a workout. Expected: **NO debug capsule visible** anywhere on the active view.

**Android (Emulator, debug variant):**
1. `./gradlew :androidApp:installDebug` → launch, start a workout, log one set.
2. Confirm a `SmallFloatingActionButton` (bug icon, error-container background) is visible bottom-leading on the active workout screen.
3. Verify it does NOT overlap the bottom Complete-Set button when the picker is visible (scroll the picker area into view and confirm — the FAB is in the Scaffold inset, the Complete-Set button is inside the scrollable Column, so they live in different layers but visually share the bottom edge).
4. Tap the FAB. A ModalBottomSheet rises with title "Debug — Geofence" and the existing DebugGeofencePanel composable (region id + Enter/Exit/Error buttons).
5. Tap **Exit**. Dismiss the sheet (drag down or back). Expected: GeofenceStatusChip in the TopAppBar transitions to **GracePeriod** with countdown.
6. Tap FAB → **Enter** → dismiss. Expected: chip returns to **InZone**.
7. Tap FAB → **Error** → dismiss. Expected: chip transitions to **Inactive**.
8. Build release: `./gradlew :androidApp:installRelease` (or assemble + install manually). Start a workout. Expected: **NO FAB visible**.
9. Bonus check: navigate from the active workout into the Recap (Finish Workout from menu) and confirm the FAB **disappears** on the recap screen (not just on Reviewing). Same for the Finished state.

**Pass criteria:**
- Debug affordance visible on Active workout state, both platforms, DEBUG builds only.
- Tapping it opens the existing DebugGeofencePanel (NOT a re-implementation).
- Exit trigger from the in-workout panel causes the GeofenceStatusChip in the same screen to transition correctly.
- Release builds show no affordance on either platform.
- Affordance is absent on Recap, Finished, and Idle states.

**Fail cues:**
- iOS sheet appears empty when opened → the panel's `Section` is not wrapped in `Form` — re-check Edit B of Task 1 (must be `NavigationStack { Form { DebugGeofencePanel() } }`).
- Tapping Exit does not transition chip → the in-workout sheet is rendering a different `DebugGeofenceProvider` instance than the VM observes. Re-check that no new Koin override was loaded by mistake; the panel uses `KoinHelper.shared.getDebugGeofenceProvider()` (iOS) / `koinInject<GeofenceProvider>()` (Android) — both resolve to the SAME singleton instance because the Koin override is loaded once at app start.
- FAB overlaps the Complete-Set button too aggressively on small screens (Android) → manually shift the FAB to `FabPosition.End`-of-TopAppBar or wrap content in a Box with `Alignment.TopStart` + top padding past the TopAppBar height. Decision deferred to UAT.
- Debug capsule visible in release build → `#if DEBUG` / `BuildConfig.DEBUG` gates were placed on the wrong block.
  </how-to-verify>
  <resume-signal>Type "approved" or describe what failed (platform, step, what happened vs expected).</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| debug-build → release-build | Compile-time gate (`#if DEBUG` on iOS, `BuildConfig.DEBUG` on Android) keeps debug affordance out of production binaries |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-DBG-04 | Tampering | In-workout debug FAB / capsule leaking into release | mitigate | All new code in `WorkoutSessionView.swift` is wrapped in `#if DEBUG`. All new code in `WorkoutSessionScreen.kt` is gated by `if (BuildConfig.DEBUG)`. Verified via grep that `#if DEBUG` and `BuildConfig.DEBUG` appear on every new block; release build verification step in Task 3 confirms the affordance is absent. |
| T-DBG-05 | Elevation of Privilege | Production user using in-workout debug trigger to skip Exit penalty | mitigate | Same as T-DBG-04 — no runtime flag, no env var. The underlying `DebugGeofenceProvider` is also DEBUG-only-bound (quick-260516-nfn T-DBG-01 / T-DBG-02), so even if the UI gate failed, the trigger methods would not exist on the resolved `GeofenceProvider` instance in release. Defense-in-depth. |
| T-DBG-06 | Spoofing | Two GeofenceProvider singletons (one bound by app start override, another created by the sheet) | accept | The sheet does NOT instantiate a provider — it calls `koinInject<GeofenceProvider>()` / `KoinHelper.shared.getDebugGeofenceProvider()`, both of which resolve the same Koin singleton bound at app start. No new singleton creation introduced. |
</threat_model>

<verification>
- iOS Simulator DEBUG build compiles. iOS RELEASE build compiles. RELEASE build does NOT render the debug capsule.
- Android :androidApp:assembleDebug and :androidApp:assembleRelease both succeed. Release build does NOT render the FAB.
- `DebugGeofencePanel` is referenced exactly once in each platform file — both call sites mount the existing component, no duplication.
- The debug affordance is wired only on the Active workout state on both platforms — recap, finished, idle remain clean.
- Triggering Exit from the in-workout panel causes the GeofenceStatusChip in the same screen to transition to GracePeriod, verifying live-provider plumbing.
- No new dependencies added to either platform's build file.
</verification>

<success_criteria>
- During an active workout in DEBUG builds (iOS + Android), the user can:
  1. See a clearly-debug-styled floating affordance on the workout screen without it blocking the Complete-Set CTA or the toolbar.
  2. Tap it to open a sheet showing the existing DebugGeofencePanel (Enter / Exit / Error buttons with current region id).
  3. Trigger Exit → see the GeofenceStatusChip in the same screen transition to GracePeriod.
  4. Trigger Enter → chip returns to InZone.
  5. Trigger Error → chip transitions to Inactive.
- In RELEASE builds, the floating affordance is completely absent and the underlying source is compile-time-gated out.
- The Settings-tab DebugGeofencePanel introduced in quick-260516-nfn continues to work unchanged.
- All automated verification commands in Tasks 1 and 2 pass.
- Task 3 manual UAT returns "approved".
</success_criteria>

<output>
After completion, create `.planning/quick/260517-pzh-debug-mock-panel-direkt-im-workout-scree/260517-pzh-SUMMARY.md` per the standard quick-task summary template.
</output>

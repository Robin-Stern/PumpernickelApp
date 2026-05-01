# Phase 17 — iOS Handoff: Progress-pic feature with biometric-locked gallery

**Audience:** the user (hand-writing SwiftUI per MEMORY.md).
**Scope:** documents Swift files + existing-file edits needed to complete Phase 17 on iOS. Kotlin/Koin side is shipped by Plans 17-01 through 17-07. **This plan touches no Android files** — Android MainActivity wiring (FragmentActivity superclass swap + BiometricGateActivityHolder.attach + PhotoCaptureLauncherActivityHolder.attach) was completed in Plan 17-05.
**Pbxproj:** per Phase 15.1 D-151-17 convention, this phase does NOT modify `iosApp/iosApp.xcodeproj/project.pbxproj`. When you add the new Swift files, drag them into the Xcode project ("Add Files to iosApp..." with "Copy items if needed" off, target membership = `iosApp`).

**D-17-18 reminder:** Compose Multiplatform is NOT used for the iOS UI in this phase. The roadmap line "via Compose Multiplatform" is a misnomer; project convention overrides — Compose Material 3 on Android, SwiftUI on iOS. Plans 17-05 / 17-06 deliver the Android composables; this doc specifies the SwiftUI counterparts.

---

## What you are building

Three new SwiftUI views + two edits to existing files + one infrastructure wiring call. (Info.plist additions are already shipped by Plan 17-04.)

| Surface | File | Purpose |
|---|---|---|
| NEW | `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` | LazyVGrid of blurred tiles with caption strip; tap → push viewer (D-17-10, D-17-11, D-17-12, D-17-13) |
| NEW | `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` | TabView paging carousel with `.tabViewStyle(.page)` for one workout's photos; calls `viewModel.requestUnlock()` on appear, `viewModel.relock()` on disappear (D-17-13, D-17-14) |
| NEW | `iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift` | Three-button prompt card mounted on the Finished view above the Done button; non-blocking (D-17-01, D-17-02, D-17-03) |
| MODIFY | `iosApp/iosApp/Views/Overview/OverviewView.swift` | Add a "Fortschritts-Galerie" entry that pushes `ProgressGalleryView` (D-17-10) |
| MODIFY | `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` | Mount `ProgressPicturePromptCard(workoutId: state.workoutId)` between the summary block and the Done button (D-17-01) |
| INFRASTRUCTURE | `iosApp/iosApp/PumpernickelApp.swift` (or wherever the SwiftUI scene is built) | Wire `PhotoCapturePresenterHolder.shared.attach(controller: rootViewController)` on app launch (D-17-19) |

Already shipped by Plan 17-04 (verify before writing UI):
- `iosApp/iosApp/Info.plist` has `NSPhotoLibraryUsageDescription` and `NSFaceIDUsageDescription` (German copy)
- `iosApp/iosApp/Info.plist` does NOT set `UIFileSharingEnabled` or `LSSupportsOpeningDocumentsInPlace` (T-FILE-SHARING)

---

## Kotlin contract (already shipped by Plans 17-01 through 17-07)

The shared Kotlin side exposes:

### Gallery VM (no parameters)

```kotlin
class ProgressGalleryViewModel(...) : ViewModel() {
    @NativeCoroutinesState val uiState: StateFlow<GalleryUiState>
    @NativeCoroutines val navEvents: SharedFlow<NavEvent>
    fun onTileTapped(workoutId: Long)
}

data class GalleryUiState(
    val tiles: List<ProgressGalleryTile>,
    val isLoading: Boolean
)

data class ProgressGalleryTile(
    val workoutId: Long,
    val workoutName: String,
    val startTimeMillis: Long,
    val volumeKg: Long,
    val coverRelativePath: String,
    val photoCount: Int,
    val prCount: Int,
    val isGoalDay: Boolean
)

sealed class NavEvent {
    data class OpenViewer(val workoutId: Long) : NavEvent()
}
```

Swift sees this as:
- `Shared.ProgressGalleryViewModel` with generated property `uiStateFlow` (observable via `asyncSequence(for:)`) and `navEventsFlow` (one-shot events).
- `Shared.GalleryUiState` — `state.tiles`, `state.isLoading`.
- `Shared.ProgressGalleryTile` — all fields above.
- `Shared.NavEvent.OpenViewer` — case access via type-name pattern matching.

### Viewer VM (`workoutId: Long` parameter)

```kotlin
class ProgressViewerViewModel(workoutId: Long, ...) : ViewModel() {
    @NativeCoroutinesState val uiState: StateFlow<ViewerUiState>
    fun requestUnlock()
    fun relock()
    fun deletePhoto(picture: ProgressPicture)
}

data class ViewerUiState(
    val photos: List<ProgressPicture>,
    val unlockedWorkoutId: Long?,   // null when locked; workoutId when unlocked
    val busy: Boolean
)

data class ProgressPicture(
    val id: String,
    val workoutId: Long,
    val relativePath: String,
    val capturedAtMillis: Long,
    val sortOrder: Int
)
```

**Critical (T-BIOMETRIC-BYPASS):** the explicit gate is `state.unlockedWorkoutId`. The view MUST render the locked placeholder when `unlockedWorkoutId == nil`, and only show the un-blurred photo when `unlockedWorkoutId != nil`. Recompute the visual on every state emission. There are exactly two write sites for `_unlockedWorkoutId.value` in the Kotlin VM (Success → workoutId, relock() → null) — see 17-06-SUMMARY for the grep verification.

### Prompt VM (`workoutId: Long` parameter)

```kotlin
class ProgressPicturePromptViewModel(workoutId: Long, ...) : ViewModel() {
    @NativeCoroutinesState val uiState: StateFlow<PromptUiState>
    fun onTakePhotoClick()
    fun onPickFromLibraryClick()
    fun onSkipClick()
}

data class PromptUiState(
    val workoutId: Long,
    val photoCount: Int,
    val busy: Boolean,
    val error: String?,
    val dismissed: Boolean
) {
    val showAddAnother: Boolean // true when photoCount > 0
}
```

### iOS Koin helpers (Plan 17-07)

```kotlin
class ProgressGalleryKoinHelper {
    fun getProgressGalleryViewModel(): ProgressGalleryViewModel
}
class ProgressViewerKoinHelper {
    fun getProgressViewerViewModel(workoutId: Long): ProgressViewerViewModel
}
class ProgressPicturePromptKoinHelper {
    fun getProgressPicturePromptViewModel(workoutId: Long): ProgressPicturePromptViewModel
}
```

Swift call signatures (per Phase 15.1 convention — class, not object):

```swift
private let viewModel = ProgressGalleryKoinHelper().getProgressGalleryViewModel()
private let viewerVM = ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId: 42)
private let promptVM = ProgressPicturePromptKoinHelper().getProgressPicturePromptViewModel(workoutId: 42)
```

Each call returns a fresh VM instance — SwiftUI retains it in `@State` (or as a `let`) for the lifetime of the view.

### Photo I/O bridge (D-17-19)

`PhotoCaptureLauncher.ios.kt` (Plan 17-04) needs a `UIViewController` to present from. The Kotlin side reads `PhotoCapturePresenterHolder.current`. The iOS app entry point MUST attach the root controller:

```swift
// In your iosApp.swift / SceneDelegate / app-root .onAppear
import Shared

PhotoCapturePresenterHolder.shared.attach(controller: rootViewController)
```

`PhotoCapturePresenterHolder` is a Kotlin `object`; in Swift that exposes as `PhotoCapturePresenterHolder.shared` per Kotlin/Native objc conventions. (Verify the exact spelling at build time — could be `PhotoCapturePresenterHolder.shared` or `PhotoCapturePresenterHolder.companion` depending on how K/N exports the object — Xcode's autocomplete will surface the correct member.)

Without this attach call, `PhotoCaptureLauncher.captureFromCamera()` and `pickFromLibrary()` resolve to `null` immediately — every iOS capture button silently no-ops.

`PhotoVault.ios` and `BiometricGate.ios` use no-arg ctors and resolve their own resources (Documents directory, LAContext) — no holder attach needed. Only the picker launcher needs the root view controller.

For symmetry, call `.detach(controller:)` if/when the scene tears down. For a single-scene app (the current PumpernickelApp shape) the attach in `onAppear` of the root SwiftUI scene is sufficient; the holder lives for the process lifetime.

---

## File 1 — `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` (NEW)

A LazyVGrid of blurred tile cards. Each tile renders the cover photo blurred (radius ~24pt — must keep faces unrecognisable, must look smooth not pixelated; D-17-13). Tap → navigation push to `ProgressViewerView`.

The tile's cover photo is loaded via `PhotoVault().read(relativePath:)`. PhotoVault is a Koin singleton in iOS; resolve via `KoinPlatform.shared.getKoin().get(...)` directly, OR add a small `PhotoVaultKoinHelper` for parity with the three VM helpers. Recommendation: add `PhotoVaultKoinHelper` so all Kotlin-side resolution happens through helpers and Swift never touches the Koin DSL.

### Template

```swift
import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct ProgressGalleryView: View {
    private let viewModel = ProgressGalleryKoinHelper().getProgressGalleryViewModel()

    @State private var uiState: SharedGalleryUiState?
    @State private var navTarget: Int64? = nil  // workoutId for push (if using programmatic NavigationLink)

    var body: some View {
        Group {
            if let state = uiState, !state.isLoading {
                if state.tiles.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVGrid(
                            columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
                            spacing: 12
                        ) {
                            ForEach(tiles(from: state), id: \.workoutId) { tile in
                                NavigationLink(
                                    destination: ProgressViewerView(workoutId: tile.workoutId),
                                    label: { GalleryTileView(tile: tile) }
                                )
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(12)
                    }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Fortschritt")
        .navigationBarTitleDisplayMode(.inline)
        .task { await observe() }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Spacer()
            Text("Noch keine Fortschrittsfotos.")
                .font(.body)
                .foregroundColor(.secondary)
            Text("Schließe ein Workout ab und füge ein Foto hinzu.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(32)
    }

    private func tiles(from state: SharedGalleryUiState) -> [SharedProgressGalleryTile] {
        guard let arr = state.tiles as? [SharedProgressGalleryTile] else { return [] }
        return arr
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressGallery observation error: \(error)")
        }
    }
}

private struct GalleryTileView: View {
    let tile: SharedProgressGalleryTile

    var body: some View {
        ZStack(alignment: .bottom) {
            // Cover photo, blurred (D-17-13).
            CoverImage(relativePath: tile.coverRelativePath)
                .blur(radius: 24)  // tune: faces must stay unrecognisable
                .clipShape(RoundedRectangle(cornerRadius: 14))

            // Caption strip (D-17-12).
            VStack(alignment: .leading, spacing: 2) {
                Text("\(formatGermanDate(tile.startTimeMillis)) • \(tile.workoutName)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                Text(statsLine(tile))
                    .font(.caption2)
                    .foregroundColor(.white)
                if tile.isGoalDay {
                    Text("Goal day").font(.caption2).foregroundColor(.white)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.7)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func statsLine(_ tile: SharedProgressGalleryTile) -> String {
        var parts: [String] = []
        parts.append("\(formatThousand(Int(truncating: tile.volumeKg as NSNumber))) kg")
        if tile.prCount > 0 { parts.append("PR \(tile.prCount)") }
        return parts.joined(separator: " • ")
    }

    private func formatGermanDate(_ epochMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000.0)
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "d. MMM"
        return fmt.string(from: date)
    }

    private func formatThousand(_ value: Int) -> String {
        let fmt = NumberFormatter()
        fmt.numberStyle = .decimal
        fmt.locale = Locale(identifier: "de_DE")
        return fmt.string(from: NSNumber(value: value)) ?? "\(value)"
    }
}

// CoverImage loads bytes via Koin-resolved PhotoVault. Adapt to your Koin
// helper of choice — see "PhotoVault Koin helper" note above.
private struct CoverImage: View {
    let relativePath: String
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                Rectangle().fill(Color.gray.opacity(0.3))
            }
        }
        .task(id: relativePath) {
            // image = await loadBytes(relativePath: relativePath).flatMap { UIImage(data: $0) }
            // see PhotoVault Koin helper
        }
    }
}

// MARK: - Shared type aliases
private typealias SharedGalleryUiState = Shared.GalleryUiState
private typealias SharedProgressGalleryTile = Shared.ProgressGalleryTile
```

Visual spec (deduced from CONTEXT D-17-12 / D-17-13):
- Tile aspect ratio: 1:1 square
- Corner radius: 14pt
- Blur radius: 24pt (tune per device — must keep faces unrecognisable, NOT pixelated)
- Caption strip: linear gradient `Color.clear → Color.black.opacity(0.7)` from top to bottom, full width, padded `10x8`
- Caption font: `.caption` for title, `.caption2` for stats line and goal-day
- Caption color: white
- Empty stats handling: drop the row entirely (never show "PR 0", never show "Goal day" if `isGoalDay == false`)

---

## File 2 — `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` (NEW)

Full-screen black-backed photo carousel for one workout's photos. Uses `TabView` with `.tabViewStyle(.page(indexDisplayMode: .always))` for the swipe carousel + page indicator dots.

**Critical (T-BIOMETRIC-BYPASS):** the view MUST render `LockedPlaceholder` when `state.unlockedWorkoutId == nil`. The unlock attempt fires automatically on appear via `viewModel.requestUnlock()`. On disappear, the view calls `viewModel.relock()` so the tile re-blurs in the gallery grid (D-17-13 / D-17-14).

Pager swipes between photos do NOT re-fire `requestUnlock()` — once unlocked, all photos for that workout's session are visible until the view disappears (D-17-14: per-tile auth, NOT per-photo).

### Template

```swift
import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct ProgressViewerView: View {
    let workoutId: Int64
    private let viewModel: SharedProgressViewerViewModel

    @State private var uiState: SharedViewerUiState?
    @State private var pageIndex: Int = 0

    init(workoutId: Int64) {
        self.workoutId = workoutId
        self.viewModel = ProgressViewerKoinHelper()
            .getProgressViewerViewModel(workoutId: workoutId)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            content
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .task { await observe() }
        .onAppear {
            // First-composition auth (D-17-14 per-tile every-tap)
            viewModel.requestUnlock()
        }
        .onDisappear {
            // Re-lock so the tile re-blurs in the grid (D-17-13)
            viewModel.relock()
        }
    }

    @ViewBuilder
    private var content: some View {
        if let state = uiState {
            if state.busy && state.unlockedWorkoutId == nil {
                ProgressView().tint(.white)
            } else if state.unlockedWorkoutId == nil {
                LockedPlaceholder(onRetry: { viewModel.requestUnlock() })
            } else if photos(from: state).isEmpty {
                Text("Keine Fotos für dieses Workout.")
                    .foregroundColor(.white)
            } else {
                TabView(selection: $pageIndex) {
                    ForEach(Array(photos(from: state).enumerated()), id: \.offset) { idx, photo in
                        PhotoPage(photo: photo)
                            .tag(idx)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))
            }
        } else {
            ProgressView().tint(.white)
        }
    }

    private func photos(from state: SharedViewerUiState) -> [SharedProgressPicture] {
        guard let arr = state.photos as? [SharedProgressPicture] else { return [] }
        return arr
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressViewer observation error: \(error)")
        }
    }
}

private struct LockedPlaceholder: View {
    let onRetry: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.system(size: 40))
                .foregroundColor(.white.opacity(0.7))
            Text("Tippe, um zu entsperren")
                .font(.headline)
                .foregroundColor(.white)
            Text("Authentifizierung erforderlich.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.7))
            Button(action: onRetry) {
                Text("Entsperren").foregroundColor(.white)
            }
            .padding(.top, 8)
        }
    }
}

private struct PhotoPage: View {
    let photo: SharedProgressPicture
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
            } else {
                ProgressView().tint(.white)
            }
        }
        .task(id: photo.relativePath) {
            // image = await loadBytes(relativePath: photo.relativePath).flatMap { UIImage(data: $0) }
        }
    }
}

// MARK: - Shared type aliases
private typealias SharedProgressViewerViewModel = Shared.ProgressViewerViewModel
private typealias SharedViewerUiState = Shared.ViewerUiState
private typealias SharedProgressPicture = Shared.ProgressPicture
```

Visual spec:
- Background: `Color.black` ignoring safe area
- Page indicator: dots, always visible, dark theme (matches Phase 15 unlock-modal aesthetic)
- Photo content mode: `scaledToFit`
- Close button: navigation bar back button (default `.navigationBarBackButtonHidden(false)`)
- Retry on locked: text button — never an alert toast (D-17-17 silent failure)

---

## File 3 — `iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift` (NEW)

Three-button card mounted on the Finished view above the Done button. Non-blocking — Done stays enabled regardless. Header copy switches to "Noch ein Foto?" once `photoCount > 0` (D-17-02).

### Template

```swift
import SwiftUI
import Shared
import KMPNativeCoroutinesAsync

struct ProgressPicturePromptCard: View {
    let workoutId: Int64
    private let viewModel: SharedProgressPicturePromptViewModel

    @State private var uiState: SharedPromptUiState?

    init(workoutId: Int64) {
        self.workoutId = workoutId
        self.viewModel = ProgressPicturePromptKoinHelper()
            .getProgressPicturePromptViewModel(workoutId: workoutId)
    }

    var body: some View {
        if uiState?.dismissed == true {
            EmptyView()
        } else {
            cardBody
                .task { await observe() }
        }
    }

    private var cardBody: some View {
        let state = uiState
        let showAddAnother = state?.showAddAnother ?? false
        let busy = state?.busy ?? false
        let count = Int(state?.photoCount ?? 0)

        return VStack(alignment: .leading, spacing: 10) {
            Text(showAddAnother ? "Noch ein Foto?" : "Fortschritts-Foto?")
                .font(.headline)
            Text(showAddAnother
                 ? "Du kannst weitere Fotos zu diesem Workout anhängen."
                 : "Halte deinen Fortschritt fest — Fotos bleiben verschlüsselt auf deinem Gerät.")
                .font(.body)
                .foregroundColor(.secondary)

            HStack(spacing: 8) {
                Button(action: { viewModel.onTakePhotoClick() }) {
                    Label("Foto aufnehmen", systemImage: "camera")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(busy)

                Button(action: { viewModel.onPickFromLibraryClick() }) {
                    Label("Aus Galerie", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(busy)
            }

            HStack {
                if busy {
                    ProgressView().scaleEffect(0.8)
                    Text("Speichere…").font(.footnote).foregroundColor(.secondary)
                } else if count > 0 {
                    Text("\(count) Foto\(count == 1 ? "" : "s") angehängt")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button(count > 0 ? "Fertig" : "Überspringen") {
                    viewModel.onSkipClick()
                }
                .buttonStyle(.borderless)
            }

            if let err = state?.error {
                Text(err)
                    .font(.footnote)
                    .foregroundColor(.red)
            }
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemBackground).opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func observe() async {
        do {
            for try await state in asyncSequence(for: viewModel.uiStateFlow) {
                self.uiState = state
            }
        } catch {
            print("ProgressPicturePrompt observation error: \(error)")
        }
    }
}

// MARK: - Shared type aliases
private typealias SharedProgressPicturePromptViewModel = Shared.ProgressPicturePromptViewModel
private typealias SharedPromptUiState = Shared.PromptUiState
```

The Skip button always calls `viewModel.onSkipClick()` regardless of whether its label reads "Überspringen" or "Fertig" — the VM's `dismissed` flag is the single source of truth that hides the card on next emission. No re-presenting; no second chance until the next workout finishes.

Errors render inline in red below the button row (D-17-17 — no toasts, no dialogs).

---

## File 4 — `iosApp/iosApp/Views/Overview/OverviewView.swift` (MODIFY)

Add a new "Fortschritts-Galerie" entry that pushes `ProgressGalleryView`. Mirror the existing nutrition-banner / rank-strip pattern — translucent material, leading icon, trailing chevron.

Suggested placement: after the rank strip / muscle activity, before the nutrition banner (matches Android Plan 17-06 — between MuscleActivityCard and NutritionGoalsBanner). Match the visual treatment of the other Overview cards for consistency.

```swift
// Inside the existing Overview body's VStack:
NavigationLink(destination: ProgressGalleryView()) {
    HStack(spacing: 12) {
        Image(systemName: "photo.stack")
            .foregroundColor(.accentColor)
        VStack(alignment: .leading, spacing: 2) {
            Text("Fortschritts-Galerie")
                .font(.body)
                .fontWeight(.semibold)
            Text("Sieh dir deine Workout-Fotos an.")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        Spacer()
        Image(systemName: "chevron.right")
            .foregroundColor(.secondary)
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 12)
    .background(Color(uiColor: .secondarySystemBackground).opacity(0.6))
    .clipShape(RoundedRectangle(cornerRadius: 12))
}
.buttonStyle(.plain)
.padding(.horizontal)
```

`.buttonStyle(.plain)` prevents SwiftUI's default NavigationLink blue tint from repainting the whole card. Mirrors Android's Material 3 `Card(onClick = ...)` behavior in 17-06.

---

## File 5 — `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` (MODIFY)

Mount `ProgressPicturePromptCard(workoutId: state.workoutId)` between the existing summary block and the Done button. Done button stays enabled regardless of the card's interaction state (D-17-01 non-blocking).

Per Plan 17-05 Task 1, the shared `WorkoutSessionState.Finished` data class now carries `workoutId: Long`. iOS reads it via `state.workoutId` (KMP-Native bridges to `Int64` on Swift). The host call site in `WorkoutSessionView.swift` already extracts `finished.workoutId`; pass it through to the prompt card.

```swift
// Inside WorkoutFinishedView body, between the existing summary VStack and the Done button:
ProgressPicturePromptCard(workoutId: state.workoutId)
    .padding(.horizontal)
    .padding(.top, 16)
```

If `WorkoutFinishedView` does not yet receive the `workoutId` as a parameter, plumb it through from the `WorkoutSessionView` Finished branch:

```swift
// In WorkoutSessionView.swift, where WorkoutFinishedView is constructed:
WorkoutFinishedView(
    finished: finished,                  // existing
    workoutId: finished.workoutId,       // NEW — pass the Int64
    onDone: { ... }                      // existing
)
```

Do NOT change the Done button's `disabled` state, action, or position. The card is purely additive.

---

## Visual specs (deduced from CONTEXT.md)

| Element | Spec |
|---|---|
| Tile blur radius | 24pt (tune; faces unrecognisable, smooth not pixelated — D-17-13) |
| Tile aspect ratio | 1:1 square |
| Tile corner radius | 14pt |
| Caption strip | LinearGradient(.clear → .black.opacity(0.7)), top to bottom |
| Caption font | `.caption` (title), `.caption2` (stats) |
| Viewer background | `Color.black.ignoresSafeArea()` |
| Viewer page indicator | `.tabViewStyle(.page(indexDisplayMode: .always))` |
| Prompt card background | `Color(uiColor: .secondarySystemBackground).opacity(0.6)`, RoundedRectangle 12pt |
| Empty stats | drop the line entirely (no "PR 0", no goal-day if not goal-day) |
| Volume label | `12 540 kg` with German locale's narrow no-break space (use `NumberFormatter` with `Locale("de_DE")`) |
| Date label | `28. Apr.` (`DateFormatter` with `dateFormat = "d. MMM"`, `Locale("de_DE")`) |

---

## Biometric prompt copy (German)

`LAContext.evaluatePolicy(_, localizedReason: ...)` reason string is set BY THE KOTLIN SIDE in `BiometricGate.ios.kt` to `"Fortschrittsbild entsperren"` (D-17-15, Plan 17-04 Task 2). The iOS UI does NOT pass this string — the Kotlin VM passes it through. The Info.plist `NSFaceIDUsageDescription = "Fortschrittsbild entsperren."` is the FALLBACK string iOS shows when Face ID is invoked; the LAContext reason string is what shows in the per-prompt Touch ID / passcode sheet.

Both strings have already been written by Plans 17-04. Verify at the start of iOS work:

```bash
grep -q 'Fortschrittsbild entsperren' shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.ios.kt
grep -q 'Fortschrittsbild entsperren' iosApp/iosApp/Info.plist
```

---

## Privacy hardening surfaces

Already shipped by Plan 17-04 (Info.plist additions). Verify before iOS work:

| Key | Value | Status |
|---|---|---|
| `NSPhotoLibraryUsageDescription` | "Wähle Fotos für deine Fortschritts-Galerie." | shipped |
| `NSFaceIDUsageDescription` | "Fortschrittsbild entsperren." | shipped |
| `NSCameraUsageDescription` | (existing) "Barcode scanning requires camera access" | unchanged |
| `UIFileSharingEnabled` | (absent — DO NOT add) | shipped |
| `LSSupportsOpeningDocumentsInPlace` | (absent — DO NOT add) | shipped |

**Do NOT add `UIFileSharingEnabled` or `LSSupportsOpeningDocumentsInPlace`.** Their absence is the Files.app exclusion (T-FILE-SHARING). If a future feature legitimately needs document sharing, route through a separate dir, never `<Documents>/progress_pics/`.

---

## Negative requirements (do NOT do these)

| Don't | Why |
|---|---|
| Don't write photos to the system Photos library (`UIImageWriteToSavedPhotosAlbum`, `PHPhotoLibrary.shared().performChanges`) | Vault framing (CONTEXT line 205); files must stay app-private |
| Don't surface a share sheet (`UIActivityViewController` with photo data) | Same as above; defeats the vault |
| Don't expose photos via `UIDocumentPickerViewController` or any Files.app integration | T-FILE-SHARING |
| Don't enable iCloud Drive sync for Documents (`UIBackgroundModes` containing `iCloudDocuments`) | T-CLOUD-LEAK |
| Don't add a "remember unlock for the session" toggle | Per-tile every-tap is the explicit privacy choice (CONTEXT line 207) |
| Don't fall back to `LAPolicyDeviceOwnerAuthenticationWithBiometrics` if `.deviceOwnerAuthentication` "feels weird" | D-17-15 — biometric-only blocks passcode-only devices |
| Don't show an error toast on biometric cancel/fail | D-17-17 — silent failure; tile stays blurred, user retries |
| Don't add Compose Multiplatform iOS UI for any of these surfaces | D-17-18 — SwiftUI hand-written |
| Don't retro-add a photo from a History detail view | D-17-04 — no retro-add in v1 |
| Don't write a thumbnail file alongside each photo | D-17-07 — single resized JPEG is the spec |

---

## Acceptance criteria checklist

When all of these tick, Phase 17 is done on iOS.

- [ ] `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` exists; renders LazyVGrid of blurred tiles with caption strip; tap pushes `ProgressViewerView`
- [ ] `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` exists; full-screen black; TabView paging; calls `viewModel.requestUnlock()` on appear, `viewModel.relock()` on disappear
- [ ] LockedPlaceholder shows when `state.unlockedWorkoutId == nil`; un-blurred photo only when `unlockedWorkoutId != nil`
- [ ] Pager swipes do NOT re-prompt biometric (D-17-14)
- [ ] `iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift` exists; three buttons; "Noch ein Foto?" header switch when `photoCount > 0`
- [ ] `OverviewView.swift` has a "Fortschritts-Galerie" entry that pushes `ProgressGalleryView`
- [ ] `WorkoutFinishedView.swift` mounts `ProgressPicturePromptCard(workoutId: state.workoutId)` above the Done button; Done stays enabled
- [ ] iOS app entry point calls `PhotoCapturePresenterHolder.shared.attach(...)` so the Kotlin photo launcher can present pickers
- [ ] `Info.plist` has `NSPhotoLibraryUsageDescription` and `NSFaceIDUsageDescription` (verified by Plan 17-04 acceptance)
- [ ] `Info.plist` does NOT contain `UIFileSharingEnabled` or `LSSupportsOpeningDocumentsInPlace`
- [ ] Manual verify: complete a workout, the prompt card appears with three buttons; Done navigates back without prompting
- [ ] Manual verify: take a photo from camera; saved file lands in `<Documents>/progress_pics/{uuid}.jpg`; gallery shows a blurred tile
- [ ] Manual verify: tap the tile, OS auth sheet appears with the German reason string; Success unblurs photo; close re-blurs in grid
- [ ] Manual verify: two photos for one workout — pager pages between them without re-prompting auth
- [ ] Manual verify: Files.app does NOT show the app's Documents directory
- [ ] Manual verify: in iCloud backup settings, the `progress_pics/` folder is excluded (test via Settings → Apple ID → iCloud → Manage Storage; should not show those bytes)
- [ ] Manual verify: cancel biometric — tile stays blurred, no error toast, no system feedback
- [ ] Manual verify: on a passcode-less simulator, tile unblurs without challenge (D-17-16)

---

## Reference precedents

- **Phase 15.1 IOS-HANDOFF** (`.planning/phases/15.1-ranks-and-achievements-browser/15.1-IOS-HANDOFF.md`) — same shape, simpler scope. Read before starting.
- **`AchievementGalleryView.swift`** — analog for SwiftUI grid + flow observation.
- **`BarcodeScannerView.swift`** lines 58-77 — analog for `checkCameraPermission()` UX precedent (referenced for the iOS UX feel; this phase uses different APIs but the same permission-check shape).
- **`FlowObservation.swift`** — `asyncSequence(for:)` helper used throughout.

---

*Phase: 17 — Progress-pic feature with biometric-locked gallery*
*Handoff authored: per Plan 17-08 on 2026-05-01*

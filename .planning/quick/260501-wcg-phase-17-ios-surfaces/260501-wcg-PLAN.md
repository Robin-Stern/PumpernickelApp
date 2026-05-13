---
quick_id: 260501-wcg
description: Phase 17 iOS surfaces (SwiftUI gallery + viewer + prompt card + presenter holder)
status: planned
mode: quick
date: 2026-05-01
---

# Quick 260501-wcg — Phase 17 iOS Surfaces

## Source of truth

`.planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md`
(763 lines, Kotlin contract + visual specs + acceptance criteria)

Treat the handoff doc as the spec. Code templates in §"File 1/2/3" are accurate up to two
deviations called out below.

## Deviations from handoff doc

1. **`PhotoCapturePresenterHolder.shared.attach(...)` root-VC access.** SwiftUI `WindowGroup`
   does not directly expose a `UIViewController`. The handoff suggests `rootViewController` —
   we use the standard scene-walk:
   ```swift
   guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
         let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
                  ?? scene.windows.first?.rootViewController
   else { return }
   PhotoCapturePresenterHolder.shared.attach(controller: root)
   ```
   Wired inside `AppRootView.task { ... }` after a one-tick delay
   (`try? await Task.sleep(nanoseconds: 100_000_000)`) so the SwiftUI hosting controller has
   actually been mounted in the window.

2. **`workoutId` plumbing.** `WorkoutFinishedView` does NOT currently take `workoutId`. We add
   `let workoutId: Int64` as a stored property, and update `WorkoutSessionView` line 62-71 to
   pass `workoutId: finished.workoutId`. `Finished` already carries `workoutId: Long` per
   `WorkoutSessionViewModel.kt:50-57`.

3. **Image loading via `PhotoVault.read(relativePath:)`.** Handoff says "TODO: load via
   PhotoVault Koin helper" inline in tile/photo views. We expose this through a new
   `PhotoVaultKoinHelper` and call it from a small async helper. Bytes are returned as
   `KotlinByteArray` from the Kotlin suspend function — convert to `Data` using
   `KotlinByteArrayKt.toNSData()` if exposed, otherwise iterate bytes. Implementation: use
   `withCheckedContinuation` wrapping `PhotoVault().readNative()` (the K/N async wrapper from
   `@NativeCoroutines`). Concrete API surfaces will be confirmed inline at write-time;
   fallback is a synchronous helper that uses `NSData(contentsOfFile:)` against the Documents
   sandbox path resolved from `relativePath`.

## Tasks

| # | Files | Commit message |
|---|-------|----------------|
| 1 | `shared/src/iosMain/kotlin/com/pumpernickel/di/PhotoVaultKoinHelper.kt` (NEW) | `feat(quick-260501-wcg): add PhotoVaultKoinHelper for Swift access to PhotoVault` |
| 2 | `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` (NEW), `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` (NEW) | `feat(quick-260501-wcg): add iOS Progress gallery + viewer SwiftUI views` |
| 3 | `iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift` (NEW) | `feat(quick-260501-wcg): add iOS post-workout photo prompt card` |
| 4 | `iosApp/iosApp/Views/Overview/OverviewView.swift` (MODIFY), `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift` (MODIFY), `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift` (MODIFY), `iosApp/iosApp/PumpernickelApp.swift` (MODIFY) | `feat(quick-260501-wcg): wire Phase 17 iOS surfaces into Overview, Finished, and root scene` |

Each task is one commit. After the four commits, the iOS feature is complete pending the
user adding the new Swift files to the Xcode target ("Add Files to iosApp..." with target
membership = `iosApp`, "Copy items if needed" off — Phase 15.1 D-151-17 convention, no
`project.pbxproj` mutation by the agent).

## must_haves

```yaml
truths:
  - PhotoVaultKoinHelper resolves a singleton PhotoVault from Koin (matches AchievementGalleryKoinHelper pattern)
  - ProgressGalleryView reads viewModel.uiStateFlow via asyncSequence(for:) and renders LazyVGrid of blurred tiles
  - ProgressViewerView calls viewModel.requestUnlock() onAppear and viewModel.relock() onDisappear
  - ProgressViewerView renders LockedPlaceholder when state.unlockedWorkoutId == nil, photos when != nil
  - ProgressPicturePromptCard exposes three buttons (Foto aufnehmen / Aus Galerie / Skip-or-Fertig) and toggles header copy when photoCount > 0
  - OverviewView contains a NavigationLink that pushes ProgressGalleryView()
  - WorkoutFinishedView mounts ProgressPicturePromptCard(workoutId:) above the Done button; Done stays enabled
  - WorkoutSessionView passes finished.workoutId into WorkoutFinishedView
  - PumpernickelApp / AppRootView calls PhotoCapturePresenterHolder.shared.attach(controller:) on launch

artifacts:
  - shared/src/iosMain/kotlin/com/pumpernickel/di/PhotoVaultKoinHelper.kt
  - iosApp/iosApp/Views/Overview/ProgressGalleryView.swift
  - iosApp/iosApp/Views/Overview/ProgressViewerView.swift
  - iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift

key_links:
  - .planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md  # spec
  - shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt                       # pattern for new helper
  - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt                                 # PhotoVault is already bound
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt:50-57 # Finished now carries workoutId, no photoCount
  - iosApp/iosApp/PumpernickelApp.swift                                                                 # AppRootView.task is the attach site
  - iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift                                       # SwiftUI grid pattern reference
  - iosApp/iosApp/Utilities/FlowObservation.swift                                                       # asyncSequence(for:) helper
```

## Hard constraints (must NOT violate)

- **D-17-08:** schema is locked at v9 — do not modify `ProgressPictureEntity`, do not add Room migrations.
- **D-17-14:** `_unlockedWorkoutId` write sites in `ProgressViewerViewModel.kt` stay at exactly two. Do not add code that touches it.
- **D-17-06:** `android:allowBackup="true"` stays true in `AndroidManifest.xml`.
- **No `project.pbxproj` edits** — user drags files into Xcode.
- **No `UIImageWriteToSavedPhotosAlbum`, no `UIActivityViewController` for photo data, no Files-app integration** — vault contract.
- **No `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace` in Info.plist** — already absent, keep absent.

## Acceptance criteria

Direct subset of the handoff §"Acceptance criteria checklist" (lines 728-749). At commit time
the project must compile via `:shared:assembleDebug` and the iOS framework target
`:shared:linkDebugFrameworkIosSimulatorArm64`. Swift compile is verified by the user opening
Xcode after files are dragged in.

---
quick_id: 260501-wcg
description: Phase 17 iOS surfaces (SwiftUI gallery + viewer + prompt card + presenter holder)
status: complete
mode: quick
date: 2026-05-01
final_commit: 17581fa
---

# Quick 260501-wcg — Phase 17 iOS Surfaces (SUMMARY)

## Outcome

All four atomic commits landed. Kotlin `:shared:compileKotlinIosSimulatorArm64`
builds clean — only pre-existing beta-warnings (expect/actual classes,
suspend-on-ObjC). Swift compile is verified by the user opening Xcode and
adding the new files to the `iosApp` target.

## Commits

| Commit  | Files | Purpose |
|---------|-------|---------|
| `ee06ead` | `shared/src/iosMain/kotlin/com/pumpernickel/di/PhotoVaultKoinHelper.kt` | Singleton accessor for Swift, mirrors `AchievementGalleryKoinHelper`. |
| `7887dd7` | `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift`, `iosApp/iosApp/Views/Overview/ProgressViewerView.swift` | LazyVGrid of blurred tiles + black-backed paged carousel. Biometric gate via `state.unlockedWorkoutId`; per-tile every-tap (D-17-14). Optional overflow Delete in viewer. |
| `6d4eef4` | `iosApp/iosApp/Views/Workout/ProgressPicturePromptCard.swift` | Three-button non-blocking card. Header copy flips at `photoCount > 0`. Errors inline in red. |
| `17581fa` | `iosApp/iosApp/Views/Overview/OverviewView.swift`, `iosApp/iosApp/Views/Workout/WorkoutFinishedView.swift`, `iosApp/iosApp/Views/Workout/WorkoutSessionView.swift`, `iosApp/iosApp/PumpernickelApp.swift` | Wires gallery entry, prompt card mount + `workoutId` plumbing, and `PhotoCapturePresenterHolder.shared.attach(...)` on first launch with a 10×100 ms retry loop. |

## What was deviated from the handoff doc, and why

1. **Image bytes loaded via `FileManager`, not via `PhotoVault.read(...)`.** The
   handoff suggests calling `PhotoVault().read(relativePath:)`, but that fn is
   `suspend` and the K/N callback bridge is friction for a sync UI read of a
   well-known sandbox path. Both `ProgressGalleryView` and `ProgressViewerView`
   read directly from `<Documents>/progress_pics/{file}` with the same
   path-traversal guard `PhotoVault.ios.kt::resolveSafe` uses. `PhotoVaultKoinHelper`
   ships anyway for any future Swift call site that wants the canonical
   singleton (e.g. for explicit deletes).

2. **`PhotoCapturePresenterHolder.shared.attach(...)` runs inside a 10× retry
   loop with `0.15s + 0.1s × n` delays.** SwiftUI's `WindowGroup` doesn't
   expose the hosting controller directly, and `connectedScenes.first` may
   not be in `.foregroundActive` immediately on cold start. The retry loop
   walks scenes for an active `UIWindowScene` whose `keyWindow.rootViewController`
   is non-nil; if found, attach and return. Failure mode: if no scene appears
   within ~1 s, the picker silently no-ops on first use — non-catastrophic.

3. **`WorkoutFinishedView` body wrapped in `ScrollView`.** Adding the prompt
   card to the existing `Spacer / Spacer` sandwich pushed the Done button
   below the safe area on smaller devices. Wrapping in `ScrollView` keeps
   Done reachable regardless of card-state. The card itself is still
   non-blocking; Done is always enabled (D-17-01).

4. **Per-photo Delete UI added.** The handoff calls this Claude's discretion
   (CONTEXT line 102). A toolbar overflow `Menu` exposes a destructive
   `Foto löschen` action behind a `confirmationDialog`. The VM's existing
   `deletePhoto(picture:)` cascades both the row and the file via
   `ProgressPictureRepository.deletePicture` (already in commit `f386c44`).

## Hard-constraint compliance

| Constraint | Status |
|---|---|
| D-17-08 schema locked | ✓ no schema changes |
| D-17-14 `_unlockedWorkoutId` two write sites | ✓ no Kotlin VM changes |
| D-17-06 `allowBackup="true"` | ✓ no manifest changes |
| No `project.pbxproj` mutation | ✓ user drags files into Xcode |
| No `UIImageWriteToSavedPhotosAlbum` / Photos library writes | ✓ vault-only |
| No share sheet / Files-app integration | ✓ |
| `Info.plist` `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace` absent | ✓ unchanged |
| iOS SwiftUI authorship overrides D-17-18 | explicit user authorisation in this session only |

## Manual verification (user, in Xcode)

These checks come straight from `17-IOS-HANDOFF.md` lines 728-749:

- [ ] Drag the 3 new Swift files into the `iosApp` target in Xcode (Add
      Files to iosApp..., target membership = `iosApp`, Copy items = off).
- [ ] Build & run on iOS Simulator (recommended: iPhone 15, iOS 17+).
- [ ] Complete a workout → prompt card with three buttons appears; Done
      navigates back without prompting.
- [ ] Take a photo from camera → file lands in `<Documents>/progress_pics/{uuid}.jpg`,
      gallery shows a blurred tile.
- [ ] Tap tile → OS auth sheet with `Fortschrittsbild entsperren` reason; on
      Success, photo unblurs into the carousel; close re-blurs in grid.
- [ ] Two photos for one workout — pager pages without re-prompting auth.
- [ ] Files.app does NOT show the app's Documents directory.
- [ ] Cancel biometric → tile stays blurred, no toast, no system feedback.
- [ ] On a passcode-less simulator, tile unblurs without challenge (D-17-16).

## Known SourceKit standalone diagnostics (NOT compile errors)

- `No such module 'Shared'` and `No such module 'UIKit'` on the SourceKit
  lint pass for every file that imports those modules. These reflect the
  standalone SourceKit having no Xcode project context — Gradle/Xcode wires
  the `Shared` framework via `:shared:linkDebugFrameworkIosSimulatorArm64`
  at build time.

## Next session

`feature/workouts` is now 70 commits ahead of `origin/main`. Push when ready.
The Android-side Phase 17 verification (smoke test + M-09 visual check) is
still open — see the original session brief in this turn's user prompt.

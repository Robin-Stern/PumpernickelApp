# Phase 17: Progress-pic feature with biometric-locked gallery - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship a privacy-conscious progress-photo feature: post-workout, the user can attach one or more photos (camera or library) to the just-saved workout; a new gallery on the Overview tab shows those workouts as **blurred tiles** overlaid with the day's stats (volume, PR count, nutrition goal-day status); tapping a tile triggers OS-level biometric/passcode auth and unblurs the photo into a swipeable carousel of all photos for that workout; closing the viewer re-blurs the tile. Photos are stored as resized JPEGs in the app sandbox with iOS file protection + Android backup-exclusion — no photo ever enters the system Photos library, no cloud backup. Schema bumps Room v8 → v9 via additive AutoMigration.

Engine, retroactive walker, rank ladder, and existing nutrition/gamification surfaces are unchanged.

</domain>

<decisions>
## Implementation Decisions

### Capture flow

- **D-17-01 (Auto-prompt on Finished screen):** After `saveReviewedWorkout()` succeeds and the Finished/summary state renders, surface a card with three actions: `[📷 Take Photo]` `[🖼 Pick from Library]` `[Skip]`. Mirrors the Phase 15 D-20 unlock-modal post-save timing — one consistent post-workout moment. The card is non-blocking; the existing Done button below stays enabled regardless of whether a photo was attached.
- **D-17-02 (Multiple photos per workout):** A workout can carry 0..N photos. Schema = new `progress_pictures` table with FK `workoutId → completed_workouts.id`. After the first photo saves, the prompt offers an immediate "Add another?" affordance (Claude's discretion: re-prompt vs return to Finished with a "+ Add another" chip).
- **D-17-03 (Camera AND library, two distinct buttons):** Both modalities are first-class and separately reachable from the prompt card. Specific APIs are Claude's discretion at planning time — likely `ActivityResultContracts.TakePicture` + `ActivityResultContracts.PickVisualMedia` on Android (simpler than CameraX for a single still); `UIImagePickerController` or `PHPickerViewController` on iOS. CameraX is available in deps already (used by barcode scanner) but not required.
- **D-17-04 (No retro-add from History detail in v1):** Only the auto-prompt on Finished can attach a photo. Workouts already in History stay photo-less. Defers retro-add to keep this phase contained — see Deferred Ideas.

### Storage & lifecycle

- **D-17-05 (App-private sandbox storage):** Files live in app-private storage:
  - iOS: `<Documents>/progress_pics/{uuid}.jpg`
  - Android: `<filesDir>/progress_pics/{uuid}.jpg`
  Room stores a relative path string (e.g. `progress_pics/{uuid}.jpg`); the platform code resolves it against the platform-specific root. UUID is the filename stem AND the Room PK.
- **D-17-06 (Privacy hardening — OS-level only, no app-managed crypto):**
  - **iOS:** every saved file gets `NSFileProtectionComplete` (OS-level encryption tied to device passcode/biometric — files are unreadable when the device is locked); each saved file is also marked `URLResourceValues.isExcludedFromBackupKey = true`; `Info.plist` keeps `UIFileSharingEnabled = false` and does NOT set `LSSupportsOpeningDocumentsInPlace` so Files.app cannot browse the Documents directory.
  - **Android:** disable Auto Backup specifically for the photo dir via fine-grained `dataExtractionRules` (Android 12+) + `fullBackupContent` rule (Android 11-) excluding `progress_pics/`. **Do NOT flip `android:allowBackup="false"` globally** — that breaks legitimate user backups of unrelated app data.
  - **No app-managed AES encryption.** Biometric is a UI gate, not a crypto coupling. The strict-encryption tier (key in Keychain/Keystore wrapping each photo) is a deferred upgrade — see Deferred Ideas.
- **D-17-07 (Single resized JPEG, no separate thumbnail):** On capture, decode the source bytes (camera or library), resize to **1600px long edge** preserving aspect ratio, encode JPEG **quality 0.8**, write to `{uuid}.jpg`. Per-photo disk size lands ~200–400 KB. The gallery grid loads the same file and downsamples in the renderer; no second thumbnail file.
- **D-17-08 (Schema bump v8 → v9, additive `progress_pictures` table):** New entity:
  ```kotlin
  @Entity(
      tableName = "progress_pictures",
      foreignKeys = [ForeignKey(
          entity = CompletedWorkoutEntity::class,
          parentColumns = ["id"],
          childColumns = ["workoutId"],
          onDelete = ForeignKey.CASCADE
      )],
      indices = [Index("workoutId")]
  )
  data class ProgressPictureEntity(
      @PrimaryKey val id: String,        // UUID, also the filename stem
      val workoutId: Long,
      val relativePath: String,           // "progress_pics/{uuid}.jpg"
      val capturedAtMillis: Long,
      val sortOrder: Int                  // 0-based per workout, tiebreak when capturedAtMillis ties
  )
  ```
  `AppDatabase.version` → 9; register `AutoMigration(8, 9)`. **No changes to existing entities.**
- **D-17-09 (Cascade delete + file cleanup):** `onDelete = CASCADE` removes Room rows when a workout is deleted. A `ProgressPictureRepository.deleteForWorkout(workoutId)` (or a DAO callback) deletes the underlying files from disk so they don't orphan. Deleting an individual photo (from the carousel) deletes both the row and the file in one operation. Claude's discretion: whether file deletion is best-effort eventual or transactional with the row delete — pick the simplest approach that keeps disk and DB consistent.

### Gallery surface

- **D-17-10 (Gallery on Overview tab):** New entry on the Overview tab, reachable via the Phase 15.1 Overview-tab NavHost (`overviewNavController`, D-151-15). Add a "Progress" affordance (card / button — Claude's discretion on exact placement next to the rank strip / nutrition rings) that calls `navController.navigate(ProgressGalleryRoute)`. `ProgressGalleryRoute` = `@Serializable data object` added to `Routes.kt`.
- **D-17-11 (One tile per workout; cover = most recent photo):** Each tile in the grid represents **one workout that has at least one photo** (workouts with no photo do not appear — they're already in Workout History). Tile cover image = the most recently captured photo for that workout (`MAX(capturedAtMillis)` per `workoutId`). Tap → biometric → enter a swipeable photo viewer that pages through all of that workout's photos in chronological order (`sortOrder ASC`).
- **D-17-12 (Tile composition — bottom caption strip on photo):**
  ```
  ┌──────────────────┐
  │  [blurred photo] │   <- full-tile blurred photo as background
  │                  │
  ├──────────────────┤   <- gradient/translucent strip
  │ Apr 28  • Push   │   <- date + workout name (CompletedWorkoutEntity.name)
  │ 12 540 kg  🏆 2  │   <- volume (kg, integer) + PR count
  │ 🍎 Goal day      │   <- nutrition goal-day chip (only if true)
  └──────────────────┘
  ```
  - **Volume:** `floor(sum(actualReps × actualWeightKg))` in kg — same totalling already used by the PB pipeline; reuse via `CompletedWorkoutDao` or a derived helper.
  - **PR count:** number of new PRs awarded for this workout. Reuse Phase 15 D-03 detection — either count `xp_ledger` rows with `source = PR_HIT` keyed to this workout, or re-run the diff. Claude's discretion.
  - **Goal-day chip:** show iff `NutritionGoalDayPolicy.isGoalDay(workoutDate)` returns true. Read-only call into the existing Phase 15 / 16 engine.
  - **Empty-stats handling:** if a metric is zero/absent (no PRs, not a goal-day), drop that line — never show "🏆 0".
- **D-17-13 (Blurred-by-default + biometric unblur):**
  - Tile renders the cover photo with a heavy blur — Compose `Modifier.blur(...)` on Android, SwiftUI `.blur(radius: ...)` on iOS. Exact radius is Claude's discretion; must keep faces unrecognisable but tone/colour visible.
  - On tap → biometric prompt → on success, push to a full-screen photo viewer for that workout (carousel of all photos, swipeable). Caption strip stays visible inside the viewer.
  - On viewer dismiss → tile re-blurs in the grid.

### Biometric unlock

- **D-17-14 (Per-tile auth, every tap):** Each tile-tap fires a fresh biometric/passcode prompt. Returning to the grid re-blurs that tile. Photos do NOT remain unblurred across taps. Inside the viewer, swiping between that one workout's photos does NOT re-prompt.
- **D-17-15 (OS-level auth with built-in passcode fallback):**
  - **iOS:** `LAContext` with policy `.deviceOwnerAuthentication` (NOT `.deviceOwnerAuthenticationWithBiometrics`) so the OS handles Face ID / Touch ID / device passcode automatically. Reason string in German per app convention (e.g. `"Fortschrittsbild entsperren"`). `Info.plist` adds `NSFaceIDUsageDescription`.
  - **Android:** `BiometricPrompt` with `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` so the OS handles fingerprint / face / PIN / pattern / password. New dependency: `androidx.biometric:biometric:<latest stable>` (Claude picks exact version at planning time).
- **D-17-16 (No-credential device → free access):** If the device has no biometric AND no passcode, the OS auth call resolves with no challenge and photos unblur freely. Treat "no device security" as the user's own choice; do not block the feature, do not show a "set a passcode" upsell. (Strict-block tier deferred — see Deferred Ideas.)
- **D-17-17 (Auth failure handling):** Cancel / fail → close the auth sheet, tile stays blurred, no error toast. The user retries by tapping again. The OS already handles biometric lockout (failed attempts → biometric disabled until passcode used). Don't roll our own retry counter.

### Cross-platform shape (locked convention, not re-asked)

- **D-17-18 (KMP shared VM + native UI per project convention):** Despite ROADMAP.md's "via Compose Multiplatform" wording, this phase follows the **Phase 15 / 15.1 / 16 convention**: shared ViewModel(s) + state in `commonMain`, **Compose Material 3** for Android, hand-written **SwiftUI** for iOS (per `MEMORY.md` — user owns iOS UI). Plans deliver Android composables and emit a `17-IOS-HANDOFF.md` describing expected SwiftUI surfaces, observation pattern, and entry points. **No Compose Multiplatform iOS UI in this phase.**
- **D-17-19 (Photo I/O via expect/actual):** Camera/library invocation, file write/read, and biometric prompt are all platform code wrapped by `expect`/`actual` interfaces in `commonMain`. The shared VM only sees `ProgressPicture` domain models and operations like `capturePhoto()`, `loadPhotoBytes(id)`, `requestUnlock()`. Concrete platform API choices stay in `androidMain` / `iosMain`. Pattern reference: existing `createDataStore.ios.kt`.

### Claude's Discretion

- Exact blur radius / blur algorithm (must keep faces unrecognisable; must look smooth, not pixelated chunks).
- Where exactly the gallery entry sits on `OverviewScreen` / `OverviewView` (new card vs button on existing card vs section header — match Material 3 / SwiftUI conventions).
- Photo viewer UI: full-screen black background, edge swipe, page indicator (dots vs counter "2 / 5"), close button placement.
- Whether to allow deleting an individual photo from inside the viewer (long-press / overflow menu) — recommended yes; cascade-delete the row + file via `ProgressPictureRepository.delete(id)`.
- Capture flow's "Add another?" ergonomics — re-prompt vs back-to-Finished with a "+ Add another" chip.
- Camera vs library API choice (`TakePicture` vs CameraX on Android; `UIImagePickerController` vs `PHPickerViewController` on iOS) — pick the simpler API that meets D-17-03.
- File naming: `{uuid}.jpg` is locked; UUID flavour (`Uuid.random()` vs `UUID.randomUUID()`) is Claude's call.
- Exact `androidx.biometric` version, `LAContext` policy reason copy.
- Volume label format: `12 540 kg` with thin-space thousand separator (locale-aware German) recommended.
- Date label format on tile (`28. Apr.` German short).
- PR-count derivation method: read `xp_ledger` (Phase 15) vs re-run the PB diff. Either is acceptable.
- Whether to date-group tiles in the grid (sectioned by month) vs flat reverse-chronological grid.
- Single shared VM for gallery + viewer vs two VMs (`ProgressGalleryViewModel` listing tiles + `ProgressViewerViewModel(workoutId)` for the carousel). Claude leans toward two VMs for symmetry with Phase 15-09.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & State
- `.planning/ROADMAP.md` §"Phase 17" — Phase entry text + dependency on Phase 16.
- `.planning/PROJECT.md` §"Current State", §"Key Decisions" — Tech stack: Kotlin 2.3.20, Compose Multiplatform 1.10 (Android), SwiftUI iOS, Room KMP v8, Koin 4.2, kotlinx-datetime, KMPNativeCoroutinesAsync, DataStore Preferences.
- `.planning/STATE.md` §"Current Position" / §"Untracked Drift" — confirms Phase 16 just shipped; Phase 15 gamification + Phase 16 nutrition goals are foundation for the day-highlights overlay.

### Phase 15 (gamification — read for D-17-12 metric derivation)
- `.planning/phases/15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r/15-CONTEXT.md` — D-02 workout-volume formula, D-03 PR XP / PR detection contract, D-04 NutritionGoalDayPolicy ±10% predicate.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt` — read-only call from gallery tile builder.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/XpLedgerEntity.kt` + `GamificationDao` — option for counting PRs per workout via `source = PR_HIT` rows.

### Phase 15.1 (Overview-tab NavHost — required entry-point plumbing)
- `.planning/phases/15.1-ranks-and-achievements-browser/15.1-CONTEXT.md` D-151-15 — Overview tab now has its own `overviewNavController` + NavHost; this phase adds `ProgressGalleryRoute` to that NavHost.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/Routes.kt` — add `ProgressGalleryRoute` here.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/navigation/MainScreen.kt` — Overview NavHost lives here; wire `composable<ProgressGalleryRoute>`.
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/OverviewScreen.kt` — gains a "Progress" entry that navigates to `ProgressGalleryRoute`.

### Phase 16 (nutrition goals — for goal-day chip)
- `.planning/phases/16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p/16-CONTEXT.md` — D-16-15 keeps `±10%` tolerance; tile's "Goal day" chip reads through the same predicate. **Do not modify the predicate.**

### Workout integration points (capture trigger)
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt` — owns `saveReviewedWorkout()`. Per Phase 15-06, save returns the new workout `Long` id. Finished state must expose that id so the photo prompt can attach photos to it.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — `saveCompletedWorkout` already returns `Long` per Phase 15-06; no change needed.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/CompletedWorkoutEntity.kt` — parent table. **No schema change to this entity.**
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt` — Finished branch is where the auto-prompt card mounts on Android.
- `iosApp/iosApp/Views/Workout/` — corresponding Finished view on iOS (planner confirms exact path during research).

### Existing camera surface (pattern reference)
- `iosApp/iosApp/Views/Nutrition/BarcodeScannerView.swift` — establishes iOS `AVCaptureSession` + camera permission pattern. Photo capture uses different APIs (`UIImagePickerController` / `PHPickerViewController`) — the precedent worth reusing is the `NSCameraUsageDescription` Info.plist convention and the SwiftUI ↔ UIKit bridge style.
- `androidApp/build.gradle.kts` — `androidx.camera:camera-camera2/lifecycle/view 1.6.0` already on the classpath from the barcode scanner. Available but not required for D-17-03 (system pickers are simpler).
- `androidApp/src/androidMain/AndroidManifest.xml` — `CAMERA` permission already declared.

### Schema, DI, and KoinHelper convention
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — currently `version = 8` with `AutoMigration(7, 8)`. This phase bumps to **`version = 9`**, adds `AutoMigration(8, 9)`, adds `ProgressPictureEntity::class` to the entities list, adds the new DAO.
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — register `ProgressPictureRepository`, `ProgressPictureDao` provider, the new VM(s). Claude's discretion: standalone `ProgressGalleryModule.kt` (parity with `GamificationUiModule`) vs fold into `SharedModule`.
- `shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt` + `RanksAndAchievementsKoinHelper.kt` — canonical KoinHelper shape (one helper per VM, no caching) to mirror for `ProgressGalleryKoinHelper`.

### Permissions / config files (must touch)
- `iosApp/iosApp/Info.plist` — already has `NSCameraUsageDescription`. **Add** `NSPhotoLibraryUsageDescription` and `NSFaceIDUsageDescription` (mandatory for `LAContext.deviceOwnerAuthentication` on devices with Face ID).
- `androidApp/src/androidMain/AndroidManifest.xml` — `CAMERA` already declared. Photo Picker (`PickVisualMedia`) needs no extra permission on Android 13+; on older devices, planner decides whether to request `READ_MEDIA_IMAGES`. **Add** `<application>` `dataExtractionRules` (Android 12+) + `fullBackupContent` (Android 11-) referencing a new `androidApp/src/androidMain/res/xml/backup_rules.xml` that excludes `progress_pics/`.

### expect/actual platform code patterns
- `shared/src/iosMain/kotlin/com/pumpernickel/data/preferences/createDataStore.ios.kt` — existing example of platform-specific path resolution and `expect`/`actual` factory pattern. New `expect class PhotoVault`, `expect class PhotoCaptureLauncher`, `expect class BiometricGate` follow this shape.

### Reference (no direct port)
- `/Users/olli/schenanigans/gymtracker` — Firmware reference. No photo / vault logic to port; this phase is mobile-only.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 15 PR detection + volume formula** — `XpFormula`, `GamificationEngine`, `CompletedWorkoutDao` PB query give us PR counts and volume per workout for the tile overlay. No need to re-derive.
- **Phase 15/16 NutritionGoalDayPolicy** — pure predicate; the tile's "Goal day" chip is a one-liner read.
- **Phase 15.1 Overview-tab NavHost** — already exists; add a single new route. No NavHost reshape.
- **Phase 15-09 KoinHelper convention** — model for the new gallery VM's DI shape. One helper per VM, no caching.
- **Existing iOS `NSCameraUsageDescription` precedent** — established by the barcode scanner; this phase extends Info.plist with the photo-library + Face ID strings.
- **CameraX deps already on Android classpath** — but for a single still photo, `ActivityResultContracts.TakePicture` + `PickVisualMedia` are simpler and lighter than wiring CameraX preview UI.
- **Phase 15-06 `saveCompletedWorkout: Long` return** — the just-saved workout id is already plumbed through `WorkoutSessionViewModel`; the photo prompt can read it from the Finished state.

### Established Patterns
- **Room KMP additive AutoMigration** — Phase 15 (`7→8`) and pre-existing (`6→7`) set the precedent. `8→9` follows the exact same shape, additive only.
- **Shared VM + StateFlow + KMPNativeCoroutines** — `@NativeCoroutinesState val uiState: StateFlow<...>` collected via `collectAsState()` on Android and `asyncSequence` on iOS. New gallery + viewer VMs follow this line-for-line.
- **One KoinHelper per VM (iosMain)** — `AchievementGalleryKoinHelper`, `RanksAndAchievementsKoinHelper`. New helper(s) mirror them.
- **expect/actual for platform-specific I/O** — DataStore (`createDataStore.ios.kt`) is the existing example. New `expect class PhotoVault`, `BiometricGate`, `PhotoCaptureLauncher` follow this shape.
- **iOS handoff doc convention** — Phase 15.1 D-151-16 set the precedent for `15.1-IOS-HANDOFF.md`. This phase emits `17-IOS-HANDOFF.md` capturing the SwiftUI surfaces the user will hand-write.
- **kg display convention** — workout weights are stored as kg×10 integers; UI displays the actual kg value (D-17-12 volume label uses real kg, not kg×10).
- **Material 3 surface + gradient overlay** — established in workout history detail / nutrition cards; same primitives apply to the tile caption strip.

### Integration Points
- `WorkoutSessionViewModel` Finished state must expose the just-saved `workoutId: Long` (Claude's discretion: extend the `Finished` data class with `workoutId` + a `photoCount: Int` for the in-prompt counter, or pass them as constructor args).
- `OverviewScreen` (Android) + `OverviewView` (iOS) gain a "Progress" entry → `ProgressGalleryRoute` (Android nav) / `.fullScreenCover` or `NavigationLink` (iOS).
- New shared VMs: `ProgressGalleryViewModel` (lists workouts-with-photos as tiles, plus their derived stats), `ProgressViewerViewModel(workoutId)` (one workout's carousel).
- New shared services (expect/actual): `PhotoVault` (write/read/delete bytes by id), `PhotoCaptureLauncher` (open camera or library, return bytes + suggested format), `BiometricGate` (`requestUnlock(reason: String): UnlockResult`).
- New Room: `ProgressPictureEntity`, `ProgressPictureDao` (queries by `workoutId` ordered by `sortOrder ASC`, plus a "workouts-with-photos" query for the gallery list).
- New Koin module `ProgressGalleryModule.kt` (or fold into `SharedModule.kt`).
- New Android route `ProgressGalleryRoute` + screens `ProgressGalleryScreen.kt` and `ProgressViewerScreen.kt`. Routes.kt + MainScreen.kt overview-tab NavHost edits.
- iOS handoff: `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift`, `ProgressViewerView.swift`, `OverviewView.swift` edit (add the gallery entry button), `Info.plist` edits (`NSPhotoLibraryUsageDescription` + `NSFaceIDUsageDescription`).
- Workout-save hook: extend `WorkoutSessionScreen.kt` (Android) and the iOS Finished view to mount the photo-prompt card driven by a `ProgressPicturePromptViewModel` (or fold into `WorkoutSessionViewModel` — Claude's discretion).

</code_context>

<specifics>
## Specific Ideas

- **"Vault" framing is load-bearing.** The user's word: files must be inaccessible from outside the app. App-private storage + `NSFileProtectionComplete` + `dataExtractionRules` is the agreed level. Don't surface photos via a share sheet, don't write to system Photos, don't expose via Files.app, don't copy to a public dir.
- **Per-tile every-tap auth was the explicit privacy choice over a friendlier per-session unlock.** Resist softening this in the planner — every tap is a fresh auth. If real-world feedback shows it's too aggressive, a settings toggle for per-session can ride later (deferred idea).
- **Photo-first tile, stats subordinate.** Bottom caption strip preview accepted. Photo dominates the tile; stats sit in a translucent gradient strip. Don't move to "stat card with photo backdrop" or "split: photo top, stat row below".
- **Multiple photos per workout, gallery is one tile per workout.** The unlock target is the workout, not the photo. Carousel inside.
- **Roadmap text "via Compose Multiplatform" is a misnomer.** Existing project convention overrides — Android Compose + SwiftUI iOS, not Compose Multiplatform iOS UI. Plans deliver an iOS handoff doc.
- **Skip is non-blocking.** The auto-prompt is a card on the Finished screen, not a modal. The user can ignore it and tap Done. Don't block the workout-completion flow on photo capture.
- **No-credential device unblurs freely.** That was the deliberate softening — the user accepted that "no device security" is the user's own choice, not the app's problem.

</specifics>

<deferred>
## Deferred Ideas

- **Retro-add photos from Workout History detail.** Out of v1 (D-17-04). Could ship as a small follow-up phase.
- **App-managed AES encryption + biometric-bound key.** The strict-vault tier was discussed and deferred to keep prototype scope tight. Revisit if the threat model expands (jailbroken/rooted device extracting bytes is the failure mode the deferred tier defends against).
- **Per-session unlock (auth once on gallery open).** Rejected for v1 — per-tile is the privacy framing. Could be added as a settings toggle later if every-tap fatigue surfaces in real use.
- **Re-lock on app background.** Implicitly handled by per-tile every-tap (re-entering the gallery after backgrounding still re-prompts on the next tap), but a coarse "lock all on background" handler is deferred.
- **Editing photos (filters, crops, brightness, rotation).** Out of scope. The captured image is what's stored. Revisit if users ask for it.
- **Sharing / exporting photos out of the app.** Explicitly out of scope — undermines the vault. Deferred indefinitely.
- **Per-photo caption / notes.** Useful, but defer — Phase 17 is the foundation; notes can ride later.
- **Date-grouped sectioning ("April", "March") in the gallery grid.** Claude's discretion at planning time — could ship in v1, could defer to a polish phase.
- **Auto-cropping / face detection / pose estimation.** No.
- **Progress charts / weight-over-time graphs alongside photos.** Project-wide deferral — no charting library; not reintroduced here.
- **Strict-block tier (refuse access if no biometric enrolled, force-prompt the user to set a passcode).** Rejected (D-17-16) — treats the user as adult.
- **Compose Multiplatform iOS UI for this phase.** Rejected (D-17-18) — project convention is SwiftUI iOS.
- **Generate a separate thumbnail file per photo.** Rejected (D-17-07) — single resized JPEG is enough for prototype scale.
- **Originals at full resolution.** Rejected — keeping originals balloons disk usage; resize is the pragmatic choice.
- **Seed photos for retroactive workouts.** N/A — there's no retroactive walker; pre-existing workouts simply have no photos and don't appear in the gallery.

### Reviewed Todos (not folded)

None — `gsd-sdk query todo.match-phase 17` returned 0 matches.

</deferred>

---

*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Context gathered: 2026-04-28*

---
phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
plan: 08
subsystem: ios-handoff-doc
tags: [docs, ios, swiftui, kmp-native-coroutines, koin-helpers, biometric, info-plist, threat-model, handoff]

# Dependency graph
requires:
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 04
    provides: "iOS actuals (PhotoVault.ios / BiometricGate.ios / PhotoCaptureLauncher.ios) + PhotoCapturePresenterHolder + Info.plist usage descriptions"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 05
    provides: "ProgressPicturePromptViewModel in commonMain + WorkoutSessionState.Finished.workoutId field"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 06
    provides: "ProgressGalleryViewModel + ProgressViewerViewModel in commonMain (with explicit unlockedWorkoutId gate)"
  - phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work
    plan: 07
    provides: "Three iOS Koin helpers (ProgressGalleryKoinHelper / ProgressViewerKoinHelper / ProgressPicturePromptKoinHelper)"
provides:
  - "17-IOS-HANDOFF.md — single doc the user reads to ship the SwiftUI half of Phase 17 without consulting the Kotlin source"
affects:
  - "User's hand-written SwiftUI work — three new view files, two existing-file edits, one PhotoCapturePresenterHolder.attach call site"

# Tech tracking
tech-stack:
  added: []  # docs only
  patterns:
    - "iOS-handoff doc convention (Phase 15.1 D-151-16 precedent) — 9-section structure (header, what-you-are-building table, Kotlin contract, per-file templates, visual specs, biometric copy, privacy hardening, negative requirements, acceptance checklist)"
    - "Per-VM Swift call signature documented as ClassName().method() — class-not-object Koin helpers are the Phase 15.1+ convention; doc replicates it 1:1 for the three new helpers"
    - "asyncSequence(for: viewModel.uiStateFlow) observation snippet repeated in each per-file template — copy-paste-friendly for hand-written SwiftUI"

key-files:
  created:
    - ".planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md"
  modified: []

key-decisions:
  - "Doc is 763 lines (well over the 200-line minimum the plan called for as 'comparable depth to 15.1's IOS-HANDOFF'). Phase 17 has more iOS surface than Phase 15.1 (three NEW Swift files vs two; biometric carousel vs read-only ladder; three Koin helpers vs two; explicit privacy / negative-requirements section to enforce the vault framing) — so the extra length is load-bearing, not padding."
  - "Single 'INFRASTRUCTURE' row in the what-you-are-building table for the PhotoCapturePresenterHolder.shared.attach call. The 17-04-SUMMARY hand-off note flagged this as 'a small infrastructure task the user owns' — the doc surfaces it as a first-class checklist item rather than a buried footnote, because forgetting the attach silently breaks every iOS capture button."
  - "Negative requirements section enumerated 10 'don't do these' items (no Photos library write, no share sheet, no Files.app exposure, no iCloud Drive sync, no per-session unlock, no biometric-only fallback, no error toast on cancel, no Compose Multiplatform iOS UI, no retro-add from History, no separate thumbnail file). Aligns with CONTEXT specifics line 205 'vault framing is load-bearing' and the 17-04 / 17-06 threat models."
  - "T-BIOMETRIC-BYPASS specifically called out in the viewer template — the doc tells the user 'render LockedPlaceholder when state.unlockedWorkoutId == nil' and links that to the Kotlin VM's two-write-site invariant from 17-06. This is a defense in depth: even if the Kotlin invariant ever drifted, the doc tells the SwiftUI side to treat unlockedWorkoutId as the gate."
  - "Volume label and date label use German locale (Locale('de_DE')) per CONTEXT D-17-12; the narrow no-break-space character was deliberately replaced with a regular space in the example code (matches 17-06 deviation 3 — the visual character is best produced via NumberFormatter at runtime, not as a literal in source)."
  - "PR/goal-day emoji (🏆 / 🍎) replaced with plain text 'PR' / 'Goal day' in the doc's example code, per CLAUDE.md preference 'avoid writing emojis to files unless asked'. The visual concept is preserved — the user can substitute their preferred glyphs (or SF Symbols) at write time."

# Metrics
duration: 4min 7sec
completed: 2026-05-01

requirements-completed:
  - D-17-18  # SwiftUI hand-written for iOS UI in this phase (not Compose Multiplatform); doc enforces this convention
---

# Phase 17 Plan 08: 17-IOS-HANDOFF.md authored Summary

**One 763-line markdown doc (`17-IOS-HANDOFF.md`) lands at the canonical path: 9-section structure mirroring Phase 15.1's IOS-HANDOFF, three full SwiftUI view templates (ProgressGalleryView LazyVGrid + blurred tiles, ProgressViewerView TabView paged carousel + biometric gate, ProgressPicturePromptCard three-button card), two existing-file edit templates (OverviewView entry, WorkoutFinishedView card mount), one infrastructure wiring call (PhotoCapturePresenterHolder.shared.attach), the full Kotlin contract block (three VMs + three Koin helpers + photo-I/O bridge), visual specs table, German biometric copy reference, privacy hardening table, 10-item negative-requirements list, 18-item acceptance checklist. D-17-18 stated explicitly twice (header reminder + negative-requirements row). All 19 acceptance grep patterns pass.**

## Performance

- **Duration:** 4 min 7 sec
- **Started:** 2026-05-01T16:06:17Z
- **Completed:** 2026-05-01T16:10:24Z
- **Tasks:** 1 (single atomic commit)
- **Files created:** 1 (`17-IOS-HANDOFF.md`)
- **Files modified:** 0 (Kotlin/Swift code untouched — the doc IS the deliverable)
- **Doc length:** 763 lines (vs. 458 lines for 15.1-IOS-HANDOFF — Phase 17 has more surface area: 3 new Swift files vs 2, biometric carousel vs read-only ladder, three Koin helpers vs two)

## Accomplishments

- **`17-IOS-HANDOFF.md` (NEW, 763 lines)** — Single deliverable. Section structure:
  1. **Header** — audience, scope, pbxproj convention reminder, D-17-18 explicit reminder.
  2. **What you are building** — 6-row table (3 NEW Swift files + 2 MODIFY + 1 INFRASTRUCTURE wire) + Info.plist verification subsection.
  3. **Kotlin contract** — full code blocks for `ProgressGalleryViewModel`, `ProgressViewerViewModel`, `ProgressPicturePromptViewModel` + their UI-state data classes, `NavEvent` sealed class, three Koin helpers, and the `PhotoCapturePresenterHolder` photo-I/O bridge with the SwiftUI attach call.
  4. **File 1 — `ProgressGalleryView.swift`** — full SwiftUI template (LazyVGrid 2-col + blur radius 24pt + caption strip with date/name/volume/PR/goal-day), `GalleryTileView` private struct, `CoverImage` placeholder for PhotoVault byte-loading, plus visual spec list.
  5. **File 2 — `ProgressViewerView.swift`** — full SwiftUI template (full-screen black + TabView paged carousel + LockedPlaceholder + per-photo PhotoPage), explicit T-BIOMETRIC-BYPASS callout (render LockedPlaceholder when `unlockedWorkoutId == nil`), `.onAppear { requestUnlock() }` / `.onDisappear { relock() }` lifecycle hooks documented.
  6. **File 3 — `ProgressPicturePromptCard.swift`** — full SwiftUI template (three-button card with bordered prominent / bordered / borderless treatments + busy/count footer + inline error), `dismissed`-flag handling, "Noch ein Foto?" header switch on `photoCount > 0`.
  7. **File 4 — `OverviewView.swift` MODIFY** — drop-in NavigationLink with translucent material + leading icon + trailing chevron (matches existing nutrition-banner shape).
  8. **File 5 — `WorkoutFinishedView.swift` MODIFY** — single drop-in `ProgressPicturePromptCard(workoutId: state.workoutId)` mount above Done; explicit reminder NOT to change Done's enabled state (D-17-01 non-blocking).
  9. **Visual specs** + **Biometric prompt copy** + **Privacy hardening** + **Negative requirements** + **Acceptance criteria checklist** + **Reference precedents**.

- **All 19 acceptance grep patterns pass** (full list from the plan's `<acceptance_criteria>`):
  - All 5 file-path mentions (3 NEW + 2 MODIFY)
  - All 3 VM class names (ProgressGalleryViewModel / ProgressViewerViewModel / ProgressPicturePromptViewModel)
  - All 3 method/property names (requestUnlock, relock, unlockedWorkoutId)
  - Both Info.plist keys (NSPhotoLibraryUsageDescription, NSFaceIDUsageDescription)
  - Both forbidden Info.plist keys (UIFileSharingEnabled, LSSupportsOpeningDocumentsInPlace — referenced as MUST NOT add)
  - D-17-18 explicit
  - PhotoCapturePresenterHolder explicit
  - "Acceptance criteria checklist" section header
  - asyncSequence observation pattern (used in all three SwiftUI templates)
  - Doc length ≥ 200 lines (763 lines, far exceeds the bar)

- **Mirrors Phase 15.1 IOS-HANDOFF shape verbatim** — same 9-section flow, same audience-line / scope-line / pbxproj-convention header, same per-file template style with code blocks + visual spec list + rules/gotchas, same acceptance-checklist format. Differences are purely scope-driven (more files, biometric carousel adds a complete sub-section).

## Task Commits

Each task committed atomically with `--no-verify` (worktree convention):

1. **Task 1: Author 17-IOS-HANDOFF.md spec doc** — `4d60cf5` (docs)

## Decisions Made

- **Doc length 763 lines, not "minimal" 200.** The plan called for "comparable depth to 15.1's IOS-HANDOFF" (which is 458 lines). Phase 17 has more iOS surface than Phase 15.1: three NEW Swift files vs two (added the prompt card), three Koin helpers vs two, biometric viewer carousel vs read-only ladder, plus extensive privacy hardening / negative requirements that 15.1 didn't need. The 305-line increment over 15.1 is all load-bearing — every section maps to a CONTEXT decision or threat the SwiftUI hand-writer must respect.
- **Negative requirements section enumerates 10 items.** CONTEXT specifics ("vault framing is load-bearing") and the 17-04 / 17-06 threat models flag at least 8 distinct things the SwiftUI side must NOT do. Adding a single "don't do this" table is more legible than scattering the warnings through individual templates. The user reads the table once, internalises it, and the templates stay code-focused.
- **PhotoCapturePresenterHolder.shared.attach surfaced as a first-class INFRASTRUCTURE row.** The 17-04-SUMMARY hand-off note explicitly flagged this as "a small infrastructure task the user owns — not yet wired by Kotlin." Forgetting the attach silently breaks every iOS capture button (PhotoCaptureLauncher.captureFromCamera() / pickFromLibrary() resolve to null). Surfacing it in the headline table + acceptance checklist makes it impossible to miss.
- **PR/goal-day emoji replaced with plain text in the example code.** CLAUDE.md says "avoid writing emojis to files unless asked" — applied to the doc's code samples. The visual concept is preserved (the comment line above each text says what the emoji would represent); the user can substitute their preferred glyphs (or SF Symbols like "trophy.fill" / "leaf.fill") at write time without the doc dictating a specific glyph.
- **T-BIOMETRIC-BYPASS doubly defended.** The Kotlin VM's invariant (two write sites for `_unlockedWorkoutId.value`) is the load-bearing protection per 17-06; the doc adds a "render LockedPlaceholder when state.unlockedWorkoutId == nil" rule for the SwiftUI side. Two layers of defense — even if the Kotlin invariant ever drifted, the SwiftUI side would still gate on the state field. Documented explicitly in the File 2 template's "Critical" callout.
- **Type aliases at the bottom of each Swift template.** Mirrors 15.1-IOS-HANDOFF's `private typealias SharedRankRow = Shared.RankRow` pattern. Makes the templates compile (or fail with helpful diagnostics) without forcing the user to remember the `Shared.` prefix on every type reference. Documented once per file.

## Deviations from Plan

None. Plan executed exactly as written. The plan's `<action>` block provided the full doc body; this executor faithfully transcribed it with three small adjustments documented in **Decisions Made** above (none of which constitute deviations from the plan's intent):

1. Emoji glyphs replaced with plain text in code samples (CLAUDE.md preference).
2. Narrow no-break space replaced with regular space in code samples (matches 17-06 deviation 3 — the locale-aware character is produced at runtime via NumberFormatter, not via a literal in source).
3. The plan's "iosApp.swift / SceneDelegate / ContentView.onAppear" placeholder for the PhotoCapturePresenterHolder.attach site clarified to point at `iosApp/iosApp/PumpernickelApp.swift` (which is the actual entry-point file in this codebase, verified via `ls iosApp/iosApp/`).

All three are within the plan's explicit latitude ("Use the Write tool to create the file. Do not embed any invisible Unicode characters; if the user wants the German narrow no-break space in the volume label, they get it via NumberFormatter + Locale('de_DE') which inserts it correctly without source-code shenanigans.").

## Issues Encountered

- None. No checkpoints, no Rule 4 architectural escalations, no auth gates. The plan was a single-file documentation task; the only adjustments were format-level (emoji, whitespace) and entry-point-path clarifications.

## TDD Gate Compliance

Plan type: `execute` (not TDD). Plan frontmatter does not declare `type: tdd`. No RED/GREEN/REFACTOR gate required. Task is `type="auto" tdd="false"`.

## User Setup Required

None on the Kotlin / Android side — this plan ships only documentation. The user's iOS work, when undertaken, follows the doc's checklist:

1. Create three Swift files (`ProgressGalleryView.swift`, `ProgressViewerView.swift`, `ProgressPicturePromptCard.swift`) under their canonical paths.
2. Edit two existing Swift files (`OverviewView.swift` to add the gallery entry, `WorkoutFinishedView.swift` to mount the prompt card).
3. Add the `PhotoCapturePresenterHolder.shared.attach(controller:)` call in the iOS app entry point (`PumpernickelApp.swift` or wherever the SwiftUI scene is built).
4. Drag the new Swift files into the Xcode project (no `.pbxproj` edit by GSD — Xcode owns that file per the 15.1 D-151-17 convention).
5. Build, run, and walk the 18-item acceptance checklist.

## Threat Flags

None. The doc itself introduces no new attack surface — it is a derivation of CONTEXT decisions and existing Plan summaries. The plan's `<threat_model>` register (T-17-08-01 doc-author copy/paste leak, T-17-08-02 future-maintainer drift) is mitigated by:

- T-17-08-01: the "Negative requirements" section explicitly enumerates the 10 'don't do these' items, including no Photos library writes, no share sheet, no Files.app exposure, no iCloud sync. The doc is reviewed by the user (the iOS hand-writer) before code lands.
- T-17-08-02: doc lives in version control; CONTEXT.md decisions are the load-bearing source — the doc is a derivation, not the source of truth. PR drift on this doc would surface on review.

No threat flags raised — every iOS surface the doc describes is already in the phase-level threat model from 17-02 / 17-03 / 17-04 / 17-06.

## Known Stubs

None. The doc fully describes every iOS surface the user must implement; there are no `TODO`s, `FIXME`s, or "coming soon" placeholders. The two `task(id:)` blocks in the example `CoverImage` and `PhotoPage` views are intentionally commented-out (`// image = await loadBytes...`) because the exact `PhotoVault` Koin-helper shape is the user's discretion (the doc explicitly recommends adding a `PhotoVaultKoinHelper` for parity but does not dictate the helper's name — the user gets to decide whether to use `KoinPlatform.shared.getKoin().get(...)` directly or wrap it in a helper).

## Deferred Items Surfaced

- **`PhotoCapturePresenterHolder.shared.attach(controller:)` call site is the user's infrastructure task.** The doc surfaces this as INFRASTRUCTURE row in the headline table + an acceptance checklist item. It is documented but not implemented by Kotlin — the iOS hand-writer adds the single `PhotoCapturePresenterHolder.shared.attach(controller: rootViewController)` call in `iosApp/iosApp/PumpernickelApp.swift` (or wherever the SwiftUI scene is constructed) per the doc's "File 5 / Photo I/O bridge" section.
- **Optional `PhotoVaultKoinHelper`.** The doc recommends adding one for symmetry with the three VM helpers (so SwiftUI never touches the Koin DSL directly), but does not require it. The user can either add a fourth helper class (one-liner mirroring the existing helpers) or use `KoinPlatform.shared.getKoin().get<PhotoVault>()` inline. Not blocking.
- **Pbxproj registration.** Per Phase 15.1 D-151-17 convention, GSD does not modify `iosApp/iosApp.xcodeproj/project.pbxproj`. When the user adds the new Swift files, they drag them into the Xcode project ("Add Files to iosApp..." with target membership = `iosApp`). Documented in the doc's header.

## Next Plan Readiness

This is the final plan in Phase 17. Phase 17 is complete on the Kotlin/Android side; the iOS deliverable is owned by the user and tracked via the acceptance checklist in `17-IOS-HANDOFF.md`.

Phase 17 closes with:
- Schema bumped to v9 with `progress_pictures` table (Plan 17-01)
- ProgressPictureRepository + DAO + expect classes shipped (Plan 17-02)
- Android actuals shipped (Plan 17-03)
- iOS actuals shipped (Plan 17-04)
- Capture flow VM + Compose card + WorkoutSessionScreen mount + MainActivity wiring shipped (Plan 17-05)
- Gallery + Viewer VMs + Compose screens + nav wiring shipped (Plan 17-06)
- Koin DI + iOS Koin helpers shipped (Plan 17-07)
- 17-IOS-HANDOFF.md authored (this Plan 17-08)

The next phase (per ROADMAP.md) is the responsibility of upstream planning. No blockers from this plan.

## Self-Check: PASSED

Verified before returning:

- `[ -f .planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-IOS-HANDOFF.md ]` -> FOUND
- `wc -l 17-IOS-HANDOFF.md` -> 763 lines (≥ 200 plan minimum)
- All 19 acceptance grep patterns pass (verified inline before commit):
  - 5 file-path patterns (ProgressGalleryView.swift, ProgressViewerView.swift, ProgressPicturePromptCard.swift, OverviewView.swift, WorkoutFinishedView.swift) — all FOUND
  - 3 VM class names (ProgressGalleryViewModel, ProgressViewerViewModel, ProgressPicturePromptViewModel) — all FOUND
  - 3 VM method/property names (requestUnlock, relock, unlockedWorkoutId) — all FOUND
  - 2 required Info.plist keys (NSPhotoLibraryUsageDescription, NSFaceIDUsageDescription) — both FOUND
  - 2 forbidden Info.plist keys (UIFileSharingEnabled, LSSupportsOpeningDocumentsInPlace) — both FOUND (referenced as MUST NOT add)
  - D-17-18 token — FOUND
  - PhotoCapturePresenterHolder token — FOUND
  - "Acceptance criteria checklist" section header — FOUND
  - asyncSequence pattern — FOUND
- `git log --oneline | grep 4d60cf5` -> FOUND: `docs(17-08): author 17-IOS-HANDOFF.md spec for SwiftUI surfaces`
- Worktree base verified at agent startup: reset to `e66eb88` per parent's expected commit
- Post-commit deletion check: zero deletions (only the new file added)
- No STATE.md / ROADMAP.md modifications (per parent agent constraints)

---
*Phase: 17-progress-pic-feature-with-biometric-locked-gallery-post-work*
*Completed: 2026-05-01*

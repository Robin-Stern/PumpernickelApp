---
phase: quick-260506-0hk
plan: 01
subsystem: ios-ui
tags: [ios, swiftui, progress-pics, layout-bug, phase-17-followup]
status: incomplete
status_reason: "Code fix shipped and iOS build succeeds. Visual confirmation on device/simulator deferred to human checkpoint — agent did not have simulator access."
requires:
  - phase-17 progress-pic feature surfaces (ProgressGalleryView from quick-260501-wcg)
provides:
  - Fixed gallery grid layout: tiles render inside the safe area with 16pt outer padding
  - CoverImage clips overflow at the layout level so .scaledToFill() cannot push the tile past its column
affects:
  - iosApp/iosApp/Views/Overview/ProgressGalleryView.swift
tech_stack:
  added: []
  patterns:
    - ".frame(maxWidth: .infinity, maxHeight: .infinity).clipped() inside an Image-loading view to constrain .scaledToFill() before parent .blur(opaque: true)"
    - "16pt horizontal screen-edge padding matches OverviewView convention (was 12pt)"
key_files:
  created: []
  modified:
    - iosApp/iosApp/Views/Overview/ProgressGalleryView.swift
decisions:
  - "Fix CoverImage from inside (not at the call site) so the layout system sees a constrained size before .blur(opaque: true) expands render bounds"
  - "Use .padding(.horizontal, 16).padding(.vertical, 12) instead of single-arg .padding(.) so the gallery aligns with the OverviewView 16pt horizontal convention while preserving 12pt vertical breathing room"
metrics:
  duration_minutes: 3
  completed: 2026-05-06
  task_count: 2
  files_modified: 1
  task_1_commit: db2812e
requirements:
  - BUG-260506-0hk-FIX
---

# Quick 260506-0hk: Fix iOS Progress Gallery Tile Layout Summary

**One-liner:** Two surgical SwiftUI edits to `ProgressGalleryView.swift` — `CoverImage` now clips its `.scaledToFill()` overflow at the layout level, and the `LazyVGrid` outer padding now matches the 16pt horizontal convention used by `OverviewView`.

## Root cause (two-line)

1. **Primary cause (image overflow):** `CoverImage` rendered an `Image` with `.scaledToFill()` but no constraining frame, so its intrinsic layout size was the oversized image — and `.blur(opaque: true)` rendered at that source size. The parent `ZStack`'s `.aspectRatio(1, contentMode: .fit)` then collapsed the visible square, but the underlying ZStack content had already overflowed left of its column. `.clipShape` only masked rounded corners; it did not constrain layout.
2. **Contributing cause (sub-convention padding):** The `LazyVGrid` used `.padding(12)`, 4pt below the 16pt horizontal padding used everywhere else in `OverviewView`. Combined with cause 1, the leading character of the date label sat on or under the safe-area edge.

## Edits applied

Both edits are in `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift`:

### Edit 1 — `CoverImage` body (primary cause)

Added inside `CoverImage.body`, between the `Group { … }` closure and `.task(id: relativePath)`:

```swift
.frame(maxWidth: .infinity, maxHeight: .infinity)
.clipped()
```

The flexible-frame modifier reports a column-derived size to the layout system, and `.clipped()` clips overflow at the layout level (not just visually) — so the oversized `.scaledToFill()` image cannot leak past the tile's bounds when `.blur(opaque: true)` is applied on the parent at the call site (`GalleryTileView` line 95). The fix is inside `CoverImage` (not at the call site after `.blur`) because the layout system needs to see a constrained size for the unblurred image before blur expands its render bounds.

### Edit 2 — `LazyVGrid` outer padding (contributing cause)

Replaced:

```swift
.padding(12)
```

with:

```swift
.padding(.horizontal, 16)
.padding(.vertical, 12)
```

16pt horizontal aligns with `OverviewView.swift` line 70 (`.padding(.horizontal, 16)`). 12pt vertical preserves the prior tile-to-screen-top breathing room.

## Convention reference

`OverviewView.swift` uses `.padding(.horizontal, 16)` at line 70 (outer scroll content) and line 459 (`NutritionGoalsBannerView`). The Overview header card, banner, and section surfaces all sit at 16pt from the screen edge. The gallery is now flush with that convention.

## Bug-report scorecard

The dispatch bug report listed five "likely causes" — for traceability, here is what was actually true. The dominant cause was image clipping, **which the report did not mention.**

| Bug-report guess                              | Verdict        | Reality                                                                                                          |
| --------------------------------------------- | -------------- | ---------------------------------------------------------------------------------------------------------------- |
| Negative offset / leading inset               | Ruled out      | No `.offset` calls in the file.                                                                                  |
| GridItem `.adaptive(...)` without min width   | Ruled out      | Grid uses `.flexible()`, not `.adaptive`.                                                                        |
| `.frame(maxWidth: .infinity)` w/o alignment   | Partial / no   | Used on the label VStack with `alignment: .leading` — correct, not the cause.                                    |
| Custom shape/clip drawing outside its frame   | **TRUE**       | `.scaledToFill()` does this; rounded `.clipShape` is a paint-time mask, not a layout clip. **Primary cause.**    |
| HStack/VStack `.leading` without padding      | Ruled out      | The label has its own `.padding(.horizontal, 10)` — fine.                                                        |
| (Not in report) Outer grid padding sub-convention | **TRUE**   | `.padding(12)` was 4pt below the 16pt convention used elsewhere in Overview. Contributing cause.                 |

One of five report guesses was correct. The dominant cause and the contributing cause both required reading the file rather than trusting the report.

## Verification

- **Code-level (passed):**
  - `grep -c "\.padding(\.horizontal, 16)" iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` → 1
  - `grep -c "\.clipped()" iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` → 1
  - `grep -cE "^\s*\.padding\(12\)\s*$" iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` → 0 (single-arg padding removed as expected)
- **Build-level (passed):** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' -configuration Debug build` → `** BUILD SUCCEEDED **` in 62.7s. No new errors. Pre-existing run-script warnings ("Build Kotlin Framework", "Embed Frameworks") unrelated to this change.
- **Static code-side checks for Task 2 (passed):**
  - SwiftUI structure: `.frame(...).clipped()` is correctly placed after the `Group { Image / Rectangle }` and before `.task(id:)`, so both the loaded-image branch and the placeholder-rectangle branch are constrained.
  - Scope: `CoverImage` is `private` to this file (`grep -rn CoverImage iosApp/` returned only `ProgressGalleryView.swift`). No other Phase 17 surface uses it — `ProgressViewerView`, gallery prompt card, and presenter holder unchanged.
  - File scope: `git status --short` shows only `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift` modified. Diff is +4 / -1, exactly the two intended edits.

## Human checkpoint — REQUIRED to mark complete

**Task 2 (`type=checkpoint:human-verify`) is OUTSTANDING.** This agent did not have iOS-simulator access; the code fix has been shipped and the build succeeds, but the visual outcome has not been confirmed.

To close the checkpoint, run on a simulator or device with at least one progress photo attached:

1. Open the Overview tab → tap **Fortschritts-Galerie**.
2. **Expect:** single tile sits inside the left column with 16pt left padding from the screen edge — same horizontal alignment as the Overview header / cards on the previous screen (compare by going Back; left edges should line up).
3. **Expect:** date label (e.g. "5. Mai · …") fully readable; the leading "5" not clipped against the screen edge.
4. **Expect:** tile is square, blurred cover image, rounded corners (14pt) — unchanged.
5. Add a second progress photo and reopen the gallery → **Expect:** two tiles flow side-by-side in row 1 with 12pt gutter and 16pt padding on both screen edges.
6. Tap a tile → **Expect:** ProgressViewerView opens normally; auth flow unchanged.

Failure modes to watch for (with hypothetical follow-ups):

- Tile still extending past the left screen edge → CoverImage clip didn't take effect at the right level; investigate placing `.clipped()` after `.blur` at the call site as a secondary clip.
- Tile too narrow with one-sided gap → padding asymmetric; confirm Edit 2 was applied as `.padding(.horizontal, 16).padding(.vertical, 12)` and not `.padding(16)`.
- Date label still clipped despite tile being on-screen → the tile's internal label padding was accidentally modified; recheck `GalleryTileView.body` lines 113–114.

## Deviations from Plan

None. Both edits applied exactly as the plan specified, in the files specified, with no scope creep.

The visual checkpoint (Task 2) is documented above as flagged-incomplete, in line with the executor constraints (no simulator access).

## Commits

- `db2812e` — fix(ios): clip ProgressGallery cover image and align grid padding to 16pt

## Self-Check: PASSED

- File `iosApp/iosApp/Views/Overview/ProgressGalleryView.swift`: FOUND
- Commit `db2812e`: FOUND in `git log` on `main`
- Grep gates `\.padding(\.horizontal, 16)` and `\.clipped()`: FOUND in modified file
- xcodebuild on iosApp scheme: BUILD SUCCEEDED

## Status reminder

`status: incomplete` until a human runs the visual verification on a simulator or device with at least one progress photo attached. Code fix and build are landed; only the visual confirmation is outstanding.

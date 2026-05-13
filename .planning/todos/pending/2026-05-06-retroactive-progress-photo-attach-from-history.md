---
created: 2026-05-06T20:47:40.361Z
title: Retroactive progress photo attach from History
area: ios
files:
  - composeApp/src/iosMain/kotlin/.../PhotoCaptureLauncher.ios.kt
  - iosApp/iosApp/Views/.../ProgressPicturePromptCard.swift
  - History tab workout-detail view (path TBD during plan)
  - PhotoVault + ProgressPictureRepository (write path)
---

## Problem

After a workout is saved, users can already attach progress photos via the post-workout three-action prompt
(Foto aufnehmen / Aus Galerie / Done). But if the user dismisses the prompt or remembers later, there is
currently no way to retroactively attach photos to a past workout. The user surfaced this gap during iOS
Phase 17 manual verification on 2026-05-05. Phase 17 itself is complete (`260501-wcg-SUMMARY.md`); this is
net-new scope, not a Phase 17 defect.

This completes the user story "I can add photos to any workout, ever" — relevant if progress-pic feature is
demoed end-to-end at the university deadline (~end of May 2026). Otherwise reasonable to defer to backlog
and ship Phase 17 as-is.

## Solution

**Entry point:** Button in the top-right of the workout-detail view in History.

**Tap behavior:** Opens the same three-action prompt as the post-workout card
(Foto aufnehmen / Aus Galerie / Done).

**Persistence:** Photos attach to the existing workout's progress-pic vault entry (same `workoutId`).

**Display:** Gallery view (Overview → Fortschrittsgalerie) shows newly-added photos under the original
workout's date. No separate "added later" indicator unless a need emerges.

### Reuse — DO NOT rebuild
- `PhotoCaptureLauncher.ios.kt` (camera + gallery) — just fixed in 0dedcbc / 3624415 / latest post-workout
  fix. Reuse as-is.
- `ProgressPicturePromptCard.swift` (commit 6d4eef4) — already a self-contained three-button card. Likely
  reusable from History without modification.
- `PhotoVault` + `ProgressPictureRepository` — write path already supports adding to an existing
  `workoutId`.
- `BiometricGate.ios.kt` — N/A on the write path; only relevant for viewing.

### Guardrails
- DO NOT change the Phase 17 schema (D-17-08 locked).
- DO NOT add a new auth gate for adding photos — write path is already implicitly behind the user being
  inside their own app.
- DO NOT change the post-workout prompt card UX.
- Camera and gallery paths must behave identically to the post-workout flow (graceful camera-unavailable,
  cancel-from-camera/gallery returns cleanly).

### Out of scope
- Removing photos from History (already exists via gallery viewer overflow per `260501-wcg-SUMMARY.md`
  deviation #4).
- Bulk-attach / multi-select.
- Editing workout fields from History.

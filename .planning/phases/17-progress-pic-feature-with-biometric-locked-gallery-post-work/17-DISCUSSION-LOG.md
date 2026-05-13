# Phase 17: Progress-pic feature with biometric-locked gallery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 17-progress-pic-feature-with-biometric-locked-gallery-post-work
**Areas discussed:** Capture flow & timing, Storage & lifecycle, Tile composition & blur, Biometric unlock UX

---

## Capture flow & timing

### Where in the post-workout flow does the photo prompt surface?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto on Finished screen | After save, present a card on Finished with Take/Library/Skip. Mirrors Phase 15 D-20 unlock-modal timing. | ✓ |
| Manual button on Recap | Add a "Progress pic" button on Recap; opt-in before Save. No auto-prompt. | |
| Both: Recap button + History retro-add | No auto-prompt; user adds on Recap and from History detail. Two entry points. | |
| Auto on Finished + History retro-add | Auto-prompt on Finished; also attach later from History detail. | |

**User's choice:** Auto on Finished screen.

### How many progress pictures per workout?

| Option | Description | Selected |
|--------|-------------|----------|
| Exactly one per workout | Nullable photoPath column on completed_workouts. Second photo replaces first. | |
| Up to one + replaceable from History | Single photo, but History detail allows replace/remove. | |
| Multiple per workout | New progress_pictures table FK→completed_workouts. Multiple photos per workout. | ✓ |

**User's choice:** Multiple per workout.

### With multiple photos per workout, how does the gallery represent them?

| Option | Description | Selected |
|--------|-------------|----------|
| One tile per workout, swipe inside | One blurred tile per workout (cover = most recent). Tap → unblurred carousel. | ✓ |
| One tile per photo | Each photo = its own tile. Day highlights duplicate or only show on first. | |
| One tile per workout, expand-to-grid | One tile → a sub-grid of all that workout's photos opens. | |

**User's choice:** One tile per workout, swipe inside.

---

## Storage & lifecycle

### How are photo files stored and referenced?

User clarified before answering: requirement is a "secure vault" — photos must NOT be accessible from outside the app. Question reformulated to focus on vault security level.

### What level of "vault" security for stored photos?

| Option | Description | Selected |
|--------|-------------|----------|
| Sandbox + excluded from backup (Recommended) | App-private dir; iOS NSFileProtectionComplete; Android backup-rules exclude dir; Files.app sharing OFF. Biometric is UI gate only. | ✓ |
| + App-managed AES encryption | Bytes on disk are AES-GCM ciphertext. Keychain/Keystore master key, accessible whenever app runs. | |
| + Biometric-bound encryption key | Strictest. Key requires biometric to unlock (LAContext ACL / setUserAuthenticationRequired + CryptoObject). | |

**User's choice:** Sandbox + excluded from backup.

**Notes:** The strict tiers are deferred upgrades, captured in CONTEXT.md Deferred Ideas. The chosen tier still meets "vault" semantics for prototype scale: photos never enter system Photos library, never enter cloud backup, can't be browsed by Files.app, are OS-encrypted on locked iOS devices.

### On capture, how should the photo be processed before disk write?

| Option | Description | Selected |
|--------|-------------|----------|
| Single resized JPEG, no thumbnail (Recommended) | Resize to 1600px long edge, JPEG q=0.8. One file per photo. ~200-400 KB each. | ✓ |
| Original + generated thumbnail | Keep original full-res, generate 512px thumbnail alongside. Two files per photo. | |
| Original only | Store whatever camera/library hands us, no processing. ~5-10 MB per photo. | |

**User's choice:** Single resized JPEG, no thumbnail.

---

## Tile composition & blur

### How are day highlights (volume / PRs / nutrition) laid out on the blurred tile?

| Option | Description | Selected |
|--------|-------------|----------|
| Bottom caption strip on photo | Photo fills tile; translucent gradient strip at bottom holds date + metrics. Polaroid-feel. | ✓ |
| Corner badges, photo dominant | Photo fills tile; metric chips float in corners. Most photo-forward. | |
| Stat card with photo backdrop | Stats are centrepiece, photo is dark backdrop. Most legible, least photo-forward. | |
| Split: photo top, stat row below | Top 70% photo, bottom 30% solid surface card. Cleanest separation. | |

**User's choice:** Bottom caption strip on photo.

**Notes:** Caption strip carries date + workout name (line 1), volume + PR count (line 2), goal-day chip (line 3, conditional on the day being a goal-day per Phase 15 NutritionGoalDayPolicy).

---

## Biometric unlock UX

### When the gallery is open, how is biometric unlock scoped?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-tile auth, every tap (Recommended for "vault" framing) | Each tap re-authenticates. Returning to grid re-blurs that tile. Strongest privacy. | ✓ |
| Per-session: one auth on gallery open | One auth on entry; all tiles unblur for the session. Friendly, looser privacy. | |
| Per-session with re-lock on app background | One auth on entry; re-lock when app backgrounds. Balanced default. | |
| Per-tile + remember in this session | First tap on each tile authenticates; tile stays unblurred for the session. | |

**User's choice:** Per-tile auth, every tap.

### What happens when biometrics aren't available on the device?

| Option | Description | Selected |
|--------|-------------|----------|
| Passcode fallback always; block if no passcode either (Recommended) | Use OS auth with passcode fallback. If no biometric and no passcode, refuse + prompt to set passcode. | |
| Passcode fallback always; allow free access if no passcode | OS auth with passcode fallback. If no biometric and no passcode, photos unblur freely. | ✓ |
| Biometric-strict; block all access without enrolled biometric | If no Face ID / fingerprint, gallery + capture both disabled. | |

**User's choice:** Passcode fallback always; allow free access if no passcode.

**Notes:** User accepted "no device security is the user's own choice" framing — the app does not lecture or block.

---

## Claude's Discretion

- Exact blur radius / blur algorithm (must keep faces unrecognisable but smooth, not pixelated chunks).
- Exact placement of the "Progress" entry on `OverviewScreen` / `OverviewView` (new card vs button on existing card vs section header).
- Photo viewer UI: full-screen black background, edge swipe, page indicator (dots vs counter), close button placement.
- Whether to allow deleting an individual photo from inside the viewer (recommended yes, cascade row + file).
- Capture flow's "Add another?" ergonomics (re-prompt vs back-to-Finished with "+ Add another" chip).
- Camera vs library API choice (`TakePicture` vs CameraX on Android; `UIImagePickerController` vs `PHPickerViewController` on iOS).
- File naming: `{uuid}.jpg` is locked; UUID flavour is Claude's call.
- Exact `androidx.biometric` version, `LAContext` policy reason copy.
- Volume label format (locale-aware German thin-space recommended).
- Date label format on tile (`28. Apr.` German short).
- PR-count derivation method: read `xp_ledger` (Phase 15) vs re-run PB diff.
- Whether to date-group tiles in the grid (sectioned by month) vs flat reverse-chronological grid.
- Single shared VM for gallery + viewer vs two VMs (Claude leans toward two for symmetry with Phase 15-09).
- File deletion timing: best-effort eventual vs transactional with row delete.

---

## Deferred Ideas

- Retro-add photos from Workout History detail (out of v1 — D-17-04).
- App-managed AES encryption + biometric-bound key (strict-vault tier — deferred upgrade).
- Per-session unlock or "remember in session" mode (rejected; could be a settings toggle later).
- Re-lock on app background (handled implicitly by per-tile every-tap).
- Editing photos (filters, crops, brightness, rotation).
- Sharing / exporting photos (explicitly out of scope — undermines vault).
- Per-photo caption / notes.
- Date-grouped sectioning in the grid (Claude's discretion at planning time, could be deferred).
- Auto-cropping, face detection, pose estimation.
- Progress charts / weight-over-time graphs.
- Strict-block tier when no biometric enrolled.
- Compose Multiplatform iOS UI for this phase.
- Separate thumbnail file per photo.
- Originals at full resolution.
- Seed photos for retroactive workouts (N/A — no retroactive walker).

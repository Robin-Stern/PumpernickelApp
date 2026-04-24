# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — MVP

**Shipped:** 2026-03-29
**Phases:** 4 | **Plans:** 12 | **Tasks:** 26

### What Was Built
- 873-exercise seeded catalog with search, filtering, and custom exercise creation
- Workout template CRUD with drag-and-drop reorder and exercise picker
- Full workout execution flow: set logging, rest timer with haptics, crash recovery
- Workout history with detail views, previous performance inline display
- kg/lbs unit toggle with global application via DataStore Preferences
- KMP shared business logic (ViewModels, Room, Koin) + SwiftUI iOS UI

### What Worked
- **3-wave plan pattern** (data layer → ViewModels → iOS UI) worked consistently across all 4 phases — clean dependency chain, no cross-wave conflicts
- **Sealed class state machine** for workout session — clean, testable, crash-recoverable
- **Room KMP** handled 3 schema migrations smoothly (v1 → v2 → v3) with no data loss
- **KMP + SwiftUI separation** — shared ViewModels with @NativeCoroutinesState let SwiftUI observe Kotlin StateFlows naturally
- **Worktree isolation** for parallel execution prevented git conflicts between plans

### What Was Inefficient
- **Xcode project registration** — new Swift files in new directories were not added to `project.pbxproj`, caught by verifier every time. Needs a systematic fix (script or plan step)
- **No automated tests** — v1.0 shipped without unit or integration tests. Verification relied on compilation checks and human testing. Acceptable for prototype speed but creates risk for v2

### Patterns Established
- `Entity → toDomain() → Domain Model` for all data layer conversions
- `ViewModel` with `MutableStateFlow` + `@NativeCoroutinesState` for iOS observation
- `KoinHelper` with typed getter functions for each ViewModel exposed to iOS
- Weight stored as `kgX10` integer throughout, display-only conversion at presentation layer
- `PlatformModule` expect/actual for platform-specific singletons (DataStore file paths)

### Key Lessons
1. Xcode project.pbxproj must be updated when creating Swift files in new directories — add this as a plan task or post-execution check
2. Room KMP @RawQuery is not available in KMP common — use @Query with inline SQL instead
3. `String.format` is not available in KMP common — use manual string construction for number formatting
4. DataStore Preferences requires a singleton instance per file — multiple instances cause `IllegalStateException`

### Cost Observations
- Model mix: ~80% opus, ~20% sonnet (sonnet for verification/checking)
- Sessions: 3 (phases 1-3 in session 1, phase 4 discuss+plan+execute+complete in session 2-3)
- Notable: Full v1.0 MVP from zero to shipped in 2 calendar days

---

## Milestone: v1.1 — Workout Polish & Firmware Parity

**Shipped:** 2026-03-31
**Phases:** 6 | **Plans:** 9 | **Tasks:** 18

### What Was Built
- iOS scroll wheel pickers for reps (0-50) and weight (0-1000 @ 2.5kg) replacing text fields
- Auto-increment pre-fill: set 2+ from previous actuals, set 1 from template targets, with 0-reps guard
- Volume-weighted average PB display per exercise during set entry
- Post-workout recap screen with tap-to-edit sets via wheel pickers before saving
- Mid-workout exercise reorder (drag on pending) and skip, with Room v4 crash recovery
- Abandon confirmation dialog (save & exit / discard / cancel) and toolbar context menu
- Firmware-style minimal SET N lifting screen with tap-to-reveal input
- Haptic feedback on set completion, VoiceOver accessibility labels on all workout views
- Color.appAccent design token replacing hardcoded RGB values, padding standardization

### What Worked
- **2-wave plan pattern** (KMP shared logic → SwiftUI UI) worked for all 6 phases — even cleaner than v1.0's 3-wave since the data layer was already in place
- **Sealed class state machine** extended cleanly to Reviewing state — the Active/Reviewing/Finished pattern handled recap without touching core workout logic
- **Room schema migration** (v3 → v4) for exerciseOrder was smooth — single column addition with CSV string, backward-compatible fallback
- **Verification + audit pipeline** caught real issues — PB VoiceOver abbreviation and hardcoded color scope were surfaced by the audit as tech debt
- **Firmware reference** continued to be valuable — volume-weighted PB formula, minimal set screen behavior, and auto-increment logic all came directly from gymtracker specs

### What Was Inefficient
- **Progress table stale** in ROADMAP.md — phases 6-9 showed "0/X Not started" even after completion because transitions weren't updating the table. The audit caught this inconsistency
- **REQUIREMENTS.md checkboxes not maintained** — audit found phases 6-10 requirements "not checked" in the traceability table despite being implemented and verified
- **Phase-by-phase PROJECT.md updates** — each phase completion appended a detailed block to Current State, making it grow unwieldy. A milestone-level summary is cleaner

### Patterns Established
- `SetPreFill` data class for atomic picker state emission (prevents race between cursor and picker values)
- `enterReview()` + `saveReviewedWorkout()` two-step pattern for any future review-before-save flows
- `exerciseOrder` CSV string pattern for lightweight crash recovery of list ordering
- `Color.appAccent` as the design token convention for shared styling
- `.accessibilityElement(children: .ignore)` with custom `.accessibilityLabel` for compound SwiftUI rows

### Key Lessons
1. Transition workflow should automatically update ROADMAP.md progress table and REQUIREMENTS.md checkboxes — manual updates drift
2. Two-day milestone velocity (9 plans) is sustainable when the data layer is stable and only UI + ViewModel work remains
3. Tech debt items from the audit (hardcoded colors in non-workout views, PB VoiceOver abbreviation) are real but correctly deferred — they don't block the milestone
4. Sequential coroutine ordering (enterReview + saveReviewedWorkout) works under Main.immediate but is fragile — consider a combined `saveAndExit()` method if the pattern is reused

### Cost Observations
- Model mix: ~85% opus, ~15% sonnet (sonnet for verification/audit)
- Sessions: 4 (phases 5-8 in sessions 1-2, phases 9-10 + audit + completion in sessions 3-4)
- Notable: v1.1 shipped in 2 calendar days (2026-03-29 → 2026-03-31), matching v1.0 pace

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Plans | Key Change |
|-----------|----------|--------|-------|------------|
| v1.0 | 3 | 4 | 12 | First milestone — established 3-wave pattern, KMP+SwiftUI architecture |
| v1.1 | 4 | 6 | 9 | Simplified to 2-wave (KMP → SwiftUI), added verification+audit pipeline |

### Top Lessons (Verified Across Milestones)

1. Xcode pbxproj registration is a recurring gap — automate or add explicit plan task
2. N-wave pattern (data → VM → UI) scales well for feature phases; reduces to 2-wave when data layer is stable
3. ROADMAP.md progress table and REQUIREMENTS.md checkboxes drift without automated transition updates
4. Milestone audit catches real issues that per-phase verification misses (cross-phase integration, stale metadata)

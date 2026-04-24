# Research Summary: v1.1 Workout Polish & Firmware Parity

**Domain:** Feature integration architecture for existing KMP + SwiftUI workout app
**Researched:** 2026-03-29
**Overall confidence:** HIGH

## Executive Summary

The v1.1 milestone adds nine features to the existing workout tab: scroll wheel pickers, auto-increment set logic, minimal "doing set" screen, post-workout recap/edit, mid-workout exercise reorder, abandon guards, context menu, personal best display, and general UI polish. Architecture research confirms that all features integrate cleanly with the existing `WorkoutSessionViewModel` + SwiftUI architecture without requiring new libraries, new Room tables, or a rewrite of the state machine.

The features divide naturally into three architectural categories. Four features (scroll wheel pickers, minimal doing-set screen, context menu presentation, and UI polish) are **purely SwiftUI changes** requiring zero KMP modifications. Three features (auto-increment, personal best display, and exercise reorder) extend the **KMP ViewModel and data layer** with new methods, StateFlows, and one Room DAO query. Two features (post-workout recap/edit and abandon guards) require **state machine evolution** -- specifically, adding a new `Reviewing` state between `Active` and `Finished` to support editing workout data before persistence.

The firmware reference (gymtracker) provides exact behavioral specifications for every feature. The firmware's `exerciseOrder[]` indirection array pattern maps directly to the recommended reorder architecture. The firmware's `WorkoutRecapState` -> `WorkoutSaveState` flow maps to the new `Reviewing` -> `Finished` state transition. No guesswork is needed on feature behavior -- the firmware is the spec.

The critical risk is around SwiftUI `Picker(.wheel)` behavior when two pickers sit side-by-side: overlapping touch areas, tag type mismatches between Kotlin `Int` (Int32) and Swift `Int`, and picker state resetting on ViewModel state emissions. These are well-documented issues with known solutions (UIPickerView extension, explicit `.clipped()`, separate `@State` for picker values). Prototyping the two-picker layout must happen first, before building any feature that depends on it.

## Key Findings

**Stack:** Zero new dependencies required. All features use built-in SwiftUI APIs (iOS 17+) and existing KMP infrastructure.
**Architecture:** One new sealed class state (`Reviewing`), one new field on `Active` (`exerciseOrder`), one Room schema migration, three new ViewModel methods.
**Critical pitfall:** Side-by-side SwiftUI Picker(.wheel) touch area overlap -- must prototype and validate before building the full picker UI.

## Implications for Roadmap

Based on research, suggested phase structure:

1. **Auto-increment + Scroll wheel pickers** - Foundation changes first
   - Addresses: Scroll wheel input, auto-increment pre-fill
   - Avoids: Picker touch area pitfall (validated early via prototype)
   - Rationale: These are the most user-visible changes and have zero dependencies on other features

2. **Personal best display** - Standalone data feature
   - Addresses: PB query, PB StateFlow, PB display in set entry
   - Avoids: Premature Room migration (computed from existing data, no schema change needed)
   - Rationale: Independent feature, motivating for users, simple DAO addition

3. **Post-workout recap/edit** - Core state machine change
   - Addresses: New Reviewing state, recap UI, edit-before-save flow
   - Avoids: Recap state reading from Room (uses in-memory snapshot instead)
   - Rationale: Foundation for abandon guards and context menu finish action

4. **Exercise reorder + Room migration** - Active state extension
   - Addresses: exerciseOrder indirection array, reorder sheet, skip exercise
   - Avoids: Direct array reordering (uses indirection to preserve index stability)
   - Rationale: Room migration 3->4 happens here; foundation for context menu skip action

5. **Abandon guards + Context menu** - Integration features
   - Addresses: Back-navigation guard, save & exit, skip/reorder/finish via menu
   - Avoids: Unguarded back gesture (already prevented by existing navigationBarBackButtonHidden)
   - Rationale: These wire together methods from phases 3 and 4

6. **Minimal doing-set screen + UI polish** - Final polish
   - Addresses: Minimal set screen, validation, keyboard handling, accessibility
   - Avoids: Premature polish (core features stable first)
   - Rationale: Pure SwiftUI, no dependencies, can absorb schedule pressure

**Phase ordering rationale:**
- Phases 1-2 are independent and deliver immediate value
- Phase 3 (recap) must precede phase 5 (abandon guards depend on finishForReview)
- Phase 4 (reorder) must precede phase 5 (context menu skip depends on exerciseOrder)
- Phase 6 is pure polish with no downstream dependencies

**Research flags for phases:**
- Phase 1: Needs picker prototype validation (side-by-side touch area, device testing)
- Phase 4: Needs Room migration testing (version 3 -> 4 with exerciseOrder column)
- All other phases: Standard patterns, unlikely to need additional research

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Zero new deps. All SwiftUI APIs verified available at iOS 17 deployment target. |
| Features | HIGH | Every feature has a direct firmware reference implementation with exact behavioral spec. |
| Architecture | HIGH | Direct codebase analysis of ViewModel, entities, DAOs, and SwiftUI views. Integration points mapped line-by-line. |
| Pitfalls | HIGH | 11 of 13 pitfalls sourced from Apple Developer Forums, JetBrains issues, or verified codebase patterns. |

## Gaps to Address

- **Picker prototype on physical device:** Side-by-side Picker(.wheel) behavior differs between simulator and real hardware. Must test on device early in phase 1.
- **Room migration path:** The exact SQL for adding `exerciseOrderJson` and `currentQueuePosition` columns needs to be written and tested on both platforms before phase 4 implementation.
- **`saveAndExit` vs `finishForReview`:** Whether "Save & Exit" from the abandon guard should go through the recap flow or save directly is a UX decision, not a technical one. Research recommends direct save (faster), but user preference may differ.

## Sources

- Direct codebase analysis of all KMP and SwiftUI source files (HIGH confidence)
- Direct firmware analysis: 7 workout-related state files from gymtracker (HIGH confidence)
- Apple Developer Forums: SwiftUI Picker issues (HIGH confidence)
- See ARCHITECTURE.md, STACK.md, PITFALLS.md for complete source lists

---
*Research completed: 2026-03-29*
*Ready for roadmap: yes*

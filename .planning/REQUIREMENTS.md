# Requirements — v1.1 Workout Polish & Firmware Parity

**Milestone:** v1.1
**Created:** 2026-03-29
**Source:** User questioning + gymtracker firmware reference analysis
**Confidence:** HIGH — all features have direct firmware behavioral specs

## Categories

### Set Entry

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| ENTRY-01 | ~~User can select reps via iOS scroll wheel picker (0–50, step 1)~~ | Must | ~~Picker spins freely, value updates on selection, visual matches iOS timer picker style~~ **DONE (05-02)** |
| ENTRY-02 | ~~User can select weight via iOS scroll wheel picker (0–1000, step 2.5kg)~~ | Must | ~~Picker shows values 0, 2.5, 5.0, ... 1000.0; 2.5kg increments throughout range~~ **DONE (05-02)** |
| ENTRY-03 | ~~Scroll wheel pickers display correctly in both kg and lbs modes~~ | Must | ~~When unit is lbs, picker shows lbs-equivalent values; stored value remains kgX10 internally~~ **DONE (05-02)** |
| ENTRY-04 | ~~Set 2+ auto-fills with previous set's actual reps and weight (not template targets)~~ | Must | ~~Complete set 1 with 10 reps @ 50kg, set 2 picker defaults to 10 reps @ 50kg~~ **DONE (05-01)** |
| ENTRY-05 | ~~Set 1 pre-fills with template target reps and weight~~ | Must | ~~First set of each exercise shows template-defined targets in picker~~ **DONE (05-01)** |
| ENTRY-06 | ~~User cannot complete a set with 0 reps~~ | Must | ~~"Complete Set" button disabled or validation error when reps = 0~~ **DONE (05-01)** |
| ENTRY-07 | User sees personal best (running average weight) for current exercise during set entry | Should | Label shows "PB: 62.5 kg" computed from all previous completed sets for that exercise |

### Workout Flow

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| FLOW-01 | User sees post-workout recap screen with all exercises and sets before saving | Must | After finishing last set, recap screen lists all exercises with their completed sets (reps, weight) |
| FLOW-02 | User can edit any set's reps and weight from the recap screen | Must | Tapping a set in recap opens edit sheet; changes reflect immediately in recap list |
| FLOW-03 | User can reorder pending exercises mid-workout via drag gesture | Should | Drag handle on pending exercises allows reordering; completed exercises locked in place |
| FLOW-04 | Exercise reorder preserves completed set data and crash recovery integrity | Must | After reorder + app crash, resume loads correct exercise order with completed sets intact |
| FLOW-05 | User sees abandon confirmation (save & exit / discard / cancel) when leaving mid-workout | Must | Tapping exit with 1+ completed sets shows alert with 3 options; save persists to history |
| FLOW-06 | User can access context menu during workout with skip, reorder, and finish options | Should | Menu button or long-press reveals skip exercise, reorder, finish workout actions |
| FLOW-07 | User can skip current exercise and move to the next one via context menu | Should | Skipping moves to next exercise; skipped exercise has 0 completed sets in history |

### UX Polish

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| UX-01 | User sees minimal "doing set" screen while lifting (set number + exercise name + tap prompt) | Should | After completing rest or starting exercise, minimal screen shows "SET N" + exercise + "Tap when done" |
| UX-02 | Haptic feedback fires on set completion (not just rest completion) | Should | Device vibrates on "Complete Set" tap (success feedback) |
| UX-03 | All interactive elements have accessibility labels for VoiceOver | Should | VoiceOver reads meaningful labels for pickers, buttons, set rows |
| UX-04 | Visual consistency across workout screens (colors, spacing, typography) | Should | Consistent use of accent color, padding, font weights across all workout views |

## Out of Scope

| Feature | Reason |
|---------|--------|
| RPE / RIR logging | Advanced feature, not in firmware reference |
| Voice entry | Complexity disproportionate to university project scope |
| Plate calculator | Nice-to-have, not core to firmware parity |
| Exercise substitution mid-workout | Requires catalog search mid-workout, defer to v2 |
| Audio alert on rest complete | Requires UNUserNotificationCenter, separate spike |
| Superset / drop set tracking | Different template model, defer to v2 |
| Crowdedness prompt | Firmware-specific (gym occupancy logging), not relevant for mobile |

## Dependencies

- Firmware reference: `/Users/olli/schenanigans/gymtracker` — behavioral specs for all features
- Existing codebase: WorkoutSessionViewModel (KMP), WorkoutSessionView.swift (SwiftUI)
- iOS deployment target: 17.0+ (required for .sensoryFeedback, Picker(.wheel) stability)
- Room schema: Migration 3→4 needed for exerciseOrder persistence (FLOW-03, FLOW-04)

## Traceability

| Requirement | Firmware Reference | Risk |
|-------------|-------------------|------|
| ENTRY-01/02 | WorkoutSetEntryState encoder pickers | SwiftUI Picker(.wheel) touch area overlap (prototype early) |
| ENTRY-04/05 | WorkoutSetEntryState::onEnter auto-increment | Low — 10-line ViewModel change |
| ENTRY-07 | Trends PB display on set entry | Low — existing completed workout data, new DAO query |
| FLOW-01/02 | WorkoutRecapState → WorkoutEditStatsState → WorkoutSaveState | Medium — new sealed class state (Reviewing) |
| FLOW-03/04 | exerciseOrder[] indirection array, WorkoutExerciseListState | Medium — Room migration, indirection array pattern |
| FLOW-05 | WorkoutAbandonConfirmState | Low — SwiftUI .confirmationDialog |
| FLOW-06/07 | WorkoutContextMenuState, skip/reorder actions | Low — SwiftUI .contextMenu or toolbar menu |
| UX-01 | WorkoutStartSetState minimal display | Low — pure SwiftUI, no KMP changes |

---
*Requirements approved: 2026-03-29*

# Phase 3: Workout Session - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 03-workout-session
**Areas discussed:** Workout navigation flow, Rest timer behavior & alerts, Set logging UX, Session persistence & crash recovery
**Mode:** Auto (--auto flag — all selections made by Claude using recommended defaults)

---

## Workout Navigation Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Linear progression | Exercise-by-exercise, set-by-set — mirrors gymtracker FSM | ✓ |
| Free navigation | User picks any exercise in any order | |
| Hybrid | Linear default with exercise overview for jumping | ✓ (combined with linear) |

**User's choice:** [auto] Linear progression with overview access for non-sequential jumps
**Notes:** Mirrors gymtracker firmware FSM. WORK-06 requires progress tracking (exercise X of Y, set X of Y) which implies linear progression as the primary flow. Overview allows experienced users to skip ahead or revisit.

---

## Rest Timer Behavior & Alerts

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-start after set | Timer begins immediately when set marked complete | ✓ |
| Manual start | User explicitly starts timer | |
| Skip button | User can proceed before timer ends | ✓ |
| No skip | Timer must complete before next set | |
| Haptic vibration | Foreground haptic when timer ends | ✓ |
| Local notification | Background notification via UNUserNotificationCenter | |
| Inline countdown | Timer shown within workout screen | ✓ |
| Full-screen overlay | Timer takes over entire screen | |

**User's choice:** [auto] Auto-start, skippable, haptic alert, inline display
**Notes:** WORK-04 requires auto-start. Skip keeps timer as guide, not gate. Haptic is simplest for prototype — background local notifications deferred to v2. Inline display keeps exercise context visible.

---

## Set Logging UX

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-filled from targets | Reps/weight auto-populated from template | ✓ |
| Blank fields | User enters all values from scratch | |
| Individual confirmation | Mark each set complete one at a time | ✓ |
| Batch confirmation | Complete all sets at once | |
| Editable previous sets | Can tap completed set to edit | ✓ |
| Forward-only | Cannot edit once confirmed | |

**User's choice:** [auto] Pre-filled, individual confirmation, editable previous sets
**Notes:** Pre-filling reduces friction for executing a planned workout. Individual confirmation per WORK-03. Editing previous sets allows correcting mistakes without restarting.

---

## Session Persistence & Crash Recovery

| Option | Description | Selected |
|--------|-------------|----------|
| Per-set persistence | Save to Room after every set completion | ✓ |
| Per-exercise persistence | Save after completing all sets of an exercise | |
| On-finish only | Save only when workout completed | |
| Auto-detect + prompt | Check for unfinished session on launch | ✓ |
| Manual recovery | User navigates to "Resume" option | |

**User's choice:** [auto] Per-set persistence with auto-detect and resume prompt
**Notes:** Per-set is most resilient — worst case loses only the in-progress set. Auto-detect on launch satisfies WORK-09 with minimal user friction.

---

## Claude's Discretion

- Database migration strategy (Room schema version bump)
- Exact Room entity design for active session and completed workout
- ViewModel state machine implementation (sealed class states)
- SwiftUI screen layout and component decomposition
- "Start Workout" placement (list row button vs detail screen)
- Elapsed duration display format
- Discard workout confirmation dialog

## Deferred Ideas

- Background rest timer notifications (UNUserNotificationCenter) — v2 enhancement
- RPE per set — v2 (WORK-12)
- Superset grouping — v2 (WORK-13)
- Ad-hoc workout without template — v2 (WORK-10)
- Set tagging — v2 (WORK-12)

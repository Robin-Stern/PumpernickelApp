# Roadmap: PumpernickelApp

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-29)
- 🔄 **v1.1 Workout Polish & Firmware Parity** — Phases 5-10

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-29</summary>

- [x] Phase 1: Foundation & Exercise Catalog (3/3 plans) — completed 2026-03-28
- [x] Phase 2: Template Management (3/3 plans) — completed 2026-03-28
- [x] Phase 3: Workout Session (3/3 plans) — completed 2026-03-28
- [x] Phase 4: History & Settings (3/3 plans) — completed 2026-03-29

</details>

### v1.1 Workout Polish & Firmware Parity

### Phase 5: Scroll Wheel Pickers & Auto-Increment
**Requirements:** ENTRY-01, ENTRY-02, ENTRY-03, ENTRY-04, ENTRY-05, ENTRY-06
**Goal:** Replace text field inputs with iOS scroll wheel pickers and add auto-increment logic so set entry feels native and fast.
- Replace text fields with iOS wheel pickers (reps 0-50, weight 0-1000 @ 2.5kg steps)
- Auto-increment: set 2+ pre-fills from previous set's actuals
- Input validation (0 reps blocked), kg/lbs mode
- Risk: Picker(.wheel) side-by-side touch area overlap — prototype first

### Phase 6: Personal Best Display
**Requirements:** ENTRY-07
**Goal:** Show the user's personal best for the current exercise during set entry.
- Room DAO query for average weight across completed sets per exercise
- "PB: 62.5 kg" label on set entry screen

### Phase 7: Post-Workout Recap & Edit
**Requirements:** FLOW-01, FLOW-02
**Goal:** Add a post-workout recap screen where users review and edit all sets before saving.
- New `Reviewing` sealed class state between Active and Finished
- Recap screen lists all exercises + sets; tap to edit before saving

### Phase 8: Mid-Workout Exercise Reorder
**Requirements:** FLOW-03, FLOW-04, FLOW-07
**Goal:** Allow reordering pending exercises mid-workout using the firmware's exerciseOrder pattern.
- exerciseOrder indirection array (firmware pattern)
- Room migration 3→4 for order persistence
- Drag reorder pending exercises; skip exercise action

### Phase 9: Abandon Guards & Context Menu
**Requirements:** FLOW-05, FLOW-06
**Depends on:** Phase 7, Phase 8
**Goal:** Add exit confirmation and a context menu with skip, reorder, and finish actions.
- Exit confirmation: Save & Exit / Discard / Cancel
- Context menu: skip, reorder, finish workout

### Phase 10: Minimal Set Screen & UI Polish
**Requirements:** UX-01, UX-02, UX-03, UX-04
**Depends on:** Phase 5
**Goal:** Add a firmware-style minimal lifting screen and polish UX across all workout views.
- Firmware-style minimal "SET N" display while lifting
- Haptics on set complete, accessibility labels, visual consistency

## Dependency Graph

```
Phase 05 ──────────────────────────► Phase 10
Phase 06 (independent)
Phase 07 ──────────┐
                    ├──────────────► Phase 09
Phase 08 ──────────┘
```

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation & Exercise Catalog | v1.0 | 3/3 | Complete | 2026-03-28 |
| 2. Template Management | v1.0 | 3/3 | Complete | 2026-03-28 |
| 3. Workout Session | v1.0 | 3/3 | Complete | 2026-03-28 |
| 4. History & Settings | v1.0 | 3/3 | Complete | 2026-03-29 |
| 5. Scroll Wheel Pickers & Auto-Increment | v1.1 | 0/? | Not started | — |
| 6. Personal Best Display | v1.1 | 0/? | Not started | — |
| 7. Post-Workout Recap & Edit | v1.1 | 0/? | Not started | — |
| 8. Mid-Workout Exercise Reorder | v1.1 | 0/? | Not started | — |
| 9. Abandon Guards & Context Menu | v1.1 | 0/? | Not started | — |
| 10. Minimal Set Screen & UI Polish | v1.1 | 0/? | Not started | — |

## Requirement Coverage

| Requirement | Phase | Status |
|-------------|-------|--------|
| ENTRY-01 | 05 | Planned |
| ENTRY-02 | 05 | Planned |
| ENTRY-03 | 05 | Planned |
| ENTRY-04 | 05 | Planned |
| ENTRY-05 | 05 | Planned |
| ENTRY-06 | 05 | Planned |
| ENTRY-07 | 06 | Planned |
| FLOW-01 | 07 | Planned |
| FLOW-02 | 07 | Planned |
| FLOW-03 | 08 | Planned |
| FLOW-04 | 08 | Planned |
| FLOW-05 | 09 | Planned |
| FLOW-06 | 09 | Planned |
| FLOW-07 | 08 | Planned |
| UX-01 | 10 | Planned |
| UX-02 | 10 | Planned |
| UX-03 | 10 | Planned |
| UX-04 | 10 | Planned |

**Coverage:** 18/18 requirements mapped (100%)

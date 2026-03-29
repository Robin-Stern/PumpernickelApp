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

- [ ] **Phase 5: Scroll Wheel Pickers & Auto-Increment** — ENTRY-01..06
  - Replace text fields with iOS wheel pickers (reps 0-50, weight 0-1000 @ 2.5kg steps)
  - Auto-increment: set 2+ pre-fills from previous set's actuals
  - Input validation (0 reps blocked), kg/lbs mode
  - Risk: Picker(.wheel) side-by-side touch area overlap — prototype first
- [ ] **Phase 6: Personal Best Display** — ENTRY-07
  - Room DAO query for average weight across completed sets per exercise
  - "PB: 62.5 kg" label on set entry screen
- [ ] **Phase 7: Post-Workout Recap & Edit** — FLOW-01, FLOW-02
  - New `Reviewing` sealed class state between Active and Finished
  - Recap screen lists all exercises + sets; tap to edit before saving
- [ ] **Phase 8: Mid-Workout Exercise Reorder** — FLOW-03, FLOW-04, FLOW-07
  - exerciseOrder indirection array (firmware pattern)
  - Room migration 3→4 for order persistence
  - Drag reorder pending exercises; skip exercise action
- [ ] **Phase 9: Abandon Guards & Context Menu** — FLOW-05, FLOW-06 *(depends on 07, 08)*
  - Exit confirmation: Save & Exit / Discard / Cancel
  - Context menu: skip, reorder, finish workout
- [ ] **Phase 10: Minimal Set Screen & UI Polish** — UX-01..04 *(depends on 05)*
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

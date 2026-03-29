# Phase 1: Foundation & Exercise Catalog - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the KMP project foundation (database, DI, navigation) and a fully browsable exercise catalog with custom exercise creation. Users can launch the app on iOS, see a bottom navigation shell, browse ~873 seeded exercises with search and muscle group filtering, view exercise details, and create custom exercises.

**Critical architectural decision:** The app uses KMP for shared business logic but **platform-native UI** — SwiftUI for iOS, Compose for Android. iOS is developed first. This replaces the original Compose Multiplatform shared-UI approach.

</domain>

<decisions>
## Implementation Decisions

### Architecture
- **D-01:** KMP shared business logic + SwiftUI for iOS + Compose for Android (iOS-first). No Compose Multiplatform shared UI.
- **D-02:** Development order: business logic in KMP first, then iOS (SwiftUI), then Android (Compose) adaptation.

### Exercise Data Model
- **D-03:** Store ALL fields from gymtracker's `free_exercise_db.json`: name, force, level, mechanic, equipment, primaryMuscles, secondaryMuscles, instructions, category, images, id.
- **D-04:** Image paths stored in DB but NOT displayed in prototype. Preserved for future use.
- **D-05:** Custom and seeded exercises live in the same table with an `isCustom` flag. Custom exercises have nullable fields that seeded ones always fill.
- **D-06:** Seeded exercises are read-only (not editable or deletable by user).

### Exercise Browsing UX
- **D-07:** Flat scrollable list with search bar at top and muscle group filter chips.
- **D-08:** Each exercise row shows: name (primary text) + primary muscle group (subtitle/chip).
- **D-09:** Tapping an exercise navigates to a full detail screen showing all fields (name, muscles, equipment, level, instructions, etc.).
- **D-10:** Filter by muscle group only. Search handles the rest.

### Muscle Group Picker (Anatomy SVG)
- **D-11:** Port the anatomy SVG from gymtracker's web component (`AnatomyFront.svelte`, `AnatomyBack.svelte`) to a native component. Front and back body views with tappable muscle regions.
- **D-12:** The anatomy SVG is used EVERYWHERE muscle groups appear — exercise catalog filtering, custom exercise creation, and any future muscle group selection.
- **D-13:** Muscle region mapping follows gymtracker's `muscleRegionMap.ts` (16 muscle groups, ~30 individual regions mapped to groups).

### Custom Exercise Creation
- **D-14:** Required fields: name, primary muscle group, equipment, category. Selected via fixed lists derived from seeded data.
- **D-15:** Muscle group selection uses the anatomy SVG picker (per D-12).

### Navigation Shell
- **D-16:** Bottom navigation with 3 tabs: Workout, Overview, Nutrition. Only Workout tab is functional.
- **D-17:** Workout tab home screen shows an empty state with CTA ("Browse Exercises" button) — navigates to exercise catalog.
- **D-18:** Overview and Nutrition placeholder tabs show styled empty states (icon + description of upcoming feature).
- **D-19:** Exercise catalog accessed via button on Workout tab home screen.
- **D-20:** Create Exercise action lives on a FAB (+) on the exercise catalog screen.

### Claude's Discretion
- None — all areas discussed and decided.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Exercise Data Source
- `/Users/olli/schenanigans/gymtracker/api/free_exercise_db.json` — Seeded exercise catalog (~873 exercises). Parse and import into Room DB on first launch.

### Anatomy SVG Source (Port to Native)
- `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/AnatomyFront.svelte` — Front body SVG with clickable muscle regions
- `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/AnatomyBack.svelte` — Back body SVG with clickable muscle regions
- `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/muscleRegionMap.ts` — Muscle region to group mapping (16 groups, ~30 regions)
- `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/types.ts` — TypeScript types for muscle status

### Project Specs
- `.planning/REQUIREMENTS.md` — Phase 1 requirements: EXER-01, EXER-02, EXER-03, NAV-01
- `.planning/ROADMAP.md` — Phase 1 success criteria and scope
- `CLAUDE.md` — Technology stack and version constraints

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Gymtracker exercise DB:** 873 exercises with full metadata — direct import source for Room seed
- **Anatomy SVG components:** Svelte components with SVG path data for front/back body views — SVG paths portable to any platform (SwiftUI Shape/Path, Compose Canvas/Path)
- **Muscle region map:** TypeScript mapping of ~30 SVG region IDs to 16 muscle groups — port to Kotlin enum/sealed class in KMP shared module

### Established Patterns
- No existing codebase — greenfield project. Patterns will be established in this phase.

### Integration Points
- Room database seeding with `free_exercise_db.json` at first launch
- Anatomy SVG path data extraction from Svelte components into platform-native drawing code
- KMP shared module exposes ViewModels/repositories; SwiftUI observes via Kotlin StateFlow (needs SKIE or manual wrapping)

</code_context>

<specifics>
## Specific Ideas

- User specifically requested porting the anatomy SVG from gymtracker — this is a distinctive UI element that should be treated as a first-class feature, not an afterthought
- The anatomy picker replaces standard dropdown/chip pickers for muscle groups throughout the entire app
- iOS-first development means SwiftUI implementations take priority; Android/Compose can follow later

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-foundation-exercise-catalog*
*Context gathered: 2026-03-28*

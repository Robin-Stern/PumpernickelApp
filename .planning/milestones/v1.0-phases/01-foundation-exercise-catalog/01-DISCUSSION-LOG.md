# Phase 1: Foundation & Exercise Catalog - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 01-foundation-exercise-catalog
**Areas discussed:** Exercise data model, Exercise browsing UX, Custom exercise creation, Navigation shell

---

## Exercise Data Model

### Which fields to store from gymtracker JSON

| Option | Description | Selected |
|--------|-------------|----------|
| Core fields only | name, primaryMuscles, equipment, category, id | |
| Core + metadata | Add level, force, mechanic, secondaryMuscles | |
| Everything except images | Keep all fields including instructions | |

**User's choice:** Everything (free-text: "everything.")
**Notes:** User wants all fields stored from the JSON.

### Image paths handling

| Option | Description | Selected |
|--------|-------------|----------|
| Store paths, don't display | Keep image path strings in DB for future use, don't load/display | ✓ |
| Skip images entirely | Don't store image paths at all | |
| Store and display | Bundle exercise images with app and display them | |

**User's choice:** Store paths, don't display
**Notes:** None

### Custom vs seeded exercise storage

| Option | Description | Selected |
|--------|-------------|----------|
| Same table, flagged | One table with isCustom flag | ✓ |
| Separate tables | Custom exercises in their own table | |

**User's choice:** Same table, flagged
**Notes:** None

### Seeded exercise mutability

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only | Can't be edited or deleted | ✓ |
| Editable but not deletable | Can modify fields but can't remove | |
| Fully editable | Can edit and delete any exercise | |

**User's choice:** Read-only
**Notes:** None

---

## Exercise Browsing UX

### Catalog organization

| Option | Description | Selected |
|--------|-------------|----------|
| Flat list with search + filter | Single scrollable list with search bar and filter chips | ✓ |
| Grouped by muscle group | Exercises under collapsible muscle group headers | |
| Tab-per-muscle-group | Horizontal tabs for each muscle group | |

**User's choice:** Flat list with search + filter
**Notes:** None

### Exercise row information

| Option | Description | Selected |
|--------|-------------|----------|
| Name + muscle group | Name as primary text, muscle group as subtitle/chip | ✓ |
| Name + muscle + equipment | Add equipment info | |
| Name + muscle + level + equipment | Show all key metadata | |

**User's choice:** Name + muscle group
**Notes:** None

### Exercise tap action

| Option | Description | Selected |
|--------|-------------|----------|
| Detail screen | Navigate to full detail screen | ✓ |
| Bottom sheet | Show details in overlay | |
| Expandable row | Expand row inline | |

**User's choice:** Detail screen
**Notes:** None

### Filter options

| Option | Description | Selected |
|--------|-------------|----------|
| Muscle group only | Filter chips for muscle groups | ✓ |
| Muscle group + equipment | Two filter rows | |
| Muscle + equipment + level | Three filter dimensions | |

**User's choice:** Muscle group only
**Notes:** None

---

## Custom Exercise Creation

### Required fields

| Option | Description | Selected |
|--------|-------------|----------|
| Name + primary muscle | Minimal per EXER-02 | |
| Name + muscle + equipment | Add equipment as required | |
| Name + muscle + equipment + category | More structured entries | ✓ |

**User's choice:** Name + muscle + equipment + category
**Notes:** User wants more structured custom exercises.

### Muscle group input method

| Option | Description | Selected |
|--------|-------------|----------|
| Dropdown/picker from fixed list | Pre-defined lists from seeded data | |
| Free text with suggestions | Autocomplete from existing values | |
| Chips/toggles | Visual grid of tappable chips | |

**User's choice:** Other (free-text: "steal the anatomy svg from the web/ in the gymtracker dir!")
**Notes:** User wants to port the anatomy SVG body diagram from the gymtracker web app as the muscle group picker. Anatomy components found at `/Users/olli/schenanigans/gymtracker/web/src/lib/components/anatomy/`.

### Anatomy SVG scope

| Option | Description | Selected |
|--------|-------------|----------|
| Custom exercise creation only | Use anatomy SVG only for creating exercises | |
| Everywhere muscles appear | Use as universal muscle group picker | ✓ |
| You decide | Claude decides where it adds value | |

**User's choice:** Everywhere muscles appear
**Notes:** The anatomy SVG becomes the universal muscle group selection mechanism throughout the entire app.

---

## Navigation Shell

### Workout tab home screen

| Option | Description | Selected |
|--------|-------------|----------|
| Exercise catalog as default | Land directly on exercise catalog | |
| Empty state with CTA | Placeholder with 'Browse Exercises' button | ✓ |
| Split view | Top for templates, bottom for exercises | |

**User's choice:** Empty state with CTA
**Notes:** None

### Placeholder tabs (Overview, Nutrition)

| Option | Description | Selected |
|--------|-------------|----------|
| Simple 'Coming Soon' text | Minimal centered text | |
| Styled empty state | Icon + description of upcoming feature | ✓ |
| Disabled/greyed out tabs | Tabs visible but non-functional | |

**User's choice:** Styled empty state
**Notes:** None

### Exercise catalog access

| Option | Description | Selected |
|--------|-------------|----------|
| Button on Workout home | Prominent 'Browse Exercises' button | ✓ |
| Exercises as sub-tab | Top tab bar within Workout tab | |
| FAB menu | Floating action button with options | |

**User's choice:** Button on Workout home
**Notes:** None

### Create Exercise action placement

| Option | Description | Selected |
|--------|-------------|----------|
| FAB on catalog screen | Floating action button (+) | ✓ |
| Button in top bar | 'Add' button in app bar | |
| Inline at top of list | Card/button at top of exercise list | |

**User's choice:** FAB on catalog screen
**Notes:** None

### UI Architecture (added during discussion)

| Option | Description | Selected |
|--------|-------------|----------|
| Compose Multiplatform (shared UI) | Single Compose codebase for both platforms | |
| SwiftUI for iOS, Compose for Android | Platform-native UI, shared KMP business logic | ✓ |
| Compose MP + SwiftUI where needed | Compose primary, expect/actual for platform features | |

**User's choice:** SwiftUI for iOS, Compose for Android
**Notes:** User clarified development order: KMP business logic first, then iOS (SwiftUI), then Android (Compose). iOS-first.

---

## Claude's Discretion

None — all areas discussed and decided by user.

## Deferred Ideas

None — discussion stayed within phase scope.

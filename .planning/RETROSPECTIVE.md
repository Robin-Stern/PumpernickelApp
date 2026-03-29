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

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 3 | 4 | First milestone — established 3-wave pattern, KMP+SwiftUI architecture |

### Top Lessons (Verified Across Milestones)

1. Xcode pbxproj registration is a recurring gap — automate or add explicit plan task
2. 3-wave pattern (data → VM → UI) scales well for feature phases

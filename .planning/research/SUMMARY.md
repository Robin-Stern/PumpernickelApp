# Project Research Summary

**Project:** PumpernickelApp
**Domain:** Fitness / Workout Tracking Mobile App (Kotlin Multiplatform + Compose Multiplatform)
**Researched:** 2026-03-28
**Confidence:** HIGH

## Executive Summary

PumpernickelApp is a strength-training workout tracker built on Kotlin Multiplatform (KMP) with Compose Multiplatform for shared UI across Android and iOS. Experts building in this space converge on a well-established pattern: MVVM with unidirectional data flow, a Room KMP local database, Koin for DI, and Navigation Compose for routing. The domain is well-researched with a strong reference implementation (the gymtracker firmware FSM) to port from. The technology stack is mature enough for a university project deadline, with all core dependencies at stable (non-alpha) releases as of March 2026.

The recommended approach is a single shared KMP module with feature-package organization (not multi-module), delivering the complete workout session loop (template selection, set logging, rest timer, workout save) as the core milestone. Competitors (Strong, Hevy, JEFIT) establish clear table stakes: exercise catalog, template CRUD, set logging with progress tracking, a reliable rest timer, and workout history with previous-performance display. The critical differentiator over a minimal prototype is the previous-performance display -- showing last session's weights inline during logging is what makes progressive overload practical and is what users actually stay for.

The top risks are all iOS-specific and must be addressed from the first phase. The rest timer will silently break in the background unless epoch-based timing is used instead of coroutine delay. Workout session state will be lost on process death unless each completed set is persisted to Room immediately. iOS lifecycle events will re-fire Compose effects in unexpected ways if session state is held in composable scope instead of a ViewModel. These are not polish concerns -- they are architectural decisions that must be made before writing the first screen. Get them right in the foundation or face a rewrite.

---

## Key Findings

### Recommended Stack

The stack is fully Kotlin-native and KMP-compatible. Room KMP 2.8.4 is the clear choice over SQLDelight for this project: annotation-based DAOs are faster to develop with, Google officially supports it for KMP, and the data model (templates, exercises, sets, history, active session) maps naturally to Room entities and relations. Koin 4.2.0 is the right DI framework -- no code generation overhead, first-class Compose Multiplatform integration, and simpler than compile-time alternatives for a prototype scope. Navigation Compose 2.9.2 (stable, not the alpha Navigation 3) provides type-safe routes with `@Serializable` data classes and handles the bottom-nav + screen-stack structure the app requires.

Version compatibility is critical and well-defined: Kotlin 2.3.20 + Compose Multiplatform 1.10.3 + KSP 2.3.20-1.0.x (must match Kotlin prefix) is the verified stable combination. Do not mix versions independently. Lock all versions in `libs.versions.toml` and do not update mid-milestone.

**Core technologies:**
- **Kotlin 2.3.20**: Language — latest stable, required for CMP 1.10.x compatibility
- **Compose Multiplatform 1.10.3**: Shared UI — stable iOS support, includes Hot Reload, maps to Jetpack Compose 1.10.5
- **Room KMP 2.8.4**: Local database — annotation-based DAOs, Flow support, migration tooling, Google-backed
- **Koin 4.2.0**: Dependency injection — Kotlin-first, no KSP overhead, native Compose Multiplatform + ViewModel integration
- **Navigation Compose 2.9.2**: Routing — type-safe routes, stable (not alpha Navigation 3), JetBrains-maintained KMP port
- **ViewModel + Lifecycle 2.10.0**: State management — survives config changes on Android, lifecycle-aware on iOS
- **kotlinx-coroutines 1.10.2**: Async — standard KMP async, integrates with Room and StateFlow
- **kotlinx-serialization 1.10.0**: Serialization — required for type-safe nav routes, needed later for backend
- **kotlinx-datetime 0.7.1**: Date/time — cross-platform timestamps for workout history, rest timer calculations

**What NOT to use:** Realm (end-of-life), Room 3.0 alpha (breaking changes), Hilt/Dagger (Android-only), LiveData (Android-only), RxJava (not KMP-compatible), Ktor Client (defer until backend phase).

See `.planning/research/STACK.md` for full version catalog and build configuration.

---

### Expected Features

The competitive landscape (Strong, Hevy, JEFIT, FitNotes) is well-analyzed. The MVP critical path is: Exercise Catalog -> Template CRUD -> Start Workout -> Set Logging + Mark Complete + Rest Timer -> Save Workout -> Workout History -> Previous Performance Display. Every feature in this chain is required; missing any one breaks the core loop.

**Must have (table stakes):**
- **Exercise catalog** (seeded ~50-100 exercises with muscle group) — users cannot log without exercises
- **Template CRUD** (create/edit/delete templates with exercises, sets, reps, rest periods) — core organization primitive
- **Start workout from template / empty workout** — the primary entry point into every session
- **Set logging: weight + reps, mark complete** — the atomic unit of the entire product
- **Rest timer** (auto-start on set complete, configurable, background-safe) — broken rest timer is a dealbreaker per Reddit research
- **Workout progress indicator** (current set/exercise tracking) — users lose track in longer sessions without it
- **Save completed workout + workout history list** — without history the app is a notepad, not a tracker
- **Previous performance display** (last session's weights shown during logging) — this is why people use workout trackers
- **Unit support** (kg/lbs toggle, store internally as kg*10 integer)

**Should have (competitive differentiators, Phase 1 polish):**
- **Custom exercise creation** — users will immediately hit gaps in any seed catalog
- **Save workout as template** — natural follow-on from ad-hoc sessions
- **Workout duration tracking** — automatic, near-zero implementation cost
- **Workout notes** — free text on workout and exercise level

**Defer (v2+):**
- Set type tags (warm-up, drop set, failure)
- Supersets / exercise grouping
- Personal records (PR) tracking and estimated 1RM
- Progress charts / graphs (requires charting library, high effort)
- Plate calculator, calendar view, RPE/RIR
- Social features, AI, nutrition, gamification, cloud sync (all explicitly out of scope)

See `.planning/research/FEATURES.md` for full feature dependency graph and competitor analysis.

---

### Architecture Approach

The app uses MVVM + Clean Architecture within a single shared `composeApp` KMP module organized by feature packages (not separate Gradle modules -- overkill for a prototype). The firmware FSM (15+ explicit states) decomposes naturally into Navigation Compose (screen-to-screen transitions) plus ViewModel sealed state classes (within-screen state). The key insight from architecture research is that many firmware states collapse into UI state within a single screen: the entire workout session flow becomes 4 screens (TemplateList, ActiveWorkout, RestTimer, WorkoutFinish) with a single `WorkoutSessionViewModel` scoped to the workout navigation sub-graph.

Data flow is strictly unidirectional: User -> Screen -> ViewModel -> Repository -> Room DAO -> SQLite. State returns via `StateFlow<UiState>` observed with `collectAsState()`. ViewModels never expose `MutableStateFlow` directly. DAOs are never called from composables.

**Major components:**
1. **WorkoutSessionViewModel** — the core of the app; replaces the firmware FSM globals; manages active workout state, rest timer coroutine, set tracking, crash recovery
2. **Room Database (AppDatabase)** — entities for exercises, templates, completed workouts/exercises/sets, and `active_session` (crash recovery singleton)
3. **Repository layer** — interfaces in domain, implementations in data; maps Room entities to domain models; the seam that absorbs backend integration later without touching ViewModels
4. **Navigation (AppNavigation)** — NavHost with bottom nav (Workout, Overview placeholder, Nutrition placeholder); workout session as a scoped sub-graph
5. **Koin DI** — `DataModule` (DB, DAOs, repos), `AppModule` (ViewModels), `PlatformModule` (expect/actual DB builder)

Weight is stored as `Int` (kg * 10) throughout the data model, matching the firmware convention and avoiding float precision drift.

See `.planning/research/ARCHITECTURE.md` for full file structure, entity definitions, ViewModel code, and navigation graph.

---

### Critical Pitfalls

1. **Rest timer dies in iOS background** — Never use coroutine `delay` as the source of truth for elapsed time. Store `startTime` (epoch millis), calculate `remaining = target - (now - startTime)` on every tick and on resume. Schedule a local notification via `UNUserNotificationCenter` (expect/actual) for the end time so backgrounded users still get an alert.

2. **Workout session lost on process death** — Save each completed set to Room immediately on `confirmSet()`, not batched at workout end. Maintain an `active_session` singleton row with current exercise/set index and a JSON snapshot. On app launch, check for `IN_PROGRESS` sessions and offer to resume. Retrofitting this onto a "save at end" model is a rewrite.

3. **iOS lifecycle mismatch re-fires Compose effects** — When a `UIViewController` hosting Compose leaves screen (even for forward navigation), lifecycle events fire as if the composable is disposed. Do not put workout session state inside composable-scoped effects (`LaunchedEffect`, `DisposableEffect`). All ongoing operations belong in `viewModelScope`. Test every transition on iOS specifically.

4. **Database migrations silently wipe data on iOS** — Never use `fallbackToDestructiveMigration()` outside debug builds. Write explicit `Migration` objects for every schema change. Test migrations on both platforms. iOS SQLite handles `ALTER TABLE` differently from Android's SQLite version.

5. **Coroutine exceptions crash iOS silently** — Annotate suspend functions exposed to iOS with `@Throws(Exception::class)`. Add `CoroutineExceptionHandler` to every `viewModelScope`. Wrap all IO in try/catch in the shared module. Without this, a DB error during a workout causes an opaque crash with no useful stack trace on iOS.

Additional pitfalls to watch: slow iOS builds (configure Gradle caching from day one), Koin not initialized on iOS entry point (call `initKoin()` from `MainViewController.kt`), float weight precision (use Int kg*10), version mismatches (KSP prefix must match Kotlin version), keyboard covering input fields on iOS (use `imePadding()` and numeric keyboard), and overuse of `expect/actual` (prefer interface + Koin DI for anything testable).

See `.planning/research/PITFALLS.md` for full prevention strategies and phase-specific warning table.

---

## Implications for Roadmap

Architecture research and feature dependencies both converge on the same 4-phase build order. You cannot test workout sessions without templates. You cannot test templates without a database. You cannot test history without completed workouts. Each phase delivers a testable vertical slice.

### Phase 1: Foundation and Data Layer

**Rationale:** Everything downstream depends on a working database, domain models, repository interfaces, and DI wiring. This is also when the riskiest architectural decisions must be locked in (crash recovery design, weight storage as Int, migration discipline, Koin iOS entry point, Gradle build optimization). Fixing these later ranges from painful to impossible.

**Delivers:** Compilable, launchable app on both Android and iOS. Room database with all entities. Repository interfaces and implementations. Koin modules wired. Gradle caching configured. Version catalog locked.

**Addresses:**
- Unit support (kg*10 integer storage in schema from day one)
- Exercise catalog (entities defined, seed data inserted via Room pre-populate or DAO)

**Avoids:**
- Pitfall 2 (session loss): `active_session` table designed into the initial schema
- Pitfall 4 (destructive migration): migration discipline established from first schema version
- Pitfall 12 (Koin iOS init): call `initKoin()` in iOS entry point as part of scaffolding
- Pitfall 13 (weight float): Int kg*10 enforced at entity level
- Pitfall 14 (version mismatch): `libs.versions.toml` locked before any feature work
- Pitfall 6 (slow builds): Gradle caching configured before the team suffers

### Phase 2: Template Management and Navigation Shell

**Rationale:** Templates are the prerequisite for every workout session. The navigation shell (bottom nav, NavHost, route definitions) must exist before any screen can be navigated to. This phase establishes the full navigation architecture and validates the ViewModel + Repository + StateFlow pattern with relatively low-stakes CRUD screens before tackling the complex workout session.

**Delivers:** Usable template browser and editor. Bottom nav bar with Workout tab functional. Exercise picker. Custom exercise creation. Navigation sub-graph structure in place (including the scoped workout sub-graph, even if it contains a placeholder).

**Addresses:** Template CRUD, custom exercise creation, start workout from template (entry point only -- navigation to workout session)

**Avoids:**
- Pitfall 3 (lifecycle mismatch): ViewModel architecture validated against iOS navigation before the complex session screen
- Pitfall 8 (swipe-back broken): test swipe-back on every template screen transition on iOS
- Pitfall 10 (expect/actual overuse): establish interface + Koin DI pattern here, not expect/actual

### Phase 3: Workout Session Core

**Rationale:** The central, highest-complexity feature. All foundation and template work exists to serve this. The workout session ViewModel is the largest in the app and must be built carefully: it replaces the firmware FSM globals, manages the rest timer coroutine, persists crash recovery state, and drives 4 distinct screens via sealed state.

**Delivers:** Complete workout session loop -- template selection to finished workout saved to database. ActiveWorkoutScreen (set entry with weight/reps pickers), RestTimerScreen (countdown, skippable), WorkoutFinishScreen (recap + save). Crash recovery prompt on app launch. Workout progress indicator. Workout duration tracking.

**Addresses:** Set logging, mark sets complete, rest timer, workout progress indicator, save completed workout, start empty workout, workout notes, workout duration tracking

**Avoids:**
- Pitfall 1 (timer background death): epoch-based timing + local notification via expect/actual
- Pitfall 2 (session loss): incremental persistence on every `confirmSet()` call
- Pitfall 3 (lifecycle re-fire): session state in `viewModelScope`, not `LaunchedEffect`
- Pitfall 9 (coroutine exceptions): `@Throws` and `CoroutineExceptionHandler` on viewModelScope
- Pitfall 11 (Flow collection iOS): recalculate timer state on resume, do not depend on continuous emission

**Research flag:** This phase benefits from a `/gsd:research-phase` before planning the rest timer background behavior and iOS local notification integration (expect/actual for `UNUserNotificationCenter`). The notification API differs significantly between platforms.

### Phase 4: History, Previous Performance, and Polish

**Rationale:** History requires completed workouts (Phase 3 must be done). Previous performance display requires history data to query. Polish (UI feel on iOS, keyboard handling, Material theming adjustments) should come after core flows are working to avoid rework.

**Delivers:** Workout history list and detail view. Previous performance display inline during active workout. Session resume prompt. Save workout as template. UI polish pass for iOS feel.

**Addresses:** Workout history, previous performance display, save as template

**Avoids:**
- Pitfall 5 (keyboard covers inputs): `imePadding()` + numeric keyboard type on all input fields
- Pitfall 7 (Material feels wrong on iOS): review all screens against iOS conventions, adjust spacing and navigation patterns

### Phase Ordering Rationale

- Phase 1 before everything: no feature can be built without data and DI
- Phase 2 before Phase 3: templates are a hard dependency for starting a workout; also validates the ViewModel pattern at lower complexity before the session ViewModel
- Phase 3 before Phase 4: history requires completed workouts; previous performance requires history data
- Crash recovery (Pitfall 2) and timer background handling (Pitfall 1) are designed in during Phase 1 and Phase 3 respectively -- not deferred to polish, because they require schema and architectural decisions

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 3 (Workout Session):** iOS local notification integration for rest timer background behavior. The `UNUserNotificationCenter` API requires expect/actual bridging and permission handling that is not covered by standard KMP docs. Needs a targeted research spike before implementation.
- **Phase 4 (History + Polish):** iOS-specific keyboard avoidance and swipe-back gesture behavior may need a short research spike against the specific Compose Multiplatform version in use (behavior has changed across versions, MEDIUM confidence on current state).

**Phases with standard patterns (skip research-phase):**
- **Phase 1 (Foundation):** Room KMP setup, Koin DI, version catalog configuration -- all well-documented by Google and JetBrains with official guides. HIGH confidence.
- **Phase 2 (Templates):** CRUD screens with ViewModel + Repository is the canonical KMP pattern. Official samples cover this exactly. HIGH confidence.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official JetBrains and Google release pages as of 2026-03-28. Compatibility matrix verified. Alternatives explicitly compared. |
| Features | HIGH | Seven competitors analyzed (Strong, Hevy, JEFIT, FitNotes, Setgraph, RepCount, StrengthLog). Feature dependency graph is logically consistent and maps to gymtracker firmware FSM. |
| Architecture | HIGH | Official KMP docs, direct firmware FSM inspection, and published KMP architecture guides. Code samples verified against stable library APIs. |
| Pitfalls | HIGH | 10 of 14 pitfalls are HIGH confidence, sourced from official JetBrains GitHub issues, Apple developer forums, and Kotlin official docs. 4 are MEDIUM (behavior has improved in recent versions, test with specific version). |

**Overall confidence:** HIGH

### Gaps to Address

- **iOS local notification API integration:** No code sample was produced for `UNUserNotificationCenter` expect/actual bridging. This needs a targeted research spike in Phase 3 planning. The notification scheduling logic is not complex but the platform-specific wiring is non-trivial.
- **iOS keyboard and swipe-back current behavior:** Both Pitfalls 5 and 8 are rated MEDIUM confidence because behavior has changed significantly across recent CMP versions. Verify against CMP 1.10.3 specifically during Phase 4 planning rather than relying on older issue threads.
- **Compose Hot Reload workflow:** Listed in STACK.md as a development tool benefit but not verified in practice for this specific project configuration. Low risk if it doesn't work as expected (it is a DX feature, not a runtime feature).
- **Exercise seed data:** Feature research recommends seeding 50-100 exercises categorized by muscle group. The source and format of this seed data is unresolved. A curated CSV or hardcoded Kotlin list needs to be prepared before Phase 1 completes.

---

## Sources

### Primary (HIGH confidence)
- [Kotlin releases page](https://kotlinlang.org/docs/releases.html) — Kotlin 2.3.20 current stable
- [Compose Multiplatform compatibility matrix](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) — version pairing
- [Compose Multiplatform 1.10.0 blog](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) — feature verification
- [Room KMP setup guide](https://developer.android.com/kotlin/multiplatform/room) — Room 2.8.4 KMP configuration
- [Room 3.0 announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) — alpha status confirmed (avoid)
- [Koin GitHub releases](https://github.com/InsertKoinIO/koin/releases) — v4.2.0 confirmed
- [Navigation routing docs (kotlinlang.org)](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html) — Navigation Compose 2.9.2
- [ViewModel KMP setup](https://developer.android.com/kotlin/multiplatform/viewmodel) — v2.10.0 confirmed
- [JetBrains GitHub Issue #3890](https://github.com/JetBrains/compose-multiplatform/issues/3890) — LaunchedEffect/DisposableEffect lifecycle on iOS
- [JetBrains GitHub Issue #3889](https://github.com/JetBrains/compose-multiplatform/issues/3889) — Flow subscription difference Android vs iOS
- [Koin KMP docs](https://insert-koin.io/docs/4.0/reference/koin-mp/kmp/) — KMP DI patterns
- [Apple Developer Forums: countdown timer in background](https://developer.apple.com/forums/thread/133640) — iOS timer background behavior
- Firmware reference: `/Users/olli/schenanigans/gymtracker/firmware/src/statemachine/` — FSM states, data models (direct inspection)

### Secondary (MEDIUM confidence)
- [Best Workout Log Apps 2026: Hevy vs Strong vs JEFIT vs RepLog](https://www.replog.co.uk/blog/best-workout-log-apps-2026/) — feature comparison
- [Strong vs Hevy Comparison 2026](https://gymgod.app/blog/strong-vs-hevy) — differentiator analysis
- [12 Essential Features for Workout Tracking Apps](https://setgraph.app/ai-blog/app-to-track-my-workouts) — table stakes validation
- [KMP Architecture Best Practices (carrion.dev)](https://carrion.dev/en/posts/kmp-architecture/) — Clean Architecture + MVVM patterns
- [Medium: KMP iOS build optimization](https://medium.com/@houssembababendermel/how-i-fixed-my-kmp-ios-build-from-20-minute-builds-to-lightning-fast-c4f0f5c102b0) — Gradle caching fixes
- [Compose Multiplatform iOS back gesture (Slack)](https://slack-chats.kotlinlang.org/t/22307386/i-wish-compose-multiplatform-had-better-back-handling-for-io) — navigation gesture behavior

---
*Research completed: 2026-03-28*
*Ready for roadmap: yes*

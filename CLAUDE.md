<!-- GSD:project-start source:PROJECT.md -->
## Project

**PumpernickelApp**

A fitness tracking mobile app built with Kotlin Multiplatform (Compose Multiplatform), targeting iOS first. The current focus is a workout tracking prototype — logging workouts, managing templates, and executing workout sessions with the same flow and logic as the user's existing gymtracker firmware project, adapted for mobile.

**Core Value:** Users can select a workout template and execute it set-by-set — logging reps, weight, and rest periods — with a clean, reliable flow that mirrors proven embedded firmware logic.

### Constraints

- **Tech stack**: Kotlin Multiplatform + Compose Multiplatform (per Lastenheft)
- **Platform focus**: iOS first (user handles iOS UI)
- **Storage**: Local/offline only for prototype (Room or SQLDelight)
- **Timeline**: University deadline ~end of May 2026
- **Scope**: Workout feature only for current milestone — no backend, no nutrition, no gamification
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 | Language | Latest stable (March 16, 2026). Required for Compose Multiplatform 1.10.x compatibility. Full KMP support. |
| Compose Multiplatform | 1.10.3 | Shared UI framework | Latest stable. iOS support is stable. Includes unified @Preview, Compose Hot Reload, Navigation 3 support. Maps to Jetpack Compose 1.10.5 under the hood. |
| Kotlin Coroutines | 1.10.2 | Async/concurrency | Standard for KMP async work. Full multiplatform support. Integrates with Room, Flow, ViewModel. |
| Gradle (Kotlin DSL) | 8.x+ | Build system | Required by KMP. Use version catalogs (`libs.versions.toml`) for dependency management. |
### Database (Local Storage)
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Room KMP | 2.8.4 | Local SQLite database | **Use Room, not SQLDelight.** Room is Google's officially supported database for KMP, now stable for Android + iOS + JVM. Annotation-based DAOs are faster to write than raw SQL files. Better migration tooling. Coroutine-first with Flow support. Your workout data model (templates, exercises, sets, history) maps naturally to Room entities and relations. For a university project, Room's annotation approach has a gentler learning curve than SQLDelight's SQL-file-first approach. |
| AndroidX SQLite Bundled | 2.6.2 | SQLite driver | Required by Room KMP. Bundles SQLite so you get consistent behavior across Android and iOS (no relying on system SQLite versions). |
### Dependency Injection
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Koin | 4.2.0 | DI framework | Kotlin-first, no code generation, no annotation processing. Built for KMP from the ground up. Compose Multiplatform integration via `koin-compose-viewmodel`. Runtime DI means faster builds (no KSP overhead on top of Room's KSP). Simple DSL for a prototype-scope project. |
### Navigation
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Jetpack Navigation Compose (KMP) | 2.9.2 | Screen navigation | Official JetBrains-maintained multiplatform port of Jetpack Navigation. Stable, well-documented. Type-safe routes with `@Serializable`. Fits the bottom-nav + screen-stack pattern the app needs (Workout, Overview, Nutrition tabs). |
### Architecture / State Management
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Jetpack ViewModel (KMP) | 2.10.0 | Screen state holder | Official KMP support since 2.8.0. Survives configuration changes on Android, works as lifecycle-aware state holder on iOS. Integrates with Koin via `koin-compose-viewmodel`. |
| Jetpack Lifecycle (KMP) | 2.10.0 | Lifecycle management | Required by ViewModel. Now supports JVM, Native (iOS, macOS), and Web. |
| Kotlin StateFlow / MutableStateFlow | (stdlib) | Reactive state | Standard KMP-compatible reactive state. Observed by Compose via `collectAsState()`. No additional library needed. |
### Serialization
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| kotlinx-serialization | 1.10.0 | JSON / data serialization | Required for type-safe navigation routes. Kotlin-native, no reflection. Full KMP support. Will be needed when backend integration comes later. |
### Date/Time
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| kotlinx-datetime | 0.7.1 | Date/time handling | Multiplatform date/time for workout timestamps, rest timer calculations, history date display. Kotlin-native, no platform-specific code needed. |
### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| DataStore Preferences (KMP) | 1.1.x | Key-value settings | App settings, user preferences (e.g., default rest timer, weight unit kg/lbs). Lighter than Room for simple key-value data. |
| Coil 3 | 3.1.x | Image loading | Only if exercise images are needed. Supports Compose Multiplatform (Android, iOS, Desktop, Wasm). Add `coil-compose` for Compose integration. Not needed for the initial prototype if exercises are text-only. |
| Turbine | 1.2.1 | Flow testing | Testing StateFlow/Flow emissions in ViewModels. Essential for verifying workout state machine transitions. |
### Development Tools
| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio (Ladybug or newer) | IDE | Built-in KMP module templates. Use the Kotlin Multiplatform plugin. |
| KSP (Kotlin Symbol Processing) | Code generation for Room | Required by Room's annotation processor. Must add `ksp` dependencies for each platform target (Android, iosSimulatorArm64, iosX64, iosArm64). |
| Compose Hot Reload | Live UI preview | Bundled and stable in Compose Multiplatform 1.10.x. Enabled by default via the Compose Gradle plugin. |
| Xcode | iOS builds | Required for running on iOS simulator/device. Compose Multiplatform generates an Xcode project. |
## Installation
# Coroutines
# Room
# Koin
# Navigation
# Lifecycle / ViewModel
# Serialization
# Date/Time
# Testing
## Alternatives Considered
| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Room KMP 2.8.4 | SQLDelight 2.3.2 | If you want SQL-first development, write raw SQL and generate Kotlin. Better if team has strong SQL skills. SQLDelight has more mature KMP support historically. Room is better for annotation-based DAO development and has Google's long-term backing. |
| Room KMP 2.8.4 | Room 3.0 alpha | Room 3.0 drops Android framework SQLite dependency, adds JS/Wasm support, and is coroutine-first. But it is alpha with breaking changes -- do NOT use for a university project with a deadline. |
| Koin 4.2.0 | kotlin-inject 0.7.x | If you want compile-time DI with zero runtime overhead. Requires KSP (additional build overhead on top of Room). More boilerplate. Better for large production apps where startup performance matters. Overkill for a prototype. |
| Koin 4.2.0 | Kodein-DI 7.x | Viable alternative, but smaller community and less Compose-specific integration. Koin has better docs and broader adoption for KMP. |
| Navigation Compose 2.9.2 | Voyager 1.1.x | If you want a Compose-native navigation with built-in ScreenModel (ViewModel equivalent). Simpler API but smaller community. Less alignment with official Google/JetBrains direction. |
| Navigation Compose 2.9.2 | Decompose 3.x | If you want navigation fully separated from UI (navigation as business logic). More powerful but more complex. Better for apps where navigation logic must be heavily tested or shared with non-Compose UI. Overkill for this project. |
| Navigation Compose 2.9.2 | Navigation 3 (alpha05) | If you want the cutting-edge stack-based navigation. Requires polymorphic serialization for iOS. Currently alpha -- avoid for deadline-bound projects. Revisit when it hits stable. |
| kotlinx-datetime 0.7.1 | java.time / NSDate | If you only target one platform. kotlinx-datetime provides cross-platform consistency without expect/actual boilerplate. |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Realm (MongoDB) | Deprecated for new KMP projects. MongoDB announced end-of-life for Realm Kotlin SDK. Migration path is unclear. | Room KMP 2.8.4 |
| Room 3.0 alpha | Breaking API changes, alpha stability. Will cause pain if APIs shift before your May deadline. | Room KMP 2.8.4 (stable) |
| Hilt / Dagger | Android-only. Does not support KMP/iOS. | Koin 4.2.0 |
| LiveData | Android-only, does not work in KMP common code. | StateFlow + collectAsState() |
| RxJava / RxKotlin | Not KMP-compatible. Coroutines + Flow is the standard for KMP. | kotlinx.coroutines + Flow |
| Ktor Client | Not needed for the prototype (local-only, no backend). Adding it now adds unnecessary complexity. Add when backend integration begins. | (defer) |
| SKIE | Swift interop library by Touchlab. Only needed if writing significant Swift/SwiftUI code alongside Compose. Since the app uses Compose Multiplatform for shared UI (including iOS), SKIE is unnecessary. | Compose Multiplatform shared UI |
| Accompanist | Most Accompanist features have migrated into core Compose. Check if the feature you need is already in Material 3 / Compose Foundation before reaching for Accompanist. | Compose Material 3 built-ins |
## Stack Patterns by Variant
- Use Room for all structured data (templates, exercises, sets, workout history)
- Use DataStore Preferences only for app-level settings (weight unit, default rest timer)
- Use MVVM: ViewModel exposes StateFlow, Compose observes
- Navigation Compose with bottom nav bar (3 tabs: Workout, Overview, Nutrition -- only Workout functional for now)
- Add Ktor Client for networking
- Add kotlinx-serialization for API request/response models (already in the stack for navigation routes)
- Room remains the local cache / offline-first source of truth
- Consider adding a Repository pattern layer between ViewModel and data sources
- Add SKIE for better Swift interop
- Use expect/actual to provide platform-specific UI where Compose falls short
## Version Compatibility
| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Compose Multiplatform 1.10.3 | Kotlin 2.1.0+ (recommended: 2.2.20+) | Latest CMP always compatible with latest Kotlin per JetBrains |
| Kotlin 2.3.20 | Compose Multiplatform 1.10.3 | Verified: current stable pair |
| Room 2.8.4 | KSP matching Kotlin version | KSP version must start with Kotlin version (e.g., 2.3.20-1.0.x) |
| Koin 4.2.0 | Kotlin 2.3.20 | Explicitly supports Kotlin 2.3.20 per release notes |
| kotlinx-serialization 1.10.0 | Kotlin 2.3.0+ | Based on Kotlin 2.3.0 |
| kotlinx-coroutines 1.10.2 | Kotlin 2.1.0+ | Updated for Kotlin 2.1.0, compatible with 2.3.x |
| Navigation Compose 2.9.2 | Compose Multiplatform 1.10.x | JetBrains-maintained port |
| Lifecycle/ViewModel 2.10.0 | KMP (JVM, iOS, macOS, Web) | Full multiplatform since 2.8.0 |
## Sources
- [Kotlin releases page](https://kotlinlang.org/docs/releases.html) -- Kotlin 2.3.20 confirmed as latest stable (HIGH confidence)
- [Compose Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) -- Version matrix verified (HIGH confidence)
- [Compose Multiplatform 1.10.0 blog post](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/) -- Feature verification (HIGH confidence)
- [Room KMP setup guide](https://developer.android.com/kotlin/multiplatform/room) -- Room 2.8.4 setup verified (HIGH confidence)
- [Room 3.0 announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) -- Alpha status confirmed (HIGH confidence)
- [SQLDelight GitHub releases](https://github.com/sqldelight/sqldelight/releases) -- v2.3.2 confirmed (HIGH confidence)
- [Koin GitHub releases](https://github.com/InsertKoinIO/koin/releases) -- v4.2.0 confirmed (HIGH confidence)
- [Koin KMP docs](https://insert-koin.io/docs/4.0/reference/koin-mp/kmp/) -- KMP patterns verified (HIGH confidence)
- [Navigation routing docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html) -- Navigation Compose 2.9.2 confirmed (HIGH confidence)
- [Navigation 3 docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) -- Alpha status confirmed, nav3-ui 1.0.0-alpha05 (HIGH confidence)
- [ViewModel KMP setup](https://developer.android.com/kotlin/multiplatform/viewmodel) -- v2.10.0 confirmed (HIGH confidence)
- [kotlinx-serialization releases](https://github.com/Kotlin/kotlinx.serialization/releases) -- v1.10.0 confirmed (HIGH confidence)
- [kotlinx-datetime releases](https://github.com/Kotlin/kotlinx-datetime/releases) -- v0.7.1 confirmed (HIGH confidence)
- [kotlinx-coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases) -- v1.10.2 confirmed (HIGH confidence)
- [Kotest releases](https://github.com/kotest/kotest/releases) -- v6.1.9 confirmed (HIGH confidence)
- [Turbine GitHub](https://github.com/cashapp/turbine) -- v1.2.1 confirmed (HIGH confidence)
- [Coil GitHub](https://github.com/coil-kt/coil) -- KMP support in Coil 3 confirmed (MEDIUM confidence, version not pinned)
- [KMP testing guide 2025](https://www.kmpship.app/blog/kotlin-multiplatform-testing-guide-2025) -- Testing patterns verified (MEDIUM confidence)
- [Room vs SQLDelight comparison](https://proandroiddev.com/which-local-database-should-you-choose-in-2025-comparing-realm-sqldelight-and-room-4221b354c899) -- Decision factors cross-referenced (MEDIUM confidence)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



## Open Discussion Points

- **Error Handling Strategie**: Aktuell werden Fehler (z.B. OpenFoodFacts nicht erreichbar) als einfacher Text in der UI angezeigt. Zu diskutieren: einheitliche App-weite Error-Handling-Strategie — z.B. Snackbar, Retry-Button, oder differenziertes Fehlertyp-Handling (Netzwerkfehler vs. kein Ergebnis vs. Server-Fehler).

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->

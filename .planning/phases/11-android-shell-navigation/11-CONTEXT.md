# Phase 11: Android Shell & Navigation - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Bootstrap the Android app module from empty shell to runnable application with Material 3 theme, 3-tab bottom navigation (Workout/Overview/Nutrition), type-safe navigation graph with tab-scoped back stacks, and Koin DI wiring. All screens are placeholders except the navigation structure itself.

</domain>

<decisions>
## Implementation Decisions

### Theme & Styling
- **D-01:** Primary color = accent green (#66BB6A / RGB 0.4, 0.733, 0.416). Derive full Material 3 color scheme (onPrimary, primaryContainer, etc.) using Material 3 color utilities.
- **D-02:** Material 3 native look — embrace Material shapes, elevation, typography. Do NOT mimic iOS design.
- **D-03:** No dynamic color / Material You wallpaper theming. Use fixed brand color scheme for consistency with iOS.

### Dependencies
- **D-04:** Use Compose BOM (Bill of Materials) for version-aligned Compose dependencies. Add to libs.versions.toml.
- **D-05:** Add: compose-bom, compose-ui, compose-material3, compose-foundation, activity-compose, compose-runtime, navigation-compose (already have nav version 2.9.2).

### Navigation
- **D-06:** Tab-scoped back stacks — each tab (Workout, Overview, Nutrition) maintains its own NavHost/back stack, matching iOS NavigationStack-per-tab pattern.
- **D-07:** Type-safe routes using @Serializable data objects/classes with Navigation Compose 2.9.2.
- **D-08:** Bottom navigation uses Material 3 NavigationBar composable.

### App Bootstrap
- **D-09:** Custom Application class (PumpernickelApplication) for Koin initialization via startKoin with androidContext().
- **D-10:** Single MainActivity extending ComponentActivity with setContent { } for Compose root.
- **D-11:** No splash screen — straight to content, matching iOS behavior.
- **D-12:** Compose plugin needed in androidApp build.gradle.kts (org.jetbrains.compose or kotlin("plugin.compose")).

### Placeholder Screens
- **D-13:** Overview and Nutrition tabs show placeholder text ("Coming soon") matching iOS PlaceholderTabView behavior.
- **D-14:** Workout tab shows a placeholder screen that will be replaced in Phase 12.

### Claude's Discretion
- Material 3 typography scale choices
- Exact color scheme derivation (lightColorScheme values)
- Navigation animation transitions
- Edge-to-edge display configuration

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### iOS Reference (layout and behavior to match)
- `iosApp/iosApp/Views/MainTabView.swift` — Tab structure, tab icons, accent color
- `iosApp/iosApp/Views/Common/PlaceholderTabView.swift` — Placeholder tab layout
- `iosApp/iosApp/Extensions/Color+App.swift` — Accent color definition

### Shared KMP Module (integration points)
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin module setup, initKoin() function
- `shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt` — Android platform module (needs androidContext)
- `gradle/libs.versions.toml` — Version catalog (needs Compose deps added)

### Android Module (files to modify)
- `androidApp/build.gradle.kts` — Needs Compose plugin + dependencies
- `androidApp/src/main/AndroidManifest.xml` — Needs MainActivity declaration

### Requirements
- `.planning/REQUIREMENTS-v1.5.md` — ANDROID-01, ANDROID-02

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SharedModule.kt`: Koin module with all 8 ViewModels registered via `viewModel { }` — Android Compose can inject directly with `koinViewModel()`
- `PlatformModule.android.kt`: Already provides Room builder and DataStore for Android — just needs `androidContext()` from app init
- `initKoin()` in SharedModule.kt: Common init function that accepts additional modules

### Established Patterns
- Koin DI: shared + platform module pattern, ViewModel injection
- StateFlow-based ViewModels: All VMs expose StateFlow, Compose observes via `collectAsState()`
- Navigation: iOS uses per-tab NavigationStack — Android should mirror with per-tab NavHost

### Integration Points
- `initKoin()` called from Application.onCreate() with androidContext()
- ViewModels accessed in Compose via `koinViewModel<XxxViewModel>()`
- Navigation routes need to map to iOS screen hierarchy (TemplateList as Workout tab root)

</code_context>

<specifics>
## Specific Ideas

- iOS tab icons: dumbbell.fill, chart.bar.fill, fork.knife — map to Material Icons equivalents (FitnessCenter, BarChart, Restaurant)
- iOS accent tint RGB(0.4, 0.733, 0.416) applied to tab bar — Android applies via Material 3 NavigationBar selectedIconColor/indicatorColor
- Tab labels: "Workout", "Overview", "Nutrition" — same as iOS

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 11-android-shell-navigation*
*Context gathered: 2026-03-31*

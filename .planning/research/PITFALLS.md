# Domain Pitfalls

**Domain:** KMP Compose Multiplatform fitness/workout tracking app (iOS-first, local storage)
**Researched:** 2026-03-28

---

## Critical Pitfalls

Mistakes that cause rewrites, data loss, or weeks of wasted time.

### Pitfall 1: Rest Timer Dies When App Backgrounds on iOS

**What goes wrong:** iOS suspends your app seconds after the user locks the screen or switches to another app. A `kotlinx.coroutines` delay-based countdown timer silently stops. The user comes back after a 90-second rest, and the timer shows 87 seconds remaining. For a workout app, this is a trust-destroying bug -- rest timing is core to the exercise flow.

**Why it happens:** iOS does not allow arbitrary background execution. Unlike Android (which has foreground services), iOS will suspend your process. Kotlin coroutines have no special immunity to this -- they are paused along with the process.

**Consequences:** Timer shows wrong time after returning to app. Users lose trust in the app's core mechanic. If the timer drives workout state transitions (auto-advance to next set), those transitions never fire.

**Prevention:**
- Do NOT rely on coroutine delay for elapsed time. Store the `startTime` (epoch millis) when the rest period begins. On every UI tick and on resume, calculate `elapsed = now - startTime`. The timer is always correct regardless of suspension.
- Schedule an iOS local notification (via `expect/actual` wrapping `UNUserNotificationCenter`) for the rest period end time. If the user is in another app, they still get a "Rest complete" alert.
- On resume (`Lifecycle.Event.ON_RESUME`), immediately recalculate timer state from the stored start time. If rest is already over, advance the workout state.

**Detection:** Test by starting a rest timer, backgrounding the app for longer than the rest period, and returning. If the timer does not show 0 or advance, you have this bug.

**Phase relevance:** Must be solved in the very first phase that implements the rest timer. Do not defer.

**Confidence:** HIGH -- this is a fundamental iOS platform behavior, well-documented by Apple.

---

### Pitfall 2: Workout Session Lost on Process Death / Crash

**What goes wrong:** User is 40 minutes into a workout. iOS kills the app (memory pressure, user force-quit, crash). They reopen and land on the home screen with zero workout data. All logged sets are gone.

**Why it happens:** Workout state is held only in ViewModel memory (StateFlow). Neither Room nor SQLDelight is written to until the "finish workout" action. Process death wipes the ViewModel.

**Consequences:** Complete data loss of an in-progress workout. Users will not use the app for real workouts after experiencing this once.

**Prevention:**
- Persist workout session state to the database incrementally. Every completed set should be written to a `workout_sessions` table (or similar) immediately, not batched at the end.
- Store a `WorkoutSession` row with status `IN_PROGRESS` when the workout starts. Include current exercise index and set index.
- On app launch, check for `IN_PROGRESS` sessions. If found, offer to resume. This is the "crash recovery" path.
- The gymtracker firmware reference already uses an FSM pattern -- adapt this to persist the FSM state to disk, not just hold it in memory.

**Detection:** Start a workout, log 3 sets, then force-kill the app from the iOS task switcher. Reopen. If there is no recovery prompt, you have this pitfall.

**Phase relevance:** Must be designed into the data model from the start. Retrofitting crash recovery onto a "save only at end" model is a rewrite.

**Confidence:** HIGH -- standard mobile development concern, amplified for workout apps where sessions are long-running (30-90 min).

---

### Pitfall 3: iOS Lifecycle Mismatch Causes Double-Firing Effects and Stale State

**What goes wrong:** `LaunchedEffect` and `DisposableEffect` fire unexpectedly on iOS when navigating between screens. A timer resets, a database query re-executes, or a state machine reinitializes when the user navigates forward (not just back).

**Why it happens:** On iOS, Compose Multiplatform's lifecycle integration behaves differently from Android. When a `UIViewController` hosting Compose leaves the screen (even for forward navigation into a deeper screen), lifecycle events fire as if the composable is being disposed. This triggers `DisposableEffect` onDispose and re-triggers `LaunchedEffect` when returning. Android does not behave this way -- effects persist across the activity lifetime.

**Consequences:** Workout timer resets when navigating to an exercise detail and back. Database queries re-execute unnecessarily. State machines reset to initial state.

**Prevention:**
- Do NOT put workout session state inside composable-scoped effects. Use a ViewModel that survives navigation. The ViewModel's `viewModelScope` is the correct coroutine scope for ongoing operations.
- Use `collectAsState()` to observe ViewModel StateFlow from composables, rather than launching collectors inside `LaunchedEffect`.
- If you must use `LaunchedEffect` with a key, make the key stable and meaningful (e.g., `workoutId`), not `Unit` which re-fires on every recomposition of the composable.
- Test every screen transition on iOS specifically -- do not assume Android behavior matches.

**Detection:** Navigate forward from the active workout screen to any detail screen, then back. If timers reset or state changes, you have this bug.

**Phase relevance:** Foundation phase. The navigation and ViewModel architecture must account for this from day one.

**Confidence:** HIGH -- documented in [JetBrains GitHub issue #3890](https://github.com/JetBrains/compose-multiplatform/issues/3890) and [#3889](https://github.com/JetBrains/compose-multiplatform/issues/3889).

---

### Pitfall 4: Database Migrations Silently Wipe Data on iOS

**What goes wrong:** You add a column to an entity (e.g., adding `notes` to a `WorkoutSet`). On Android, Room handles the migration. On iOS, the migration fails silently and Room falls back to destructive migration, wiping the entire database. The user's workout history vanishes.

**Why it happens:** If `fallbackToDestructiveMigration()` is set (common in tutorials and starter templates), a missing or broken migration deletes all tables and recreates them. iOS SQLite handles `ALTER TABLE` slightly differently from Android's SQLite version, particularly around non-null default values. A migration that passes on Android can fail on iOS.

**Consequences:** Silent, total data loss. No crash, no error message -- just empty tables.

**Prevention:**
- Never use `fallbackToDestructiveMigration()` except in debug builds. Remove it before any user testing.
- Write explicit `Migration` objects for every schema change. Test them on both platforms.
- Add an integration test that opens a pre-populated database, runs the migration, and verifies data survives. Run this test on both Android and iOS targets in CI.
- Since Database Inspector is Android-only, build a debug DAO that can dump table schemas programmatically so you can verify migrations on iOS.
- Consider using version-numbered `.sq` files if you go with SQLDelight, which has a more explicit migration story.

**Detection:** Change a schema, run on iOS without providing a migration, check if data persists. If it doesn't, and no error was thrown, you have this pitfall.

**Phase relevance:** Data model phase. Establish migration discipline from the first schema. Do not wait until you have user data to start caring about migrations.

**Confidence:** HIGH -- documented in Android developer docs and multiple KMP migration guides.

---

## Moderate Pitfalls

### Pitfall 5: iOS Keyboard Covers Weight/Rep Input Fields

**What goes wrong:** On the set logging screen, the user taps the weight or reps `TextField`. The iOS software keyboard slides up and covers the input field. The user is typing blind, which is especially bad when entering precise weights.

**Why it happens:** Compose Multiplatform's iOS `TextField` keyboard avoidance behaves differently from Android. The `WindowInsets` API for keyboard avoidance exists but requires explicit handling. Material `TextField` on iOS has known issues with keyboard interaction (tracked in JetBrains issues [#3621](https://github.com/JetBrains/compose-multiplatform/issues/3621), [#3530](https://github.com/JetBrains/compose-multiplatform/issues/3530)).

**Prevention:**
- Wrap scrollable content in a `Column` inside a `verticalScroll` modifier with `imePadding()` applied to the parent.
- Test every input screen on a real iOS device (simulator keyboard behavior differs).
- Use `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)` for weight and rep fields to get the numeric pad, which is shorter and less likely to obscure content.
- Consider placing input fields in the upper portion of the screen, or using a bottom sheet that sits above the keyboard.

**Detection:** Run on a real iOS device, tap every text field, verify nothing is obscured.

**Phase relevance:** UI implementation phase. Test early on a real device, not just simulator.

**Confidence:** MEDIUM -- issues are documented but behavior has improved in recent Compose Multiplatform versions (1.8+). Verify with your target version.

---

### Pitfall 6: 20-Minute iOS Builds Kill Development Velocity

**What goes wrong:** Every code change triggers a Kotlin/Native compilation for iOS that takes 10-20 minutes. Development becomes painfully slow. You start avoiding running on iOS and only testing on Android, which leads to iOS-specific bugs shipping late.

**Why it happens:** Kotlin/Native compiles Kotlin to native machine code (LLVM), which is inherently slower than JVM compilation. Building for multiple iOS architectures (simulator arm64, simulator x86_64, device arm64) multiplies the cost. Default Gradle configuration does not enable caching or limit target architectures.

**Prevention:**
- Set `kotlin.native.cacheKind=static` in `gradle.properties` for faster incremental rebuilds.
- During development, only build for the simulator architecture you are actually using. Do not build all architectures on every change.
- Enable Gradle configuration cache and build cache (`org.gradle.configuration-cache=true`, `org.gradle.caching=true`).
- Use Gradle convention plugins to avoid duplicating build configuration across modules. Version updates become one-line changes instead of find-and-replace.
- Set a consistent `jvmToolchain` across all modules to avoid recompilation from toolchain mismatches.

**Detection:** Time your first iOS build and your incremental build after a one-line Kotlin change. If incremental is over 2 minutes, optimize.

**Phase relevance:** Project setup phase. Get build configuration right from day one. Fixing this later means fighting Gradle while also trying to ship features.

**Confidence:** HIGH -- widely reported, with documented fixes.

---

### Pitfall 7: Material Design UI Feels Wrong on iOS

**What goes wrong:** The app ships with Material 3 components everywhere. iOS users find it jarring -- navigation bars look wrong, switches look wrong, the bottom sheet behavior is wrong, the whole thing feels like "an Android app on my iPhone." For a university project targeting iOS-first, this undermines the premise.

**Why it happens:** Compose Multiplatform defaults to Material Design components. There is no built-in Cupertino/iOS-native component kit from JetBrains. The assumption that "shared UI = identical UI" leads to an app that feels native on neither platform.

**Consequences:** Poor user experience on iOS. Evaluators (professors) who are iOS users will notice immediately.

**Prevention:**
- Accept Material Design as the foundation but customize it to feel less jarring on iOS. Use iOS-style color schemes, rounded corners, and spacing conventions.
- For critical touchpoints (navigation bar, back navigation), consider using `expect/actual` to provide platform-appropriate behavior.
- Look into community libraries like `compose-cupertino` (by Alex Zhukovich) that provide iOS-style components for Compose Multiplatform. Evaluate maturity before adopting.
- Prioritize behavior over appearance: iOS users care more about swipe-back gestures working correctly than about the exact shape of a button.

**Detection:** Show the app to an iOS user and ask "Does this feel like an iPhone app?" If they hesitate, you have this problem.

**Phase relevance:** UI implementation phase. Make this decision early -- switching component styles later means touching every screen.

**Confidence:** MEDIUM -- the severity depends on the evaluator's expectations and how much iOS-native feel matters for grading.

---

### Pitfall 8: Navigation Swipe-Back Gesture Broken or Janky on iOS

**What goes wrong:** Users swipe from the left edge to go back (the most fundamental iOS gesture). Either nothing happens, or the animation is janky and non-native-feeling, or it works on some screens but not others.

**Why it happens:** Compose Multiplatform's navigation library has experimental support for iOS back gestures as of recent versions. However, it does not perfectly replicate the native iOS interactive dismissal (where you see the previous screen sliding in behind). Custom composable transitions may conflict with the gesture system.

**Consequences:** The app feels broken on iOS. Swipe-back is muscle memory for every iOS user.

**Prevention:**
- Use the official Compose Navigation library's built-in back gesture support (enabled by default on iOS in recent versions). Do not fight it with custom transition animations.
- Test swipe-back on every screen transition, not just the main ones.
- If the official library's back gesture is insufficient, evaluate Decompose as a navigation library -- it has more mature iOS back gesture support.
- Avoid WebView or other UIKit interop on screens where back gesture matters, as gesture conflicts arise.

**Detection:** Navigate 3 screens deep, then swipe back from the left edge on each. If any transition feels wrong or does not work, address it.

**Phase relevance:** Navigation setup phase. Choose the navigation library with this in mind.

**Confidence:** MEDIUM -- the official library has improved significantly, but edge cases remain. Test with your specific version.

---

### Pitfall 9: Coroutine Exception Crashes iOS App Silently

**What goes wrong:** A Kotlin `suspend` function throws an exception. On Android, it propagates normally through the coroutine hierarchy. On iOS, the app crashes with no meaningful stack trace because Kotlin/Native exceptions do not automatically bridge to Swift error handling.

**Why it happens:** Kotlin suspend functions called from Swift/ObjC do not propagate exceptions as Swift `Error`. Without `@Throws(Throwable::class)` annotation, the exception is uncatchable on the iOS side and crashes the app. Even within shared Kotlin code, unhandled exceptions in coroutines launched on iOS can produce opaque crash logs.

**Consequences:** Crashes during workout sessions (the worst possible time). Difficult to debug because the stack trace is unhelpful on iOS.

**Prevention:**
- Add `@Throws(Exception::class)` to any suspend function exposed to iOS/Swift.
- Use `CoroutineExceptionHandler` on every `CoroutineScope` and `viewModelScope` to catch and log unhandled exceptions instead of crashing.
- Wrap database operations and any IO in try/catch within the shared module, not at the UI layer.
- If using KMP-NativeCoroutines library, it handles this bridging automatically. Consider it for any suspend functions exposed to Swift.

**Detection:** Throw an exception in a suspend function called from a composable. If the iOS app crashes instead of showing an error state, you have this pitfall.

**Phase relevance:** Foundation phase. Establish error handling patterns from the first coroutine.

**Confidence:** HIGH -- documented in Kotlin docs and widely discussed in KMP community.

---

### Pitfall 10: Overengineering expect/actual Instead of Using Interfaces

**What goes wrong:** The developer creates `expect/actual` declarations for everything that differs between platforms -- database construction, timer utilities, notification scheduling, settings storage. The `commonMain` module becomes tightly coupled to platform specifics. Testing requires running on actual platform targets instead of JVM.

**Why it happens:** `expect/actual` is the first pattern KMP newcomers learn. It seems like the right tool for every platform difference. The alternative (interface + dependency injection) feels like more boilerplate initially.

**Consequences:** Shared code becomes hard to unit test (can't run on JVM). Platform-specific code bleeds into common modules. Adding a third platform later requires implementing every `actual` declaration.

**Prevention:**
- Use `expect/actual` only for things that are truly compile-time platform decisions (e.g., creating a database driver, providing a platform context).
- For everything else, define an interface in `commonMain` and inject the platform implementation via Koin. This keeps common code testable on JVM.
- Rule of thumb: if you can define it as an interface with a single implementation per platform, prefer interface + DI over expect/actual.

**Detection:** Count your `expect` declarations. If there are more than 5-8 in a prototype app, you are likely overusing the pattern.

**Phase relevance:** Architecture/foundation phase. Establish the DI pattern early.

**Confidence:** HIGH -- this is a widely discussed KMP architectural best practice.

---

## Minor Pitfalls

### Pitfall 11: Flow collectAsState Behavior Differs Between Android and iOS

**What goes wrong:** A `StateFlow` observed with `.collectAsState()` keeps the subscription alive when the Android app goes to background, but pauses collection on iOS when the user presses the home button. If the workout timer is updating state via a Flow, the UI goes stale on iOS background/foreground transitions.

**Prevention:** Rely on `Lifecycle`-aware collection (`collectAsStateWithLifecycle` where available) and recalculate state on resume rather than depending on continuous Flow emission. Store authoritative state in the database, not just in a Flow.

**Phase relevance:** Any phase using Flows. Be aware from the start.

**Confidence:** HIGH -- documented in [JetBrains GitHub issue #3889](https://github.com/JetBrains/compose-multiplatform/issues/3889).

---

### Pitfall 12: Koin Module Not Initialized on iOS Entry Point

**What goes wrong:** The app works perfectly on Android but crashes on iOS launch with "No definition found for class" errors. Dependency injection simply was not started.

**Prevention:** Ensure `initKoin()` is called from the iOS app entry point (e.g., in `MainViewController.kt` or the iOS `AppDelegate` equivalent via `expect/actual`). Android typically handles this in `Application.onCreate()`, but iOS has a different entry point. Follow Koin's KMP setup guide exactly.

**Detection:** First run on iOS. If it crashes immediately with Koin resolution errors, you missed this step.

**Phase relevance:** Initial project setup.

**Confidence:** HIGH -- Koin's own docs flag this as a common mistake.

---

### Pitfall 13: Weight Stored as Float Causes Precision Drift

**What goes wrong:** User enters 62.5 kg. After a few save/load cycles, the value displays as 62.499999 or 62.500001. Float/Double precision errors accumulate, and the displayed weight looks wrong.

**Prevention:** Store weight as an integer in the smallest unit (e.g., grams or decigrams, matching gymtracker's `kg x 10` approach). Display by dividing: `625 / 10 = 62.5`. Never store user-facing decimal weights as Float or Double in the database. Use `Int` or `Long` columns.

**Phase relevance:** Data model design. Get this right in the schema, not as a UI format hack later.

**Confidence:** HIGH -- fundamental floating-point behavior; the gymtracker reference already solved this with `kg * 10`.

---

### Pitfall 14: Version Mismatch Between Kotlin, Compose, and Libraries Breaks Build

**What goes wrong:** Updating Kotlin from 2.0 to 2.1 breaks Compose Multiplatform. Or updating Compose Multiplatform breaks a third-party library. The build fails with cryptic compiler errors about incompatible metadata.

**Prevention:**
- Use the [JetBrains compatibility matrix](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) to pick compatible versions of Kotlin, Compose Multiplatform, and AGP.
- Pin all versions in a Gradle version catalog (`libs.versions.toml`).
- Do not update Kotlin, Compose, or AGP independently. Update them together using the compatibility matrix.
- Lock dependency versions early and do not update mid-milestone unless forced by a blocker.

**Phase relevance:** Project setup. Define versions once and do not touch them during development unless necessary.

**Confidence:** HIGH -- version conflicts are the most commonly reported KMP build issue.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Project setup / scaffolding | Slow iOS builds (Pitfall 6), version mismatches (Pitfall 14), Koin not initialized on iOS (Pitfall 12) | Configure Gradle caching, pin versions from compatibility matrix, test iOS launch immediately |
| Data model / database | Destructive migration (Pitfall 4), float precision (Pitfall 13) | Never use `fallbackToDestructiveMigration()`, use integer weight storage |
| Workout session state machine | Session loss on process death (Pitfall 2), lifecycle mismatch (Pitfall 3) | Persist FSM state to DB incrementally, keep state in ViewModel not LaunchedEffect |
| Rest timer implementation | Timer dies in background (Pitfall 1), Flow behavior difference (Pitfall 11) | Epoch-based timing, local notifications for background, recalculate on resume |
| Navigation | Swipe-back broken (Pitfall 8), lifecycle effects re-fire (Pitfall 3) | Test every transition on iOS, use ViewModel-scoped state |
| UI / input forms | Keyboard covers inputs (Pitfall 5), Material feels wrong on iOS (Pitfall 7) | `imePadding()`, numeric keyboard, test on real iOS device |
| Error handling | Coroutine exceptions crash iOS (Pitfall 9) | `@Throws`, `CoroutineExceptionHandler`, wrap IO in try/catch |
| Architecture patterns | expect/actual overuse (Pitfall 10) | Prefer interface + Koin DI, limit expect/actual to platform bootstrapping |

---

## Sources

- [JetBrains: Compose Multiplatform 1.8.0 iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) -- Confidence: HIGH
- [JetBrains GitHub Issue #3890: LaunchedEffect/DisposableEffect lifecycle on iOS](https://github.com/JetBrains/compose-multiplatform/issues/3890) -- Confidence: HIGH
- [JetBrains GitHub Issue #3889: Flow subscription difference Android vs iOS](https://github.com/JetBrains/compose-multiplatform/issues/3889) -- Confidence: HIGH
- [JetBrains GitHub Issue #3621: iOS TextField keyboard issues](https://github.com/JetBrains/compose-multiplatform/issues/3621) -- Confidence: HIGH
- [Kotlin Docs: Kotlin/Native memory management](https://kotlinlang.org/docs/native-memory-manager.html) -- Confidence: HIGH
- [Kotlin Docs: Compatibility and versioning](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) -- Confidence: HIGH
- [Apple Developer Forums: countdown timer in background](https://developer.apple.com/forums/thread/133640) -- Confidence: HIGH
- [Android Developers: Room KMP setup](https://developer.android.com/kotlin/multiplatform/room) -- Confidence: HIGH
- [Android Developers Blog: Room 3.0](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) -- Confidence: HIGH
- [Koin: KMP advanced patterns](https://insert-koin.io/docs/reference/koin-mp/kmp/) -- Confidence: HIGH
- [KMP vs CMP: Lessons from real projects](https://www.aetherius-solutions.com/blog-posts/kotlin-multiplatform-vs-compose-multiplatform) -- Confidence: MEDIUM
- [KMP Architecture best practices (carrion.dev)](https://carrion.dev/en/posts/kmp-architecture/) -- Confidence: MEDIUM
- [Compose Multiplatform iOS back gesture (Slack)](https://slack-chats.kotlinlang.org/t/22307386/i-wish-compose-multiplatform-had-better-back-handling-for-io) -- Confidence: MEDIUM
- [Medium: KMP iOS build optimization](https://medium.com/@houssembababendermel/how-i-fixed-my-kmp-ios-build-from-20-minute-builds-to-lightning-fast-c4f0f5c102b0) -- Confidence: MEDIUM

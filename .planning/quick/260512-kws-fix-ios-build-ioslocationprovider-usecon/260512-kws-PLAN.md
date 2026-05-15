---
phase: quick-260512-kws
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt
autonomous: true
requirements:
  - QUICK-260512-kws
must_haves:
  truths:
    - "iOS shared module compiles without 'Unresolved reference latitude/longitude' errors"
    - "CLLocationCoordinate2D struct is accessed via useContents { ... } scope, not via direct property access"
  artifacts:
    - path: "shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt"
      provides: "iOS CLLocationManager-based LocationProvider impl"
      contains: "import kotlinx.cinterop.useContents"
  key_links:
    - from: "IosLocationProvider.LocationDelegate.locationManager(didUpdateLocations)"
      to: "GeoPoint constructor"
      via: "loc.coordinate.useContents { latitude, longitude }"
      pattern: "loc\\.coordinate\\.useContents"
---

<objective>
Fix the iOS build failure in `IosLocationProvider.kt` at line 47. Kotlin/Native represents Objective-C structs like `CLLocationCoordinate2D` as `CValue<CLLocationCoordinate2D>`; direct `.latitude` / `.longitude` access is invalid. Wrap the access in `useContents { ... }` and add the matching `kotlinx.cinterop.useContents` import.

Purpose: Unblock iOS compilation of the `shared` module. This is pre-existing drift surfaced after the recent `libs.versions.toml` merge-marker cleanup (commit `ff044e5`), not a regression introduced by an earlier quick task.

Output: Single-file edit + one atomic commit.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/STATE.md
@shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt

<interfaces>
<!-- Current state of the relevant block (lines 45-49) -->

```kotlin
override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
    val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
    cont?.resume(GeoPoint(loc.coordinate.latitude, loc.coordinate.longitude))
    cont = null
}
```

<!-- Target state -->

```kotlin
override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
    val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
    loc.coordinate.useContents { cont?.resume(GeoPoint(latitude, longitude)) }
    cont = null
}
```

<!-- Required new import (alphabetical placement among kotlinx.* imports — between
     `import kotlinx.coroutines.suspendCancellableCoroutine` and platform.* imports
     is also acceptable; the file already opts into `kotlinx.cinterop.ExperimentalForeignApi`
     and `kotlinx.cinterop.BetaInteropApi` at the top). -->

```kotlin
import kotlinx.cinterop.useContents
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Wrap CLLocationCoordinate2D access in useContents and add import</name>
  <files>shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt</files>
  <action>
Make exactly two edits to `shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt`:

1. Add the import `import kotlinx.cinterop.useContents` in the import block. Place it alphabetically among the `kotlinx.*` imports (e.g., directly after `import kotlinx.coroutines.suspendCancellableCoroutine` is acceptable; it must sit with the other `kotlinx.*` imports and before the `platform.*` block is also acceptable as long as it stays grouped with `kotlinx.*`).

2. Replace the body of `locationManager(manager:, didUpdateLocations:)` (currently lines 45-49) so that `loc.coordinate` is opened via `useContents { ... }` and `latitude` / `longitude` are read from inside that scope. Final target:

```kotlin
override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
    val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
    loc.coordinate.useContents { cont?.resume(GeoPoint(latitude, longitude)) }
    cont = null
}
```

Do NOT modify anything else in the file:
- Do NOT touch `requestSingleFix`, the `didFailWithError` override, or the `IosLocationProvider` class itself
- Do NOT change the `@file:OptIn(...)` line (it already includes `ExperimentalForeignApi`, which is required for `useContents`)
- Do NOT change the `GeoPoint` / `LocationProvider` interfaces or any other file
- Do NOT add explanatory comments — this is a standard Kotlin/Native cinterop pattern with no non-obvious WHY

After the edit, commit with message exactly:
```
fix(ios): use useContents for CLLocationCoordinate2D access in IosLocationProvider
```
  </action>
  <verify>
    <automated>
# 1. Import was added exactly once
test "$(grep -cE '^import kotlinx\.cinterop\.useContents$' shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt)" -eq 1

# 2. useContents call on loc.coordinate present exactly once
test "$(grep -cE 'loc\.coordinate\.useContents' shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt)" -eq 1

# 3. No remaining direct property access on loc.coordinate
test "$(grep -cE 'loc\.coordinate\.(latitude|longitude)' shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt)" -eq 0

# 4. Gradle compile succeeds for an iOS target (try simulator-arm64 first, then arm64 device, then metadata)
( ./gradlew :shared:compileKotlinIosSimulatorArm64 --quiet ) \
  || ( ./gradlew :shared:compileKotlinIosArm64 --quiet ) \
  || ./gradlew :shared:compileKotlinMetadata --quiet
    </automated>
  </verify>
  <done>
- `IosLocationProvider.kt` imports `kotlinx.cinterop.useContents` exactly once
- The `didUpdateLocations` handler reads latitude/longitude inside `loc.coordinate.useContents { ... }`
- No remaining `loc.coordinate.latitude` or `loc.coordinate.longitude` references in the file
- At least one of `:shared:compileKotlinIosSimulatorArm64`, `:shared:compileKotlinIosArm64`, or `:shared:compileKotlinMetadata` exits 0
- Single atomic commit with message `fix(ios): use useContents for CLLocationCoordinate2D access in IosLocationProvider`
  </done>
</task>

</tasks>

<verification>
- File-level grep gates above (import present == 1, useContents call == 1, direct property access == 0).
- iOS compile target green (simulator-arm64 preferred; fall back to arm64 device or metadata if a target is unavailable in this environment).
- `git log -1 --pretty=%s` matches the required commit subject exactly.
</verification>

<success_criteria>
- iOS shared module compiles without the previous "Unresolved reference 'latitude' / 'longitude'" errors.
- Only one file changed (`IosLocationProvider.kt`); diff is the two-line body change plus the one import line.
- One atomic commit landed with the specified subject.
</success_criteria>

<output>
After completion, create `.planning/quick/260512-kws-fix-ios-build-ioslocationprovider-usecon/260512-kws-SUMMARY.md` summarizing the fix (1 file, 1 commit, iOS compile target that ran green).
</output>

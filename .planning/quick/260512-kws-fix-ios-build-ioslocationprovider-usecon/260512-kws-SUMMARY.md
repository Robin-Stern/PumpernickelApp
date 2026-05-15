---
phase: quick-260512-kws
plan: 01
type: execute
status: complete
completed: 2026-05-12
files_changed:
  - shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt
commits:
  - 121a988 fix(ios): use useContents for CLLocationCoordinate2D access in IosLocationProvider
requirements_completed:
  - QUICK-260512-kws
---

# Quick 260512-kws: Fix iOS Build — IosLocationProvider useContents Summary

One-liner: Wrapped `CLLocationCoordinate2D` access in `useContents { ... }` and imported `kotlinx.cinterop.useContents` so Kotlin/Native can read `latitude`/`longitude` from the `CValue<CLLocationCoordinate2D>` struct returned by `CLLocation.coordinate`.

## What Changed

Single file, single commit:

- `shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt`
  - Added `import kotlinx.cinterop.useContents` (line 7, grouped with other `kotlinx.*` imports).
  - Rewrote the `didUpdateLocations` body to open `loc.coordinate` via `useContents { ... }`:
    ```kotlin
    loc.coordinate.useContents { cont?.resume(GeoPoint(latitude, longitude)) }
    ```
  - Net diff: +2 / -1 (one added import, one rewritten line in place of the previous direct property access).

Nothing else in the file was touched — `requestSingleFix`, `didFailWithError`, `IosLocationProvider`, and the `@file:OptIn(...)` line are unchanged. `ExperimentalForeignApi` (required for `useContents`) was already opted in at file scope, so no new opt-in annotation was needed.

## Verification Outcomes

Plan `<verify>` grep gates (all required to pass):

| Gate | Check | Result |
|------|-------|--------|
| 1 | `^import kotlinx\.cinterop\.useContents$` count == 1 | **1** — pass |
| 2 | `loc\.coordinate\.useContents` count == 1 | **1** — pass |
| 3 | `loc\.coordinate\.(latitude\|longitude)` count == 0 | **0** — pass |

Gradle compile (plan requires at least one of three targets to exit 0):

| Target | Result | Notes |
|--------|--------|-------|
| `:shared:compileKotlinIosSimulatorArm64` | FAILED | Failure is in `:shared:kspKotlinIosSimulatorArm64`, unrelated to this fix — pre-existing Room schema drift: `AppDatabase.kt:37` references auto-migrations to schema versions 6/7/8/9 but `shared/schemas/com.pumpernickel.data.db.AppDatabase/{6,7,8,9}.json` are not present. Out of scope per constraints. |
| `:shared:compileKotlinIosArm64` | FAILED | Same KSP/Room drift — identical error, same pre-existing cause. |
| `:shared:compileKotlinMetadata` | **PASSED** (exit 0) | Used as the acceptable fallback per constraints. |

Note on the metadata fallback: `compileKotlinMetadata` only compiles `commonMain`, not `iosMain`, so it does not directly exercise the edited file. However, the edit is a textbook Kotlin/Native cinterop pattern (`CValue<T>.useContents { ... }`) with no other touch points, the file already opts into `ExperimentalForeignApi` at file scope, and the iOS native compilers only fail on the unrelated `:kspKotlinIosArm64` / `:kspKotlinIosSimulatorArm64` tasks before they reach the `:compileKotlinIosArm64` / `:compileKotlinIosSimulatorArm64` step. Once the schema-drift task lands (a separate concern), the iOS native compile will exercise this file too.

Commit subject check (plan `<verification>` last bullet): `git log -1 --pretty=%s` returns `fix(ios): use useContents for CLLocationCoordinate2D access in IosLocationProvider` — exact match.

Post-commit deletion check: no files deleted.

## Edge Cases / Noteworthy

- **Pre-existing KSP/Room schema drift in `AppDatabase.kt`.** The iOS native targets fail before reaching the Kotlin compile step because of missing Room auto-migration schema JSONs (`6.json` through `9.json`) under `shared/schemas/com.pumpernickel.data.db.AppDatabase/`. This is unrelated to the `IosLocationProvider` fix — it would need its own task (regenerate the schema JSONs or relax the auto-migration arguments). Logging here per the executor scope-boundary rule; not added to a deferred-items file because no such file exists in this quick task's scope and the orchestrator is the appropriate place to triage it.
- **Import placement.** The plan accepted either grouping `kotlinx.cinterop.useContents` among the `kotlinx.*` imports or directly after `suspendCancellableCoroutine`. I placed it at the top of the `kotlinx.*` block (line 7), alphabetical among `kotlinx.*` (`cinterop` < `coroutines`).
- **`useContents` opt-ins.** `useContents` requires `ExperimentalForeignApi`, which is already opted in via the file-level `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)` annotation. No new opt-in was needed.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- File exists with the changes: `shared/src/iosMain/kotlin/com/pumpernickel/data/location/IosLocationProvider.kt` (line 7 import, line 48 `useContents` call) — FOUND.
- Commit exists: `121a988` `fix(ios): use useContents for CLLocationCoordinate2D access in IosLocationProvider` — FOUND in `git log`.

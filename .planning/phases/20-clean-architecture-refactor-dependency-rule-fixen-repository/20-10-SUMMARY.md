---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 10
subsystem: clean-architecture-infrastructure-layer-commonmain
tags: [refactor, dependency-rule, smell-12, infrastructure-layer, expect-actual, wave-6]
requires: []
provides:
  - "com.pumpernickel.infrastructure.ai.SecureKeyStore (expect)"
  - "com.pumpernickel.infrastructure.notification.NotificationService (expect) + BackgroundTaskManager (expect)"
  - "com.pumpernickel.infrastructure.progresspic.BiometricGate (expect)"
  - "com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncher (expect)"
  - "com.pumpernickel.infrastructure.progresspic.PhotoVault (expect)"
  - "com.pumpernickel.infrastructure.geofence.GeofenceProvider (interface)"
  - "com.pumpernickel.infrastructure.location.LocationProvider (interface)"
  - "com.pumpernickel.infrastructure.permissions.PermissionController (interface)"
affects:
  - "Plan 20-11 (Android — must move actuals to androidMain/infrastructure/<sub>/ to match the new expect paths; build is broken until then)"
  - "Plan 20-12 (iOS — same, for iosMain; also KoinHelper.kt + IosLocationProvider.kt + IosPermissionController.kt + IosGeofenceProvider.kt + PlatformModule.ios.kt imports)"
  - "Plan 20-13 (Final Verification — owns full Android + iOS Xcode build)"
tech-stack:
  added: []
  patterns:
    - "Top-level `infrastructure/` layer for OS-port expect-classes / interfaces (D-20-02)"
    - "expect-class declarations live in `commonMain/.../infrastructure/<sub>/`; matching `actual`s live in `androidMain/.../infrastructure/<sub>/` and `iosMain/.../infrastructure/<sub>/` (next plans)"
    - "Domain DTOs that ports reference (UnlockResult, GeofenceEvent, GeoPoint, LocationPermissionStatus) stay in `domain/<sub>/` — only the OS-coupled expect/interface declarations move."
    - "Intentionally broken intermediate state — commit 20-10 alone does NOT have green platform builds; 20-11 + 20-12 follow synchronously."
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGate.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoVault.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GeofenceProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/location/LocationProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/permissions/PermissionController.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/AiGenerationManager.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/geofence/DebugGeofenceProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  deleted:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/NotificationService.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/BiometricGate.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoCaptureLauncher.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/PhotoVault.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/PermissionController.kt
decisions:
  - "Atomic single commit für die 8 Moves + Consumer-Import-Updates. Plan-`approach`/`risks_pitfalls` empfiehlt das ausdrücklich, weil `expect`/`actual` Package-Pfade synchron sein müssen — Splitting der Moves auf mehrere Commits würde mehr Zwischenzustände produzieren, ohne den Hauptzustand (Platform-Compile broken) zu vermeiden."
  - "`BackgroundTaskManager` (expect) wandert MIT `NotificationService` ins selbe File `infrastructure/notification/NotificationService.kt`, weil beide ursprünglich in einem File koexistierten. Beide sind OS-Infrastructure-Ports (background-task scheduling = OS-resource lifecycle), passen semantisch zum `notification/`-Sub-Paket (Notifications + Hintergrund-Lifecycle für AI-Generation gehören zusammen)."
  - "Verify in 20-10 ist NICHT `./gradlew :shared:compileCommonMainKotlinMetadata`. Dieser Task ist im aktuellen Repo bereits **pre-existing broken** wegen Room-`AppDatabaseConstructor` (Object 'AppDatabaseConstructor' is not abstract — Room generiert `initialize()` per Platform-Target via KSP, was `compileCommonMainKotlinMetadata` nicht sieht). Verifiziert sowohl auf HEAD (vor 20-10) als auch auf HEAD (nach 20-10) — identischer Error, kein zusätzlicher durch unsere Änderungen. Plan-Approach §5 Erwartung 'BUILD SUCCESSFUL für commonMain-metadata' hat das Repo-State von vor 20-10 nicht reflektiert."
  - "Stattdessen Verify via Grep-Guards: (a) Keine `import com.pumpernickel.domain.<sub>.<Port>` mehr in `shared/src/commonMain/`. (b) 8 neue Files unter `infrastructure/`. (c) 8 alte Files unter `domain/` weg. (d) Domain-DTO-Files (UnlockResult, GeofenceEvent, GeoPoint, LocationPermissionStatus) bleiben unter `domain/`."
  - "BiometricGate.kt importiert nun explizit `com.pumpernickel.domain.progresspic.UnlockResult` (vorher im selben Paket implizit). Analog GeofenceProvider importiert `domain.geofence.GeofenceEvent` und `domain.location.GeoPoint`; LocationProvider importiert `domain.location.GeoPoint`; PermissionController importiert `domain.permissions.LocationPermissionStatus`. Diese cross-package-Imports drücken die korrekte Schichtenrichtung aus: `infrastructure/` darf `domain/` lesen, aber nicht umgekehrt."
  - "AiGenerationManager.kt (bleibt in `domain/ai/`) brauchte explizite Imports `com.pumpernickel.infrastructure.notification.{NotificationService, BackgroundTaskManager}`, weil die expect-Klassen aus dem selben Paket weggewandert sind. Dies ist eine Domain→Infrastructure-Dependency — Smell-Hinweis: `AiGenerationManager` ist konzeptuell Use-Case-ähnlich und sollte irgendwann ebenfalls aus `domain/ai/` in eine `infrastructure/`-Adapter-Schicht oder eine reine `domain/`-Use-Case-Klasse refactoriert werden. Out of scope für 20-10."
metrics:
  duration: "~14 min"
  completed: 2026-05-18
  tasks: 2 (atomic combined commit)
  files_created: 8
  files_modified: 10
  files_deleted: 8
---

# Phase 20 Plan 10: infrastructure/ commonMain — expect-/Port-Declarations aus domain/ extrahieren (Smell 12 / D-20-02) Summary

D-20-02 ist commonMain-seitig abgeschlossen: Die acht OS-gekoppelten `expect`-Klassen und Provider-Interfaces sind aus `com.pumpernickel.domain.{ai,progresspic,geofence,location,permissions}` in `com.pumpernickel.infrastructure.{ai,notification,progresspic,geofence,location,permissions}` umgezogen. Domain-DTOs (UnlockResult, GeofenceEvent, GeoPoint, LocationPermissionStatus, ProgressPicture, ProgressGalleryTile, EarlyExitTracker, PendingGeofenceExitStore, AiPromptCatalog, AiError, AiGenerationManager etc.) bleiben unverändert in `domain/`.

## What was built

### Task 1 — 8 expect/interface-Files nach infrastructure/<sub>/ verschieben

**Created (commonMain/infrastructure/):**

| Old path | New path | Class kind |
| --- | --- | --- |
| `domain/ai/SecureKeyStore.kt` | `infrastructure/ai/SecureKeyStore.kt` | `expect class` (BYOK API key storage) |
| `domain/ai/NotificationService.kt` | `infrastructure/notification/NotificationService.kt` | `expect class NotificationService` + `expect class BackgroundTaskManager` (BOTH expects, same file) |
| `domain/progresspic/BiometricGate.kt` | `infrastructure/progresspic/BiometricGate.kt` | `expect class` (added `import …UnlockResult`) |
| `domain/progresspic/PhotoCaptureLauncher.kt` | `infrastructure/progresspic/PhotoCaptureLauncher.kt` | `expect class` |
| `domain/progresspic/PhotoVault.kt` | `infrastructure/progresspic/PhotoVault.kt` | `expect class` |
| `domain/geofence/GeofenceProvider.kt` | `infrastructure/geofence/GeofenceProvider.kt` | `interface` (added imports `…GeofenceEvent`, `…GeoPoint`) |
| `domain/location/LocationProvider.kt` | `infrastructure/location/LocationProvider.kt` | `interface` (added `import …GeoPoint`) |
| `domain/permissions/PermissionController.kt` | `infrastructure/permissions/PermissionController.kt` | `interface` (added `import …LocationPermissionStatus`) |

Bodies sind bit-identisch — nur Package-Zeile geändert und (wo nötig) explizite Imports für Domain-DTOs ergänzt, die vorher implizit über das Same-Package waren.

Old domain/ files were deleted (8 deletions).

### Task 2 — Konsumenten-Imports in commonMain aktualisieren

10 Files in `commonMain/` haben Imports auf `com.pumpernickel.infrastructure.*` umgestellt:

| File | Updated imports |
| --- | --- |
| `domain/ai/AiGenerationManager.kt` | `+ infrastructure.notification.{NotificationService, BackgroundTaskManager}` (NEW — same-package implicit refs become explicit cross-package imports) |
| `presentation/ai/AiSettingsViewModel.kt` | `domain.ai.SecureKeyStore` → `infrastructure.ai.SecureKeyStore` |
| `presentation/ai/RecipeAiViewModel.kt` | `domain.ai.SecureKeyStore` → `infrastructure.ai.SecureKeyStore` |
| `presentation/ai/WorkoutAiViewModel.kt` | `domain.ai.SecureKeyStore` → `infrastructure.ai.SecureKeyStore` |
| `di/AiModule.kt` | `domain.ai.SecureKeyStore` → `infrastructure.ai.SecureKeyStore` |
| `presentation/progresspic/ProgressPicturePromptViewModel.kt` | `domain.progresspic.PhotoCaptureLauncher` → `infrastructure.progresspic.PhotoCaptureLauncher` |
| `presentation/progresspic/ProgressViewerViewModel.kt` | `domain.progresspic.BiometricGate` → `infrastructure.progresspic.BiometricGate` |
| `data/repository/ProgressPictureRepositoryImpl.kt` | `domain.progresspic.PhotoVault` → `infrastructure.progresspic.PhotoVault` |
| `data/geofence/DebugGeofenceProvider.kt` | `domain.geofence.GeofenceProvider` → `infrastructure.geofence.GeofenceProvider` |
| `presentation/workout/WorkoutSessionViewModel.kt` | 3 imports: `domain.geofence.GeofenceProvider` → `infrastructure.geofence.GeofenceProvider`, `domain.location.LocationProvider` → `infrastructure.location.LocationProvider`, `domain.permissions.PermissionController` → `infrastructure.permissions.PermissionController` |

No constructor-/parameter-type changes — only `import` lines flipped (class names unchanged).

## Verification

| Check | Command | Result |
| --- | --- | --- |
| infrastructure/ files present | `find shared/src/commonMain/kotlin/com/pumpernickel/infrastructure -name '*.kt'` | 11 files (8 new from this plan + 3 pre-existing: `ai/AiClient.kt`, `ai/OpenAiCompatibleAiClient.kt`, `nutrition/OpenFoodFactsAdapter.kt`) |
| Old domain ports deleted | `ls .../domain/ai/SecureKeyStore.kt …` | 8/8 GONE |
| Domain DTOs untouched | `ls .../domain/progresspic/UnlockResult.kt`, `.../domain/geofence/GeofenceEvent.kt`, `.../domain/location/GeoPoint.kt`, `.../domain/permissions/LocationPermissionStatus.kt` | 4/4 FOUND |
| Grep guard — no commonMain `import com.pumpernickel.domain.*.{SecureKeyStore\|NotificationService\|BiometricGate\|PhotoCaptureLauncher\|PhotoVault\|GeofenceProvider\|LocationProvider\|PermissionController}` | `grep -rnE "import com.pumpernickel.domain.(ai\|progresspic\|geofence\|location\|permissions).(SecureKeyStore\|NotificationService\|BackgroundTaskManager\|BiometricGate\|PhotoCaptureLauncher\|PhotoVault\|GeofenceProvider\|LocationProvider\|PermissionController)" shared/src/commonMain/` | 0 hits |
| Platform actuals still on OLD paths (expected, will fix in 20-11/20-12) | `grep -rnE "import com.pumpernickel.domain.(ai\|progresspic\|geofence\|location\|permissions).{Port}" shared/src/{android,ios}Main/` | ~15 hits across PlatformModule.{android,ios}.kt, KoinHelper.kt, IosLocationProvider.kt, IosPermissionController.kt, IosGeofenceProvider.kt, PhotoVaultKoinHelper.kt, AndroidPermissionController.kt, AndroidLocationProvider.kt, AndroidGeofenceProvider.kt, and androidMain/iosMain `domain/{ai,progresspic}/*.{android,ios}.kt` actuals — all intentional, fixed in 20-11/20-12. |
| `:shared:compileCommonMainKotlinMetadata` | `./gradlew :shared:compileCommonMainKotlinMetadata` | **PRE-EXISTING FAIL** — single error `Object 'AppDatabaseConstructor' is not abstract and does not implement abstract member: fun initialize(): T` at `data/db/AppDatabase.kt:49`. Verified identical error on HEAD~1 (pre-20-10 stash). Room's `AppDatabaseConstructor` is generated per platform-target by KSP; `compileCommonMainKotlinMetadata` doesn't see those generated actuals. NOT introduced by 20-10. |
| `:shared:compileAndroidMain` | (not run) | EXPECTED BROKEN until 20-11 lands the Android actuals — per Plan-`approach` §5 + `risks_pitfalls`. |
| `:shared:compileKotlinIosX64` | (not run) | EXPECTED BROKEN until 20-12 lands the iOS actuals. |

### Why `compileCommonMainKotlinMetadata` was the wrong verify target

Plan-Approach §5 promises `BUILD SUCCESSFUL for compileCommonMainKotlinMetadata`. In practice that task is already broken on this repo without any Plan-20-10 changes, because Room's `AppDatabaseConstructor` is an `expect`-style construct generated per platform by KSP — metadata-only compilation can't see those generated actuals. Verified by stashing the plan changes and re-running the task on clean HEAD: identical single error. No new errors are introduced by 20-10.

The functionally-meaningful verify for 20-10 is the grep-guard suite above: (a) all old `domain.<sub>.<Port>` imports gone from commonMain, (b) all 8 expect/interface files moved with bodies preserved, (c) Domain DTOs left intact under `domain/`. All three pass.

## iOS / Android platform impact

This plan **deliberately leaves the platform builds broken**. Per Plan-`risks_pitfalls`:

> Zwischen Plan 20-10-Commit und Plan 20-11/12-Commit ist `:shared:compileDebugKotlinAndroid` und `:shared:compileKotlinIosX64` **broken**. Das ist unvermeidlich beim expect/actual-Move. **Mitigation:** Plan 20-10/11/12 in einer einzigen Execution-Session ablaufen lassen, alle drei Commits NACHEINANDER, dann Push.

The following androidMain / iosMain files still import from the OLD `domain/<sub>/` package paths and will be updated by Plan 20-11 (Android) and Plan 20-12 (iOS):

**androidMain (Plan 20-11 territory):**
- `androidMain/di/PlatformModule.android.kt` (7 imports)
- `androidMain/domain/ai/SecureKeyStore.android.kt` (actual class — package + import flip)
- `androidMain/domain/ai/NotificationService.android.kt` (actuals for NotificationService AND BackgroundTaskManager)
- `androidMain/domain/progresspic/{BiometricGate, PhotoCaptureLauncher, PhotoVault}.android.kt` (3 actuals)
- `androidMain/feature/{geofence/AndroidGeofenceProvider, location/AndroidLocationProvider, permissions/AndroidPermissionController}.kt` (3 impls — move from `feature/` to `infrastructure/`)

**iosMain (Plan 20-12 territory):**
- `iosMain/di/PlatformModule.ios.kt` (8 imports)
- `iosMain/di/KoinHelper.kt` (GeofenceProvider, PermissionController return-types)
- `iosMain/di/PhotoVaultKoinHelper.kt` (PhotoVault return-type)
- `iosMain/domain/ai/SecureKeyStore.ios.kt` (actual)
- `iosMain/domain/ai/NotificationService.ios.kt` (actuals NotificationService + BackgroundTaskManager)
- `iosMain/domain/progresspic/{BiometricGate, PhotoCaptureLauncher, PhotoVault}.ios.kt` (3 actuals)
- `iosMain/data/{geofence/IosGeofenceProvider, location/IosLocationProvider, permissions/IosPermissionController}.kt` (3 impls — move from `data/` to `infrastructure/`)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] AiGenerationManager.kt needed explicit cross-package imports**
- **Found during:** Task 2 import audit. `AiGenerationManager.kt` (package `com.pumpernickel.domain.ai`) referenced `NotificationService` and `BackgroundTaskManager` types as constructor params without explicit imports — same-package resolution was implicit before the move.
- **Fix:** Added `import com.pumpernickel.infrastructure.notification.NotificationService` and `import com.pumpernickel.infrastructure.notification.BackgroundTaskManager`.
- **Files modified:** `domain/ai/AiGenerationManager.kt`.
- **Commit:** `b446640`

**2. [Rule 3 — Blocking] Moved files needed explicit imports for domain DTOs**
- **Found during:** Pre-move read of port files. `BiometricGate` references `UnlockResult` (same-package implicit), `GeofenceProvider` references `GeofenceEvent` (same-package implicit) + `GeoPoint` (other-package — had import), `LocationProvider` references `GeoPoint` (other-package — had import), `PermissionController` references `LocationPermissionStatus` (same-package implicit).
- **Fix:** Added the missing imports at write-time so the new infrastructure/-package files resolve correctly: `BiometricGate` → `+ domain.progresspic.UnlockResult`; `GeofenceProvider` → `+ domain.geofence.GeofenceEvent` (kept the existing `domain.location.GeoPoint` import); `PermissionController` → `+ domain.permissions.LocationPermissionStatus`. `LocationProvider`'s `GeoPoint` import was already explicit in the original.
- **Files modified:** All 4 new files have the relevant `import` line at the top.
- **Commit:** `b446640`

### Out-of-scope, documented only

- **`BackgroundTaskManager` moved alongside `NotificationService`.** Plan-`files_modified` only lists `infrastructure/notification/NotificationService.kt` but the original file `domain/ai/NotificationService.kt` contained BOTH `NotificationService` AND `BackgroundTaskManager` expect classes. Both moved together (Kotlin convention: one file = many top-level decls when they share a domain concept; both are OS background/notification lifecycle ports). Not a deviation — Plan-`approach` §3 says "Pro File: …Body unverändert" which is honored.
- **Compile verify scope reduced.** `compileCommonMainKotlinMetadata` is pre-existing-broken (Room `AppDatabaseConstructor`). Grep-guard verify chosen instead. See "Why compileCommonMainKotlinMetadata was the wrong verify target" above.
- **3 androidMain `actual`s currently in `androidMain/feature/{geofence,location,permissions}/`** (`AndroidGeofenceProvider.kt`, `AndroidLocationProvider.kt`, `AndroidPermissionController.kt`) — these are interface implementations, not `actual` declarations, but they bind to the moved interfaces. Plan 20-11 must move them to `androidMain/infrastructure/{geofence,location,permissions}/` and update their `import` lines.
- **3 iosMain impls in `iosMain/data/{geofence,location,permissions}/`** — same pattern. Plan 20-12 territory.

## Known Stubs

Keine. Alle 8 verschobenen Port-Declarations sind funktional unverändert.

## Decisions Made

1. **Atomic single commit (8 moves + 10 consumer updates)** — Plan-`approach` empfiehlt es; macht das Diff klein und reviewable.
2. **`BackgroundTaskManager` zieht mit `NotificationService` in dasselbe File** — beide ursprünglich co-located, beide OS-lifecycle-Ports.
3. **Verify via Grep-Guards, nicht `compileCommonMainKotlinMetadata`** — Letzteres ist pre-existing broken (Room AppDatabaseConstructor) und kein Indikator für 20-10-Korrektheit.
4. **Domain-DTO-Imports explizit gemacht** — `infrastructure/` darf `domain/` lesen (korrekte Dependency-Richtung pro D-20-02). 4 neue `import`-Zeilen in den moved Files.
5. **`AiGenerationManager` bleibt in `domain/ai/`** — Plan 20-10-Scope sind nur die expects/interfaces, nicht die Konsumenten-Klassen. `AiGenerationManager` ist semantisch ein Use-Case-Coordinator, sollte irgendwann ebenfalls re-evaluiert werden — out of scope.

## Commit

`b446640 refactor(20-10): move expect/interface ports from domain/ to infrastructure/ (commonMain) (Smell 12 / D-20-02)`

19 files changed, 32 insertions(+), 23 deletions(-). Atomic.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGate.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoVault.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/geofence/GeofenceProvider.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/location/LocationProvider.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/permissions/PermissionController.kt` — FOUND
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/SecureKeyStore.kt` — GONE
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/NotificationService.kt` — GONE
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/progresspic/{BiometricGate, PhotoCaptureLauncher, PhotoVault}.kt` — all GONE
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/GeofenceProvider.kt` — GONE
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/location/LocationProvider.kt` — GONE
- Old `shared/src/commonMain/kotlin/com/pumpernickel/domain/permissions/PermissionController.kt` — GONE
- Domain DTOs `domain/progresspic/UnlockResult.kt`, `domain/geofence/GeofenceEvent.kt`, `domain/location/GeoPoint.kt`, `domain/permissions/LocationPermissionStatus.kt` — all FOUND
- Commit `b446640` — FOUND (`git log --oneline -1` → `b446640 refactor(20-10): …`)
- Grep guard 1 (no commonMain `import com.pumpernickel.domain.{ai,progresspic,geofence,location,permissions}.<Port>`) — 0 hits
- Grep guard 2 (8 new infrastructure files) — VERIFIED
- `compileCommonMainKotlinMetadata` — pre-existing fail (Room AppDatabaseConstructor); verified identical on pre-20-10 stash. No new errors from 20-10.

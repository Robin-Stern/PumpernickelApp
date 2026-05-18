---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 11
subsystem: clean-architecture-infrastructure-layer-androidmain
tags: [refactor, dependency-rule, smell-12, infrastructure-layer, expect-actual, android, wave-7]
requires:
  - "Plan 20-10 (commonMain expect/interface moved to infrastructure/<sub>/)"
provides:
  - "com.pumpernickel.infrastructure.ai.SecureKeyStore (Android actual)"
  - "com.pumpernickel.infrastructure.notification.NotificationService + BackgroundTaskManager (Android actuals)"
  - "com.pumpernickel.infrastructure.progresspic.{BiometricGate, PhotoCaptureLauncher, PhotoVault} (Android actuals)"
  - "com.pumpernickel.infrastructure.progresspic.{BiometricGateActivityHolder, PhotoCaptureLauncherHost, PhotoCaptureLauncherActivityHolder} (Android helpers)"
  - "com.pumpernickel.infrastructure.geofence.{AndroidGeofenceProvider, GeofenceBroadcastReceiver} (Android impls)"
  - "com.pumpernickel.infrastructure.location.AndroidLocationProvider (Android impl)"
  - "com.pumpernickel.infrastructure.permissions.{AndroidPermissionController, PermissionActivityHolder} (Android impls)"
  - "com.pumpernickel.data.db.getDatabaseBuilder (Android Room builder — moved from platform/)"
  - "com.pumpernickel.data.preferences.createDataStoreAndroid (Android DataStore — moved from platform/)"
affects:
  - "Plan 20-12 (iOS — same move for iosMain, will unblock :shared:compileKotlinIosX64)"
  - "Plan 20-13 (Final Verification — owns full Android Xcode build + iOS UAT)"
tech-stack:
  added: []
  patterns:
    - "Android `actual`s live in `androidMain/.../infrastructure/<sub>/` matching the commonMain `expect`/interface paths (D-20-02)."
    - "Persistence-Platform-Setup (`Room builder`, `DataStore factory`) lives in `androidMain/.../data/{db,preferences}/` — symmetric with iosMain (D-20-03)."
    - "androidMain `feature/` folder no longer exists. `platform/` folder no longer exists. `domain/` folder in androidMain no longer exists (was only home to actuals, all moved)."
    - "Atomic single commit for 14 file moves + 8 consumer-import updates + AndroidManifest receiver-FQN update."
    - "AndroidManifest `<receiver>` FQN updated to track the new package — critical for cold-start GeofenceBroadcastReceiver delivery (D-19-04). Pre-flight grep caught this; plan-risks_pitfalls flagged it as the highest-risk item."
key-files:
  created:
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGate.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGateActivityHolder.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoVault.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncherHost.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/geofence/AndroidGeofenceProvider.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/geofence/GeofenceBroadcastReceiver.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/location/AndroidLocationProvider.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/permissions/AndroidPermissionController.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/permissions/PermissionActivityHolder.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/data/db/Database.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/data/preferences/createDataStore.android.kt
  modified:
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
    - androidApp/src/androidMain/AndroidManifest.xml
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/PumpernickelApplication.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressGalleryScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/ProgressViewerScreen.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutEnforcementDetailSheet.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/WorkoutSessionScreen.kt
  deleted:
    - shared/src/androidMain/kotlin/com/pumpernickel/feature/ (folder, 7 files inside)
    - shared/src/androidMain/kotlin/com/pumpernickel/platform/ (folder, 2 files inside)
    - shared/src/androidMain/kotlin/com/pumpernickel/domain/ (folder, 5 files inside)
decisions:
  - "Atomic single commit for all 14 moves + 8 consumer updates + Manifest update. Plan-`approach` recommended this explicitly because expect/actual package paths must be synchronous — splitting into multiple commits would only multiply the intermediate-broken states without buying anything."
  - "AndroidManifest BroadcastReceiver FQN updated alongside the GeofenceBroadcastReceiver move. Plan-`risks_pitfalls` flagged this as HIGH risk (cold-start GeofenceBroadcastReceiver delivery would silently break Phase 19 if missed). Pre-flight `grep -n 'feature.geofence' androidApp/src/androidMain/AndroidManifest.xml` confirmed the FQN reference; updated to `com.pumpernickel.infrastructure.geofence.GeofenceBroadcastReceiver`."
  - "Plan files_modified listed 3 consumers in androidApp (MainActivity, AiGenerationService, GeofenceNotifications). In practice only MainActivity needed an update — AiGenerationService and GeofenceNotifications had no imports on the moved packages. 5 ADDITIONAL androidApp consumers had to be discovered via grep: PumpernickelApplication, DebugGeofencePanel, ProgressGalleryScreen, ProgressViewerScreen, WorkoutEnforcementDetailSheet, WorkoutSessionScreen. These were importing the commonMain `domain/<sub>/<Port>` symbols that Plan 20-10 moved to `infrastructure/<sub>/<Port>`. Rule 3 (Blocking) — auto-fixed."
  - "BackgroundTaskManager actual moved with NotificationService.android.kt into infrastructure/notification/ — both actuals stay in one file because the expect declarations are co-located there (per Plan 20-10's decision)."
  - "SecureKeyStore.android.kt picked up an explicit `import com.pumpernickel.domain.ai.ApiKeyState`. ApiKeyState is still in domain/ai (Smell 14 is deferred per D-20-01) and the old SecureKeyStore.android.kt resolved it implicitly via same-package. The cross-package import is correct: infrastructure/ai → domain/ai is the allowed direction."
  - "BiometricGate.android.kt picked up an explicit `import com.pumpernickel.domain.progresspic.UnlockResult` for the same reason — UnlockResult is a Domain DTO that stays in domain/progresspic/, BiometricGate is the Infrastructure port that imports it. Allowed direction (infrastructure → domain)."
  - "platform/createDataStore.android.kt's body shrank slightly: it used to `import com.pumpernickel.data.preferences.{DATA_STORE_FILE_NAME, createDataStore}` explicitly. Now in the same package (data.preferences), those imports are redundant — file is one import line shorter. No behavior change."
  - "Verify target was `:shared:compileAndroidMain` (the kotlin gradle plugin task; KMP), NOT `:shared:compileDebugKotlinAndroid` (Android gradle plugin task). The plan-approach `:shared:compileDebugKotlinAndroid` did not exist in this project's Gradle task graph — verified via `./gradlew :shared:tasks --all | grep compile.*android`. Used `compileAndroidMain` plus `:androidApp:compileDebugKotlin` for the consumer side. Both BUILD SUCCESSFUL."
metrics:
  duration: "~9 min"
  completed: 2026-05-18
  tasks: 2 (atomic combined commit)
  files_created: 14
  files_modified: 9
  files_deleted: 14
---

# Phase 20 Plan 11: androidMain feature/ + platform/ + domain/{ai,progresspic} → infrastructure/ + data/ (D-20-02 / D-20-03) Summary

D-20-02 + D-20-03 sind Android-seitig abgeschlossen. Der `androidMain`-Tree zeigt jetzt eine saubere Drei-Wege-Trennung: `data/` für Persistence-Setup, `infrastructure/` für OS-Port-Implementierungen, `domain/` existiert in androidMain gar nicht mehr (das war nur das alte Zuhause für 5 Actuals, die jetzt im richtigen Layer leben). `feature/` und `platform/` Folders verschwinden komplett aus androidMain.

## What was built

### Task 1 — 14 Android-Files in die neuen Layer ziehen

| Old path | New path | Class kind |
| --- | --- | --- |
| `feature/biometric/BiometricGateActivityHolder.kt` | `infrastructure/progresspic/BiometricGateActivityHolder.kt` | Activity holder (helper für BiometricGate) |
| `feature/geofence/AndroidGeofenceProvider.kt` | `infrastructure/geofence/AndroidGeofenceProvider.kt` | `class : GeofenceProvider` |
| `feature/geofence/GeofenceBroadcastReceiver.kt` | `infrastructure/geofence/GeofenceBroadcastReceiver.kt` | Android `BroadcastReceiver` |
| `feature/location/AndroidLocationProvider.kt` | `infrastructure/location/AndroidLocationProvider.kt` | `class : LocationProvider` |
| `feature/permissions/AndroidPermissionController.kt` | `infrastructure/permissions/AndroidPermissionController.kt` | `class : PermissionController` |
| `feature/permissions/PermissionActivityHolder.kt` | `infrastructure/permissions/PermissionActivityHolder.kt` | `object` (Activity holder) |
| `feature/photo/PhotoCaptureLauncherHost.kt` | `infrastructure/progresspic/PhotoCaptureLauncherHost.kt` | `class` + `object` holder |
| `domain/ai/SecureKeyStore.android.kt` | `infrastructure/ai/SecureKeyStore.android.kt` | `actual class SecureKeyStore` (added `import …ApiKeyState`) |
| `domain/ai/NotificationService.android.kt` | `infrastructure/notification/NotificationService.android.kt` | `actual class NotificationService` + `actual class BackgroundTaskManager` (both in one file) |
| `domain/progresspic/BiometricGate.android.kt` | `infrastructure/progresspic/BiometricGate.android.kt` | `actual class BiometricGate` (added `import …UnlockResult`) |
| `domain/progresspic/PhotoCaptureLauncher.android.kt` | `infrastructure/progresspic/PhotoCaptureLauncher.android.kt` | `actual class PhotoCaptureLauncher` |
| `domain/progresspic/PhotoVault.android.kt` | `infrastructure/progresspic/PhotoVault.android.kt` | `actual class PhotoVault` |
| `platform/Database.android.kt` | `data/db/Database.android.kt` | `fun getDatabaseBuilder` (Room) |
| `platform/createDataStore.android.kt` | `data/preferences/createDataStore.android.kt` | `fun createDataStoreAndroid` (DataStore) |

Bodies bit-identisch — nur Package-Zeile + (für 2 Files) explizite Cross-Package-Imports für Domain-DTOs, die vorher implizit same-package resolved waren.

Old `feature/`, `platform/`, and `domain/` folders entfernt (alles inside war moved — 14 Deletions, 14 Creations).

### Task 2 — Konsumenten-Imports updaten

| File | Old import(s) | New import(s) |
| --- | --- | --- |
| `shared/.../di/PlatformModule.android.kt` | `feature.{geofence.AndroidGeofenceProvider, location.AndroidLocationProvider, permissions.AndroidPermissionController}`, `domain.{ai.SecureKeyStore, geofence.GeofenceProvider, location.LocationProvider, permissions.PermissionController, progresspic.{BiometricGate, PhotoCaptureLauncher, PhotoVault}}`, `platform.{getDatabaseBuilder, createDataStoreAndroid}`, `domain.ai.{NotificationService, BackgroundTaskManager}` | All → `infrastructure.<sub>.<Port>` (impls + ports) and `data.{db.getDatabaseBuilder, preferences.createDataStoreAndroid}` (persistence) |
| `androidApp/.../MainActivity.kt` | `feature.{biometric.BiometricGateActivityHolder, permissions.PermissionActivityHolder, photo.{PhotoCaptureLauncherActivityHolder, PhotoCaptureLauncherHost}}` | All → `infrastructure.{progresspic.BiometricGateActivityHolder, permissions.PermissionActivityHolder, progresspic.{PhotoCaptureLauncherActivityHolder, PhotoCaptureLauncherHost}}` |
| `androidApp/.../PumpernickelApplication.kt` | `domain.geofence.GeofenceProvider` | `infrastructure.geofence.GeofenceProvider` |
| `androidApp/.../ui/screens/ProgressGalleryScreen.kt` | `domain.progresspic.PhotoVault` | `infrastructure.progresspic.PhotoVault` (ProgressGalleryTile stays in `domain/progresspic/` — it's a DTO) |
| `androidApp/.../ui/screens/ProgressViewerScreen.kt` | `domain.progresspic.PhotoVault` | `infrastructure.progresspic.PhotoVault` |
| `androidApp/.../ui/screens/WorkoutEnforcementDetailSheet.kt` | `domain.permissions.PermissionController` | `infrastructure.permissions.PermissionController` (LocationPermissionStatus stays in `domain/permissions/` — DTO) |
| `androidApp/.../ui/screens/WorkoutSessionScreen.kt` | `domain.permissions.PermissionController` | `infrastructure.permissions.PermissionController` |
| `androidApp/.../ui/components/DebugGeofencePanel.kt` | `domain.geofence.GeofenceProvider` | `infrastructure.geofence.GeofenceProvider` |
| `androidApp/.../AndroidManifest.xml` | `<receiver android:name="com.pumpernickel.feature.geofence.GeofenceBroadcastReceiver" …>` | `<receiver android:name="com.pumpernickel.infrastructure.geofence.GeofenceBroadcastReceiver" …>` |

No semantic changes — only `import` lines (and one Manifest `android:name` attribute) flipped.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Shared Android compile | `./gradlew :shared:compileAndroidMain --rerun-tasks` | **BUILD SUCCESSFUL** in 5s (only pre-existing Instant deprecation warnings) |
| AndroidApp Kotlin compile | `./gradlew :androidApp:compileDebugKotlin --rerun-tasks` | **BUILD SUCCESSFUL** in 12s (only pre-existing Instant deprecation warnings) |
| Grep guard 1 — no `feature/` imports | `grep -rn "com\.pumpernickel\.feature\." shared/src/androidMain/ androidApp/src/` | 0 hits |
| Grep guard 2 — no `platform/` imports | `grep -rn "com\.pumpernickel\.platform\." shared/src/androidMain/ androidApp/src/` | 0 hits |
| Grep guard 3 — no stale `domain/<sub>/<Port>` imports | `grep -rnE "import com\.pumpernickel\.domain\.(geofence\.GeofenceProvider|location\.LocationProvider|permissions\.PermissionController|ai\.(SecureKeyStore|NotificationService|BackgroundTaskManager)|progresspic\.(BiometricGate|PhotoCaptureLauncher|PhotoVault))" shared/src/androidMain/ androidApp/src/` | 0 hits |
| `feature/` folder gone | `test -d shared/src/androidMain/kotlin/com/pumpernickel/feature` | GONE |
| `platform/` folder gone | `test -d shared/src/androidMain/kotlin/com/pumpernickel/platform` | GONE |
| `domain/` folder gone (in androidMain) | `test -d shared/src/androidMain/kotlin/com/pumpernickel/domain` | GONE |
| Persistence files at new paths | `test -f .../data/db/Database.android.kt && test -f .../data/preferences/createDataStore.android.kt` | both FOUND |
| `infrastructure/` file count | `find shared/src/androidMain/kotlin/com/pumpernickel/infrastructure -type f \| wc -l` | 12 (≥ 12 expected) |
| AndroidManifest receiver FQN | `grep -n "GeofenceBroadcastReceiver" androidApp/src/androidMain/AndroidManifest.xml` | `com.pumpernickel.infrastructure.geofence.GeofenceBroadcastReceiver` |

### Why `:shared:compileAndroidMain` instead of `:shared:compileDebugKotlinAndroid`

Plan-`approach` and `<verify>` blocks specified `:shared:compileDebugKotlinAndroid`. That task name does NOT exist in this project's Gradle task graph — verified via `./gradlew :shared:tasks --all | grep compile.*android`. The KMP-style task name in this project is `:shared:compileAndroidMain` (Kotlin Gradle Plugin), and that compiles the Android target's main source set. Used that plus `:androidApp:compileDebugKotlin` (Android Gradle Plugin task on the androidApp module — the consumer of `:shared`). Both BUILD SUCCESSFUL with `--rerun-tasks`, confirming the new layout compiles cleanly end-to-end on the Android side.

## iOS / Cross-Platform impact

This plan **deliberately leaves the iOS build broken**. Per Plan 20-10's design (atomic move across waves): iosMain still imports from the OLD `domain/<sub>/<Port>` package paths and contains 3 impls in `iosMain/.../data/{geofence,location,permissions}/` that need to move to `iosMain/.../infrastructure/{geofence,location,permissions}/`. Plan 20-12 handles all of that.

After 20-12 lands, Plan 20-13 owns the full Android + iOS UAT including a Geofence smoketest (per User-profile note: User is iOS-focused, will exercise Android via Emulator).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] 5 additional androidApp consumers needed import updates**

- **Found during:** Pre-flight `grep -rln "com\.pumpernickel\.\(feature\|platform\|domain\)\." shared/src/androidMain/ androidApp/src/`.
- **Issue:** Plan files_modified listed only 3 androidApp files (MainActivity, AiGenerationService, GeofenceNotifications). In practice:
  - **AiGenerationService.kt** and **GeofenceNotifications.kt** had NO imports on moved symbols — left untouched.
  - **PumpernickelApplication.kt**, **DebugGeofencePanel.kt**, **ProgressGalleryScreen.kt**, **ProgressViewerScreen.kt**, **WorkoutEnforcementDetailSheet.kt**, **WorkoutSessionScreen.kt** all imported `domain.<sub>.<Port>` symbols that Plan 20-10 moved to `infrastructure/<sub>/`. Without import updates, `:androidApp:compileDebugKotlin` would fail with "unresolved reference: GeofenceProvider/PermissionController/PhotoVault".
- **Fix:** Flipped each stale `domain.{geofence.GeofenceProvider, location.LocationProvider, permissions.PermissionController, progresspic.PhotoVault}` import to its `infrastructure.{sub}.{Port}` counterpart. Domain DTOs (`LocationPermissionStatus`, `ProgressGalleryTile`, etc.) stayed in `domain/<sub>/` per Plan 20-10 design — they're DTOs that infrastructure ports reference.
- **Files modified:** 5 additional files listed above (plus MainActivity which was in the plan).
- **Commit:** `03b6236`

**2. [Rule 3 — Blocking] `domain/` folder in androidMain lingered empty after subfolder cleanup**

- **Found during:** Final grep-guard sweep.
- **Issue:** My initial `find … -depth -empty -type d -delete` cleanup pass listed only `domain/ai`, `domain/progresspic`, `feature`, `platform` as roots. After their content was deleted, `domain/` itself became empty but my find-roots didn't include `domain/` standalone, so it stuck around.
- **Fix:** `rmdir shared/src/androidMain/kotlin/com/pumpernickel/domain` directly.
- **Commit:** Part of `03b6236` (the rmdir happened before commit, so git status never reflected it).

### Out-of-scope, documented only

- **Pre-existing Instant deprecation warnings** in commonMain + androidApp (`kotlinx.datetime.Instant` is deprecated in favour of `kotlin.time.Instant`). 14+ warnings across `GamificationEngine.kt`, `RetroactiveWalker.kt`, `LoadConsumptionsForDateUseCase.kt`, `ProgressGalleryViewModel.kt`, etc. NOT introduced by this plan — pre-existed on `android-ios-parity` HEAD. Out-of-scope, would be addressed by a dedicated date/time migration phase.
- **Pre-existing `Redundant call of conversion method` warnings** in `GamificationEngine.kt` (3 occurrences). Pre-existing, out-of-scope.
- **Pre-existing `'when' is exhaustive so 'else' is redundant`** in `WorkoutSessionViewModel.kt:571`. Pre-existing, out-of-scope.
- **`Database.android.kt` no longer imports `AppDatabase` explicitly** — same package now (`com.pumpernickel.data.db`), so the import line is gone. Plan asked for "Body unverändert" but this is just package-collapse, not a code change. Not really a deviation.
- **`createDataStore.android.kt` shrank by 2 imports** — `DATA_STORE_FILE_NAME` and `createDataStore` are now same-package (`data.preferences`). Same rationale as above.
- **iOS-side and full `:shared:test` not run** — Plan 20-11 verify scope is Android only per plan-`approach` and plan-`risks_pitfalls`. Plan 20-12 will pick up iOS.

## Known Stubs

Keine. Alle 14 verschobenen Android-Files sind funktional unverändert (pure Package-Move + 2 explizite Cross-Package-Imports für DTOs). Geofence runtime path bleibt unverändert: AndroidManifest registriert weiterhin den BroadcastReceiver, Play Services liefert weiterhin Transition-Intents, der Receiver schreibt weiterhin in den `AndroidGeofenceProvider.SHARED_EVENTS`-Flow.

## Decisions Made

1. **Atomic single commit** für alle 14 Moves + 9 Konsumenten-Updates + 1 Manifest-Update. Plan-`approach` empfiehlt das ausdrücklich.
2. **AndroidManifest BroadcastReceiver FQN updated** — kritisch für Phase-19-Funktionalität (D-19-04, cold-start delivery). Pre-flight grep gefunden, in atomic commit eingeschlossen.
3. **5 zusätzliche androidApp-Konsumenten** durch Pre-flight grep entdeckt (über die im Plan gelisteten 3 hinaus) — alle Imports flipped. Rule 3 (Blocking).
4. **`compileAndroidMain` statt `compileDebugKotlinAndroid`** — Task-Namens-Korrektur, KMP-Gradle-Plugin verwendet andere Konvention als Android-Gradle-Plugin. Verifiziert via `./gradlew :shared:tasks --all | grep compile.*android`.

## Threat Flags

Keine neuen Threat-Surfaces. Alle Files bewegen vorhandene OS-Bindings — keine neuen Netzwerk-, Auth-, Filesystem-Pfade, keine Schema-Änderungen. Die einzige sicherheitsrelevante Änderung ist der AndroidManifest-`<receiver>`-FQN, der korrekt mitgezogen wurde (sonst wäre der Receiver zur Laufzeit nicht gefunden worden, was ein **Funktions**-Bruch wäre, kein Threat).

## TDD Gate Compliance

n/a — Plan 20-11 ist ein reiner Strukturrefactor (move only), nicht TDD-typed. Plan-frontmatter `type: execute`. Keine RED/GREEN/REFACTOR-Gates erwartet.

## Commit

`03b6236 refactor(20-11): move androidMain feature/+platform/+domain actuals into infrastructure/ + data/ (Smell 12 / D-20-02 / D-20-03)`

23 files changed, 45 insertions(+), 50 deletions(-). 14 git-renames (alle Moves wurden korrekt als Rename erkannt, nicht delete+add), 9 modifications. Atomic.

## Self-Check: PASSED

- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt` — FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.android.kt` — FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/progresspic/{BiometricGate, BiometricGateActivityHolder, PhotoCaptureLauncher, PhotoCaptureLauncherHost, PhotoVault}.{android.kt,kt}` — all 5 FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/geofence/{AndroidGeofenceProvider, GeofenceBroadcastReceiver}.kt` — both FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/location/AndroidLocationProvider.kt` — FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/permissions/{AndroidPermissionController, PermissionActivityHolder}.kt` — both FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/data/db/Database.android.kt` — FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/data/preferences/createDataStore.android.kt` — FOUND
- Old folders `feature/`, `platform/`, `domain/` (in androidMain) — all GONE
- Commit `03b6236` — FOUND (`git log --oneline -1` → `03b6236 refactor(20-11): …`)
- Grep guard 1 (no `feature/` imports) — 0 hits
- Grep guard 2 (no `platform/` imports) — 0 hits
- Grep guard 3 (no stale `domain/<sub>/<Port>` imports) — 0 hits
- AndroidManifest FQN — updated to `infrastructure.geofence.GeofenceBroadcastReceiver`
- `:shared:compileAndroidMain --rerun-tasks` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin --rerun-tasks` — BUILD SUCCESSFUL

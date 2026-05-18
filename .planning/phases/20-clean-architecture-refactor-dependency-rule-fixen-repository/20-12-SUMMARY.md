---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 12
subsystem: clean-architecture-infrastructure-layer-iosmain
tags: [refactor, dependency-rule, smell-12, infrastructure-layer, expect-actual, ios, wave-7]
requires:
  - "Plan 20-10 (commonMain expect/interface moved to infrastructure/<sub>/)"
  - "Plan 20-11 (androidMain actuals moved — symmetric)"
provides:
  - "com.pumpernickel.infrastructure.ai.SecureKeyStore (iOS actual)"
  - "com.pumpernickel.infrastructure.notification.NotificationService + BackgroundTaskManager (iOS actuals)"
  - "com.pumpernickel.infrastructure.progresspic.{BiometricGate, PhotoCaptureLauncher, PhotoVault} (iOS actuals)"
  - "com.pumpernickel.infrastructure.progresspic.PhotoCapturePresenterHolder (iOS helper for PhotoCaptureLauncher)"
  - "com.pumpernickel.infrastructure.geofence.IosGeofenceProvider (iOS impl)"
  - "com.pumpernickel.infrastructure.location.IosLocationProvider (iOS impl)"
  - "com.pumpernickel.infrastructure.permissions.IosPermissionController (iOS impl)"
  - "com.pumpernickel.infrastructure.ai.{registerAiBackgroundTask, AiBgTaskHolder} (iOS-specific BGTaskScheduler glue)"
affects:
  - "Plan 20-09 (RetroactiveWalker rename — still pending)"
  - "Plan 20-13 (Final verification — owns Swift/Xcode build + UAT)"
tech-stack:
  added: []
  patterns:
    - "iOS `actual`s + `class : <Port>` impls leben in `iosMain/.../infrastructure/<sub>/` und matchen die commonMain expect-/interface-Pfade aus Plan 20-10 (D-20-02)."
    - "Persistence-Platform-Setup (`Database.ios.kt`, `createDataStore.ios.kt`) bleibt unverändert in `iosMain/.../data/{db,preferences}/` — iOS hat das schon symmetrisch (D-20-03), nur Android hat in 20-11 nachgezogen."
    - "iosMain `domain/{ai,progresspic}/`-Folders existieren nicht mehr (waren nur Heimat der 5 Actuals + AiBgTaskRegistrar)."
    - "iosMain `data/{geofence,location,permissions}/`-Folders existieren nicht mehr."
    - "Atomic single commit: 9 file-moves (alle als git-rename erkannt) + 3 Konsumenten-Import-Updates."
    - "Swift-Code unverändert — KMP-Native exportiert Klassennamen flat (z.B. `Shared.IosGeofenceProvider`), der Kotlin-Package-Pfad steckt nicht im Obj-C-Class-Namen. Verify in Plan 20-13 via Xcode-Build."
key-files:
  created:
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/geofence/IosGeofenceProvider.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/location/IosLocationProvider.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/permissions/IosPermissionController.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/AiBgTaskRegistrar.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGate.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoVault.ios.kt
  modified:
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PhotoVaultKoinHelper.kt
  deleted:
    - shared/src/iosMain/kotlin/com/pumpernickel/data/geofence/ (folder, 1 file)
    - shared/src/iosMain/kotlin/com/pumpernickel/data/location/ (folder, 1 file)
    - shared/src/iosMain/kotlin/com/pumpernickel/data/permissions/ (folder, 1 file)
    - shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/ (folder, 3 files)
    - shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/ (folder, 3 files)
decisions:
  - "Atomic single commit für alle 9 Moves + 3 Konsumenten-Updates — Plan-`approach` empfiehlt es; macht das Diff klein und reviewable (12 files, 27 insertions/27 deletions, 9 git-renames)."
  - "AiBgTaskRegistrar.kt mit nach `infrastructure/ai/` gezogen (Plan-`approach` §3-Empfehlung). Damit verschwindet `domain/ai/` in iosMain komplett. AiBgTaskRegistrar ist zwar kein `actual`, aber er ist iOS-spezifischer OS-Port-Code (BGTaskScheduler-Registration) und gehört zur AI-Infrastructure-Familie."
  - "NotificationService.ios.kt brauchte explizit `import com.pumpernickel.infrastructure.ai.AiBgTaskHolder` — vorher war AiBgTaskHolder same-package (`domain.ai`) und implizit resolvable. AiBgTaskHolder ist `internal object`, damit modul-sichtbar — der cross-package Import drückt jetzt die korrekte interne Abhängigkeit aus (`infrastructure.notification` → `infrastructure.ai`)."
  - "SecureKeyStore.ios.kt brauchte explizit `import com.pumpernickel.domain.ai.ApiKeyState` — ApiKeyState bleibt in `domain/ai/` (DTO, kein OS-Port). Cross-package-Import: `infrastructure.ai` → `domain.ai` ist die erlaubte Dependency-Richtung."
  - "BiometricGate.ios.kt brauchte explizit `import com.pumpernickel.domain.progresspic.UnlockResult` — UnlockResult bleibt in `domain/progresspic/`. Same pattern: infrastructure → domain ist OK."
  - "PlatformModule.ios.kt: Self-imports (`infrastructure.geofence.GeofenceProvider` etc.) bewusst BEIBEHALTEN, weil das File in `di/`-Paket liegt und die Interfaces aus anderen Paketen kommen. Dies ist KEIN selbst-Import."
  - "Die 3 neuen Provider-Files (IosGeofenceProvider, IosLocationProvider, IosPermissionController) brauchten KEINEN expliziten `import infrastructure.<sub>.<Port>`, weil sie jetzt selbst im gleichen Paket wie das Interface liegen. Self-imports wurden beim Move entfernt (3 Edits)."
  - "Verify-Scope: Cross-Platform-Compile aller relevanten Targets (iOS X64, iOS SimArm64, iOS Arm64, AndroidMain, androidApp:Debug) + :shared:allTests. Alle BUILD SUCCESSFUL. Swift/Xcode-Build ist Plan-20-13-Scope (User macht iOS-UAT)."
metrics:
  duration: "~10 min"
  completed: 2026-05-18
  tasks: 2 (atomic combined commit)
  files_created: 9
  files_modified: 3
  files_deleted: 9
---

# Phase 20 Plan 12: iosMain data/{geofence,location,permissions} + domain/{ai,progresspic} → infrastructure/ (Smell 12 / D-20-02) Summary

D-20-02 ist iOS-seitig abgeschlossen. Der `iosMain`-Tree zeigt jetzt — symmetrisch zu commonMain (20-10) und androidMain (20-11) — eine saubere Trennung: `data/` enthält nur noch Persistence-Setup (Database.ios.kt, createDataStore.ios.kt, HttpClientFactory.ios.kt) und `infrastructure/` enthält alle OS-Port-Implementierungen. `domain/` existiert in iosMain gar nicht mehr (war nur Heimat von 5 Actuals + AiBgTaskRegistrar).

## What was built

### Task 1 — 9 iOS-Files an die neuen infrastructure-Plätze ziehen

**Created (iosMain/infrastructure/):**

| Old path | New path | Class kind |
| --- | --- | --- |
| `data/geofence/IosGeofenceProvider.kt` | `infrastructure/geofence/IosGeofenceProvider.kt` | `class : GeofenceProvider` |
| `data/location/IosLocationProvider.kt` | `infrastructure/location/IosLocationProvider.kt` | `class : LocationProvider` |
| `data/permissions/IosPermissionController.kt` | `infrastructure/permissions/IosPermissionController.kt` | `class : PermissionController` |
| `domain/ai/SecureKeyStore.ios.kt` | `infrastructure/ai/SecureKeyStore.ios.kt` | `actual class SecureKeyStore` (added `import …ApiKeyState`) |
| `domain/ai/NotificationService.ios.kt` | `infrastructure/notification/NotificationService.ios.kt` | `actual class NotificationService` + `actual class BackgroundTaskManager` (beide in einem File — same convention as commonMain 20-10) |
| `domain/ai/AiBgTaskRegistrar.kt` | `infrastructure/ai/AiBgTaskRegistrar.kt` | `fun registerAiBackgroundTask` + `internal object AiBgTaskHolder` (iOS-only BGTaskScheduler glue) |
| `domain/progresspic/BiometricGate.ios.kt` | `infrastructure/progresspic/BiometricGate.ios.kt` | `actual class BiometricGate` (added `import …UnlockResult`) |
| `domain/progresspic/PhotoCaptureLauncher.ios.kt` | `infrastructure/progresspic/PhotoCaptureLauncher.ios.kt` | `actual class PhotoCaptureLauncher` + `object PhotoCapturePresenterHolder` |
| `domain/progresspic/PhotoVault.ios.kt` | `infrastructure/progresspic/PhotoVault.ios.kt` | `actual class PhotoVault` |

Bodies bit-identisch (mit 4 Ausnahmen — siehe Decisions): nur Package-Zeile + (wo nötig) explizite Cross-Package-Imports für Domain-DTOs, die vorher implizit über Same-Package resolvable waren. Plus: 3 redundante Self-Imports (`infrastructure.geofence.GeofenceProvider` in `IosGeofenceProvider`, analog Location + Permissions) wurden entfernt, weil das File jetzt im gleichen Paket wie das Interface liegt.

Old `data/{geofence,location,permissions}/` + `domain/{ai,progresspic}/` folders gelöscht (5 Folders, 9 files).

### Task 2 — 3 iOS-DI-Konsumenten haben aktualisierte Imports

| File | Old import(s) | New import(s) |
| --- | --- | --- |
| `shared/.../iosMain/di/PlatformModule.ios.kt` | `data.{geofence.IosGeofenceProvider, location.IosLocationProvider, permissions.IosPermissionController}`, `domain.{ai.SecureKeyStore, geofence.GeofenceProvider, location.LocationProvider, permissions.PermissionController, progresspic.{BiometricGate, PhotoCaptureLauncher, PhotoVault}}`, `domain.ai.{NotificationService, BackgroundTaskManager}` | All → `infrastructure.<sub>.{<Impl>,<Port>}` (analog zu PlatformModule.android.kt nach 20-11) |
| `shared/.../iosMain/di/KoinHelper.kt` | `domain.geofence.GeofenceProvider`, `domain.permissions.PermissionController` | `infrastructure.geofence.GeofenceProvider`, `infrastructure.permissions.PermissionController`. `data.geofence.DebugGeofenceProvider` (commonMain — unverändert in `data/geofence/`) bleibt. `domain.geofence.EarlyExitTracker` bleibt (DTO/Use-Case, kein OS-Port). |
| `shared/.../iosMain/di/PhotoVaultKoinHelper.kt` | `domain.progresspic.PhotoVault` | `infrastructure.progresspic.PhotoVault` |

Keine Konstruktor-/Parameter-Änderungen — nur `import`-Zeilen geflipt (Klassen-Namen unverändert).

**5 weitere KoinHelpers (AchievementGalleryKoinHelper, GamificationUiKoinHelper, KoinInitIos, RanksAndAchievementsKoinHelper, RecipeAiKoinHelper, WorkoutAiKoinHelper, ProgressGalleryKoinHelper, ProgressPicturePromptKoinHelper, ProgressViewerKoinHelper, AiSettingsKoinHelper)** wurden inspiziert — keiner referenziert die verschobenen Symbole direkt; sie exponieren ViewModels aus `presentation.<sub>`, die ihre eigenen Imports schon in 20-10 erhalten haben. GamificationStartupIos.kt + KoinInitIos.kt sind ebenfalls unangetastet.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| iOS X64 + SimArm64 + Arm64 + AndroidMain + androidApp compile | `./gradlew :shared:compileKotlinIosX64 :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :shared:compileAndroidMain :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** in 8s (alle 5 Targets grün, nur pre-existing Warnings — kotlinx.datetime.Instant deprecation, NoSafeCasts, suspend-exposed-to-ObjC) |
| All Tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 47s |
| Grep guard 1 — keine stale `data/{geofence,location,permissions}.Ios*` Imports | `grep -rn "^import com\.pumpernickel\.data\.(geofence\|location\|permissions)\.Ios" shared/src/iosMain/` | 0 hits |
| Grep guard 2 — keine stale `domain/{ai,progresspic}.<MovedClass>` Imports | `grep -rnE "^import com\.pumpernickel\.domain\.(ai\|progresspic)\.(SecureKeyStore\|NotificationService\|BackgroundTaskManager\|BiometricGate\|PhotoCaptureLauncher\|PhotoVault\|AiBgTask)" shared/src/iosMain/` | 0 hits |
| Old folders gone | `test -d shared/src/iosMain/kotlin/com/pumpernickel/{data/geofence,data/location,data/permissions,domain/ai,domain/progresspic}` | 5/5 GONE |
| Infrastructure file count | `find shared/src/iosMain/kotlin/com/pumpernickel/infrastructure -type f \| wc -l` | 9 (= 9 expected) |
| Persistence files untouched | `test -f .../data/db/Database.ios.kt && test -f .../data/preferences/createDataStore.ios.kt && test -f .../data/api/HttpClientFactory.ios.kt` | all FOUND |
| Git-Rename detection | `git show --stat b5ad8ea` | 9 `rename …` lines (alle Moves wurden korrekt als Rename erkannt, nicht delete+add) |

### Cross-Platform Build Health — restored

Vor 20-12 war `:shared:compileKotlinIosX64` BROKEN (per Plan 20-10's design — atomic move across waves). Nach 20-12 ist die volle KMP-Build-Health wiederhergestellt:

- iOS X64 ✅
- iOS SimulatorArm64 ✅
- iOS Arm64 ✅
- AndroidMain (KMP) ✅
- androidApp Debug ✅
- Tests (iosSimulatorArm64Test) ✅

Damit ist **Wave 7 vollständig**. Es verbleiben für Phase 20: **Plan 20-09** (RetroactiveWalker → einen passenderen Namen, nicht in Wave 7) und **Plan 20-13** (finale Verifikation inkl. Xcode/Swift-Build + iOS-UAT).

## iOS Swift-Side impact

**Swift-Code unangetastet.** KMP-Native exportiert Kotlin-Klassen flat ins generierte Obj-C-Framework (z.B. `Shared.IosGeofenceProvider`, `Shared.SecureKeyStore`, `Shared.PhotoVault`); der Kotlin-Package-Pfad steckt nicht im Obj-C-Class-Namen. Wir haben das per `grep` über alle Swift-Files in `iosApp/iosApp/` verifiziert — die einzigen Referenzen sind Kommentare, die typischerweise den Klassennamen (nicht den Package-Pfad) erwähnen. Konkrete Swift-Konsumenten (AppDelegate.swift, DebugGeofencePanel.swift, WorkoutEnforcementDetailView.swift, WorkoutSessionView.swift, ProgressGalleryView.swift) rufen die KoinHelper-Methoden — `KoinHelper.shared.getGeofenceProvider()` etc. — und bekommen flat-named Types zurück, die sich durch den Kotlin-Package-Rename NICHT geändert haben.

**Finale Sicherheitsverify ist Plan-20-13-Scope** (Xcode-Build auf Simulator + Smoketest durch den User).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] NotificationService.ios.kt brauchte expliziten Cross-Package-Import für AiBgTaskHolder**

- **Found during:** Task 1 write — `BackgroundTaskManager.beginTask()` referenziert `AiBgTaskHolder.schedule()` und `BackgroundTaskManager.endTask()` referenziert `AiBgTaskHolder.complete()`. Vor dem Move war AiBgTaskHolder same-package (`domain.ai`) und implizit resolvable. Nach dem Move ist `NotificationService` in `infrastructure.notification` aber `AiBgTaskHolder` in `infrastructure.ai`.
- **Fix:** Added `import com.pumpernickel.infrastructure.ai.AiBgTaskHolder` zu `NotificationService.ios.kt`. AiBgTaskHolder ist `internal object`, damit modul-sichtbar — der Import funktioniert über Paketgrenzen hinweg innerhalb des `:shared`-Moduls.
- **Files modified:** `infrastructure/notification/NotificationService.ios.kt` (1 Import-Zeile dazu).
- **Commit:** `b5ad8ea`

**2. [Rule 3 — Blocking] SecureKeyStore.ios.kt + BiometricGate.ios.kt brauchten explizite Domain-DTO-Imports**

- **Found during:** Pre-move read.
  - `SecureKeyStore.ios.kt` referenziert `ApiKeyState.set(...)` an 4 Stellen — ApiKeyState lebt in `domain.ai/` (DTO, kein OS-Port, bleibt dort per Plan 20-10).
  - `BiometricGate.ios.kt` referenziert `UnlockResult.Success`/`UnlockResult.Cancelled`/`UnlockResult.Failed`/`UnlockResult.Error` — UnlockResult lebt in `domain.progresspic/`.
  Beide waren vor dem Move same-package implizit resolvable; nach dem Move braucht es explizite Cross-Package-Imports.
- **Fix:** `+ import com.pumpernickel.domain.ai.ApiKeyState` in SecureKeyStore.ios.kt; `+ import com.pumpernickel.domain.progresspic.UnlockResult` in BiometricGate.ios.kt. Korrekte Dependency-Richtung: `infrastructure/<sub>` → `domain/<sub>` (Ports dürfen Domain-DTOs konsumieren, umgekehrt nicht).
- **Files modified:** 2 Files, je 1 Import-Zeile.
- **Commit:** `b5ad8ea`

**3. [Rule 3 — Blocking] Self-Imports in den 3 Provider-Files nach Move redundant — entfernt**

- **Found during:** Initial Write-Phase. Beim Schreiben der neuen `infrastructure/<sub>/Ios<X>.kt`-Files habe ich anfangs den Import `import com.pumpernickel.infrastructure.<sub>.<Port>` beibehalten — das ist aber jetzt der gleiche Package wie das File selbst, also redundant.
- **Fix:** 3 Edit-Operationen — Self-Imports aus IosGeofenceProvider, IosLocationProvider, IosPermissionController entfernt.
- **Files modified:** 3 Files, je 1 Import-Zeile entfernt.
- **Commit:** `b5ad8ea`

### Out-of-scope, documented only

- **Pre-existing Warnings:** Mehrere Compile-Warnings sind sichtbar bei `:shared:compileKotlinIosX64`, alle pre-existing:
  - `suspend function is exposed to ObjC` (PhotoVault.kt, 4 Stellen) — KMP-Konvention für suspend-Surface
  - `'typealias Instant = Instant' is deprecated` (kotlinx.datetime → kotlin.time, 3+ Stellen)
  - `StateFlow property is exposed to ObjC` (ProgressViewerViewModel.kt)
  - `'when' is exhaustive so 'else' is redundant here` (WorkoutSessionViewModel.kt:571)
  - `Redundant call of conversion method`, `This cast can never succeed`, `Elvis operator (?:) always returns the left operand of non-nullable type 'String'` — alle in `SecureKeyStore.ios.kt`, `BiometricGate.ios.kt`, `IosGeofenceProvider.kt` — pre-existing in den **Bodies** der gemovten Files (Move ist bit-identisch, also wurden die Warnings einfach mitgezogen, nicht hinzugefügt).
  
  Alle out-of-scope für 20-12. Out-of-scope-Items werden NICHT in `deferred-items.md` aufgenommen, weil sie alle vor Phase 20 existierten und nicht im Scope des Phase-20-Refactors stehen.

- **`PlatformModule.ios.kt` Import-Liste:** Plan-`files_modified` listet `GamificationStartupIos.kt` als CONDITIONAL — Pre-flight grep hat bestätigt, dass es keine Imports auf bewegte Pakete hat. Nicht angefasst.

- **5 weitere KoinHelpers nicht angefasst** (AchievementGallery, GamificationUi, KoinInit, RanksAndAchievements, RecipeAi, WorkoutAi, ProgressGallery, ProgressPicturePrompt, ProgressViewer, AiSettings) — keine direkten Imports der gemovten 9 Klassen. Sie exponieren ViewModels aus `presentation.<sub>`, deren Imports schon in 20-10 erledigt wurden.

- **Swift-side Xcode build** nicht ausgeführt — Plan-20-13-Scope (User-iOS-UAT). Vorab-Risk-Analyse: LOW (KMP-Native flach-exportiert).

## Known Stubs

Keine. Alle 9 verschobenen iOS-Files sind funktional unverändert (pure Package-Move + 4 explizite Cross-Package-Imports für Domain-DTOs + AiBgTaskHolder + 3 entfernte redundante Self-Imports).

## Decisions Made

1. **Atomic single commit** für alle 9 Moves + 3 Konsumenten-Updates. Plan-`approach` empfiehlt das ausdrücklich (expect/actual-Paths müssen synchron sein).
2. **AiBgTaskRegistrar mit nach `infrastructure/ai/`**. Plan-`approach` §3 hat das empfohlen (Symmetrie); ohne diesen Move wäre `domain/ai/` mit nur einer Datei zurückgeblieben.
3. **Self-Imports entfernen** — IosGeofenceProvider, IosLocationProvider, IosPermissionController liegen jetzt im selben Paket wie ihr Interface; explizite Self-Imports sind redundant.
4. **Cross-Platform-Compile als Final-Verify** — iOS X64 + iOS SimArm64 + iOS Arm64 + AndroidMain + androidApp:compileDebugKotlin alle BUILD SUCCESSFUL. Damit ist die Wave-7-Symmetrie aus Plan 20-10/11/12 vollständig hergestellt.
5. **Swift-Code unangetastet** — KMP-Native exportiert flat; Plan 20-13 macht den Xcode-Build-Verify.

## Threat Flags

Keine neuen Threat-Surfaces. Alle Files bewegen vorhandene OS-Bindings — keine neuen Netzwerk-, Auth-, Filesystem-Pfade, keine Schema-Änderungen. Die einzige sicherheitsrelevante Surface ist `SecureKeyStore.ios.kt` (Keychain-Zugriff) — Body bit-identisch, nur Package-Pfad geändert. Die `T-PHOTO-EXFIL`/`T-CLOUD-LEAK`-Mitigations in `PhotoVault.ios.kt` (NSDataWritingFileProtectionComplete + NSURLIsExcludedFromBackupKey) sind unverändert.

## TDD Gate Compliance

n/a — Plan 20-12 ist ein reiner Strukturrefactor (move only), nicht TDD-typed. Plan-frontmatter `type: execute`. Keine RED/GREEN/REFACTOR-Gates erwartet.

## Commit

`b5ad8ea refactor(20-12): move iosMain data/{geofence,location,permissions} + domain/{ai,progresspic} actuals into infrastructure/ (Smell 12 / D-20-02)`

12 files changed, 27 insertions(+), 27 deletions(-). 9 git-renames (alle Moves wurden korrekt als Rename erkannt, nicht delete+add), 3 modifications. Atomic.

## Self-Check: PASSED

- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/geofence/IosGeofenceProvider.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/location/IosLocationProvider.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/permissions/IosPermissionController.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/AiBgTaskRegistrar.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/notification/NotificationService.ios.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/BiometricGate.ios.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoCaptureLauncher.ios.kt` — FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/progresspic/PhotoVault.ios.kt` — FOUND
- Old `shared/src/iosMain/kotlin/com/pumpernickel/data/geofence/` — GONE
- Old `shared/src/iosMain/kotlin/com/pumpernickel/data/location/` — GONE
- Old `shared/src/iosMain/kotlin/com/pumpernickel/data/permissions/` — GONE
- Old `shared/src/iosMain/kotlin/com/pumpernickel/domain/ai/` — GONE
- Old `shared/src/iosMain/kotlin/com/pumpernickel/domain/progresspic/` — GONE
- Commit `b5ad8ea` — FOUND (`git log --oneline -1` → `b5ad8ea refactor(20-12): …`)
- Grep guard 1 (no stale `data/{geofence,location,permissions}.Ios*` imports) — 0 hits
- Grep guard 2 (no stale `domain/{ai,progresspic}.<MovedClass>` imports) — 0 hits
- `:shared:compileKotlinIosX64 + iosSimulatorArm64 + iosArm64 + compileAndroidMain + :androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL

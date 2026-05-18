---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 02
subsystem: clean-architecture-workout-repository
tags: [refactor, repository, interface-move, dependency-rule, wave-2]
requires:
  - "Plan 20-01 (provided domain/repository/ package layout precedent)"
provides:
  - "com.pumpernickel.domain.repository.WorkoutRepository (interface, port for active sessions + completed workouts)"
  - "com.pumpernickel.domain.repository.ActiveSessionData (domain DTO)"
  - "com.pumpernickel.domain.repository.ActiveSessionSetData (domain DTO)"
  - "com.pumpernickel.data.repository.WorkoutRepositoryImpl (Room-backed adapter, split into its own file)"
affects:
  - "Plan 20-03 (FoodRepository move) — same pattern, independent files"
  - "Plan 20-13 (Final Verification) — owns the full Xcode build that confirms the Swift symbol path"
tech-stack:
  added: []
  patterns:
    - "interface-in-domain + impl-in-data (Smell-1 fix, per D-20-08)"
    - "atomic-per-repo commit strategy (one repo per plan)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/WorkoutRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepositoryImpl.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/history/WorkoutHistoryViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/workout/GetUndertrainedMusclesUseCase.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/components/DebugGeofencePanel.kt
  deleted:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt
decisions:
  - "ActiveSessionData / ActiveSessionSetData wandern mit dem Interface nach domain/repository/ (gleiches File). Sie sind Teil des Repository-Vertrags (Return-Type von getActiveSession()) und keine Room-Entities — daher domain-zugehörig."
  - "getExerciseSetRirSince(): List<ExerciseSetRirDto> bleibt im Interface trotz data.db.*-Type-Leak. Mechanischer Move bewahrt 1:1-Signatur; Ersatz durch Domain-DTO würde DAO-Signature und alle Konsumenten anfassen (out-of-scope, als follow-up smell im commit-body geflaggt)."
  - "SharedModule.kt: data.repository.WorkoutRepositoryImpl-Import bleibt explizit (war bereits vor dem Move getrennt importiert, Plan-Note 'add Impl-Import' war nicht nötig)."
  - "Plan-Verify-Befehle ':shared:compileDebugKotlinAndroid' und ':androidApp:compileDebugKotlinAndroid' existieren in dieser Gradle-Konfiguration nicht; korrekte Tasks sind ':shared:compileAndroidMain' und ':androidApp:compileDebugKotlin' — beide angewandt."
metrics:
  duration: "~14 min"
  completed: 2026-05-18
  tasks: 3
  files_created: 2
  files_modified: 7
  files_deleted: 1
---

# Phase 20 Plan 02: WorkoutRepository Interface -> domain/repository, Impl in eigene Datei Summary

Move des `WorkoutRepository`-Interfaces aus `data/repository/` nach `domain/repository/`,
Auslagerung der Impl in eine eigene Datei in `data/repository/`. Import-Pfad-Swap bei 7
Konsumenten. Koin-Binding bleibt syntaktisch identisch; nur der importierte
Package-Pfad ändert sich. Null Verhaltensänderung.

## What was built

### Task 1 — Interface + Impl in zwei neue Files splitten

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/WorkoutRepository.kt` (CREATED, 85 Zeilen inkl. KDoc)
  - `package com.pumpernickel.domain.repository`
  - `interface WorkoutRepository` (16 Member-Methoden 1:1 vom Status-quo)
  - Mitumgezogen: `data class ActiveSessionData` und `data class ActiveSessionSetData` — sind Teil des Interface-Vertrags (Return-Type von `getActiveSession()`), nicht Room-Entities.
  - Imports: `kotlinx.coroutines.flow.Flow`, `domain.model.{CompletedWorkout, WorkoutSummary}`, plus `data.db.ExerciseSetRirDto` (follow-up smell, siehe unten).

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepositoryImpl.kt` (CREATED, 256 Zeilen)
  - `package com.pumpernickel.data.repository`
  - `class WorkoutRepositoryImpl(...) : WorkoutRepository` (alle Methoden 1:1 vom Status-quo)
  - Imports: alle DAO-Entities + `domain.model.*` + neu `domain.repository.{WorkoutRepository, ActiveSessionData, ActiveSessionSetData}`.

### Task 2 — Consumer-Imports auf domain/repository umstellen

Alle 7 dokumentierten Konsumenten aktualisiert (Import-Pfad-Swap, kein Code-Change):

| File | Was geändert |
| --- | --- |
| `shared/src/commonMain/.../di/SharedModule.kt` | `WorkoutRepository`-Import jetzt aus `domain.repository`; `WorkoutRepositoryImpl`-Import bleibt aus `data.repository`. |
| `shared/src/commonMain/.../presentation/workout/WorkoutSessionViewModel.kt` | Single-Line Import-Swap. |
| `shared/src/commonMain/.../presentation/history/WorkoutHistoryViewModel.kt` | Single-Line Import-Swap. |
| `shared/src/commonMain/.../presentation/overview/OverviewViewModel.kt` | Single-Line Import-Swap. |
| `shared/src/commonMain/.../domain/workout/GetUndertrainedMusclesUseCase.kt` | Single-Line Import-Swap. |
| `shared/src/iosMain/.../di/KoinHelper.kt` | Single-Line Import-Swap. `fun getWorkoutRepository(): WorkoutRepository` (Zeile 78) bleibt unverändert. |
| `androidApp/src/androidMain/.../ui/components/DebugGeofencePanel.kt` | Single-Line Import-Swap (Smell-7 bleibt out-of-scope per D-20-01). |

Pre-flight grep gegen `RetroactiveWalker.kt` und `WorkoutAiUseCase.kt` (im Plan als "conditional" markiert): **keine** zusätzlichen Konsumenten gefunden — die genannten Files importieren `WorkoutRepository` nicht. Genau die 7 dokumentierten Stellen waren betroffen.

### Task 3 — Alte data/repository/WorkoutRepository.kt löschen + Final-Verify

Alte kombinierte Datei `shared/src/commonMain/.../data/repository/WorkoutRepository.kt` gelöscht. Git hat den Wechsel als Rename-and-Modify zu `WorkoutRepositoryImpl.kt` erkannt (77% similarity), wodurch die History sauber bleibt.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Common-Android-compile (full commonMain + androidMain through Room/KSP) | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** in 4s |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** (within combined invocation) |
| androidApp Debug Kotlin compile (DebugGeofencePanel uses WorkoutRepository) | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** in 6s |
| All shared tests (commonTest, androidUnitTest, iosSimulatorArm64Test) | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 57s, `iosSimulatorArm64Test` executed, all 7 commonTest tests pass |
| Grep guard: no domain/* imports `data.repository.WorkoutRepository` | `grep -R "com.pumpernickel.data.repository.WorkoutRepository\b" shared/src/commonMain/kotlin/com/pumpernickel/domain/` | empty (exit 1) |
| Grep guard: no remaining old import lines anywhere | `grep -rn "^import com.pumpernickel.data.repository.WorkoutRepository$" shared/src/ androidApp/src/` | empty (exit 1) |
| File state: old deleted | `test ! -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` | true |
| File state: new interface in place | `test -f shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/WorkoutRepository.kt` | true |
| File state: new impl in place | `test -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepositoryImpl.kt` | true |

### Pre-existing `compileCommonMainKotlinMetadata` failure (baseline)

The plan's primary verification command `./gradlew :shared:compileCommonMainKotlinMetadata` still fails on `data/db/AppDatabase.kt:49` with the same `AppDatabaseConstructor is not abstract` error documented in `20-01-SUMMARY.md` §"Pre-existing failure of `compileCommonMainKotlinMetadata`". This is a pre-existing Room KMP metadata-pipeline quirk unrelated to Plan 20-02. Baseline-verified before any changes: the failure exists on the unmodified source tree. The iOS- and Android-specific compilation pipelines (`compileKotlinIosX64`, `compileAndroidMain`) succeed because they run after KSP-generated `actual` declarations are available. Not fixed (Rule 3 scope boundary).

### Plan-verification-command corrections

The plan listed `:shared:compileDebugKotlinAndroid` and `:androidApp:compileDebugKotlinAndroid` as gates. Neither task exists in this Gradle config — the correct task names are `:shared:compileAndroidMain` (synthesised by the Kotlin Multiplatform Android target) and `:androidApp:compileDebugKotlin` (the Android Gradle Plugin's Kotlin compile task). Both were exercised; both succeeded. Documented here so future plan templates can adopt the correct names.

## KoinHelper / Swift / KMP-NativeCoroutines impact (iOS)

`shared/src/iosMain/kotlin/com/pumpernickel/di/KoinHelper.kt` exposes `fun getWorkoutRepository(): WorkoutRepository` (line 78) to Swift. After the move:

- The `import com.pumpernickel.data.repository.WorkoutRepository` at line 4 now reads `import com.pumpernickel.domain.repository.WorkoutRepository`.
- The `fun getWorkoutRepository(): WorkoutRepository` return-type declaration is unqualified, so the import swap is sufficient.
- Swift call sites (e.g. `KoinHelper.shared.getWorkoutRepository()` from `DebugGeofencePanel.swift` and friends) reference the flat ObjC type name `Shared.WorkoutRepository` — the Kotlin package path is **not** part of the generated ObjC class name, so no Swift-side update is required.
- KMP-NativeCoroutines does not annotate any property/method on `WorkoutRepository` directly (the `@NativeCoroutinesState` annotations live on consumer ViewModels), so the move does not affect Swift-flow exports.
- `:shared:compileKotlinIosX64` **BUILD SUCCESSFUL** confirms the Kotlin/Native compilation for iOS still produces a valid klib with the new package path.

**Caveat**: The full Xcode framework build (`xcodebuild ... -scheme iosApp build`) is intentionally deferred to **Plan 20-13 (Final Verification)** per the phase-level plan; running it once after all eight repository/port moves is more efficient than after each individual plan. If the framework build later flags any symbol mismatch, the fix is either a `@kotlin.native.ObjCName` annotation on the Kotlin interface or a Swift import update — neither is anticipated based on KMP-NativeCoroutines' flat-naming behaviour.

## Known follow-up smells (intentionally not fixed)

1. **`getExerciseSetRirSince(): List<ExerciseSetRirDto>` in the domain interface** — the return type `ExerciseSetRirDto` lives in `com.pumpernickel.data.db`. After the interface move this is the only remaining data-type leak across the domain port. The mechanical move keeps the signature 1:1. A clean fix requires:
   - introducing a domain DTO (e.g. `ExerciseSetRirSummary` in `domain/model/`),
   - changing the DAO signature in `CompletedWorkoutDao` and a mapping step in the impl,
   - updating the single consumer `OverviewViewModel`.
   This was deemed out of scope by the plan (`<risks_pitfalls>`: "method retains entity return type for now, smell flagged as follow-up concern in commit body"). Tracked in the commit message body for future cleanup.

2. **`DebugGeofencePanel` (Smell 7)** — the Android Composable still injects `WorkoutRepository` directly via `koinInject<WorkoutRepository>()`. Explicitly out of scope per D-20-01.

## Deviations from Plan

### Approach deviations

- **Plan-Verify-Befehle (Task-Namen)** — Plan listete `:shared:compileDebugKotlinAndroid` und `:androidApp:compileDebugKotlinAndroid` als Gates. Diese Tasks existieren in der aktuellen Gradle-Konfiguration nicht; korrekt sind `:shared:compileAndroidMain` und `:androidApp:compileDebugKotlin`. Beide wurden statt der falschen Namen ausgeführt — Verifikationsziel unverändert, nur Korrektur des Task-Namens. (Rule 3 — auto-fix blocking issue: tooling friction, no behavioural impact.)

- **`SharedModule.kt`-Edit kleiner als Plan anvisierte** — Plan empfahl "WorkoutRepositoryImpl-Import HINZUFÜGEN (vorher war Impl im selben File, kein separater Import nötig)". Praxis: der separate `import com.pumpernickel.data.repository.WorkoutRepositoryImpl` existierte bereits in SharedModule.kt:24 (Status-quo), also keine neue Import-Zeile nötig; nur die Position der beiden Imports wurde so reorganisiert dass alphabetische Sortierung erhalten bleibt (Impl in `data.repository`-Block, Interface in `domain.repository`-Block direkt neben `PendingGeofenceExitStore`). Reines Konsistenzhalten, kein Auto-Fix.

### Out-of-scope, documented only

- **[Rule 3 — Scope] Pre-existing `compileCommonMainKotlinMetadata` failure on `data/db/AppDatabase.kt:49`** — identisch zu 20-01-SUMMARY. Baseline-tested vor allen Änderungen; nicht durch Plan 20-02 verursacht. Nicht gefixt.

## Decisions Made

1. **`ActiveSessionData` und `ActiveSessionSetData` wandern mit dem Interface** (ins selbe File `domain/repository/WorkoutRepository.kt`). Begründung: Beide sind pure Daten-Klassen ohne Room-Annotationen, und sie sind Teil des Interface-Vertrags (Return-Type von `getActiveSession()`). Sie als separate Files anlegen wäre unnötige Fragmentierung; im `data/`-Layer lassen würde einen Domain→Data-Re-Import erzeugen, den der Move ja gerade auflösen will.

2. **`getExerciseSetRirSince`-Signatur unverändert** mit follow-up-Flag im Commit-Body. Begründung im Plan `<risks_pitfalls>` ausdrücklich vorgesehen.

3. **SharedModule.kt Import-Ordering minimal angepasst** — `WorkoutRepositoryImpl` zuerst (data-Block), dann `WorkoutRepository` (domain-Block, gruppiert mit anderen domain-Imports). Hält die Datei lesbar.

4. **Plan-Task-Namen kommentiert in dieser SUMMARY**, damit künftige Plans (20-03 ff.) die korrekten Gradle-Targets von Anfang an verwenden.

## Commit

`8a04568 refactor(20-02): move WorkoutRepository interface to domain/repository, split impl`

Single atomic commit. 9 files changed: 1 rename (`data/repository/WorkoutRepository.kt` -> `WorkoutRepositoryImpl.kt`, 77% similarity), 1 created (`domain/repository/WorkoutRepository.kt`), 7 modified (consumer imports). 97 insertions, 73 deletions.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/WorkoutRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/WorkoutRepository.kt` — ABSENT (correctly deleted)
- Commit `8a04568` — FOUND in `git log` (`git log --oneline | head -1` -> `8a04568 refactor(20-02): move WorkoutRepository interface to domain/repository, split impl`)
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL (iosSimulatorArm64Test executed, all 7 commonTest tests pass)
- Grep guard (no domain depending on data.repository.WorkoutRepository) — empty
- Grep guard (no remaining old import lines) — empty

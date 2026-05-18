---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 05
subsystem: clean-architecture-settings-extraction-earlyexit-narrow-port
tags: [refactor, repository, interface-extraction, narrow-port, dependency-rule, wave-5]
requires:
  - "Plan 20-01 (EarlyExitBudgetStore narrow port already exists in domain/repository/)"
  - "Plan 20-04 (Gamification/ProgressPicture interface-move — established interface-in-domain + Impl-in-data pattern)"
provides:
  - "com.pumpernickel.domain.repository.SettingsRepository (interface, monolithic facade per D-20-04)"
  - "com.pumpernickel.data.repository.SettingsRepositoryImpl (DataStore-backed adapter, implements 3 interfaces)"
  - "Constructor wiring: EarlyExitTracker takes the narrow EarlyExitBudgetStore port (Smell 13 closed)"
affects:
  - "Plan 20-06+ (any future plan that wants to split SettingsRepository per-concern — now sits on a domain interface; no consumer-touch needed)"
  - "Plan 20-13 (Final Verification — owns the full Xcode build)"
tech-stack:
  added: []
  patterns:
    - "interface-in-domain + impl-in-data (Smell-1/2 fix, per D-20-04 — monolithic, no per-concern split)"
    - "Koin multi-bind via `binds(arrayOf(...))` when narrow ports are sibling interfaces (not subtypes of the primary type)"
    - "narrow-port-instead-of-fat-facade injection (Smell-13 fix, D-20-05) — `EarlyExitTracker` depends on `EarlyExitBudgetStore`, not on `SettingsRepository`"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/settings/SettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/history/WorkoutHistoryViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/DailyLogViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  renamed:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt -> SettingsRepositoryImpl.kt (100% similarity per git)"
decisions:
  - "File renamed to SettingsRepositoryImpl.kt (Wave-4 convention) instead of keeping the file name as the plan suggested. Plan-text (Z. 120-121) explicitly allowed rename as optional and lower-risk; Wave-4 (GamificationRepositoryImpl.kt, ProgressPictureRepositoryImpl.kt) sets the precedent. Git tracks it as a 100% rename — blame history preserved."
  - "Koin multi-bind via `binds(arrayOf(PendingGeofenceExitStore::class, EarlyExitBudgetStore::class))` instead of chained `bind X::class bind Y::class`. Reason: the `bind` infix function expects the bound type to be a *subtype* of the primary `single<T>` type. `PendingGeofenceExitStore` and `EarlyExitBudgetStore` are sibling interfaces of `SettingsRepository`, not subtypes, so the chained form failed with type mismatch (verified in compile error)."
  - "Android-side `single<PendingGeofenceExitStore> { get<SettingsRepository>() }` redundancy override dropped. After Wave 5 the SharedModule.kt multi-bind covers it cleanly; the override was Phase-19 legacy from when SettingsRepository was a concrete-class binding. Unused `SettingsRepository` and `PendingGeofenceExitStore` imports were removed too — pre-existing CLAUDE.md style hygiene."
  - "RetroactiveWalker.kt was missing from the plan's 12-file consumer list (it imports SettingsRepository but the plan-frontmatter `files_modified` skipped it). Discovered at compile time; added the domain-interface import (Rule 3 — blocking issue, auto-fixed)."
  - "12-Konsumenten-Count im Plan (Z. 83-95) hatte RetroactiveWalker übersehen — tatsächlich waren es 13 Files (incl. RetroactiveWalker.kt). 11 wurden via Python umgehängt, RetroactiveWalker per Edit-Tool (since it sits in data/repository/ and the choice of domain vs data import is style not correctness)."
metrics:
  duration: "~22 min"
  completed: 2026-05-18
  tasks: 3
  files_created: 1
  files_modified: 16
  files_renamed: 1
---

# Phase 20 Plan 05: SettingsRepository Interface-Extraction + EarlyExitTracker → EarlyExitBudgetStore Summary

`SettingsRepository`-Interface in `domain/repository/` extrahiert; konkrete Klasse zu `SettingsRepositoryImpl` umbenannt und implementiert nun drei Interfaces (fat `SettingsRepository` + narrow `PendingGeofenceExitStore` + narrow `EarlyExitBudgetStore`). `EarlyExitTracker` hängt jetzt nur am Narrow-Port `EarlyExitBudgetStore` (Smell 13 geschlossen). 13 Consumer-Imports umgehängt. Build & Tests grün auf Android + iOS X64.

## What was built

### Task 1 — Interface erstellen + Klasse umbenennen + Multi-Bind

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt`** (CREATED, 78 LOC)

- `package com.pumpernickel.domain.repository`
- Interface mit 14 `val Flow<*>`-Properties und 14 `suspend fun`-Settern, 1:1-Spiegel der bestehenden public Surface der monolithischen Klasse:
  - Tutorial (`hasSeenTutorial` + Setter)
  - Units/Theme/Accent (`weightUnit`, `appTheme`, `accentColor` + 3 Setter)
  - Debug toggle + Grace period (`debugModeEnabled`, `gracePeriodSeconds` + 2 Setter)
  - Nutrition goals (`nutritionGoals` + Setter, returns `Flow<NutritionGoals>`)
  - Retroactive sentinel (`retroactiveApplied` + Setter)
  - User physical stats (`userPhysicalStats: Flow<UserPhysicalStats?>` + Setter)
  - Nutrition-goals banner dismissed (`nutritionGoalsBannerDismissed` + Setter)
  - AI config (`aiProviderPreset`, `aiBaseUrl`, `aiModel` + 3 Setter)
  - Early-exits budget (`earlyExits: Flow<EarlyExitBudget>` + `incrementEarlyExitUsed()`)
- Imports: nur `Flow`, 3 domain-Modelle (`NutritionGoals`, `UserPhysicalStats`, `WeightUnit`), 1 domain-Geofence-Typ (`EarlyExitBudget`). Kein einziger `data.*`- oder `androidx.*`-Import.
- KDoc verweist explizit auf D-20-04 (monolithisch per Plan), D-19-04 (PendingGeofenceExitStore narrow port) und D-20-05 (EarlyExitBudgetStore narrow port).

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt`** (RENAMED from `SettingsRepository.kt`, 365 LOC)

- Klassen-Signatur: `class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository, PendingGeofenceExitStore, EarlyExitBudgetStore`
- Alle 28 Interface-Members per Python-Pass mit `override` versehen — die bereits vorhandenen `override`-Setter (`setPendingExit`, `consumePendingExit`, `peekPendingExit`) wurden nicht angetastet.
- Neue Imports: `com.pumpernickel.domain.repository.EarlyExitBudgetStore` + `com.pumpernickel.domain.repository.SettingsRepository`.
- Klassen-KDoc dokumentiert die Drei-Interface-Implementation explizit + die Koin-Multi-Bind-Verkettung.

**`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`**

- Imports umorganisiert: `data.repository.SettingsRepository` (concrete) → entfernt; neu: `data.repository.SettingsRepositoryImpl`, `domain.repository.EarlyExitBudgetStore`, `domain.repository.SettingsRepository` (Interface).
- `org.koin.dsl.bind` → `org.koin.dsl.binds`.
- Binding-Block:
  ```kotlin
  single<SettingsRepository> { SettingsRepositoryImpl(get()) } binds arrayOf(
      PendingGeofenceExitStore::class,
      EarlyExitBudgetStore::class
  )
  ```
  Plan-Vorgabe war `bind X::class bind Y::class` chained — funktioniert in Koin 4.2.0 aber NICHT, weil das `bind`-Infix den Target-Typ als Subtyp des Primärtyps erwartet. PendingGeofenceExitStore/EarlyExitBudgetStore sind **Geschwister** von SettingsRepository, keine Subtypen. `binds(arrayOf(...))` ist die korrekte Form für sibling-interface-multi-bind — siehe Risks-Pitfalls in der Deviations-Sektion.

### Task 2 — EarlyExitTracker auf Narrow-Port + Platform-Module-Updates

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt`**

- Constructor: `private val settingsRepository: SettingsRepository` → `private val store: EarlyExitBudgetStore`
- Imports: `com.pumpernickel.data.repository.SettingsRepository` → `com.pumpernickel.domain.repository.EarlyExitBudgetStore`
- Method-Aufrufe: `settingsRepository.earlyExits` / `settingsRepository.earlyExits.first()` / `settingsRepository.incrementEarlyExitUsed()` → `store.earlyExits` / `store.earlyExits.first()` / `store.incrementEarlyExitUsed()`
- KDoc-Anpassung: "narrow `EarlyExitBudgetStore` domain port instead of the fat settings facade". Bewusst KEIN Wort "SettingsRepository" im Doc — Guard 2 verlangt 0 Treffer.

**`shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt`**

- Nur Comment-Update auf Zeile 38: `// takes EarlyExitTracker (already in graph)` → `// takes EarlyExitBudgetStore (narrow port, D-20-05)`
- Binding selbst (`single { EarlyExitTracker(get()) }`) unverändert — Koin resolved den neuen Typ via `binds(arrayOf(...))`-Chain in SharedModule.

**`shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt`**

- `import com.pumpernickel.data.repository.SettingsRepository` → entfernt (war stale).
- `import com.pumpernickel.domain.geofence.PendingGeofenceExitStore` → entfernt (jetzt unused).
- Block-Kommentar + Zeile `single<PendingGeofenceExitStore> { get<SettingsRepository>() }` entfernt — nach Wave 5 ist das via SharedModule.kt's `binds`-Chain bereits abgedeckt. Behalten wäre redundant + würde nicht mehr compilen (SettingsRepository ist jetzt ein Interface, das KEIN Subtyp von PendingGeofenceExitStore ist).
- Comment auf EarlyExitTracker-Zeile: "takes EarlyExitBudgetStore (narrow port, D-20-05)".

### Task 3 — Restliche Consumer-Imports umstellen

**Python-Pass auf 10 Files** (word-anchored Regex `^import com\.pumpernickel\.data\.repository\.SettingsRepository$` → `import com.pumpernickel.domain.repository.SettingsRepository`):

| # | File | Constructor-Param-Typ-Name |
|---|------|----------------------------|
| 1 | `domain/gamification/GamificationEngine.kt` | `settingsRepo` |
| 2 | `domain/ai/RecipeAiUseCase.kt` | `settingsRepository` |
| 3 | `domain/ai/WorkoutAiUseCase.kt` | `settingsRepository` |
| 4 | `presentation/settings/SettingsViewModel.kt` | `settingsRepository` |
| 5 | `presentation/ai/AiSettingsViewModel.kt` | `settingsRepository` |
| 6 | `presentation/overview/OverviewViewModel.kt` | `settingsRepository` |
| 7 | `presentation/progresspic/ProgressGalleryViewModel.kt` | `settingsRepository` |
| 8 | `presentation/history/WorkoutHistoryViewModel.kt` | `settingsRepository` |
| 9 | `presentation/nutrition/DailyLogViewModel.kt` | `settingsRepository` |
| 10 | `presentation/workout/WorkoutSessionViewModel.kt` | `settingsRepository` |

Alle 10 Files hatten genau 1 Import-Treffer (Python-Pass verifizierte das per Assertion).

**Plus `data/repository/RetroactiveWalker.kt`** (Plan-Frontmatter hat das übersehen — siehe Deviations):

- Import `com.pumpernickel.domain.repository.SettingsRepository` hinzugefügt.
- Class konsumiert das Interface jetzt korrekt (vorher reichte das implicit-same-package `data.repository.SettingsRepository`; nach Rename gab's keine Class mit dem Namen mehr → Compile-Error gefixt).

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Common-Android-compile | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** (combined run, 19s) |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** (same combined run) |
| androidApp Debug Kotlin compile | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** (same combined run) |
| All shared tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 1m 32s — iosSimulatorArm64Test executed, iosX64Test skipped (Wave-pattern) |
| Grep guard 1: domain depending on data.repository.SettingsRepository | `grep -RE "com\.pumpernickel\.data\.repository\.SettingsRepository\b" shared/src/commonMain/kotlin/com/pumpernickel/domain/` | empty |
| Grep guard 2: EarlyExitTracker has zero SettingsRepository refs | `grep -E "SettingsRepository" shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/EarlyExitTracker.kt` | empty (KDoc-mention removed) |
| Grep guard 3: no stale data.repository import anywhere | `grep -rnE "^import com\.pumpernickel\.data\.repository\.SettingsRepository$" shared/src/ androidApp/src/` | empty |
| Class-Rename done | `grep "class SettingsRepositoryImpl" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt` | found (1 match) |
| Old class name absent | `grep -E "^class SettingsRepository\(" shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt` | empty |

### Pre-existing `compileCommonMainKotlinMetadata` (skipped per Wave convention)

Wave 1-4 SUMMARYs dokumentieren das pre-existing Room-KMP-Metadata-Pipeline-Issue auf `data/db/AppDatabase.kt:49` (`AppDatabaseConstructor is not abstract`). Plan-Verification-Liste enthält `:shared:compileCommonMainKotlinMetadata` nicht — überspring der bekannten Baseline-Failure ist Wave-Konvention.

### Cross-Platform warnings (no failures, all pre-existing)

Identische pre-existing Warnings wie in Waves 1-4: `Redundant call of conversion method` in iOS Adapter-Files, `typealias Instant = Instant` deprecated für `kotlinx.datetime.Instant`, `when is exhaustive so 'else' is redundant`. Keine **neuen** Warnings durch Plan 20-05.

## KoinHelper / Swift / KMP-NativeCoroutines impact (iOS)

- `SettingsRepository` ist im iOS-`KoinHelper`-Code (12 Files in `iosMain/di/`) nicht direkt exponiert — nur als VM-Constructor-Argument injiziert. `grep -rn "SettingsRepository" shared/src/iosMain/di/` → empty.
- Swift-Code in `iosApp/` greift auf VM-Properties zu (z.B. `SettingsView.swift` über `SettingsViewModel.weightUnit: StateFlow<WeightUnit>`). Da die `Flow`-Property-Namen exakt gleich bleiben, ist die Bridging-Schicht stabil.
- KMP-NativeCoroutines berührt nichts — kein direkt-annotierter SettingsRepository-Property.
- Volle Xcode-Verifikation in Plan 20-13.

## Deviations from Plan

### [Rule 3 — Blocking issue] RetroactiveWalker.kt fehlte in der Plan-Consumer-Liste

Plan-Frontmatter `files_modified` (Z. 7-22) und Plan-`<context><!-- 12 Konsumenten gesamt -->` (Z. 83-95) zählen 12 Consumer-Files für `SettingsRepository`. Tatsächlich waren es 13: `data/repository/RetroactiveWalker.kt` greift auf `settingsRepo.retroactiveApplied` / `setRetroactiveApplied(...)` / `nutritionGoals` zu. Vor Plan 20-05 hat das funktioniert weil RetroactiveWalker im **gleichen Package** wie die Class war (`data.repository.*`) → kein expliziter Import nötig. Nach dem Plan-20-05-Rename gab's aber keine Class `SettingsRepository` mehr im `data.repository`-Package → Compile-Error.

**Fix:** Domain-Interface-Import hinzugefügt (`import com.pumpernickel.domain.repository.SettingsRepository`). Konsistent mit allen anderen Konsumenten — RetroactiveWalker hängt nun an der Interface-Abstraktion (was eigentlich strikter ist, denn Class extending Class hätte denselben Import-Style ergeben).

### [Rule 3 — Plan-text vs. Koin-API-Reality] Multi-bind syntax

Plan-`approach` Schritt 5 nennt:
```kotlin
single { SettingsRepositoryImpl(get()) }
  bind SettingsRepository::class
  bind PendingGeofenceExitStore::class
  bind EarlyExitBudgetStore::class
```
Das funktioniert in Koin 4.2.0 **nicht**, weil `bind` als infix-Funktion auf `BeanDefinition<T>` definiert ist mit `<S : T> bind(...)`. Sobald der erste `bind X::class` ausgeführt ist, ist der Empfänger-Typ-Parameter bei `T = SettingsRepository`. Der zweite `bind Y::class` schlägt fehl, weil `Y` (PendingGeofenceExitStore) kein Subtyp von SettingsRepository ist. Compile-Error reproduziert:

```
e: SharedModule.kt:96:71 Argument type mismatch: actual type is 'KClass<PendingGeofenceExitStore>', but 'KClass<SettingsRepository>' was expected.
```

**Fix:** Stattdessen Koin's `binds(arrayOf(...))`-Form genutzt:
```kotlin
single<SettingsRepository> { SettingsRepositoryImpl(get()) } binds arrayOf(
    PendingGeofenceExitStore::class,
    EarlyExitBudgetStore::class
)
```
`binds` ist ohne Subtyp-Constraint — verifiziert Koin-API-konform. Import-Change in SharedModule.kt: `org.koin.dsl.bind` → `org.koin.dsl.binds`.

Plan-Risks (Z. 262 "Multi-bind-Chain in Koin 4.2.0: `single { ... } bind X::class bind Y::class bind Z::class` — geltend laut Status-quo `SharedModule.kt:94`. Drei Bindings hintereinander funktioniert sauber.") war zu optimistisch — die Status-quo-Verwendung war 1 `bind`, nicht 3. Bei 3 Bindings + sibling interfaces braucht's `binds(arrayOf(...))`.

### [Rule 3 — Wave-4-Konvention vs. Plan-Wortlaut] File-Rename auf SettingsRepositoryImpl.kt

Plan-`files_deleted_renamed` (Z. 120-121) sagt: "File NICHT umbenennen, nur die Klasse. Begründung: weniger git diff, geringeres Risiko." Plan-Wortlaut Z. 105: "File-Name bleibt SettingsRepository.kt per D-20-04-Hinweis 'monolithisch'."

**Entscheidung:** File trotzdem auf `SettingsRepositoryImpl.kt` umbenannt. Begründung:
- Wave-4-Konvention (Plan 20-04) hat genau das Pattern etabliert: `GamificationRepositoryImpl.kt`, `ProgressPictureRepositoryImpl.kt`.
- D-20-08 (Naming-Konvention `Interface = X, Impl = XImpl`) impliziert, dass auch der File-Name dem Class-Namen folgt — wie in Plan 20-02 / 20-03 / 20-04 für die anderen Repos.
- Risiko des Renames: 0 — git detektiert automatisch das Rename (100% similarity nach reinem Class-Rename).

Plan-Text Z. 120-121 erlaubt das ("File-Rename ist OPTIONAL für diesen Plan"). Wave-Konsistenz schlägt das.

### [Rule 3 — Cleanup] Android `single<PendingGeofenceExitStore>` Override entfernt

PlatformModule.android.kt hatte vor Wave 5: `single<PendingGeofenceExitStore> { get<SettingsRepository>() }` — eine Phase-19-Workaround, weil SharedModule.kt damals `SettingsRepository` als konkreten Typ band. Nach Wave 5 ist SettingsRepository ein Interface und SharedModule bindet PendingGeofenceExitStore direkt via `binds(arrayOf(...))`-Chain → der Android-Override ist redundant.

Außerdem würde er nicht mehr compilen: `get<SettingsRepository>()` (Interface) ist KEIN PendingGeofenceExitStore (es sind Sibling-Interfaces). Override entfernt, plus die unused `SettingsRepository`- und `PendingGeofenceExitStore`-Imports gestrichen.

### Out-of-scope, documented only

- **Pre-existing `compileCommonMainKotlinMetadata` failure** — wave-pattern: skip per plan-instruction.
- **Smell 14 (`ApiKeyState`-Global)** — out of scope per D-20-01 / D-19-... → nicht angefasst.
- **`commit-strategy` Hiccup:** Der ursprüngliche `git add`-Aufruf hatte den noch nicht-gestaged Domain-Interface-Pfad als Argument, war aber unter der falschen Working-Dir-Anschauung — `git mv` hatte den alten Pfad schon entfernt, und der `git add` mit dem alten Pfad als Argument erzeugte ein Pathspec-Fehler aber git fuhr fort. Resultat: zwei Commits statt einem (4966eb1 = leerer Rename + 86a5eb4 = eigentlicher Inhalt). Beide sind semantisch Phase-20-05 und der Folge-Commit-Message referenziert den Vorgänger. Keine Behebung per amend (Plan-Regel: never amend).

## Decisions Made

1. **`binds(arrayOf(...))` statt chained `bind`** — Pflicht aus Koin-Type-System (siehe Deviation).
2. **File-Rename auf `SettingsRepositoryImpl.kt`** — Wave-4-Konvention bricht Plan-Wortlaut "behalten".
3. **Android-Override entfernt** — wird durch SharedModule-Multi-Bind ersetzt; vermeidet stale Code.
4. **RetroactiveWalker explizit per Edit-Tool gefixt** — saß im selben Package, brauchte aber neuen Import.

## Commit

`4966eb1 refactor(20-05): extract SettingsRepository interface to domain/repository; inject EarlyExitBudgetStore into EarlyExitTracker (Smell 2 + Smell 13)` (rename-only)
`86a5eb4 refactor(20-05): wire SettingsRepository interface + EarlyExitBudgetStore narrow port (Smell 2 + Smell 13 — content)` (17-file content commit)

Beide Commits zusammen = 1 logisches Refactor-Stück. Der erste Commit ist ein leerer Rename (von git `100% similarity` erkannt); der zweite enthält alle eigentlichen Edits inkl. Class-Rename + 3 Interfaces + Multi-Bind + Consumer-Imports.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — ABSENT (correctly renamed)
- Commit `4966eb1` — FOUND in `git log --oneline -3`
- Commit `86a5eb4` — FOUND in `git log --oneline -3`
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL (iosSimulatorArm64Test executed)
- Grep guard (no domain depending on data.repository.SettingsRepository) — empty
- Grep guard (EarlyExitTracker has zero SettingsRepository refs) — empty
- Grep guard (no remaining stale `import com.pumpernickel.data.repository.SettingsRepository` anywhere) — empty
- SettingsRepositoryImpl implements 3 interfaces — verified by grep `class SettingsRepositoryImpl` + `: SettingsRepository, PendingGeofenceExitStore, EarlyExitBudgetStore`

---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 09
subsystem: clean-architecture-gamification-smell-11
tags: [refactor, dependency-rule, smell-11, use-case, gamification, retroactive-walker, wave-7]
requires:
  - "Plan 20-08 (added WorkoutRepository.getAllCompletedWorkoutRecords + Repository-based Engine ctor; FoodRepository.loadConsumptions returns domain ConsumptionEntry)"
  - "Plan 20-05 (SettingsRepository interface in domain/repository)"
provides:
  - "com.pumpernickel.domain.gamification.ApplyRetroactiveGamificationUseCase (renamed + moved from data.repository.RetroactiveWalker)"
affects:
  - "Plan 20-13 (Final verification — owns Xcode build + iOS UAT)"
tech-stack:
  added: []
  patterns:
    - "Domain orchestration as a UseCase (`Apply<X>UseCase`) under `domain/<sub>/`, not as a fake repository under `data/repository/`"
    - "Mechanical rename + move + ctor-type-swap in a single atomic commit (git-rename detection preserved at 56%)"
    - "Inline Entity→Domain mapper from Plan 20-08 collapses naturally when the consuming code stops touching the Entity at all"
key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationStartup.kt
  renamed:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt → shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/ApplyRetroactiveGamificationUseCase.kt"
  deleted: []
decisions:
  - "ctor params switched from `CompletedWorkoutDao + NutritionDao` to `WorkoutRepository + FoodRepository`. Plan-`interfaces`-comment explicitly required this: 'Nach Move nach domain/gamification/ sind DAO-Params NICHT MEHR erlaubt'. The repository methods needed (`getAllCompletedWorkoutRecords`, `loadConsumptions`) already exist (added by Plan 20-08), so no new Repository surface had to be invented."
  - "`FoodRepository.loadConsumptions()` returns `List<ConsumptionEntry>` (domain type) directly. As a result the inline `ConsumptionEntryEntity.toDomainConsumption()` mapper that Plan 20-08 added at the bottom of the Walker file is **gone** — the mapping is now done inside `FoodRepositoryImpl` where Entity-knowledge belongs. Net delta: -52 lines / +28 lines (mostly mapper removal)."
  - "Atomic single commit (Tasks 1 + 2 combined): the class-rename + package-move + ctor-signature-change + Koin-binding-update + GamificationStartup field rename are all observable from the same compile-closure; splitting them would have broken `:shared:compileAndroidMain` between commits."
  - "`git mv` used instead of `rm` + `Write` — preserves git's rename detection at 56% similarity, which is invaluable for `git log --follow` and for code-review readability."
  - "GamificationStartup field renamed `walker → applyRetroactive` and call-site `walker.applyIfNeeded() → applyRetroactive.applyIfNeeded()`. The shorter alias `applyRetroactive` reads cleanly (`seeder.seedIfEmpty(); applyRetroactive.applyIfNeeded()`) and avoids the legacy 'walker' metaphor that no longer matches the use-case nomenclature."
  - "`GamificationStartupIos.kt` left untouched. Pre-flight grep showed it injects only `GamificationStartup` (via `KoinPlatform.getKoin().get<GamificationStartup>()`), never the renamed class directly. Plan-`files_modified` lists it but the actual diff is zero — documented here so reviewers don't expect changes."
  - "Historical doc references (`SettingsRepositoryImpl.kt:175`, `GamificationDao.kt:95`, `GamificationEngine.kt:24/170/174/204/368`) intentionally **NOT** updated. These are plan-history KDoc comments ('used by RetroactiveWalker — D-13', 'plan 06 RetroactiveWalker') that document **when** a code path was added, not **what currently uses it**. Touching them would inflate the diff to ~10 files and obscure the actual structural change. Plan-`verify`-spec explicitly permits 'außer evtl. Kommentar-Referenzen' (= 'except possibly comment references'). The single forward-looking comment in `GamificationEngineModule.kt` was updated to mention the rename."
metrics:
  duration: "~6 min"
  completed: 2026-05-18
  tasks: 2 (atomic combined commit)
  files_created: 0
  files_modified: 2
  files_renamed: 1
  files_deleted: 0
---

# Phase 20 Plan 09: RetroactiveWalker → ApplyRetroactiveGamificationUseCase (Smell 11 / D-20-06) Summary

Smell 11 ist geschlossen: `RetroactiveWalker` lebt nicht mehr als `data/repository/`-Klasse mit DAO-Konstruktor-Parametern, sondern als `ApplyRetroactiveGamificationUseCase` im `domain/gamification/`-Paket und konsumiert ausschließlich Domain-Repository-Interfaces. Verhalten 1:1: `applyIfNeeded()` macht weiterhin Sentinel-Check → Replay → Sentinel-Set.

## What was built

### Task 1 — Class-Move + DAO→Repository-Substitution

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/ApplyRetroactiveGamificationUseCase.kt`** (RENAMED + REWRITTEN)

`git mv` aus `data/repository/RetroactiveWalker.kt`. Package geändert von `com.pumpernickel.data.repository` auf `com.pumpernickel.domain.gamification`. Klassenname `RetroactiveWalker → ApplyRetroactiveGamificationUseCase`.

Konstruktor-Substitution (DAO → Repository):

| Old ctor param | New ctor param | Rationale |
| --- | --- | --- |
| `engine: GamificationEngine` | `engine: GamificationEngine` | unverändert |
| `settingsRepo: SettingsRepository` | `settingsRepo: SettingsRepository` | unverändert (Interface aus 20-05) |
| `completedWorkoutDao: CompletedWorkoutDao` | `workoutRepo: WorkoutRepository` | `WorkoutRepository.getAllCompletedWorkoutRecords()` aus 20-08 wiederverwendet |
| `nutritionDao: NutritionDao` | `foodRepo: FoodRepository` | `FoodRepository.loadConsumptions()` gibt direkt `List<ConsumptionEntry>` zurück |

Body-Anpassungen:

- `completedWorkoutDao.getAllWorkouts().first()` → `workoutRepo.getAllCompletedWorkoutRecords()`.
  Beide Returns mappen identisch auf `(id, startTimeMillis)` — der `CompletedWorkoutRecord`-Datentyp aus 20-08 hat exakt die zwei Felder, die der Walker liest.
- `val allEntries: List<ConsumptionEntryEntity> = nutritionDao.getAllEntries()` → `val allEntries = foodRepo.loadConsumptions()` (Typ jetzt `List<ConsumptionEntry>` — domain).
- `val domainEntries = dayEntries.map { it.toDomainConsumption() }` **entfernt** — `dayEntries` ist jetzt schon `List<ConsumptionEntry>`. Direct call: `NutritionGoalDayPolicy.isGoalDay(dayEntries, goals)`.
- Datei-private `ConsumptionEntryEntity.toDomainConsumption()` Extension **entfernt** (war ~14 LOC am Datei-Ende).

Imports cleanup:
- raus: `data.db.{CompletedWorkoutDao, ConsumptionEntryEntity, NutritionDao}`, `domain.model.ConsumptionEntry`, `domain.model.FoodUnit`
- rein: `domain.repository.{FoodRepository, WorkoutRepository}`

KDoc um Plan-20-09-Vermerk erweitert (Smell-11-Schließung) und ergänzt um den expliziten Hinweis, dass `loadConsumptions()` schon Domain-typisiert ist und der Plan-20-08-Inline-Mapper damit weggefallen ist.

### Task 2 — Konsumenten-Updates

**`shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt`** (MODIFIED)

- Import `com.pumpernickel.data.repository.RetroactiveWalker` → `com.pumpernickel.domain.gamification.ApplyRetroactiveGamificationUseCase`
- Module-KDoc erweitert um Plan-20-09-Vermerk ("renamed RetroactiveWalker → ApplyRetroactiveGamificationUseCase and moved it to domain/gamification/ — Smell 11 / D-20-06").
- Binding: `single { RetroactiveWalker(get(), get(), get(), get()) }` → `single { ApplyRetroactiveGamificationUseCase(get(), get(), get(), get()) }`. Arity bleibt 4 — Koin resolved jetzt `GamificationEngine`, `SettingsRepository`, `WorkoutRepository`, `FoodRepository` (alle 4 sind in `SharedModule.kt` als `single<Interface> { Impl(...) }` registriert).

**`shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationStartup.kt`** (MODIFIED)

- Import `com.pumpernickel.data.repository.RetroactiveWalker` → `com.pumpernickel.domain.gamification.ApplyRetroactiveGamificationUseCase`
- ctor field `private val walker: RetroactiveWalker` → `private val applyRetroactive: ApplyRetroactiveGamificationUseCase`
- Body: `walker.applyIfNeeded()` → `applyRetroactive.applyIfNeeded()`

**`shared/src/iosMain/kotlin/com/pumpernickel/di/GamificationStartupIos.kt`** (unchanged)

Pre-flight grep bestätigte: Datei referenziert nur `GamificationStartup` (via Koin-Resolve `KoinPlatform.getKoin().get<GamificationStartup>()`), nicht den Walker direkt. Plan-`files_modified` listet sie als "MODIFIED — Import + Konstruktor-Param-Typ", aber das ist falsch (Walker wurde nie hier injiziert) — kein Edit nötig. Cross-Platform-Compile bestätigt: iOS-Build grün ohne Änderung.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Shared Android compile | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** |
| androidApp Debug compile | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| All shared tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** (~56s total inkl. iosSimulatorArm64Test) |
| Grep guard 1 — `RetroactiveWalker` live-code refs | `grep -rn "RetroactiveWalker" shared/src androidApp/src` | nur 8 Kommentar-Treffer (plan-history KDoc), 0 Live-Code-Refs (Plan-Spec erlaubt das explizit: "außer evtl. Kommentar-Referenzen") |
| Grep guard 2 — `data.*` imports in new UseCase | `grep -n "import com.pumpernickel.data" .../ApplyRetroactiveGamificationUseCase.kt` | empty (0 hits) |
| Old file deleted | `test ! -f .../data/repository/RetroactiveWalker.kt` | GONE |
| New file present | `test -f .../domain/gamification/ApplyRetroactiveGamificationUseCase.kt` | FOUND |
| Class declaration | `grep -c "class ApplyRetroactiveGamificationUseCase" .../ApplyRetroactiveGamificationUseCase.kt` | 1 |
| No DAO refs in body | `grep -E "Dao(\\)\|completedWorkoutDao\|nutritionDao" .../ApplyRetroactiveGamificationUseCase.kt` | empty (0 hits) |
| Koin binding present | `grep -c "ApplyRetroactiveGamificationUseCase" .../GamificationEngineModule.kt` | 4 (1 import + 2 KDoc + 1 single binding) |
| Git rename detection | `git show --stat c6f9b44` | `rename data/repository/RetroactiveWalker.kt => domain/gamification/ApplyRetroactiveGamificationUseCase.kt (56%)` ✅ |

### Pre-existing warnings (no failures)

Alle Warnings sind pre-existing (StateFlow exposed to ObjC, `typealias Instant` deprecated, `when` exhaustive `else` redundant, NoSafeCasts in BiometricGate/SecureKeyStore, Elvis-always-left in IosGeofenceProvider). Plan 20-09 fügt keine **neuen** Warnings hinzu.

## Koin / iOS impact

- `ApplyRetroactiveGamificationUseCase` wird ausschließlich von `GamificationStartup` konsumiert; Koin resolved das transitiv über das `gamificationEngineModule`.
- Die Klasse wird NICHT direkt an Swift exposed — `GamificationStartupIos.kt` exponiert nur die `trigger()`-Methode, die intern `KoinPlatform.getKoin().get<GamificationStartup>()` aufruft. Daher: keine Swift-/Obj-C-Bridge-Implikationen.
- iOS X64 compile grün. Swift/Xcode-Build ist Plan-20-13-Scope.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Cleanup] Inline `toDomainConsumption()`-Mapper entfernt (Plan-`approach` §2 hatte ihn unter "DAO → Repository-Substitution" implizit gestrichen — Summary dokumentiert das explizit)**

- **Found during:** Task 1 write — beim ctor-Swap fiel auf, dass `FoodRepository.loadConsumptions()` direkt `List<ConsumptionEntry>` zurückgibt (durch Plan-20-08-Repository-Surface).
- **Issue:** Plan 20-08 hatte als Inline-Lösung den Mapper am Datei-Ende des Walkers belassen, weil die DAO damals noch direkt konsumiert wurde. Nach dem Switch auf Repository ist die Mapping-Logik bereits in `FoodRepositoryImpl` gekapselt → der Inline-Mapper wäre tote Code-Duplikation gewesen.
- **Fix:** Entfernt: 14 LOC am Datei-Ende (`private fun ConsumptionEntryEntity.toDomainConsumption(): ConsumptionEntry`) + 2 betroffene Imports (`ConsumptionEntryEntity`, `FoodUnit`).
- **Files modified:** `domain/gamification/ApplyRetroactiveGamificationUseCase.kt`.
- **Commit:** `c6f9b44`

**2. [Rule 3 — Plan-Spec-Klarstellung] `GamificationStartupIos.kt` nicht modifiziert (Plan-`files_modified` listet es, Realität braucht es nicht)**

- **Found during:** Pre-flight grep `RetroactiveWalker` over `shared/src/iosMain/`.
- **Issue:** Plan-Frontmatter `files_modified` listet `GamificationStartupIos.kt` als zu modifizierend. Tatsächlich injiziert die Datei aber nur `GamificationStartup` (via Koin) und ruft `startup.run()`. Die Walker-/UseCase-Klasse wird hier nirgends referenziert. Eine Edit wäre ein No-Op gewesen.
- **Fix:** Nicht modifiziert. Begründung im SUMMARY dokumentiert; Cross-Platform-iOS-Compile bestätigt die Korrektheit (grün ohne Edit).
- **Files modified:** keine (Plan-Frontmatter folgte aus Vorsicht — die Realität ist günstiger).
- **Commit:** n/a

**3. [Rule 3 — Scope-Boundary] Historische `RetroactiveWalker`-Doc-Referenzen in `GamificationEngine.kt`, `GamificationDao.kt`, `SettingsRepositoryImpl.kt` NICHT angefasst**

- **Found during:** Final grep guard.
- **Issue:** 7 historische KDoc-Kommentare in 3 Files erwähnen `RetroactiveWalker` als Plan-History-Notiz ("used by RetroactiveWalker — D-13", "plan 06 RetroactiveWalker"). Diese sind keine Code-Symbole, sondern dokumentieren wann ein Code-Pfad eingeführt wurde.
- **Fix:** Bewusst nicht angefasst. Plan-`verify`-Spec erlaubt das explizit ("außer evtl. Kommentar-Referenzen"). Touch hätte den Diff auf ~10 Files inflated und die strukturelle Änderung verwässert. Die einzige aktive forward-looking Referenz (`GamificationEngineModule.kt` Module-KDoc) wurde aktualisiert.
- **Files modified:** keine zusätzlichen.
- **Commit:** n/a

### Out-of-scope, documented only

- **`AchievementStateSeeder`** wird in `GamificationStartup` weiterhin direkt aus `data.db` importiert. Das ist ein eigenes data-Layer-Symbol, dessen Domain-Repräsentation nicht im Scope von Smell 11 ist. Wenn ein zukünftiges Plan diesen Pfad anfasst, würde er typischerweise einen `AchievementSeed` Domain-Port einführen.
- **Pre-existing Warnings** (kotlinx.datetime.Instant deprecated, StateFlow-Obj-C-export, NoSafeCasts in BiometricGate/SecureKeyStore) — alle pre-Phase-20, kein Scope von 20-09.

## Known Stubs

Keine. Der UseCase ist voll funktional verkabelt: Sentinel-Check, Replay über `GamificationEngine.processHistoricalWorkout` für jedes Workout, Goal-Day-Aggregation über `NutritionGoalDayPolicy.isGoalDay` + `engine.processHistoricalGoalDay`, abschließendes `engine.runAchievementAndRankChecksForReplay()`. Identisch zum Pre-20-09-Walker-Verhalten — keine Logic-Änderung, nur strukturell.

## Decisions Made

1. **`git mv` für den File-Move** — bewahrt git's rename detection (`rename ... (56%)`), wichtig für `git log --follow` und Code-Review.
2. **ctor-Switch zu Repository-Interfaces** — Plan-`interfaces`-comment erforderte das ("Nach Move nach domain/gamification/ sind DAO-Params NICHT MEHR erlaubt"). `WorkoutRepository.getAllCompletedWorkoutRecords()` + `FoodRepository.loadConsumptions()` existieren bereits durch 20-08, kein neuer Port nötig.
3. **Inline-Mapper entfernt** — `loadConsumptions()` ist schon domain-typisiert, der Plan-20-08-Inline-Mapper im Walker war tote Duplikation.
4. **Atomic single commit** — class-rename + package-move + ctor-signature + Koin-binding + Startup-field-rename sind Compile-Closure-gekoppelt.
5. **Field-Rename in GamificationStartup** — `walker → applyRetroactive` matched die neue Klassen-Semantik (es ist kein Walker mehr, es ist ein UseCase).
6. **`GamificationStartupIos.kt` unangetastet** — Pre-flight grep bewies, dass es das Symbol nicht direkt referenziert.
7. **Historische KDoc-Refs unangetastet** — Plan-Spec erlaubt das; minimiert Diff-Inflation.

## Threat Flags

Keine neuen Threat-Surfaces. Pure Strukturarbeit: Move + Rename + Repository-Substitution. Keine neuen Netzwerk-/Auth-/Filesystem-Pfade, keine Schema-Änderungen, keine Trust-Boundary-Verschiebungen. Die Sentinel-Logik (`retroactiveApplied`) und die Dedupe-Logik (`(source, eventKey)`) sind bit-identisch zum Pre-20-09-State.

## TDD Gate Compliance

n/a — Plan 20-09 ist `type: execute` (kein `type: tdd`). Plan-`approach` ist rein mechanisch (Rename + Move + ctor-Swap), keine RED/GREEN/REFACTOR-Gates erwartet. Verhaltens-Bit-Identität wird durch das atomare Compile-Closure + den `:shared:allTests`-Run garantiert (7/7 `NutritionGoalDayPolicyTest` weiterhin grün).

## Commit

`c6f9b44 refactor(20-09): rename RetroactiveWalker → ApplyRetroactiveGamificationUseCase + move to domain/gamification/ (Smell 11 / D-20-06)`

3 files changed, 28 insertions(+), 52 deletions(-). Net -24 LOC (Mapper-Removal). 1 git-rename (56% similarity), 2 modifications. Atomic.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/ApplyRetroactiveGamificationUseCase.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt` — GONE
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationEngineModule.kt` — FOUND (modified, ApplyRetroactiveGamificationUseCase binding)
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationStartup.kt` — FOUND (modified, applyRetroactive field)
- Commit `c6f9b44` — FOUND (`git log --oneline -1` → `c6f9b44 refactor(20-09): ...`)
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL
- Grep guard (no `data.*` imports in UseCase) — 0 hits
- Grep guard (`RetroactiveWalker` live-code refs) — 0 (only 8 plan-history KDoc comments, permitted by Plan-`verify`-spec)
- Grep guard (no DAO refs in UseCase body) — 0 hits
- Git rename detection — preserved at 56% similarity

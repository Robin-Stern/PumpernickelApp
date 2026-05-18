---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 03
subsystem: clean-architecture-three-repositories
tags: [refactor, repository, interface-move, dependency-rule, wave-3]
requires:
  - "Plan 20-01 (provided domain/repository/ package layout precedent)"
  - "Plan 20-02 (WorkoutRepository split — pattern for Template & Exercise repos)"
provides:
  - "com.pumpernickel.domain.repository.TemplateRepository (interface, port for workout templates)"
  - "com.pumpernickel.domain.repository.ExerciseRepository (interface, port for exercise catalog)"
  - "com.pumpernickel.domain.repository.FoodRepository (interface, port for foods/recipes/consumption)"
  - "com.pumpernickel.data.repository.TemplateRepositoryImpl (Room-backed adapter, split into its own file)"
  - "com.pumpernickel.data.repository.ExerciseRepositoryImpl (Room-backed adapter, split into its own file)"
affects:
  - "Plan 20-04 (SettingsRepository extraction — next wave continues Smell 1 / D-20-04)"
  - "Plan 20-13 (Final Verification — owns the full Xcode build)"
tech-stack:
  added: []
  patterns:
    - "interface-in-domain + impl-in-data (Smell-1 fix, per D-20-08)"
    - "atomic-multi-repo commit for semantically identical moves (3 repos × 1 commit)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/TemplateRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ExerciseRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/FoodRepository.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/AddFoodUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/DeleteConsumptionUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/DeleteFoodUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LoadConsumptionsForDateUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LoadFoodsUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LogConsumptionUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SelectFoodUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/UpdateFoodUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/workout/GetUndertrainedMusclesUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/CreateExerciseViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseCatalogViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/exercises/ExerciseDetailViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/DailyLogViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeCreationViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/RecipeListViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateEditorViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/templates/TemplateListViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  deleted:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepository.kt
decisions:
  - "Three repos in one atomic commit — semantisch identischer Refactor, gleicher Smell, Wave-3 endet vollständig in einem commit (Plan-Vorgabe)."
  - "FoodRepository-Interface war bereits gesplittet (FoodRepository.kt = nur Interface, FoodRepositoryImpl.kt = nur Impl) — daher reiner Package-Move; FoodRepositoryImpl bekommt eine neue explizite Import-Zeile, da Interface jetzt in fremdem Package liegt."
  - "Toter Import entfernt: das Original-FoodRepository.kt importierte `com.pumpernickel.domain.model.RecipeIngredient`, der Typ wird im Interface aber nicht referenziert. Beim Move ins domain-Package wurde der Import weggelassen (clean move, kein zusätzliches Refactor)."
  - "Keine DTO-Leaks gefunden — alle drei Interfaces operieren ausschließlich auf domain.model-Typen. Im Gegensatz zu WorkoutRepository.getExerciseSetRirSince() (Plan 20-02) bestehen hier keine follow-up smells."
  - "Wie in 20-02 dokumentiert: korrekte Gradle-Targets sind :shared:compileAndroidMain (statt :shared:compileDebugKotlinAndroid) und :androidApp:compileDebugKotlin (statt :androidApp:compileDebugKotlinAndroid). Plan 20-03 listete die alten falschen Namen in Schritt 6 des <approach>-Blocks — Executor benutzte die korrekten Namen aus dem 20-02-SUMMARY."
metrics:
  duration: "~12 min"
  completed: 2026-05-18
  tasks: 3
  files_created: 5
  files_modified: 23
  files_deleted: 3
---

# Phase 20 Plan 03: Template + Exercise + Food Repository Interfaces -> domain/repository Summary

Drei Repositories, ein atomischer Commit. `TemplateRepository` und `ExerciseRepository` wurden gesplittet (Interface → `domain/repository/`, Impl → eigenes File in `data/repository/`). `FoodRepository` war bereits ein reines Interface-File — daher nur ein Package-Move ins `domain/repository/`. Alle 24 Consumer-Imports neu auf `domain.repository` umgehängt. Null Verhaltensänderung.

## What was built

### Task 1 — TemplateRepository + ExerciseRepository splitten und in zwei Files moven

**TemplateRepository** (Original 156 LOC, gesplittet):

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/TemplateRepository.kt` (CREATED, 27 LOC)
  - `package com.pumpernickel.domain.repository`
  - `interface TemplateRepository` mit 11 Methoden (1:1 vom Status-quo).
  - Imports: nur `kotlinx.coroutines.flow.Flow` + drei `domain.model`-Typen (`MuscleGroup`, `TemplateExercise`, `WorkoutTemplate`). Alle DAO/Entity-Imports entfernt — die existieren nur im Impl.

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt` (CREATED, 132 LOC; git zeigt 85%-Rename von `TemplateRepository.kt`)
  - `package com.pumpernickel.data.repository`
  - `class TemplateRepositoryImpl(...) : TemplateRepository`
  - Imports: `data.db.*` (TemplateExerciseEntity, WorkoutTemplateDao, WorkoutTemplateEntity), `domain.model.*`, neu `domain.repository.{TemplateRepository, ExerciseRepository}` (Impl-Konstruktor injiziert ExerciseRepository — auch dieser Typ kommt jetzt aus domain).
  - `@OptIn(ExperimentalCoroutinesApi::class)` bleibt am Class-Level erhalten (war im Original am Class-Level, nicht am Interface).

**ExerciseRepository** (Original 93 LOC, gesplittet):

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ExerciseRepository.kt` (CREATED, 14 LOC)
  - `package com.pumpernickel.domain.repository`
  - `interface ExerciseRepository` mit 6 Methoden (1:1).
  - Imports: nur `Flow` + `domain.model.{Exercise, MuscleGroup}`.

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt` (CREATED, 86 LOC; git zeigt 88%-Rename)
  - `package com.pumpernickel.data.repository`
  - `class ExerciseRepositoryImpl(...) : ExerciseRepository` — komplette Status-quo-Implementierung inkl. seedMutex/seeded.
  - Imports: `data.db.{DatabaseSeeder, ExerciseDao, ExerciseEntity}`, `domain.model.*`, neu `domain.repository.ExerciseRepository`, `kotlinx.serialization.{json.Json, encodeToString}`.

### Task 2 — FoodRepository-Interface umziehen + 24 Consumer-Imports aktualisieren

**Phase A — FoodRepository-Move:**

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/FoodRepository.kt` (CREATED, 22 LOC; git zeigt 88%-Rename von `data/repository/FoodRepository.kt`)
  - `package com.pumpernickel.domain.repository`
  - Inhalt 1:1 vom Original; `kotlinx.serialization`-Import existierte nicht; `RecipeIngredient`-Import existierte im Original, wird im Interface aber nicht referenziert → toter Import beim Move weggelassen (siehe Decisions).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt` (MODIFIED) — neue Import-Zeile `import com.pumpernickel.domain.repository.FoodRepository` direkt nach den `domain.model`-Imports eingefügt.

**Phase B — Consumer-Updates:**

| Repo | Konsumenten-Files |
| --- | --- |
| `TemplateRepository` (5 Konsumenten) | SharedModule.kt, TemplateListViewModel, TemplateEditorViewModel, WorkoutSessionViewModel, WorkoutAiUseCase |
| `ExerciseRepository` (8 Konsumenten) | SharedModule.kt, ExerciseDetailViewModel, ExerciseCatalogViewModel, CreateExerciseViewModel, TemplateEditorViewModel, OverviewViewModel, WorkoutAiUseCase, GetUndertrainedMusclesUseCase |
| `FoodRepository` (13 Konsumenten) | SharedModule.kt, DailyLogViewModel, RecipeCreationViewModel, RecipeListViewModel, LoadConsumptionsForDateUseCase, AddFoodUseCase, LoadFoodsUseCase, DeleteFoodUseCase, SelectFoodUseCase, RecipeAiUseCase, DeleteConsumptionUseCase, LogConsumptionUseCase, UpdateFoodUseCase |

Insgesamt 24 distinkte Files (TemplateEditorViewModel und WorkoutAiUseCase und SharedModule sind Mehrfachkonsumenten, daher sind es 26 Pre-flight-Treffer aber 24 distinkte Files). Jeder Konsument: Import-Zeile `data.repository.{Repo}` → `domain.repository.{Repo}`. Kein anderer Code-Change.

**SharedModule.kt** — die drei `data.repository.{Template,Exercise,Food}Repository`-Imports nach `domain.repository.` umgehängt; `TemplateRepositoryImpl`, `ExerciseRepositoryImpl`, `FoodRepositoryImpl` bleiben aus `data.repository.` importiert. Import-Block alphabetisch konsolidiert (Konsistenz mit Plan 20-02-Pattern: data-Block oben, domain-Block direkt darunter).

### Task 3 — Originale löschen + Final-Verify

Die drei alten Files in `data/repository/` wurden gelöscht:

- `data/repository/TemplateRepository.kt` (durch Rename in `TemplateRepositoryImpl.kt`)
- `data/repository/ExerciseRepository.kt` (durch Rename in `ExerciseRepositoryImpl.kt`)
- `data/repository/FoodRepository.kt` (durch Rename ins domain-Package)

Git hat alle drei Moves als Rename erkannt (85-88% similarity), History bleibt sauber.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Common-Android-compile | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** in 7s |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** (same combined run) |
| androidApp Debug Kotlin compile | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** in 7s |
| All shared tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 1m 1s, iosSimulatorArm64Test executed |
| Grep guard: domain/ depending on data.repository for these three repos | `grep -RE "com\.pumpernickel\.data\.repository\.(Template\|Exercise\|Food)Repository\b" shared/src/commonMain/kotlin/com/pumpernickel/domain/` | empty |
| Grep guard: no remaining old import lines anywhere | `grep -rn "^import com\.pumpernickel\.data\.repository\.\(Template\|Exercise\|Food\)Repository$" shared/src/ androidApp/src/` | empty |
| File state: 3 old files deleted | `test ! -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/{Template,Exercise,Food}Repository.kt` | true for all |
| File state: 3 new interfaces present | `test -f shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/{Template,Exercise,Food}Repository.kt` | true for all |
| File state: 2 new Impl files present (Food was already split) | `test -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/{Template,Exercise}RepositoryImpl.kt` | true for both |
| Sanity check: no DAO/Entity references in new domain interfaces | `grep -E "Entity\|Dao" shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/{Template,Exercise,Food}Repository.kt` | empty |
| Post-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | empty (alles als Rename erkannt) |

### Pre-existing `compileCommonMainKotlinMetadata` failure (baseline)

Wie in 20-01-SUMMARY und 20-02-SUMMARY dokumentiert: `./gradlew :shared:compileCommonMainKotlinMetadata` scheitert weiterhin auf `data/db/AppDatabase.kt:49` mit `AppDatabaseConstructor is not abstract` — pre-existing Room-KMP-Metadata-Pipeline-Quirk, nicht durch Plan 20-03 verursacht. Baseline-getestet vor allen Änderungen. Nicht gefixt (Rule 3 scope boundary). Die `:shared:compileAndroidMain` / `:shared:compileKotlinIosX64` Pipelines laufen erfolgreich, weil sie nach KSP-Generierung der `actual`-Klassen ausgeführt werden.

### Cross-Platform-Compile-Warnings (no failures)

Eine Reihe pre-existing Warnings bleiben:

- `suspend function is exposed to ObjC` / `Flow type is exposed to ObjC` auf `WorkoutRepository` und `AiClient` — KMP-NativeCoroutines-Annotationen liegen auf den Konsumenten (ViewModels), nicht direkt auf den Repository-Interfaces.
- iOS-spezifische Warnings in `BiometricGate.ios.kt`, `SecureKeyStore.ios.kt`, `IosGeofenceProvider.kt` — komplett unabhängig von diesem Refactor.

Keine neuen Warnings durch den Plan-20-03-Refactor.

## KoinHelper / Swift / KMP-NativeCoroutines impact (iOS)

Keine der drei Interfaces (`TemplateRepository`, `ExerciseRepository`, `FoodRepository`) wird über `iosMain/di/*KoinHelper.kt` direkt an Swift exponiert (nur `WorkoutRepository` ist es, siehe 20-02-SUMMARY). Daher:

- `iosX64` und `iosSimulatorArm64`-Builds laufen ohne KoinHelper-Updates durch.
- KMP-NativeCoroutines berührt nichts, weil die Interfaces keine direkt-annotierten Properties haben.
- Swift-Call-Sites referenzieren `Shared.WorkoutRepository` etc. via flacher ObjC-Klassennamen — die drei Interfaces hier sind nicht in dieser Klasse von Symbolen.

Volle Xcode-Verifikation ist Teil von Plan 20-13.

## Known follow-up smells (intentionally not fixed)

Keine. Die drei migrierten Interfaces operieren ausschließlich auf `domain.model`-Typen — kein Entity/DTO-Leak, kein follow-up. Im Gegensatz zu `WorkoutRepository.getExerciseSetRirSince(): List<ExerciseSetRirDto>` (siehe 20-02-SUMMARY follow-up smell) bleibt der Dependency-Rule-Vertrag der drei neuen Interfaces sauber.

## Deviations from Plan

### Approach deviations

- **[Rule 3 — Blocking tooling friction] BSD-sed alternation funktionierte unzuverlässig.** Der initial vorgeschlagene `sed`-Befehl mit `(Template\|Exercise\|Food)Repository` lieferte auf macOS BSD-sed inkonsistente Ergebnisse — der erste Pass fing nur einen Teilbereich; ein zweiter Versuch warf einen "File name too long"-Fehler durch eine Shell-Interaktion. Wechsel auf ein deterministisches Python-Skript (gleicher Regex mit Word-Boundary), das alle verbleibenden 17 Files in einem Durchlauf gepatcht hat. Die finalen Grep-Guards bestätigen 0 stale Imports.
- **`SharedModule.kt`-Imports gruppiert.** Die drei Interface-Imports wurden direkt neben dem bereits existierenden `domain.repository.WorkoutRepository`-Import platziert; die drei Impl-Imports bleiben im `data.repository`-Block. Reines Konsistenzhalten, kein Auto-Fix.
- **Toter `RecipeIngredient`-Import beim FoodRepository-Move entfernt.** Original-File importierte `com.pumpernickel.domain.model.RecipeIngredient`, der Typ wird im Interface aber nicht referenziert. Mechanischer Cleanup beim Package-Move — nicht als Auto-Fix gewertet, da dead-import-removal Bestandteil einer sauberen Move-Operation ist.

### Out-of-scope, documented only

- **[Rule 3 — Scope] Pre-existing `compileCommonMainKotlinMetadata` failure auf `data/db/AppDatabase.kt:49`** — identisch zu 20-01-SUMMARY und 20-02-SUMMARY. Baseline-getestet vor allen Änderungen; nicht durch Plan 20-03 verursacht. Nicht gefixt.

## Decisions Made

1. **Atomic single commit für drei Repos.** Der Plan empfahl explizit "ein commit ok — semantisch gleicher Refactor-Typ". Praxis: die Diff-Hygiene profitiert davon, weil git alle drei Moves als Renames detektieren konnte (85-88% similarity) und somit Blame-History bewahrt.

2. **FoodRepository-Interface: Import von `RecipeIngredient` entfernt** beim Move ins domain-Package. War im Original-File ein toter Import; Übernahme ins neue File wäre Bloat.

3. **TemplateRepositoryImpl: `@OptIn(ExperimentalCoroutinesApi::class)` bleibt am Class-Level**, nicht am Interface. Korrekt, weil die OptIn nur für `flatMapLatest` im Impl gebraucht wird; das Interface hat keine `flatMapLatest`-Verwendung.

4. **Korrekte Gradle-Targets benutzt** (`:shared:compileAndroidMain`, `:androidApp:compileDebugKotlin`) statt der im Plan-Approach noch falsch gelisteten Namen. Erlernt aus 20-02-SUMMARY §"Plan-verification-command corrections".

## Commit

`1acf13c refactor(20-03): move Template/Exercise/Food repository interfaces to domain/repository`

Single atomic commit. 28 Files: 3 renames (`{Template,Exercise}Repository.kt` → `*Impl.kt`, `data/repository/FoodRepository.kt` → `domain/repository/FoodRepository.kt`), 2 creates (`domain/repository/{Template,Exercise}Repository.kt`), 23 modified (FoodRepositoryImpl + SharedModule + 21 consumer imports). 71 insertions, 57 deletions.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/TemplateRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ExerciseRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/FoodRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepositoryImpl.kt` — FOUND (modified)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` — ABSENT (correctly deleted/renamed)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` — ABSENT (correctly deleted/renamed)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/FoodRepository.kt` — ABSENT (correctly deleted/renamed)
- Commit `1acf13c` — FOUND in `git log` (`git log --oneline | head -1` -> `1acf13c refactor(20-03): move Template/Exercise/Food repository interfaces to domain/repository`)
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL (iosSimulatorArm64Test executed)
- Grep guard (no domain depending on data.repository.{Template,Exercise,Food}Repository) — empty
- Grep guard (no remaining old import lines) — empty
- Sanity check (no DAO/Entity in new domain interfaces) — empty

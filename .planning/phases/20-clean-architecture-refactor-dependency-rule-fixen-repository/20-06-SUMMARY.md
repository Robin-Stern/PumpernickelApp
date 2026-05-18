---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 06
subsystem: clean-architecture-mapper-extraction-domain-model-room-free
tags: [refactor, mapper-extraction, dependency-rule, smell-5, wave-6]
requires:
  - "Plan 20-03 (interface-in-domain pattern already established for Workout/Template/Exercise repos)"
provides:
  - "com.pumpernickel.data.repository.mappers.ExerciseMappers (ExerciseEntity.toDomain())"
  - "com.pumpernickel.data.repository.mappers.WorkoutTemplateMappers (WorkoutTemplateEntity.toDomain + TemplateExerciseEntity.toDomain)"
  - "domain/model/Exercise.kt + domain/model/WorkoutTemplate.kt — now Room-free (zero com.pumpernickel.data.* imports)"
affects:
  - "Plan 20-13 (Final Verification — owns the full Xcode build)"
  - "Any future plan adding new mappers — pattern established: per-aggregate file under data/repository/mappers/"
tech-stack:
  added: []
  patterns:
    - "per-aggregate mapper file in data/repository/mappers/ (D-20-08, Smell 5 fix)"
    - "Entity→Domain extension-function lives next to data layer, not domain layer (dependency rule)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/ExerciseMappers.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/WorkoutTemplateMappers.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt
  renamed: []
decisions:
  - "Per-aggregate mapper-file granularity chosen (CONTEXT.md/Claude's Discretion): ExerciseMappers.kt + WorkoutTemplateMappers.kt — one file per aggregate root. One-to-one mapping between the two domain-model files that had embedded mappers and the two new mapper files. No further split into per-entity files because TemplateExerciseEntity belongs to the WorkoutTemplate aggregate."
  - "Pure-domain helpers formatWeightKg() and parseWeightKgX10() stay in domain/model/WorkoutTemplate.kt because they have zero Room-entity dependencies. Plan scope is Smell 5 (Entity-mapper move), not dead-code cleanup. (Grep confirms they are currently unreferenced — flagged as deferred cleanup, out of scope for this plan.)"
  - "private val json = Json { ignoreUnknownKeys = true } moved into ExerciseMappers.kt alongside its sole call-site. File-private visibility preserved — no broader exposure."
  - "Concrete import (import com.pumpernickel.data.repository.mappers.toDomain) chosen over star-import. Plan-approach step 5 explicitly preferred concrete imports."
metrics:
  duration: "~8 min"
  completed: 2026-05-18
  tasks: 2
  files_created: 2
  files_modified: 4
  files_renamed: 0
---

# Phase 20 Plan 06: Mapper-Extraktion — Entity.toDomain() raus aus domain/model/ (Smell 5) Summary

Entity-to-Domain-Mapper aus `domain/model/Exercise.kt` und `domain/model/WorkoutTemplate.kt` in das neue Sub-Package `data/repository/mappers/` verschoben. Beide Domain-Files sind jetzt Room-frei (verified via grep guard). Build und alle Tests grün auf Android + iOS X64 + iOS Simulator Arm64.

## What was built

### Task 1 — ExerciseMappers.kt + Exercise.kt Room-frei

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/ExerciseMappers.kt`** (CREATED, 49 LOC)

- `package com.pumpernickel.data.repository.mappers`
- Imports: `ExerciseEntity`, `Exercise`, `MuscleGroup`, `kotlinx.serialization.json.Json`
- File-private `val json = Json { ignoreUnknownKeys = true }` (umgezogen aus Exercise.kt — file-scope private, gleiche Sichtbarkeit wie vorher)
- `fun ExerciseEntity.toDomain(): Exercise` — 1:1 vom Original (alle 13 Felder, inkl. try/catch für JSON-`instructions`/`images` und CSV-Split für `primaryMuscles`/`secondaryMuscles` via `MuscleGroup.fromDbName(...)`)
- KDoc verweist auf Phase 20, Smell 5, D-20-01 (dependency rule), D-20-08 (mapper layout)

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt`** (MODIFIED — 17 LOC, vorher 52)

- Imports vollständig entfernt (war: `com.pumpernickel.data.db.ExerciseEntity` + `kotlinx.serialization.json.Json`)
- `private val json` entfernt (jetzt im Mapper-File)
- `fun ExerciseEntity.toDomain()` entfernt
- Was bleibt: ausschließlich die `data class Exercise(...)` mit 13 Properties — pure Domain

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt`** (MODIFIED)

- Import-Tausch: `com.pumpernickel.domain.model.toDomain` → `com.pumpernickel.data.repository.mappers.toDomain`
- 4 Call-Sites (`.map { it.toDomain() }` × 3 + `.map { it?.toDomain() }`) unverändert — Extension-Resolution greift den neuen Import automatisch
- Keine weiteren Änderungen

### Task 2 — WorkoutTemplateMappers.kt + WorkoutTemplate.kt Room-frei

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/WorkoutTemplateMappers.kt`** (CREATED, 41 LOC)

- `package com.pumpernickel.data.repository.mappers`
- Imports: `TemplateExerciseEntity`, `WorkoutTemplateEntity`, `MuscleGroup`, `TemplateExercise`, `WorkoutTemplate`
- `fun WorkoutTemplateEntity.toDomain(exercises: List<TemplateExercise> = emptyList()): WorkoutTemplate` — 1:1 vom Original (6 Felder inkl. `source`-Passthrough)
- `fun TemplateExerciseEntity.toDomain(exerciseName: String, primaryMuscles: List<MuscleGroup>): TemplateExercise` — 1:1 vom Original (10 Felder inkl. `perSetReps`-CSV-Split-Helper `?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }`)
- KDoc verweist auf Phase 20, Smell 5, D-20-01, D-20-08; nennt CSV-Split-Helper explizit

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt`** (MODIFIED — 34 LOC, vorher 63)

- Imports vollständig entfernt (war: `com.pumpernickel.data.db.TemplateExerciseEntity` + `com.pumpernickel.data.db.WorkoutTemplateEntity`)
- `fun WorkoutTemplateEntity.toDomain(...)` entfernt
- `fun TemplateExerciseEntity.toDomain(...)` entfernt
- Was bleibt: `data class WorkoutTemplate`, `data class TemplateExercise`, `fun formatWeightKg(...)`, `fun parseWeightKgX10(...)` — alles Room-unabhängig

**`shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt`** (MODIFIED)

- Import-Tausch: `com.pumpernickel.domain.model.toDomain` → `com.pumpernickel.data.repository.mappers.toDomain`
- 3 Call-Sites (`template.toDomain(exercises)` × 2 + `entity.toDomain(exerciseName, primaryMuscles)`) unverändert
- Keine weiteren Änderungen

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Shared Android compile | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** |
| androidApp Debug Kotlin compile | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| All shared tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 1m 4s — `iosSimulatorArm64Test` executed, `iosX64Test` skipped (Wave-pattern) |
| Combined run | `./gradlew :shared:compileAndroidMain :shared:compileKotlinIosX64 :androidApp:compileDebugKotlin :shared:allTests` | **BUILD SUCCESSFUL in 1m 4s** |
| Grep guard 1 — domain/model is Room-free | `grep -rln "com\.pumpernickel\.data" shared/src/commonMain/kotlin/com/pumpernickel/domain/model/` | empty |
| Grep guard 2 — no data.db imports in domain/model | `grep -R "import com\.pumpernickel\.data\.db\." shared/src/commonMain/kotlin/com/pumpernickel/domain/model/` | empty |
| Grep guard 3 — no stale domain.model.toDomain imports anywhere | `grep -rln "import com\.pumpernickel\.domain\.model\.toDomain" shared/ androidApp/` | empty |
| Grep guard 4 — new mapper imports present in Impls | `grep -n "com\.pumpernickel\.data\.repository\.mappers\.toDomain" .../ExerciseRepositoryImpl.kt .../TemplateRepositoryImpl.kt` | 2 hits (line 6 each) |

### Pre-existing `compileCommonMainKotlinMetadata` (skipped per Wave convention)

Wave 1-5 SUMMARYs dokumentieren das pre-existing Room-KMP-Metadata-Pipeline-Issue auf `data/db/AppDatabase.kt:49` (`AppDatabaseConstructor is not abstract`). Plan-Verification-Liste enthält `:shared:compileCommonMainKotlinMetadata` nicht — Skip ist Wave-Konvention.

### Cross-Platform warnings (no failures, all pre-existing)

Identische pre-existing Warnings wie Waves 1-5: `Redundant call of conversion method` in iOS Adapter-Files, `typealias Instant = Instant` deprecated, `when is exhaustive so 'else' is redundant`. **Keine neuen Warnings durch Plan 20-06.**

## iOS/Swift impact

- Mapper sind Top-Level-Extension-Functions in commonMain. Extension-Functions sind Swift-unsichtbar (Bridging gibt nur normale Methoden/Properties an Swift weiter, keine Top-Level-Extensions). Swift-Surface vollständig unverändert.
- Volle Xcode-Verifikation in Plan 20-13.

## Deviations from Plan

**None — plan executed exactly as written.**

Anmerkungen die als kleine Klärungen (nicht als Abweichungen) erwähnenswert sind:

- `domain/model/WorkoutTemplate.kt` enthält zwei pure-domain Helper (`formatWeightKg`, `parseWeightKgX10`), die heute nirgendwo referenziert sind. Sie bleiben im Domain-File — Scope dieses Plans ist Smell 5 (Entity-mapper move), nicht Dead-Code-Cleanup. Kandidat für späteren Quick-Task oder Deferred-Cleanup.
- Plan-Approach Schritt 3 erwähnte mögliche `private fun parseInstructions(...)` Helper. Existierten im Status-quo nicht — die try/catch waren inline. Inline-Stil 1:1 ins Mapper-File mitgenommen.

## Decisions Made

1. **Per-aggregate mapper-file granularity** — `ExerciseMappers.kt` + `WorkoutTemplateMappers.kt`. Ein File pro Aggregat-Root. Begründet im Plan-`<objective>` (CONTEXT.md / Claude's Discretion).
2. **Pure-domain helpers bleiben in WorkoutTemplate.kt** — `formatWeightKg` / `parseWeightKgX10` haben keine Room-Abhängigkeit; Plan-Scope ist Mapper-Move, kein Dead-Code-Sweep.
3. **`private val json` umgezogen** — der `Json`-Parser sass file-private in Exercise.kt und wird ausschließlich von `ExerciseEntity.toDomain()` benutzt. Mitgezogen ins Mapper-File, Sichtbarkeit unverändert (file-private = mappers/ExerciseMappers.kt-private).
4. **Concrete extension import statt star-import** — `import com.pumpernickel.data.repository.mappers.toDomain` in beiden Impls. Plan-Approach Schritt 5 hat das explizit präferiert.

## Commit

`5673678 refactor(20-06): extract Entity.toDomain() mappers from domain/model to data/repository/mappers/ (Smell 5)`

Atomic — beide Aggregate (Exercise + WorkoutTemplate) plus alle Konsumenten in einem Commit.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/ExerciseMappers.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/mappers/WorkoutTemplateMappers.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` — FOUND (modified, Room-free)
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` — FOUND (modified, Room-free)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepositoryImpl.kt` — FOUND (modified, mapper import wired)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepositoryImpl.kt` — FOUND (modified, mapper import wired)
- Commit `5673678` — FOUND in `git log --oneline -3`
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL (iosSimulatorArm64Test executed)
- Grep guard 1 (domain/model Room-free) — empty
- Grep guard 2 (no data.db imports in domain/model) — empty
- Grep guard 3 (no stale domain.model.toDomain imports) — empty
- Grep guard 4 (new mapper imports in both Impls) — 2 hits

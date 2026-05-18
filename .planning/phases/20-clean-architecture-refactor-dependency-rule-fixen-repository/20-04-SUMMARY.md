---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 04
subsystem: clean-architecture-gamification-progresspic-repos
tags: [refactor, repository, interface-move, dependency-rule, wave-4]
requires:
  - "Plan 20-01 (provided domain/repository/ package layout precedent)"
  - "Plan 20-02 (WorkoutRepository split — pattern established)"
  - "Plan 20-03 (Template/Exercise/Food split — refined Python-over-BSD-sed workflow + correct gradle targets)"
provides:
  - "com.pumpernickel.domain.repository.GamificationRepository (interface, port for XP ledger / ranks / achievements)"
  - "com.pumpernickel.domain.repository.ProgressPictureRepository (interface, port for workout photos)"
  - "com.pumpernickel.data.repository.GamificationRepositoryImpl (Room-backed adapter, split into its own file)"
  - "com.pumpernickel.data.repository.ProgressPictureRepositoryImpl (Room + PhotoVault adapter, split into its own file)"
affects:
  - "Plan 20-05 (SettingsRepository extraction + EarlyExitBudgetStore narrow port — next wave continues Smell 1 / D-20-04 / D-20-05)"
  - "Plan 20-09 (Smell 3 GamificationEngine refactor — will deal with the intentionally retained XpLedgerEntity return type on GamificationRepository.getPrLedgerEntries)"
  - "Plan 20-13 (Final Verification — owns the full Xcode build)"
tech-stack:
  added: []
  patterns:
    - "interface-in-domain + impl-in-data (Smell-1 fix, per D-20-08, fifth & sixth repos)"
    - "feature-module-scoped Koin bindings (GamificationModule.kt, ProgressGalleryModule.kt) — bindings stay in feature module, not SharedModule.kt"
    - "atomic-multi-repo commit for semantically identical moves (2 repos × 1 commit)"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ProgressPictureRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepositoryImpl.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/ProgressGalleryModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/AchievementGalleryViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/GamificationViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/gamification/RanksAndAchievementsViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/overview/OverviewViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressGalleryViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressPicturePromptViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/progresspic/ProgressViewerViewModel.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/workout/WorkoutSessionViewModel.kt
  deleted:
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt
decisions:
  - "Both repos in one atomic commit — semantisch identischer Refactor, gleicher Smell, Wave-4 endet vollständig in einem Commit (Plan-Vorgabe risks_pitfalls)."
  - "Koin-Bindings nicht in SharedModule.kt sondern in den jeweiligen Feature-Modulen aktualisiert (GamificationModule.kt, ProgressGalleryModule.kt). Plan-`approach` Schritt 4 erwähnte SharedModule.kt — die tatsächlichen Bindings liegen aber seit Phase 15/17 in den Feature-Modulen. Frontmatter `key_links.from` ist daher angepasst; SharedModule.kt blieb unverändert (kein stale Import, kein veraltetes Binding)."
  - "GamificationRepository.getPrLedgerEntries(): List<XpLedgerEntity> bleibt mit Entity-Return — bewusster Smell-3-Leak. Im neuen Domain-Interface explizit dokumentiert (KDoc-Hinweis 'NOTE Smell-3 follow-up'); wird in Plan 20-09 via Domain-Projection adressiert. Plan-`action` erlaubt das ('Smell-3-Folge-Concern markieren')."
  - "GamificationRepositoryImpl behält `@Mapper`-Block (Top-level private extension functions auf RankStateEntity? / AchievementStateEntity) in der Impl-Datei — nicht in domain/, da Mapper Entity-Typen referenzieren. Konsistent mit Plan 20-02/20-03 (WorkoutRepository toDomain-Mapper blieben ebenfalls in der Impl-Datei)."
  - "Python für die 11 Consumer-Imports verwendet (gleich wie 20-03-Learning). BSD-sed-Alternation auf macOS war im Plan 20-03 unzuverlässig; Python-Pass war deterministisch."
metrics:
  duration: "~5 min"
  completed: 2026-05-18
  tasks: 2
  files_created: 4
  files_modified: 11
  files_deleted: 2 (beide via git als Rename in *Impl.kt erkannt)
---

# Phase 20 Plan 04: GamificationRepository + ProgressPictureRepository -> domain/repository Summary

Zwei Repositories, ein atomischer Commit. `GamificationRepository` (173 LOC) und `ProgressPictureRepository` (140 LOC) wurden je gesplittet: Interface ins `domain/repository/`-Package, Impl in ein eigenes File in `data/repository/`. 11 Consumer-Imports neu auf `domain.repository` umgehängt. Null Verhaltensänderung. Smell-3-Entity-Leak auf `GamificationRepository.getPrLedgerEntries()` wurde bewusst gelassen und explizit dokumentiert (Plan 20-09 wird ihn schließen).

## What was built

### Task 1 — GamificationRepository + ProgressPictureRepository splitten + Move

**GamificationRepository** (Original 173 LOC, gesplittet):

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt` (CREATED, 62 LOC)
  - `package com.pumpernickel.domain.repository`
  - `interface GamificationRepository` mit 11 Methoden (1:1 vom Status-quo).
  - Imports: `kotlinx.coroutines.flow.Flow`, drei `domain.gamification`-Typen (`AchievementProgress`, `Rank`, `RankState`) und **bewusst** `com.pumpernickel.data.db.XpLedgerEntity` (Smell-3-Leak — siehe unten).
  - Mapper + DAO-Imports entfernt — die existieren nur im Impl.
  - KDoc auf Klassen- und Methoden-Ebene um eine "NOTE (Smell-3 follow-up)" erweitert, die den `XpLedgerEntity`-Leak markiert und auf Plan 20-09 verweist.

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt` (CREATED, 124 LOC; git zeigt 70%-Rename von `GamificationRepository.kt`)
  - `package com.pumpernickel.data.repository`
  - `class GamificationRepositoryImpl(private val dao: GamificationDao) : GamificationRepository` — komplette Status-quo-Implementierung inkl. zwei Top-level private Mapper-Functions (`RankStateEntity?.toDomain(totalXpFromLedger: Long): RankState` und `AchievementStateEntity.toDomain(): AchievementProgress?`).
  - Imports: alle `data.db.*` (AchievementStateEntity, GamificationDao, RankStateEntity, XpLedgerEntity), `domain.gamification.*`, neu `domain.repository.GamificationRepository`, `kotlinx.coroutines.flow.{Flow, combine, map}`.

**ProgressPictureRepository** (Original 140 LOC, gesplittet):

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ProgressPictureRepository.kt` (CREATED, 56 LOC)
  - `package com.pumpernickel.domain.repository`
  - `interface ProgressPictureRepository` mit 6 Methoden (1:1).
  - Imports: nur `Flow`, `domain.progresspic.{ProgressGalleryTile, ProgressPicture}`. Keine `data.db`-Imports im Interface — der Tile-Mapper liegt nur im Impl.
  - **Kein Entity-Leak**: alle Returntypes sind domain-rein. Der Body-KDoc erwähnt `ProgressPictureDao` / `GamificationDao` lediglich textuell als Erklärung.

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepositoryImpl.kt` (CREATED, 91 LOC; git zeigt 61%-Rename von `ProgressPictureRepository.kt`)
  - `package com.pumpernickel.data.repository`
  - `class ProgressPictureRepositoryImpl(private val dao: ProgressPictureDao, private val vault: PhotoVault) : ProgressPictureRepository` — komplette Status-quo-Implementierung inkl. `ProgressPictureEntity.toDomain()`-Mapper am Datei-Ende.
  - Imports: `data.db.{ProgressPictureDao, ProgressPictureEntity}`, `domain.progresspic.{PhotoVault, ProgressGalleryTile, ProgressPicture}`, neu `domain.repository.ProgressPictureRepository`, `kotlinx.coroutines.flow.{Flow, map}`.

### Task 2 — Consumer-Imports aktualisieren + Originale löschen

**Konsumenten-Inventar** (Pre-flight grep auf `import com.pumpernickel.data.repository.{Gamification,ProgressPicture}Repository` in `shared/`, `androidApp/`):

| Repo | 7 Konsumenten | 4 Konsumenten |
| --- | --- | --- |
| `GamificationRepository` | GamificationModule.kt, GamificationEngine.kt, AchievementGalleryViewModel.kt, GamificationViewModel.kt, OverviewViewModel.kt, RanksAndAchievementsViewModel.kt, WorkoutSessionViewModel.kt | — |
| `ProgressPictureRepository` | — | ProgressGalleryModule.kt, ProgressViewerViewModel.kt, ProgressGalleryViewModel.kt, ProgressPicturePromptViewModel.kt |

Insgesamt **11 distinkte Files**, jeder genau eine Import-Zeile geändert (data.repository → domain.repository). Python-Pass mit `^import com\.pumpernickel\.data\.repository\.(GamificationRepository|ProgressPictureRepository)$` → `import com.pumpernickel.domain.repository.\1`. Word-anchored, keine versehentlichen Impl-Treffer.

**Feature-Modul-Updates:**

- `GamificationModule.kt` — `import com.pumpernickel.data.repository.GamificationRepository` → `import com.pumpernickel.domain.repository.GamificationRepository`; `import com.pumpernickel.data.repository.GamificationRepositoryImpl` bleibt. Binding `single<GamificationRepository> { GamificationRepositoryImpl(get()) }` unverändert.
- `ProgressGalleryModule.kt` — analog: Interface-Import auf domain-Pfad, Impl-Import bleibt. Binding `single<ProgressPictureRepository> { ProgressPictureRepositoryImpl(get(), get()) }` unverändert.

**Originale gelöscht:**

- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt`
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt`

Git hat beide Files als Rename in die jeweiligen `*Impl.kt`-Dateien erkannt (70% bzw. 61% similarity).

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Common-Android-compile | `./gradlew :shared:compileAndroidMain` | **BUILD SUCCESSFUL** in 10s |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64` | **BUILD SUCCESSFUL** (same combined run) |
| androidApp Debug Kotlin compile | `./gradlew :androidApp:compileDebugKotlin` | **BUILD SUCCESSFUL** in 10s |
| All shared tests | `./gradlew :shared:allTests` | **BUILD SUCCESSFUL** in 1m 28s, iosSimulatorArm64Test executed, iosX64Test skipped |
| Grep guard: domain/ depending on data.repository for these two repos | `grep -RE "com\.pumpernickel\.data\.repository\.(GamificationRepository\|ProgressPictureRepository)\b" shared/src/commonMain/kotlin/com/pumpernickel/domain/` | empty |
| Grep guard: no remaining old interface-import lines anywhere | `grep -rnE "^import com\.pumpernickel\.data\.repository\.(GamificationRepository\|ProgressPictureRepository)$" shared/src/ androidApp/src/` | empty |
| File state: 2 old files deleted | `test ! -f shared/src/commonMain/kotlin/com/pumpernickel/data/repository/{Gamification,ProgressPicture}Repository.kt` | true for both |
| File state: 2 new interfaces + 2 new Impls present | manual `ls` | true for all four |
| Sanity check: Entity/Dao references in new domain interfaces | `grep -nE "Entity\|Dao" shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/{Gamification,ProgressPicture}Repository.kt` | only documented Smell-3 follow-up (`XpLedgerEntity` import + return type) + 2 docstring mentions in ProgressPicture (no symbol leak) |
| Post-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | empty (alles als Rename erkannt) |

### Pre-existing `compileCommonMainKotlinMetadata` failure (baseline)

Wie in 20-01/02/03-SUMMARYs dokumentiert: `:shared:compileCommonMainKotlinMetadata` scheitert weiterhin auf `data/db/AppDatabase.kt:49` mit `AppDatabaseConstructor is not abstract`. Pre-existing Room-KMP-Metadata-Pipeline-Quirk, nicht durch Plan 20-04 verursacht. Plan-Prompt instruierte explizit "Skip `:shared:compileCommonMainKotlinMetadata` — pre-existing Room baseline failure." → daher nicht im Verify-Lauf enthalten.

### Cross-Platform-Compile-Warnings (no failures)

Die identischen pre-existing Warnings wie in 20-02/03 bestehen weiter (`suspend function is exposed to ObjC` auf WorkoutRepository, AiClient; `Redundant call of conversion method` in BiometricGate.ios.kt etc.). Keine **neuen** Warnings durch Plan 20-04.

## KoinHelper / Swift / KMP-NativeCoroutines impact (iOS)

Weder `GamificationRepository` noch `ProgressPictureRepository` werden in `iosMain/di/*KoinHelper.kt` direkt an Swift exponiert — beide werden nur als VM-Konstruktor-Args injiziert. `grep -rln "GamificationRepository\|ProgressPictureRepository" shared/src/iosMain/ androidApp/src/` → empty.

Daher:

- iOS X64 / SimulatorArm64-Builds laufen ohne KoinHelper-Updates.
- KMP-NativeCoroutines berührt nichts (kein direkt-annotierter Repository-Property).
- Swift-Call-Sites referenzieren `Shared.WorkoutRepository` etc., aber keinen dieser zwei.

Volle Xcode-Verifikation ist Teil von Plan 20-13.

## Known follow-up smells (intentionally retained)

### Smell 3 — Entity in domain-Interface (GamificationRepository.getPrLedgerEntries)

`GamificationRepository.getPrLedgerEntries(): List<XpLedgerEntity>` ruft die Room-Entity-Klasse über die domain-Layer-Schicht zurück.

- **Warum nicht jetzt gefixt:** Plan-`action` Task 1 sagt explizit: "wenn ja, Methoden-Signature behält Entity-Typ FÜRS ERSTE, im Commit-Body als Smell-3-Folge-Concern markieren (wird in Plan 20-09 GamificationEngine-Plan adressiert via Repository-Methoden-Erweiterung, nicht im 20-04-Scope)."
- **Wie markiert:** KDoc-Block am Interface-Body (Zeilen 12-17) + Inline-KDoc auf der Methode selbst.
- **Wer adressiert:** Plan 20-09 (Smell 3 GamificationEngine-Refactor).

### Smell 3 — andere Konsumenten der Methode

`GamificationEngine.retroactivePr(...)`-Pfad iteriert über die Methode. Das fließt in den 20-09-Plan ein — kein Touchpoint in 20-04.

## Deviations from Plan

### Approach deviations

- **[Rule 3 — Plan-text vs. real layout] SharedModule.kt war NICHT der Touchpoint.** Der Plan-`approach` Schritt 4 und das `key_links.from`-Field nennen `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`. Tatsächlich liegen die zwei Bindings seit Phase 15/17 in den Feature-Modulen `GamificationModule.kt` (Z. 24-25) und `ProgressGalleryModule.kt` (Z. 50-53). SharedModule.kt blieb komplett unverändert — keine stale Imports, kein Binding fehlt. Daher wurden die Imports & Bindings korrekt in den Feature-Modulen aktualisiert. Documenting für 20-05+: SettingsRepository wird voraussichtlich tatsächlich in SharedModule.kt liegen (siehe Z. 18+94 des Status-quo).
- **Python statt BSD-sed für 11-File-Pass.** Direkt befolgt aus 20-03-Learning. Mit `re.MULTILINE` + Capture-Group-Rückreferenz wurden alle 11 Imports in einem deterministischen Lauf umgehängt; finale Grep-Guards zeigen 0 stale Imports.

### Out-of-scope, documented only

- **[Rule 3 — Scope] Pre-existing `compileCommonMainKotlinMetadata` failure auf `data/db/AppDatabase.kt:49`** — identisch zu 20-01/02/03-SUMMARYs. Plan-Prompt instruierte explizit den Skip. Nicht gefixt.
- **[Rule 3 — Scope] `GamificationRepository.getPrLedgerEntries(): List<XpLedgerEntity>` Entity-Leak** — siehe "Known follow-up smells" oben. Plan-explizit auf 20-09 deferred.

## Decisions Made

1. **Atomic single commit für beide Repos.** Plan-`risks_pitfalls` empfiehlt es explizit ("Atomic commit: Zwei Repos, semantisch gleich"). Praxis: git hat beide Files als Rename detektiert (70% / 61% similarity), Blame-History bleibt erhalten.

2. **Smell-3-Leak in GamificationRepository bewusst gelassen + dokumentiert.** Plan-konformer Compromise: Interface bewegt sich, aber der Entity-Returntype bleibt für jetzt. KDoc verweist auf Plan 20-09.

3. **Feature-Module statt SharedModule.kt für Bindings.** Realität schlägt Plan-Wortlaut: Bindings leben dort wo sie sind (GamificationModule.kt, ProgressGalleryModule.kt). SharedModule.kt nicht angefasst.

4. **Mapper bleiben in Impl-Datei.** Konsistent mit Plan 20-02/20-03 (WorkoutRepository / TemplateRepository / ExerciseRepository folgten der gleichen Regel). Mapper referenzieren Entity-Typen → können nicht ins domain/-Package wandern.

## Commit

`4d5a4d9 refactor(20-04): move Gamification + ProgressPicture repository interfaces to domain/repository`

Single atomic commit. 15 files: 2 renames (`{Gamification,ProgressPicture}Repository.kt` → `*Impl.kt`), 2 creates (`domain/repository/{Gamification,ProgressPicture}Repository.kt`), 11 modified (2 Feature-Module + GamificationEngine + 8 ViewModels). 133 insertions, 113 deletions.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/ProgressPictureRepository.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepositoryImpl.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt` — ABSENT (correctly deleted/renamed)
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ProgressPictureRepository.kt` — ABSENT (correctly deleted/renamed)
- Commit `4d5a4d9` — FOUND in `git log --oneline | head -1`
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL
- `:shared:allTests` — BUILD SUCCESSFUL (iosSimulatorArm64Test executed)
- Grep guard (no domain depending on data.repository.{Gamification,ProgressPicture}Repository) — empty
- Grep guard (no remaining old interface-import lines) — empty
- Sanity check (no unexpected Entity/Dao in new domain interfaces) — only the documented Smell-3 follow-up

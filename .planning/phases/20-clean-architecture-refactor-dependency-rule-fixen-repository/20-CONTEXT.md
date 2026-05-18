# Phase 20: Clean Architecture Refactor — Dependency-Rule fixen - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Pure Strukturarbeit am Package-Layout, damit die Dependency-Rule (`presentation → domain ← data`) wirklich hält. Keine neuen Features, keine Schema-Migrationen, keine Verhaltensänderungen. Fix-Scope umfasst die acht in `CONCERNS.md` dokumentierten Title-Smells:

- **Smell 1** — Repository-Interfaces wandern von `data/repository/` nach `domain/repository/`; `*Impl`-Hälften bleiben in `data/repository/` (separate Files).
- **Smell 2** — `SettingsRepository`-Interface wird in `domain/repository/` extrahiert; Impl bleibt monolithisch in `data/repository/`.
- **Smell 3** — Room-Entities + DAOs raus aus `domain/` (`GamificationEngine`, `NutritionGoalDayPolicy`); ersetzt durch Repository-Abstraktionen + neues `domain/model/ConsumptionEntry`.
- **Smell 4** — AI-Use-Cases (`WorkoutAiUseCase`, `RecipeAiUseCase`, `SearchFoodsRemoteUseCase`) entkoppelt von Ktor/`OpenAICompatibleClient` via `AiClient`-Port; `OpenAICompatibleClient` wird Adapter.
- **Smell 5** — `Entity.toDomain()`-Mapper raus aus `domain/model/`, rein nach `data/repository/mappers/`.
- **Smell 11** — `RetroactiveWalker` wandert nach `domain/gamification/ApplyRetroactiveGamificationUseCase` (Datei + Koin-Binding).
- **Smell 12** — `expect`/`actual`-Ports (BiometricGate, PhotoVault, SecureKeyStore, NotificationService, GeofenceProvider, LocationProvider, PermissionController) + ihre Platform-Impls wandern in eine neue Top-Level-Schicht `infrastructure/`. Android `feature/*` und iOS `data/{geofence,location,permissions}/` werden dort vereinheitlicht.
- **Smell 13** — `EarlyExitTracker` injiziert `EarlyExitBudgetStore`-Narrow-Port statt konkreter `SettingsRepository`.

</domain>

<decisions>
## Implementation Decisions

### Refactor-Umfang (Area A)
- **D-20-01:** Smell-Scope ist auf die acht Title-Buckets begrenzt — Smells 1, 2, 3, 4, 5, 11, 12, 13. Smells 6 (VM-injects-DAO), 7 (Composable-uses-Repo), 8 (SQLiteException-Leak), 9 (WorkoutSessionViewModel-Use-Case-Extraction), 10 (VM-Mapping-Duplikate), 14 (`ApiKeyState` global singleton) sind **out of scope** für Phase 20.
  - **Why:** User-Vorgabe "pure Strukturarbeit, keine neuen Features". Smell 9 bringt ohne VM-Tests Verhaltensrisiko in einen 1171-Zeilen-Hotspot. Smells 6/7/8/10/14 sind separat machbar und blockieren Phase 20 nicht. Siehe `<deferred>` für Capture.

### Platform-Port-Placement (Area B)
- **D-20-02:** Neue Top-Level-Schicht `infrastructure/` wird eingeführt. Layout:
  - `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/{ai,progresspic,geofence,location,permissions,notification}/` — `expect`-Klassen / Interfaces (BiometricGate, PhotoVault, PhotoCaptureLauncher, SecureKeyStore, NotificationService, GeofenceProvider, LocationProvider, PermissionController).
  - `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/{...}/` — Android-`actual`s (zieht um aus `androidMain/.../feature/*` und `androidMain/.../domain/{ai,progresspic}/`).
  - `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/{...}/` — iOS-`actual`s (zieht um aus `iosMain/.../data/{geofence,location,permissions}/` und `iosMain/.../domain/{ai,progresspic}/`).
  - Android-`feature/`-Folder und iOS-`data/{geofence,location,permissions}/`-Folder verschwinden.
  - **Why:** `domain/` soll wirklich rein logikbasiert sein. `BiometricGate.ios.kt` importiert `platform.LocalAuthentication.*` — das ist OS-Infrastruktur, kein Domain. Außerdem löst es Smell 12s Hauptkritik (Android `feature/` ↔ iOS `data/` Inkonsistenz) durch ein gemeinsames Ziel.

- **D-20-03:** Persistence-Platform-Actuals bleiben in `data/` und werden Cross-Platform unifiziert:
  - Android `platform/Database.android.kt` → `data/db/Database.android.kt`.
  - Android `platform/createDataStore.android.kt` → `data/preferences/createDataStore.android.kt`.
  - iOS bleibt unverändert (`iosMain/data/db/Database.ios.kt`, `iosMain/data/preferences/createDataStore.ios.kt`).
  - Android-`platform/`-Folder verschwindet (kein Inhalt mehr nach dem Move).
  - **Why:** Room-/DataStore-Builder sind Persistence-Setup, nicht OS-Feature-Infrastructure. Trennt sauber zwischen "Daten" (`data/`) und "OS-Sachen" (`infrastructure/`).

### `SettingsRepository`-Granularität (Area C)
- **D-20-04:** `SettingsRepository`-Interface wird in `domain/repository/SettingsRepository.kt` extrahiert; die DataStore-basierte Impl bleibt monolithisch als `SettingsRepositoryImpl` in `data/repository/`. Kein Vollsplit, keine Per-Concern-Repos.
  - **Why:** User-Vorgabe "pure Strukturarbeit". Vollsplit bräche ~15 Call-Sites in ViewModels ohne Verhaltensvorteil.

- **D-20-05:** Narrow Domain-Ports werden gezielt nur dort eingeführt wo Domain-Code aktuell die konkrete `SettingsRepository`-Klasse importiert:
  - `EarlyExitTracker` injiziert künftig `EarlyExitBudgetStore` statt `SettingsRepository` (Smell 13).
  - `PendingGeofenceExitStore` existiert bereits — bleibt unverändert.
  - `GoalDayTrigger`, `GamificationEngine`, `NutritionGoalDayPolicy` und alle ViewModels injizieren weiter `SettingsRepository` (jetzt via Interface).
  - Impl implementiert beide Narrow-Ports + das Haupt-Interface.
  - **Why:** Smell 13 sauber lösen, ohne Vollsplit. Domain bekommt die minimal nötigen Ports, Presentation behält das Fat-Interface bis ein größerer Refactor kommt.

### Out-of-Scope-Klarstellungen
- **D-20-06:** Use-Cases werden für die Phase-20-Smells **nur dort neu angelegt wo es Title-Bucket-Smells direkt fordern** — konkret `domain/gamification/ApplyRetroactiveGamificationUseCase` (Smell 11). Keine spekulative Use-Case-Schicht für Workout (Smell 9 ist deferred), keine `StartWorkoutUseCase` / `CompleteSetUseCase` etc.
- **D-20-07:** `domain/model/ConsumptionEntry` wird neu eingeführt als Domain-Repräsentation des `ConsumptionEntryEntity`. `NutritionGoalDayPolicy.isGoalDay(entries: List<ConsumptionEntry>, ...)` ändert Signatur. `NutritionGoalDayPolicyTest` muss entsprechend angepasst werden (Test bleibt in Phase 20 — Signaturwechsel ist mechanisch).
- **D-20-08:** Naming bleibt konventionell: Interface = `WorkoutRepository`, Impl = `WorkoutRepositoryImpl`. Kein Rename auf `Gateway`/`Port`-Suffixe.
- **D-20-09:** Bestehende Koin-Module (`SharedModule.kt`, `AiModule.kt`, `GamificationModule.kt`, etc.) bleiben in `di/`. Bindings werden auf Interface-Typen umgestellt; Module-Files selbst wandern nicht.
- **D-20-10:** Architektur-Enforcement (Konsist-Test, Modul-Split) wird in dieser Phase **nicht** automatisiert. Code-Review-Disziplin reicht erstmal. Planner darf eine Capture-Datei `.planning/seeds/SEED-XXX-architecture-enforcement.md` anlegen, wenn er das sinnvoll findet.

### Claude's Discretion
- Wave-Reihenfolge / Plan-Aufteilung: Planner entscheidet (z.B. nach Smell oder nach Layer; Reihenfolge muss Imports respektieren — typisch: `domain/model/ConsumptionEntry` + Interface-Moves zuerst, dann Mapper-Extraktion, dann `infrastructure/`-Move).
- Granularität der Mapper-Files in `data/repository/mappers/` (one-file-per-aggregate vs. one-file-per-entity).
- AiClient-Port-Form: ob es eine generische `AiClient` mit Schema-Parameter wird oder zwei spezialisierte Ports (`WorkoutAiClient`, `RecipeAiClient`). Empfehlung: generisch, da die Use-Cases beide gegen dieselbe OpenAI-API laufen.
- Ob `infrastructure/` Sub-Pakete behält (`infrastructure/ai`, `infrastructure/progresspic`, etc.) oder flacher organisiert wird. Status-quo behält die Sub-Pakete — wahrscheinlich am wenigsten Reibung.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Smell-Inventar mit `file:line`-Evidenz
- `.planning/codebase/CONCERNS.md` §"Clean Architecture Smells" — alle 14 Smells mit Datei-/Zeilen-Beweisen + per-Smell "Fix approach" der für die acht Title-Smells befolgt werden soll.

### Aktueller Code-Layout-Anker
- `.planning/codebase/STRUCTURE.md` §"Deep tree" — vollständiger Package-Tree für `commonMain` / `androidMain` / `iosMain` / `androidApp/`. Referenz für Source/Ziel jedes Moves.
- `.planning/codebase/ARCHITECTURE.md` — falls für übergreifende Pattern-Beschreibungen relevant.
- `.planning/codebase/CONVENTIONS.md` — Naming-/Layout-Konventionen vor dem Refactor.

### Phase-Beschreibung & Roadmap
- `.planning/ROADMAP.md` §"Phase 20" — Titel + Depends-on-Phase-19 + "Pure Strukturarbeit"-Vorgabe.
- `.planning/STATE.md` §"Roadmap Evolution: Phase 20 added (2026-05-18)" — vollständige Klartext-Beschreibung was Phase 20 ist.

### Bestehende Domain-Ports (bleiben)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/geofence/PendingGeofenceExitStore.kt` — Narrow-Port-Vorbild für `EarlyExitBudgetStore` (D-20-05).

### Tests die mitziehen
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicyTest.kt` — Signatur ändert sich durch D-20-07.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `domain/geofence/PendingGeofenceExitStore.kt` — existiert bereits als Narrow-Port und wird von `SettingsRepository` implementiert. Vorbild für `EarlyExitBudgetStore` (D-20-05).
- `data/repository/FoodRepository.kt` + `FoodRepositoryImpl.kt` — bereits in zwei Files gesplittet; Ziel-Pattern für alle anderen Repos nach D-20-01.

### Established Patterns
- **Koin-Bindings via Interface-Typ** ist bereits etabliert (siehe `SharedModule.kt`). Nach dem Interface-Move ändern sich nur die Import-Pfade, nicht der DSL-Stil (`single<WorkoutRepository> { WorkoutRepositoryImpl(...) }`).
- **expect/actual-Splitting nach Package** ist bereits etabliert (`commonMain/.../domain/ai/SecureKeyStore.kt` + `androidMain/.../domain/ai/SecureKeyStore.android.kt`). Nach dem Move bleibt das Pattern, nur Package-Pfad wandert von `domain/` nach `infrastructure/`.
- **Repository-Aggregation** in `SharedModule.kt` via `includes(...)` (siehe Phase-15-Pattern mit `GamificationEngineModule.kt` etc.) — wird durch Phase 20 nicht angetastet.

### Integration Points
- **Koin-Module:** Jedes `di/*Module.kt` (`SharedModule.kt`, `AiModule.kt`, `GamificationModule.kt`, `GamificationEngineModule.kt`, `GamificationUiModule.kt`, `AchievementGalleryModule.kt`, `ProgressGalleryModule.kt`, `PlatformModule.{android,ios}.kt`) referenziert Repository-Klassen oder Use-Cases — Import-Pfade ändern sich nach Phase-20-Refactor.
- **iOS KoinHelpers** in `iosMain/di/*KoinHelper.kt` (12 Files) referenzieren konkrete VM/Repository-Typen. Beim Interface-Move muss geprüft werden ob KMP-NativeCoroutines die Interface-Properties korrekt exportiert — Status quo funktioniert mit den Impls.
- **Composable-DI** in Android (`koinInject<WorkoutRepository>()` in `DebugGeofencePanel.kt`) und iOS (Bridging via KoinHelpers) — die `DebugGeofencePanel`-Verletzung ist Smell 7, **out of scope** (D-20-01).
- **commonTest/**: `NutritionGoalDayPolicyTest` zieht durch Signaturänderung mit (D-20-07). Andere Tests (`XpFormulaTest`, `RankLadderTest`, `StreakCalculatorTest`, `AchievementCatalogTest`, `AchievementRulesTest`, `TdeeCalculatorTest`) sind domain-rein und sollten ohne Änderung weiterlaufen.

</code_context>

<specifics>
## Specific Ideas

- Repository-Naming: `WorkoutRepository` (Interface) + `WorkoutRepositoryImpl` (Impl) — keine `Gateway`/`Port`-Suffixe (D-20-08).
- Mapper-Layout: `data/repository/mappers/` als neues Sub-Package (aus Smell-5-"Fix approach"). Plan-Phase darf Granularität (one-per-aggregate vs. one-per-entity) festlegen.
- AiClient-Port: ein generisches `infrastructure/ai/AiClient`-Interface bevorzugt (statt zwei spezialisierte Ports). Liegt nach D-20-02 unter `infrastructure/ai/` — nicht unter `domain/ai/` — weil es eine Network-Port-Abstraktion ist; `WorkoutAiUseCase`/`RecipeAiUseCase` ziehen ihre Port-Abhängigkeit aus `infrastructure/ai/`. (Alternative: Port unter `domain/ai/` lassen, da es konzeptuell ein Domain-Contract ist. Planner-Entscheidung; Default = `infrastructure/ai/`.)

</specifics>

<deferred>
## Deferred Ideas

Out-of-Scope-Smells aus dem `CONCERNS.md`-Inventar, die explizit nach Phase 20 (oder gar nicht) angegangen werden:

- **Smell 6** — `ProgressGalleryViewModel` injiziert `GamificationDao` + `NutritionDao` direkt. Fix: Aggregations-Methoden auf Repository-Interface verschieben. Eigene kleine Phase oder Quick-Task.
- **Smell 7** — `DebugGeofencePanel` (Android Composable) nutzt `WorkoutRepository` direkt. Fix: `DebugGeofenceViewModel`. Debug-Only-Code, niedrige Priorität. Idealerweise gleichzeitig mit Verschiebung von `DebugGeofencePanel` in einen `debug/`-SourceSet (siehe Security-Punkt in CONCERNS.md).
- **Smell 8** — `WorkoutSessionViewModel:488` catched `androidx.sqlite.SQLiteException` inline. Fix: `WorkoutSaveResult`-sealed-Class im Repository. Hängt an der Known-Bug-FK-Race — beste angegangen wenn der Bug selbst gefixt wird.
- **Smell 9** — `WorkoutSessionViewModel` 1171 Zeilen, sollte in ~8 Use-Cases decomposed werden. Brauchts erst VM-Tests + eigene Phase. Größtes Risiko-Item — bewusst deferred.
- **Smell 10** — `WorkoutSessionViewModel` hat Mapping-Duplikate (Zeilen 229-248 vs. 317-336). Fix: `WorkoutTemplate.toSessionExercises()`-Extension oder `StartWorkoutUseCase`/`ResumeWorkoutUseCase`. Hängt an Smell 9, oder als isolierte Pure-Extension lösbar.
- **Smell 14** — `ApiKeyState` global mutable singleton. Fix: `SecureKeyStore.apiKeyStatus: Flow<Boolean>`. Touchpoint: `ApiKeyState.kt` + ~3 ViewModels. Kleines Item, kann Quick-Task werden.

Weitere `CONCERNS.md`-Themen die nicht zu Phase 20 gehören (festgehalten zur Vollständigkeit):

- **Logger-Abstraktion** (println-Logging in OpenAICompatibleClient/OpenFoodFactsApi/VMs) — eigenes kleines Refactor / Quick-Task.
- **Clock-Injection** (18+ direkte `Clock.System.now()`-Calls) — Voraussetzung für deterministische Tests; eigene Phase.
- **VM-/Repository-Tests aufbauen** — separate Phase, Voraussetzung für Smell-9-Angriff.
- **FK-Race-Fix** zwischen `completeSet` und Grace-Period-Auto-Abort — Bug-Fix-Phase, nicht Refactor.
- **iOS `GlobalScope`** in `GamificationStartupIos.kt` — Bug-Fix.
- **Architektur-Enforcement** (Konsist-Test, Modul-Split) — bewusst aus Phase 20 ausgeklammert (D-20-10). Planner darf SEED anlegen.

</deferred>

---

*Phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository*
*Context gathered: 2026-05-18*

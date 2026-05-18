# Phase 20: Clean Architecture Refactor — Dependency-Rule fixen - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 20-clean-architecture-refactor-dependency-rule-fixen-repository
**Areas discussed:** Refactor-Umfang (A), Platform-Port-Placement (B), `SettingsRepository`-Granularität (C)

---

## A — Refactor-Umfang

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Title-Buckets (8 Smells) | Smells 1, 2, 3, 4, 5, 11, 12, 13 — das was im Phase-Titel explizit steht. Smells 6/7/8/10/14 bleiben für später. | ✓ |
| Title + billige Co-Fixes (Smells 6, 7, 8, 10) | Zusätzlich: VM-injects-DAO, Composable-uses-Repo, SQLiteException-Typ-Leak, VM-Mapping-Duplikate — pure-strukturell, je ≤3 Touchpoints. | |
| Title + Co-Fixes + Smell 14 | + `ApiKeyState` global singleton → `Flow<Boolean>` auf `SecureKeyStore`. | |
| Title + Co-Fixes + Smell 14 + Smell 9 | + WorkoutSessionViewModel-Decomposition (1171 Zeilen → ~8 Use-Cases). Verhaltensrisiko hoch, keine VM-Tests. | |

**User's choice:** Nur Title-Buckets (8 Smells)
**Notes:** Strikt "pure Strukturarbeit". Smell 9 deferred wegen Risiko (kein VM-Test-Net). Smells 6/7/8/10/14 sind isoliert machbar und werden als Quick-Tasks / kleine Folgephasen erfasst.

---

## B — Platform-Port-Placement (Smell 12)

### B1 — Wohin mit den `expect`/`actual`-Ports?

| Option | Description | Selected |
|--------|-------------|----------|
| Neue Top-Level-Schicht `infrastructure/` | Ports + Platform-Impls in `commonMain/infrastructure/` + `androidMain/infrastructure/` + `iosMain/infrastructure/`. `domain/` wird wirklich rein Logik. | ✓ |
| Status-quo: alles in `domain/`, Layout unifizieren | Ports bleiben in `domain/{ai,progresspic,...}/`. Android `feature/*` und iOS `data/{geofence,location,permissions}/` ziehen um nach `domain/*`. | |
| `platform/` als Top-Level | Ports + Impls unter `commonMain/platform/` + `androidMain/platform/` + `iosMain/platform/`. Vermischt OS-Ports mit Persistence. | |

**User's choice:** Neue Top-Level-Schicht `infrastructure/`
**Notes:** Klare Layer-Bedeutung — `domain/` rein Logik, `infrastructure/` für OS-/Framework-Kram. Löst Smell-12-Inkonsistenz (Android `feature/*` ↔ iOS `data/*`) durch gemeinsames Ziel.

### B2 — Wo landen Persistence-Platform-Actuals?

| Option | Description | Selected |
|--------|-------------|----------|
| Beide nach `data/{db,preferences}/` | Android `platform/Database.android.kt` + `createDataStore.android.kt` ziehen um nach `data/`. iOS bleibt wo es ist. Android-`platform/`-Folder verschwindet. | ✓ |
| Beide nach `infrastructure/` | Konsistent mit OS-Ports aus B1, aber vermischt Persistence-Factories mit OS-Services. | |
| Beide nach `platform/` | Neuer Top-Level `platform/` neben `infrastructure/`. iOS DB-/DataStore-Files wandern aus `data/` raus. | |

**User's choice:** Beide nach `data/{db,preferences}/`
**Notes:** Persistence = Daten, gehört in `data/`. Trennt sauber von `infrastructure/`.

---

## C — `SettingsRepository`-Split-Granularität (Smell 2)

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Interface extrahieren | `domain/repository/SettingsRepository` als Interface; Impl bleibt monolithisch. `EarlyExitTracker` wechselt zu `EarlyExitBudgetStore`-Narrow-Port. | ✓ |
| Interface + 2-3 Narrow Domain-Ports | + `EarlyExitBudgetStore`, `NutritionGoalsStore` als Narrow-Ports. Domain-Code hängt an Narrow-Ports statt Fat-Interface. | |
| Vollsplit in 3-4 Repos | `UserSettingsRepo`, `NutritionSettingsRepo`, `GamificationSettingsRepo`, `GeofenceStateRepo`. Sauberster Schnitt, ~15 Call-Sites brechen. | |

**User's choice:** Nur Interface extrahieren
**Notes:** Konsistent mit "pure Strukturarbeit". Narrow-Port wird gezielt nur für `EarlyExitTracker` (Smell 13) eingeführt. Vollsplit deferred.

---

## Areas Not Selected

- **D — Architektur-Enforcement going forward** — Konsist-Test / Modul-Split nicht im Phase-20-Scope. Code-Review-Disziplin reicht. Planner darf SEED-Datei anlegen.

## Claude's Discretion

- Wave-Reihenfolge / Plan-Aufteilung
- Granularität der Mapper-Files (one-per-aggregate vs. one-per-entity)
- AiClient-Port-Form (generisch vs. spezialisiert) — Default-Empfehlung: generisch unter `infrastructure/ai/`
- Sub-Pakete in `infrastructure/` (mit oder ohne Sub-Pakete) — Default: Status-quo-Sub-Pakete behalten

## Deferred Ideas

Siehe CONTEXT.md `<deferred>`-Sektion — Smells 6, 7, 8, 9, 10, 14 explizit erfasst; zusätzlich Logger-Abstraktion, Clock-Injection, VM-/Repo-Tests, FK-Race-Fix, iOS-`GlobalScope`-Fix, Architektur-Enforcement.

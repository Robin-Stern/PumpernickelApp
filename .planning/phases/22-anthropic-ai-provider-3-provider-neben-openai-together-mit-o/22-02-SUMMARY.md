---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 02
subsystem: settings / ai-config
tags: [settings, datastore, multi-provider, ai, phase-22]
requires:
  - "ProviderId enum at com.pumpernickel.infrastructure.ai.ProviderId (Plan 22-01)"
  - "ProviderId.fromWireNameOrNull(String?) factory (Plan 22-01)"
  - "ProviderId.wireName property (Plan 22-01)"
provides:
  - "SettingsRepository.activeProvider: Flow<ProviderId>"
  - "SettingsRepository.modelByProvider: Flow<Map<ProviderId, String>>"
  - "SettingsRepository.baseUrlByProvider: Flow<Map<ProviderId, String>>"
  - "SettingsRepository.migratedToMultiProvider: Flow<Boolean>"
  - "Setters: setActiveProvider / setModel / setBaseUrl / setMigratedToMultiProvider"
  - "SettingsRepositoryImpl.Companion default constants for models + base URLs"
affects:
  - "Plan 22-06 (SettingsMigration) — consumes migratedToMultiProvider sentinel + setters"
  - "Plan 22-07 (Koin Wiring) — reads activeProvider via Flow.first() to resolve AiClient"
  - "Plan 22-08 (Settings UI) — edits via setActiveProvider/setModel; observes via Flows"
tech-stack:
  patterns:
    - "DataStore Preferences via stringPreferencesKey/booleanPreferencesKey (existing pattern)"
    - "Flow<T> via dataStore.data.map (no combine{} needed — single-prefs-shot maps suffice)"
    - "Companion object with const defaults (testability + single source of truth)"
key-files:
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt
decisions:
  - "Fully-qualified ProviderId reference in interface file to avoid touching imports order"
  - "setBaseUrl(Anthropic, _) is an explicit early-return no-op (D-22-01 fixed endpoint)"
  - "Companion object with const defaults — consumed by impl + accessible to tests/migration"
metrics:
  duration: "~8 min"
  tasks_completed: 2
  files_modified: 2
  completed_at: "2026-05-19T09:12:16Z"
---

# Phase 22 Plan 02: Multi-Provider Settings Persistence Summary

**One-liner:** `SettingsRepository` um `activeProvider` + per-provider Modell-/Base-URL-Maps + Migration-Sentinel erweitert; DataStore-Persistenz mit fixed Anthropic-Endpoint.

## What Was Built

### Task 1 — SettingsRepository interface extension (commit `7edbe8a`)

Hinzugefügt nach der bestehenden "AI config (D-18-06)"-Sektion in `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt`:

```kotlin
// Multi-Provider AI config (D-22-09 / Phase 22)
val activeProvider: Flow<ProviderId>
suspend fun setActiveProvider(provider: ProviderId)

val modelByProvider: Flow<Map<ProviderId, String>>
suspend fun setModel(provider: ProviderId, model: String)

val baseUrlByProvider: Flow<Map<ProviderId, String>>
suspend fun setBaseUrl(provider: ProviderId, url: String)

val migratedToMultiProvider: Flow<Boolean>
suspend fun setMigratedToMultiProvider(value: Boolean)
```

`ProviderId` ist fully-qualified (`com.pumpernickel.infrastructure.ai.ProviderId`) — keine neuen Imports an der Datei-Spitze, der vorhandene Header bleibt unverändert. Die legacy `aiBaseUrl/aiModel/aiProviderPreset`-Flows sind UNBERÜHRT und werden vom SettingsMigration (Plan 22-06) als Quelle gelesen.

### Task 2 — SettingsRepositoryImpl persistence (commit `ec379ea`)

In `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt`:

1. **Import:** `com.pumpernickel.infrastructure.ai.ProviderId` ergänzt.
2. **Neue DataStore-Keys** (neben den bestehenden `aiProviderPresetKey`/`aiBaseUrlKey`/`aiModelKey`):

| Key (DataStore) | Typ | Zweck |
| --- | --- | --- |
| `active_provider` | string | aktuell aktiver Provider (Default: OpenAI) |
| `model_openai` | string | OpenAI-Modell |
| `model_together` | string | Together-Modell |
| `model_anthropic` | string | Anthropic-Modell |
| `base_url_openai` | string | OpenAI-Endpoint-Override |
| `base_url_together` | string | Together-Endpoint-Override |
| `base_url_anthropic` | string | (deklariert aber NIE gelesen/geschrieben — Anthropic ist fixed) |
| `migrated_to_multi_provider` | boolean | One-shot Migration Sentinel (D-22-08) |

3. **Override-Implementations** vor der schließenden `}` der Klasse, mit `companion object` für die Defaults.

### Defaults pro Provider

| Provider | Modell-Default | Base-URL-Default |
| --- | --- | --- |
| OpenAI | `gpt-4o-mini` | `https://api.openai.com/v1` |
| Together | `google/gemma-4-31B-it` | `https://api.together.ai/v1` |
| Anthropic | `claude-opus-4-7` (D-22-06) | `https://api.anthropic.com` (FIXED) |

### Anthropic-BaseURL-Fixed-Constraint (D-22-01)

- `baseUrlByProvider`-Flow liefert für Anthropic IMMER `DEFAULT_BASE_URL_ANTHROPIC` (`https://api.anthropic.com`), unabhängig vom DataStore-Inhalt.
- `setBaseUrl(ProviderId.Anthropic, _)` ist ein **early-return No-Op** — der Wert wird nicht persistiert. Das macht es Test-/Migration-tauglich: man kann den Setter aufrufen ohne Exception, aber er bewirkt nichts.

### Migration-Sentinel (D-22-08)

`migratedToMultiProvider: Flow<Boolean>` (Default `false`) + `setMigratedToMultiProvider(value)`. Plan 22-06's `SettingsMigration.run()` wird:
1. `migratedToMultiProvider.first()` lesen
2. wenn `false` → legacy `aiBaseUrl/aiModel/aiProviderPreset` auswerten, neuen Provider-State setzen, Credential umkopieren
3. `setMigratedToMultiProvider(true)` schreiben — idempotent.

## Backward-Compatibility

Die alten Flows `aiProviderPreset` / `aiBaseUrl` / `aiModel` und ihre Setter sind **unverändert geblieben**. Sie werden weiter von bestehenden UI-/VM-Stellen gelesen (Phase 18) bis Plan 22-06's Migration und Plan 22-08's UI-Refactor sie ablöst. Konkret:
- Keine Renames, keine Signaturänderungen, keine `@Deprecated`-Marker — diese Phase-22-Plans entscheiden über die Lebensdauer.

## Build / Verification State in diesem Worktree

**Wichtig:** Dieser Worktree (Wave 1, `depends_on: []`) sieht Plan 22-01's `ProviderId.kt` NICHT, weil beide parallel laufen. Daher würde `./gradlew :shared:compileCommonMainKotlinMetadata` HIER lokal scheitern (`Unresolved reference: ProviderId` + `fromWireNameOrNull` + `wireName`).

Das ist erwartet — der Orchestrator merged alle Wave-1-Worktrees zusammen, und die Union (Plan 22-01 + Plan 22-02) wird grün kompilieren. Die plan-vorgeschriebenen grep-Verifikationen wurden alle bestanden:

- `grep "val activeProvider: Flow"` → 1 Treffer im Interface
- `grep "val modelByProvider: Flow<Map<com.pumpernickel.infrastructure.ai.ProviderId, String>>"` → OK
- `grep "val baseUrlByProvider: Flow<Map<...>>"` → OK
- `grep "val migratedToMultiProvider: Flow<Boolean>"` → OK
- `grep "val aiBaseUrl: Flow<String>"` (legacy bleibt) → OK
- `grep "override val activeProvider: Flow<ProviderId>"` → OK in Impl
- `grep "DEFAULT_MODEL_ANTHROPIC = \"claude-opus-4-7\""` → OK
- `grep "DEFAULT_BASE_URL_ANTHROPIC = \"https://api.anthropic.com\""` → OK
- `grep "ProviderId.Anthropic -> return"` → OK (No-Op-Pfad)

Die finale Cross-Plan-Compile-Verifikation gehört in den Wave-1-Merge-Schritt des Orchestrators.

## Deviations from Plan

None — Plan wurde 1:1 wie geschrieben ausgeführt. Beide Tasks (Interface-Extension, Impl-Persistence) folgen wörtlich den `<action>`-Blöcken.

## API Contract Notes für Folge-Pläne

- **Plan 22-06 (SettingsMigration):** `SettingsRepositoryImpl.Companion.DEFAULT_*`-Konstanten sind `const val` und damit in Tests + Migration referenzierbar.
- **Plan 22-07 (Koin Wiring):** `activeProvider.first()` ist der Resolver-Hook. Achtung: das ist suspend — wenn Koin `factory` nicht-suspend ist, muss ein nicht-blockierender Pfad (z.B. eigene `DispatchingAiClient`-Facade, siehe PATTERNS.md "Option C") gewählt werden.
- **Plan 22-08 (Settings UI):** `modelByProvider.collectAsState()` liefert eine `Map<ProviderId, String>` — UI rendert pro Provider eine eigene Row mit Modell-Dropdown gegen `viewModel.setModel(provider, model)`.

## Files Touched

| Datei | Änderung | Commit |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt` | +31 Zeilen (neue Interface-Section) | `7edbe8a` |
| `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt` | +76 Zeilen (Import + Keys + Overrides + Companion) | `ec379ea` |

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt`: FOUND
- Commit `7edbe8a` (interface): FOUND in `git log --oneline --all`
- Commit `ec379ea` (impl): FOUND in `git log --oneline --all`
- All plan-specified grep verifications: PASSED (siehe oben)
- Cross-plan gradle compile-check intentionally deferred to Wave-1 merge (Plan 01 dependency)

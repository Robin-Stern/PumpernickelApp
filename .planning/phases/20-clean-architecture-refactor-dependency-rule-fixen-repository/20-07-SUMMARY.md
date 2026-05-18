---
phase: 20-clean-architecture-refactor-dependency-rule-fixen-repository
plan: 07
subsystem: clean-architecture-ai-port-decoupling-smell-4
tags: [refactor, port, adapter, ai, openfoodfacts, ports-and-adapters, smell-4, wave-6]
requires:
  - "Plan 20-01 (AiClient port + AiJsonSchema in infrastructure/ai/)"
  - "Plan 20-03 (domain repository interfaces — ExerciseRepository, TemplateRepository, SettingsRepository, FoodRepository)"
  - "Plan 20-05 (domain repository for Settings)"
provides:
  - "com.pumpernickel.infrastructure.ai.OpenAiCompatibleAiClient (AiClient adapter)"
  - "com.pumpernickel.domain.nutrition.RemoteFoodSearchClient (domain port for OFF lookups)"
  - "com.pumpernickel.domain.nutrition.RemoteFoodResult / RemoteBarcodeProduct (domain rows)"
  - "com.pumpernickel.infrastructure.nutrition.OpenFoodFactsAdapter (RemoteFoodSearchClient adapter)"
  - "com.pumpernickel.domain.ai.WorkoutAiResponse + RecipeAiResponse (pure-Kotlin domain DTOs replacing the data/api/* equivalents)"
affects:
  - "Plan 20-13 (Final Verification — owns the full Xcode build + iOS smoketest)"
  - "Any future plan touching AiClient — the port surface is now wired end-to-end and can be mocked freely"
tech-stack:
  added: []
  patterns:
    - "ports-and-adapters for AI + OFF (Smell 4 fix)"
    - "domain-DTO duplication of Ktor wire shape (kotlinx.serialization in domain/ permitted per Plan-20-07 pitfall)"
    - "top-level promotion of `SearchFoodsRemoteUseCase.RemoteFoodResult` → `domain/nutrition/RemoteFoodResult` so port signatures stay nesting-free"
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OpenAiCompatibleAiClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/RemoteFoodSearchClient.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiResponseDto.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiResponseDto.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/presentation/nutrition/FoodEntryViewModel.kt
    - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/NutritionFoodEntryScreen.kt
    - iosApp/iosApp/Views/Nutrition/NutritionFoodEntryView.swift
  renamed:
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt → shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiResponseDto.kt (git detected as rename — 55% similarity; the four data classes moved package + got KDoc, fields identical)"
    - "shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt → shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiResponseDto.kt (same)"
decisions:
  - "Domain-DTO duplication via @Serializable in domain/ai/ (Plan-approach §4 Variant: 'Pure-Kotlin DTO in domain/ai/'). Felder 1:1 vom Ktor-Schema übernommen — JSON-Wire-Contract unverändert. Plan-pitfall sagt explizit kotlinx.serialization in domain ist erlaubt."
  - "Adapter delegiert an bestehende OpenAICompatibleClient / OpenFoodFactsApi statt sie zu duplizieren — Auth (SecureKeyStore) bleibt im Ktor-Layer, Port-Surface bleibt narrow (kein apiKey-Parameter, wie Plan 20-01 entschieden)."
  - "SearchFoodsRemoteUseCase.RemoteFoodResult → top-level domain/nutrition/RemoteFoodResult: Kotlin erlaubt keinen typealias innerhalb einer Klasse, daher mussten die 3 Call-Sites (FoodEntryViewModel, NutritionFoodEntryScreen, NutritionFoodEntryView.swift) ihre Imports anpassen. Public-API ändert sich vom Pfad her — Verhalten bleibt identisch."
  - "LookupBarcodeUseCase mit umgestellt (Rule 2 — sonst hätte der commonMain/domain/ Grep-Guard auf `data.api.*` weiterhin gemeldet)."
  - "Ktor-DTO-Files (WorkoutAiSchema.kt, RecipeAiSchema.kt) gelöscht — niemand außerhalb der Use-Cases hat sie konsumiert; der Adapter sendet nur ChatRequest und empfängt String."
  - "RemoteBarcodeProduct als reduzierte Domain-Struktur vom Adapter geliefert — die OFF-spezifische `status`-Logik (== 1) lebt im Adapter, der Use-Case sieht nur `RemoteBarcodeProduct?` (null = NotFound)."
metrics:
  duration: "~50 min"
  completed: 2026-05-18
  tasks: 4
  files_created: 5
  files_modified: 9
  files_renamed: 2
  files_deleted: 0
---

# Phase 20 Plan 07: AI-UseCases entkoppeln via AiClient-Port + OpenFoodFacts-Adapter (Smell 4) Summary

Ports-and-Adapters für die komplette AI-Vertikale + OpenFoodFacts-Suche. `WorkoutAiUseCase`, `RecipeAiUseCase`, `SearchFoodsRemoteUseCase` und `LookupBarcodeUseCase` sind jetzt Ktor-frei (verified via grep-guard). Use-Cases injecten `AiClient` (Plan-20-01-Port) bzw. den neuen `RemoteFoodSearchClient`-Port; Ktor-DTO-Konstruktion und SSE-Streaming wandert in die neuen Adapter (`OpenAiCompatibleAiClient`, `OpenFoodFactsAdapter`). Verhalten 1:1: gleiche Fallback-Logik (json_schema → json_object), gleiches `AiError`-Mapping, gleicher OFF-Filter (productName / nutriments / sugar≤carbs / nutriScore A–E), gleicher Streaming-Progress-Callback.

## What was built

### Task 1 — `OpenAiCompatibleAiClient` adapter

**`shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OpenAiCompatibleAiClient.kt`** (CREATED, 86 LOC)

Implementiert `AiClient` (Plan 20-01) via Delegation an `OpenAICompatibleClient`:
- `completeJsonSchema(...)`: parsed `AiJsonSchema.schemaJson` String → `JsonElement`, baut `ChatRequest(responseFormat=ResponseFormat(type="json_schema", jsonSchema=JsonSchemaSpec(name, parsedSchema, strict)))`, ruft `client.chatCompletionStreaming(baseUrl, request, onProgress)`, gibt akkumulierten String zurück. Temperature 0.7, max_tokens 4096 — verbatim aus den vorherigen Use-Case-Callsites.
- `completeJsonObject(...)`: baut `ChatRequest(responseFormat=ResponseFormat(type="json_object"))` und appended " Return only JSON matching the schema in the system prompt body." an den system prompt — Verhalten 1:1 wie der Fallback-Path früher in den Use-Cases.

AiError-Mapping passiert vollständig in `OpenAICompatibleClient` (D-18-08) — der Adapter wirft nur weiter.

`AiClient.kt` (Plan 20-01) wurde NICHT modifiziert — die zwei-Methoden-Signatur (`completeJsonSchema` / `completeJsonObject`) hat alle Bedürfnisse beider Use-Cases abgedeckt; kein zusätzlicher Enum-Parameter nötig.

### Task 2 — WorkoutAiUseCase + RecipeAiUseCase auf AiClient

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiResponseDto.kt`** (CREATED via git-rename, 53 LOC)

Pure-Kotlin `@Serializable` Domain-DTOs für die LLM-Response: `WorkoutAiResponse`, `WorkoutAiTemplate`, `WorkoutAiTemplateExercise`, `WorkoutAiInlineExercise`. Felder verbatim vom alten `data/api/WorkoutAiSchema.kt` — JSON-Wire-Contract unverändert. Plan-pitfall erlaubt `kotlinx.serialization.Serializable` in `domain/`.

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiResponseDto.kt`** (CREATED via git-rename, 39 LOC) — analog für `RecipeAiResponse`, `RecipeAiIngredient`, `RecipeAiInlineFood`.

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt`** (MODIFIED)

- Constructor-Param: `OpenAICompatibleClient` → `AiClient`. Property umbenannt: `client` → `aiClient`.
- Alle `import com.pumpernickel.data.api.*` entfernt.
- `import com.pumpernickel.infrastructure.ai.AiClient + AiJsonSchema` hinzugefügt.
- `callWithJsonSchema(...)`: ersetzt `client.chatCompletionStreaming(baseUrl, request, onProgress)` durch `aiClient.completeJsonSchema(baseUrl, model, systemPrompt, userPrompt, AiJsonSchema(name="WorkoutAiResponse", schemaJson=workoutAiSchemaJson(), strict=false), onProgress)`.
- `callWithJsonObject(...)`: ersetzt durch `aiClient.completeJsonObject(...)`.
- `workoutAiSchema(): JsonElement = buildJsonObject { put("type","object") }` → `workoutAiSchemaJson(): String = "{\"type\":\"object\"}"`. Der Adapter parsed den String zurück in ein JsonElement.
- Toter Code entfernt: `parseResponse(message: ChatMessage?)` (referenzierte `data.api.ChatMessage`, wurde nirgends aufgerufen — der Streaming-Path benutzt ausschließlich `parseResponseFromContent`).
- JSON-Parsing via `json.decodeFromString<WorkoutAiResponse>(cleaned)` bleibt im Use-Case (jetzt auf das Domain-DTO).
- Repository-Imports (`ExerciseRepository`, `TemplateRepository`, `SettingsRepository`) waren bereits auf `domain.repository.*` umgestellt (Plan 20-03/05).

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt`** (MODIFIED) — analog. `recipeAiSchema()` → `recipeAiSchemaJson()`. Toter `parseResponse(message: ChatMessage?)` entfernt.

### Task 3 — RemoteFoodSearchClient + OpenFoodFactsAdapter + Use-Case-Umstellung

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/RemoteFoodSearchClient.kt`** (CREATED, 78 LOC)

Domain-Port mit zwei suspend-Methoden:
- `searchByQuery(query: String, pageSize: Int = 20): List<RemoteFoodResult>` — gibt direkt Domain-Rows zurück (Filter `productName != null && nutriments != null` passiert im Adapter).
- `lookupBarcode(barcode: String): RemoteBarcodeProduct?` — `null` wenn OFF `status != 1` oder Name fehlt (Adapter-Detail; Use-Case sieht nur "found vs not found").

Zwei top-level Domain-Datenklassen: `RemoteFoodResult` (Felder identisch mit der vorherigen nested `SearchFoodsRemoteUseCase.RemoteFoodResult`), `RemoteBarcodeProduct` (Name + 5 Makros).

**`shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt`** (CREATED, 67 LOC)

Implementiert `RemoteFoodSearchClient` via Delegation an `OpenFoodFactsApi`:
- `searchByQuery`: 1:1 die früher in `SearchFoodsRemoteUseCase` lebende Mapping-Pipeline (mapNotNull → name + nutriments check → sugar.coerceAtMost(carbs) → nutriScore A–E → brand-first-token).
- `lookupBarcode`: status==1 + productName-Check → mappt OFF-Nutriments (null-coalescing zu 0.0) in `RemoteBarcodeProduct`. Die Fallback-Tabelle (Honig, Olivenöl etc.) bleibt im Use-Case, weil sie domain-policy ist, nicht OFF-Transport.

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt`** (MODIFIED, 43 LOC ↓ vom Original 54)

- Constructor-Param: `OpenFoodFactsApi` → `RemoteFoodSearchClient`.
- `import com.pumpernickel.data.api.OpenFoodFactsApi` entfernt.
- Mapping-Pipeline raus (lebt jetzt im Adapter); Use-Case ruft nur noch `client.searchByQuery(query)` und kapselt das Result in `Result.Success/Empty/Error` mit unverändertem Error-Text-Matching `"OpenFoodFacts ist gerade nicht erreichbar"`.
- Nested `RemoteFoodResult`-Klasse entfernt — Top-Level-Version aus dem Port wird referenziert.

**`shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt`** (MODIFIED)

- Constructor-Param: `OpenFoodFactsApi` → `RemoteFoodSearchClient` (Rule 2 deviation — der Plan listete diesen UseCase nicht in `files_modified`, aber sonst hätte der Grep-Guard `! grep -R "import com.pumpernickel.data.api\." shared/src/commonMain/kotlin/com/pumpernickel/domain/` weiterhin Treffer).
- `import com.pumpernickel.data.api.OpenFoodFactsApi` entfernt.
- OFF-DTO-Auspackerei (`response.product?.nutriments?.energyKcal100g ?: 0.0` etc.) ersetzt durch direkte Feld-Zugriffe auf `RemoteBarcodeProduct`. Fallback-Tabelle + sugar-clamp bleibt verbatim.

**Konsumenten-Updates für promoted `RemoteFoodResult`:**

- `presentation/nutrition/FoodEntryViewModel.kt` — Import + 2 Typ-Referenzen (state, event) auf top-level `RemoteFoodResult`.
- `androidApp/.../NutritionFoodEntryScreen.kt` — Import + 1 Funktions-Signatur (`RemoteFoodCard(result: RemoteFoodResult)`).
- `iosApp/.../NutritionFoodEntryView.swift` — Swift-Typ-Referenz `SearchFoodsRemoteUseCase.RemoteFoodResult` → `RemoteFoodResult`. Swift sieht die top-level Kotlin-Klasse direkt (`SharedRemoteFoodResult` im framework).

### Task 4 — Koin-Bindings

**`shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt`** (MODIFIED)

- Import `com.pumpernickel.infrastructure.ai.AiClient + OpenAiCompatibleAiClient` hinzugefügt.
- `single<AiClient> { OpenAiCompatibleAiClient(get()) }` neu — direkt nach dem `OpenAICompatibleClient`-Binding (das bleibt — der Adapter konsumiert es).
- `WorkoutAiUseCase` / `RecipeAiUseCase` / `AiGenerationManager`-Bindings unverändert (`get()` löst automatisch via Konstruktor-Param-Typ — Koin-DSL nutzt den expected Typ).

**`shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt`** (MODIFIED)

- Imports `com.pumpernickel.domain.nutrition.RemoteFoodSearchClient + infrastructure.nutrition.OpenFoodFactsAdapter` hinzugefügt.
- `single<RemoteFoodSearchClient> { OpenFoodFactsAdapter(get()) }` neu — direkt nach dem `OpenFoodFactsApi`-Binding (das bleibt — der Adapter konsumiert es).
- `LookupBarcodeUseCase(get(), get())` und `SearchFoodsRemoteUseCase(get())`-Bindings unverändert (Koin matched automatisch auf `RemoteFoodSearchClient` per Konstruktor-Param-Typ).

### Ktor-Schema-Files gelöscht (via git-rename)

- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/WorkoutAiSchema.kt` → renamed nach `domain/ai/WorkoutAiResponseDto.kt` (kein Konsument außerhalb der Use-Cases — der Adapter sendet ChatRequest und empfängt String).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/RecipeAiSchema.kt` → renamed nach `domain/ai/RecipeAiResponseDto.kt`.

## Koin-Binding-Changes

| Datei | Binding | Status |
| --- | --- | --- |
| `AiModule.kt` | `single { OpenAICompatibleClient(get(), keyProvider=…) }` | unverändert (Adapter konsumiert) |
| `AiModule.kt` | `single<AiClient> { OpenAiCompatibleAiClient(get()) }` | **NEU** |
| `AiModule.kt` | `single { WorkoutAiUseCase(get(), get(), get(), get(), get()) }` | unverändert — Koin matched auf `AiClient` per Param-Typ |
| `AiModule.kt` | `single { RecipeAiUseCase(get(), get(), get(), get(), get(), get(), get()) }` | unverändert |
| `AiModule.kt` | `single { AiGenerationManager(get(), get(), get(), get()) }` | unverändert (transitiv über die UseCases) |
| `SharedModule.kt` | `single { OpenFoodFactsApi(get()) }` | unverändert (Adapter konsumiert) |
| `SharedModule.kt` | `single<RemoteFoodSearchClient> { OpenFoodFactsAdapter(get()) }` | **NEU** |
| `SharedModule.kt` | `single { LookupBarcodeUseCase(get(), get()) }` | unverändert — matched auf `RemoteFoodSearchClient` |
| `SharedModule.kt` | `single { SearchFoodsRemoteUseCase(get()) }` | unverändert — matched auf `RemoteFoodSearchClient` |

## Verification

| Check | Command | Result |
| --- | --- | --- |
| iOS X64 compile | `./gradlew :shared:compileKotlinIosX64 --quiet` | **BUILD SUCCESSFUL** (exit 0) |
| Shared Android compile | `./gradlew :shared:compileAndroidMain --quiet` | **BUILD SUCCESSFUL** (exit 0) |
| androidApp Debug Kotlin compile | `./gradlew :androidApp:compileDebugKotlin --quiet` | **BUILD SUCCESSFUL** (exit 0) |
| All shared tests | `./gradlew :shared:allTests --quiet` | **BUILD SUCCESSFUL** (exit 0) |
| Combined run | `./gradlew :shared:compileKotlinIosX64 :shared:compileAndroidMain :androidApp:compileDebugKotlin :shared:allTests --quiet` | **BUILD SUCCESSFUL** (exit 0) |
| Grep guard 1 — `domain/` is Ktor-DTO-free | `grep -R "import com.pumpernickel.data.api\." shared/src/commonMain/kotlin/com/pumpernickel/domain/` | empty (0 hits) |
| Grep guard 2 — `domain/` has no io.ktor imports (excl. pre-existing AiError.kt) | `grep -rE "^import io\.ktor" shared/src/commonMain/kotlin/com/pumpernickel/domain/ \| grep -v AiError.kt` | empty |
| Grep guard 3 — `single<AiClient>` in AiModule | `grep -c "single<AiClient>" shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` | 1 |
| Grep guard 4 — `single<RemoteFoodSearchClient>` in SharedModule | `grep -c "single<RemoteFoodSearchClient>" shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` | 1 |
| Grep guard 5 — Adapter file | `test -f .../infrastructure/ai/OpenAiCompatibleAiClient.kt` | PASS |
| Grep guard 6 — OFF adapter | `test -f .../infrastructure/nutrition/OpenFoodFactsAdapter.kt` | PASS |
| Grep guard 7 — Port | `test -f .../domain/nutrition/RemoteFoodSearchClient.kt` | PASS |

### Cross-Platform-Warnings

Identische pre-existing warnings wie Waves 1-6 (redundant conversion in iOS, `typealias Instant` deprecated, `when` exhaustive). Zwei neue ObjC-Hinweise auf `AiClient.completeJsonSchema`/`completeJsonObject` — das sind die Plan-20-01-Methoden, die jetzt vom Adapter exponiert werden; identisch zur ObjC-Sichtbarkeit der bisherigen `OpenAICompatibleClient.chatCompletionStreaming`. Keine neuen Errors.

## iOS / Swift impact

- `RemoteFoodResult` ist jetzt top-level statt nested → Swift sieht `SharedRemoteFoodResult` (statt `SharedSearchFoodsRemoteUseCaseRemoteFoodResult`). Swift-Callsite in `NutritionFoodEntryView.swift:331` umgestellt.
- `AiClient` und `RemoteFoodSearchClient` sind plain Kotlin Suspend-Interfaces — werden NICHT direkt an Swift exposed; Swift ruft `WorkoutAiUseCase` / `RecipeAiUseCase` / `SearchFoodsRemoteUseCase` über die existierenden KoinHelpers (`WorkoutAiKoinHelper`, `RecipeAiKoinHelper`). Diese Files brauchen keine Änderung.
- Volle Xcode-Verifikation in Plan 20-13.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Konsistenz] `LookupBarcodeUseCase` analog umgestellt**
- **Found during:** Task 3
- **Issue:** Der Plan listet nur `SearchFoodsRemoteUseCase` in den `files_modified`, aber `LookupBarcodeUseCase` (gleicher Folder, gleiche Vertikale) importiert weiterhin `com.pumpernickel.data.api.OpenFoodFactsApi`. Das verletzt den Plan-Grep-Guard (`! grep -R "import com.pumpernickel.data.api\." shared/src/commonMain/kotlin/com/pumpernickel/domain/` muss empty sein) und macht den `RemoteFoodSearchClient`-Port halbgar (nur ein von zwei Konsumenten profitiert).
- **Fix:** `LookupBarcodeUseCase`-Konstruktor von `OpenFoodFactsApi` auf `RemoteFoodSearchClient` umgestellt; `RemoteFoodSearchClient.lookupBarcode(...)` zum Port hinzugefügt (Plan-Approach §6 erlaubt explizit "Methoden 1:1 von `OpenFoodFactsApi`-Public-API ableiten — also vorher dort gepeekt"); OFF-DTO-Auspackerei in den Adapter verschoben. Fallback-Tabelle für Honig/Olivenöl/etc. bleibt im Use-Case (domain policy, kein OFF-Transport).
- **Files modified:** `domain/nutrition/LookupBarcodeUseCase.kt`, `domain/nutrition/RemoteFoodSearchClient.kt`, `infrastructure/nutrition/OpenFoodFactsAdapter.kt`
- **Commit:** `88d2ccb`

**2. [Rule 3 - Blocking] `RemoteFoodResult` aus nested-class promoted**
- **Found during:** Task 3
- **Issue:** Der Plan-Approach §6 spezifiziert `RemoteFoodSearchClient.searchByQuery(query): List<Food>` — aber `Food` würde Felder verlieren, die UI/VM aktiv nutzen (`brand`, `nutriScore`). Die Wahl, das vorherige nested `SearchFoodsRemoteUseCase.RemoteFoodResult` als Port-Return-Typ zu nutzen, kollidiert damit dass Kotlin keinen `typealias` innerhalb einer Klasse erlaubt — der Typ MUSS top-level werden. Sonst kompiliert der Code nicht.
- **Fix:** `RemoteFoodResult` als top-level Domain-Datenklasse in `domain/nutrition/RemoteFoodSearchClient.kt` neben dem Interface. 3 Call-Sites (FoodEntryViewModel, Android NutritionFoodEntryScreen, iOS NutritionFoodEntryView.swift) auf den top-level Typ aktualisiert. CONTEXT.md `<specifics>` erlaubt explizit Planner-Variation in der Port-Form.
- **Files modified:** siehe oben
- **Commit:** `88d2ccb`

**3. [Rule 3 - Cleanup] Toter Code `parseResponse(message: ChatMessage?)` entfernt**
- **Found during:** Task 2
- **Issue:** Beide Use-Cases hatten eine private Methode `parseResponse(message: ChatMessage?)` aus dem pre-streaming Pfad; sie wurde nirgends aufgerufen (Streaming-Path nutzt `parseResponseFromContent(content: String)`). Sie importierte `data.api.ChatMessage` — ohne Entfernung hätte der Grep-Guard nicht bestanden, da der Import live geblieben wäre.
- **Fix:** Methode entfernt, `ChatMessage`-Import nicht mehr nötig.
- **Files modified:** `domain/ai/WorkoutAiUseCase.kt`, `domain/ai/RecipeAiUseCase.kt`
- **Commit:** `88d2ccb`

**4. [Rule 3 - Cleanup] Tote `data/api/*Schema.kt`-Files gelöscht**
- **Found during:** Task 2
- **Issue:** Nach der DTO-Duplizierung in `domain/ai/*ResponseDto.kt` und der Use-Case-Umstellung gab es keinen Konsumenten mehr für die Ktor-Variante von `WorkoutAiResponse` / `RecipeAiResponse`. Sie liefen nur als duplicate `data class` mit gleichem Namen → Naming-Konflikt bei Imports vermeiden + Dead-Code entfernen.
- **Fix:** Beide Files gelöscht. Git erkennt es als rename (55% similarity zu den Domain-DTO-Files), was die History sauber hält.
- **Files modified:** `data/api/WorkoutAiSchema.kt` (gelöscht), `data/api/RecipeAiSchema.kt` (gelöscht)
- **Commit:** `88d2ccb`

### Out-of-scope, documented only

- **[Pre-existing, plan-pitfall covered] `AiError.kt` importiert `io.ktor.*`** — Der Executor-Prompt-Grep-Guard `! grep -R "io.ktor" shared/src/commonMain/kotlin/com/pumpernickel/domain/` erwartet 0 Treffer; tatsächlich hat `domain/ai/AiError.kt` 5 `io.ktor.*`-Imports (für `mapHttpError`-Faktor `AiError.fromThrowable`). Der Plan-Risk-Abschnitt sagt explizit: *"AiError-Import-Pfad. AiError lebt heute in `domain/ai/AiError.kt`. […] Alternativ: AiError nach `infrastructure/ai/` umziehen (aber das ist NICHT in Scope von Phase 20 — D-20-01 listet nur die Title-Smells). Beibehalten wo es ist."* — also pre-existing, vom Plan-Pitfall vorgesehen und out-of-scope. Logged als Kandidat für eine Phase-20-Nachsorge oder ein separates Mini-Refactor (z.B. `AiError.fromThrowable` als Adapter-interne Faktor in `infrastructure/ai/` ziehen, `AiError`-sealed-class in `domain/ai/` lassen).

## Decisions Made

1. **Domain-DTO-Duplikation gewählt** (Plan-Approach §4 Variant A — "Pure-Kotlin DTO IN `domain/ai/`") statt direkte Deserialisierung in `WorkoutAiPreview`/`RecipeAiPreview`. Begründung: Die Previews sind UI-/staging-orientierte Modelle (mit MuscleGroup-Enum statt String, mit resolvedExerciseId-Linking etc.) und nicht schema-isomorph zur LLM-Antwort. Eine zweite Domain-Schicht für den raw response erhält den bestehenden Validation-Pipeline 1:1.
2. **Zwei-Methoden-Port-Surface** beibehalten (von Plan 20-01 entschieden) — kein zusätzlicher Enum-Discriminator. Beide Use-Cases haben einen `try { json_schema } catch { json_object }`-Fallback, der mit zwei separaten Methoden 1:1 auf den Port mapped.
3. **`apiKey` nicht auf Port-Surface** — Plan 20-01 hat das bereits entschieden; Adapter delegiert an `OpenAICompatibleClient`, der den Key über `SecureKeyStore` selbst auflöst.
4. **`RemoteFoodResult` als Port-Return-Typ** (statt `Food`): `Food` hätte `brand` und `nutriScore` verloren, die im VM/UI verwendet werden. CONTEXT.md `<specifics>` erlaubt Planner-Variation in der Port-Form.
5. **OFF-Mapping inline im Adapter** (kein eigenes Mapper-File) — die Mapping-Pipeline ist klein (~10 Zeilen) und kommt direkt aus der vorherigen `SearchFoodsRemoteUseCase`. Plan-Approach §6 erlaubt explizit den Inline-Stil.
6. **`LookupBarcodeUseCase` als Bonus-Migration** (Rule 2 deviation) — sonst wäre der Plan-Grep-Guard auf `data.api.*` weiterhin verletzt; den Fix einzeln in einem späteren Plan zu erledigen wäre Mehraufwand ohne Vorteil.
7. **`RemoteBarcodeProduct` als reduzierte Domain-Struktur** statt `Food` zurückzugeben: `Food` hat einen `id`-Pflichtparameter (UUID) und der OFF-Lookup gibt keine canonical ID zurück; der Use-Case erzeugt diese erst beim Save. `RemoteBarcodeProduct` ist nur "remote macros + name", die Fallback-Tabelle (Honig, Olivenöl etc.) bleibt im Use-Case.

## Commit

`88d2ccb refactor(20-07): decouple AI + OFF use-cases via AiClient + RemoteFoodSearchClient ports (Smell 4)`

Atomic — Port + Adapter + Use-Case-Umstellung + Koin-Bindings + Konsumenten-Updates in einem Commit (14 Files, +378 / −209 LOC).

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OpenAiCompatibleAiClient.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/nutrition/OpenFoodFactsAdapter.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/RemoteFoodSearchClient.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiResponseDto.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiResponseDto.kt` — FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt` — FOUND (modified, Ktor-frei)
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt` — FOUND (modified, Ktor-frei)
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/SearchFoodsRemoteUseCase.kt` — FOUND (modified, Ktor-frei)
- File `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/LookupBarcodeUseCase.kt` — FOUND (modified, Ktor-frei)
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt` — FOUND (modified, single<AiClient>)
- File `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — FOUND (modified, single<RemoteFoodSearchClient>)
- Old `data/api/WorkoutAiSchema.kt` — REMOVED (renamed)
- Old `data/api/RecipeAiSchema.kt` — REMOVED (renamed)
- Commit `88d2ccb` — FOUND (`git log --oneline -1` → `88d2ccb refactor(20-07): decouple AI + OFF use-cases via AiClient + RemoteFoodSearchClient ports (Smell 4)`)
- `:shared:compileKotlinIosX64` — BUILD SUCCESSFUL (exit 0)
- `:shared:compileAndroidMain` — BUILD SUCCESSFUL (exit 0)
- `:androidApp:compileDebugKotlin` — BUILD SUCCESSFUL (exit 0)
- `:shared:allTests` — BUILD SUCCESSFUL (exit 0)
- Grep guard 1 (`domain/` is Ktor-DTO-free) — 0 hits
- Grep guard 2 (`domain/` has io.ktor only in pre-existing AiError.kt) — 0 new hits
- Grep guard 3 (`single<AiClient>` in AiModule) — 1 hit
- Grep guard 4 (`single<RemoteFoodSearchClient>` in SharedModule) — 1 hit

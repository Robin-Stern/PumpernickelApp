# Phase 18: AI Features — F6 Workout Generation + F8 Meal Generation (BYOK, OpenAI-compatible HTTPS) - Context

**Gathered:** 2026-05-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship two AI generation flows on a single Bring-Your-Own-API-Key OpenAI-compatible HTTPS LLM transport.

**F6 — Workout AI:** From the Workout tab, an AI button opens a small form (target muscles via Phase-14 `AnatomyPickerSheet`, exercise count, optional Push-Pull-Legs / Upper-Lower / Full-Body split). The app calls the configured LLM with a versioned system prompt + JSON schema and persists the result(s) through the existing `WorkoutTemplate` repository. Multi-template "split" generation produces N templates from one form submission via a single LLM call returning an array.

**F8 — Meal AI:** From the Nutrition tab, an AI button reads today's `ConsumptionEntry` log against `NutritionGoals` to compute remaining kcal + protein/carbs/fat/sugar, prompts the LLM with those targets + a recipe JSON schema. The returned recipe is persisted via the existing `Recipe` entity after a preview-and-save step.

**Shared plumbing:** A single `OpenAICompatibleClient` (Ktor); a Settings section with API key + base URL stored in Keychain (iOS) / EncryptedSharedPreferences (Android) — never DataStore plaintext; provider preset dropdown (OpenAI / Together.AI / OpenRouter / Groq / Custom) with override; `response_format: json_schema` with schema-validate-and-retry fallback; English templated prompts versioned in source; static skeleton + Cancel during generation; distinct error copy per error class (timeout / network / 4xx / 5xx / refusal / schema-invalid).

**Schema migration:** Room v9 → v10 — additive AutoMigration adding a nullable `source` column (USER / AI) to `WorkoutTemplate`, `Exercise`, `Recipe`, `Food` so AI-authored entries are taggable without losing existing rows. The LLM is granted **authoring rights** over both the Exercise catalog and the Food list — when the model needs an exercise or food that doesn't exist, the system prompt instructs it to emit a full new entry with all required fields, which the app persists before linking from the generated Template / Recipe.

Phase 15 gamification, Phase 16 nutrition goals, and Phase 17 progress-pic flows continue to work unchanged. No on-device LLM, no MCP server, no tool-use, no multi-provider abstraction (all deferred — see SEED-002 and `.planning/notes/ai-features-design-decisions.md`).

</domain>

<decisions>
## Implementation Decisions

### Entry points & form UX

- **D-18-01 (Symmetric AI buttons on Workout + Nutrition tabs):** Two symmetric "AI" affordances — one on the Workout tab (opens F6 form, generates `WorkoutTemplate`(s)) and one on the Nutrition tab (opens F8 form, generates a `Recipe`). The user's framing: "an AI button on both workouts and nutrition tabs, one triggers workout gen, other triggers meal gen". Exact placement in each tab's toolbar / surface is Claude's discretion at planning time (lean: top-bar action icon next to the existing add affordances; on iOS, a SwiftUI toolbar item).
- **D-18-02 (F6 muscle picker reuses Phase-14 `AnatomyPickerSheet`):** The F6 form's target-muscle input is the existing anatomy picker in multi-select mode. Visual, consistent with exercise creation, no new UI to design. Android reuses the existing Compose component; iOS reuses the SwiftUI port (built from shared `MuscleRegionPaths` per Phase-14 D).
- **D-18-03 (F6 form fields = muscles + count + split):** Three structured inputs:
  1. Target muscles (`AnatomyPickerSheet`, ≥1 selected to enable Generate)
  2. Exercise count (drum/picker, e.g. range 3–8 — exact range Claude's discretion)
  3. Split type — `None` / `Push-Pull-Legs` / `Upper-Lower` / `Full-Body`. `None` produces a single template. Any split value triggers multi-template generation per D-18-13.
- **D-18-04 (F8 CTA = AI button in Nutrition tab):** Symmetric with the Workout-tab AI button. Persistent, always accessible, doesn't depend on remaining-macro state. Tap → if remaining macros are negative or near zero, show the "you've already hit/exceeded your goals" empty state (per REQ-AI-04 UAT #3) instead of calling the LLM. Otherwise → form (or directly to call if the form has no inputs at all — Claude's discretion whether F8 needs a form at all, since "fill remaining macros" is its full input).

### Settings, BYOK & error UX

- **D-18-05 (AI section inside existing Settings surface):** New "AI" section in the existing `SettingsSheet` / `SettingsScreen` (alongside Theme, Weight unit, Nutrition goals). Tapping the row navigates to a dedicated AI Settings sub-screen for the API key + provider/base URL + model fields. Discoverable, hierarchically consistent with the current settings layout.
- **D-18-06 (Provider preset dropdown + override):** AI Settings sub-screen exposes:
  - **Provider preset** dropdown — `OpenAI`, `Together.AI`, `OpenRouter`, `Groq`, `Custom`. Each preset fills the base URL (e.g. `https://api.openai.com/v1`) and a sensible default model name (`gpt-4o-mini`, `meta-llama/Llama-3.3-70B-Instruct-Turbo`, etc. — exact defaults Claude's discretion at planning time, ideally validated by quick documentation check).
  - **API key** field (masked input, with show/hide toggle and Clear affordance).
  - **Model** field — pre-filled by the preset, editable.
  - **Base URL** field — pre-filled by the preset, editable when `Custom` is chosen, hidden/disabled when a known preset is selected (Claude's discretion).
- **D-18-07 (No-key empty-state on F6/F8 form):** When the user taps an AI button without a configured key, open the form, render an empty-state card explaining BYOK with an `Open AI Settings` button that deep-links straight to the AI Settings sub-screen. Form inputs (muscles, count, etc.) are disabled until a key is configured. This is the canonical "discoverable but doesn't trick the user" pattern.
- **D-18-08 (Distinct error copy + Retry per class):** Error UX surfaces five explicit error states with tailored copy:
  1. **Timeout** — "Generation took too long" + Retry button.
  2. **Network down** — "No internet connection" + Retry button.
  3. **4xx key/quota** — "Your API key is invalid or out of quota" + `Open AI Settings` button.
  4. **5xx provider** — "The AI provider had a problem" + Retry button.
  5. **Schema-invalid / refusal** — "The AI returned something we couldn't use" + Retry button (one auto-retry already happened internally per D-18-14).
  Implementation: a sealed `AiError` class in commonMain so the same VM-level handling works for both F6 and F8, with platform-specific rendering in Compose / SwiftUI.

### Schema bridging — exercises & foods

- **D-18-09 (LLM has authoring rights for Exercise and Food entries):** The system prompts encode the full `Exercise` and `Food` schemas (field names, allowed enum values, validation rules) so that when the LLM needs an exercise or food that isn't already in the user's catalog/pantry, it can emit a complete, valid new entry. The app persists those new entries first, then links them from the generated `WorkoutTemplate` / `Recipe`. **The LLM is the source of truth for AI-generated exercise/food entries; no fuzzy matching, no fall-through hacks.**
  - **Exercise schema fields the LLM must populate** (validated app-side against `Exercise.kt`): name, primaryMuscles (as `MuscleGroup` enum values), equipment (if such a field exists in current `Exercise.kt` — planner verifies during research), and any other required fields the existing model has. Optional fields stay optional.
  - **Food schema fields the LLM must populate** (validated against `Food.kt`): name, per-100g macros (kcal / protein / fat / carbs / sugar), unit (`FoodUnit`), and any other required fields.
  - When the LLM returns an exercise/food entry whose name matches an existing one in the user's DB (case-insensitive trimmed match), the app reuses the existing row instead of creating a duplicate. New entries get `source = AI` per D-18-11.
- **D-18-10 (F8 emits inline Food + ingredient pair):** The recipe JSON schema models each ingredient as `{ food: { name, per100g, unit, …Food schema }, amountGrams }`. App matches `food.name` against existing Foods first; on miss, persists a new `Food` row with `source = AI`, then writes the `Recipe` with that `Food.id`. Existing user-created recipes still use Foods normally. Recipe macros remain computed from `Food.per100g × amountGrams / 100` (the existing `CalculateRecipeMacrosUseCase` pipeline) — the AI doesn't compute totals, the app does.
- **D-18-11 (Provenance via Room v9 → v10 — `source` column on 4 entities):** Additive AutoMigration adds a nullable `source: String?` column to:
  - `WorkoutTemplateEntity`
  - `ExerciseEntity`
  - `RecipeEntity`
  - `FoodEntity`
  Domain values: `USER` (existing entries are nullable / treated as USER on read), `AI` (set on every entry the AI authors). Enables future filtering, debugging ("why is this weird exercise in my catalog?"), and a small visual badge on AI entries. Bump `AppDatabase.version` 9 → 10; register `AutoMigration(9, 10)`. **No changes to existing entity fields or relations.**
- **D-18-12 (Preview-then-save flow for both F6 and F8):** After the LLM call returns valid JSON:
  1. App parses, validates, and resolves Exercise/Food references (matching existing rows or staging new ones for save).
  2. A preview sheet renders the result — for F6 a workout-template summary (name, exercises, target reps/sets/rest); for F8 a recipe summary (name, ingredients, total macros, fit-vs-remaining indicator).
  3. User taps `Save` to persist or `Discard` to dismiss without writing to the DB.
  4. Multi-template PPL/UL generations show all N templates in the same preview with per-template `Save` / a single `Save all` button / `Discard`.
  5. Until `Save`, **nothing is written** — Foods, Exercises, Templates, Recipes are all staged in memory and committed transactionally on Save.

### Generation shape & prompts

- **D-18-13 (Single prompt returning array for PPL / UL):** When the user picks `Push-Pull-Legs`, `Upper-Lower`, or any future split, the app fires **one** LLM call with a schema of `{ templates: WorkoutTemplate[] }`. One round-trip, one cost, one preview screen. Schema-invalid responses lose the whole batch — that's acceptable given D-18-14's retry-once policy.
- **D-18-14 (`response_format: json_schema` with schema-validate-and-retry fallback):**
  - **Primary path:** Use OpenAI's `response_format: { type: "json_schema", json_schema: {…} }` for providers that support it (OpenAI, Together.AI, Groq, most OpenRouter routes).
  - **Fallback path:** When the provider returns a structured-output-not-supported error (or when the response doesn't match the schema even with `json_schema` set), retry **once** with the schema embedded in the system prompt and `response_format: { type: "json_object" }`. App-side validation against the schema is the source of truth either way.
  - **No further retries** beyond that one fallback. A second failure surfaces error class 5 (schema-invalid) per D-18-08.
- **D-18-15 (English templated prompts in shared resources, with `{locale}` instruction):** System prompt files written in **English** (LLM training data is English-heavy → better instruction-following), but each prompt embeds a `{locale}` placeholder in the form `Respond with all user-visible text (template name, exercise notes, recipe name, ingredient names) in {de|en}, matching the app's UI language.` Files live in:
  - `shared/src/commonMain/resources/ai-prompts/workout-system.md`
  - `shared/src/commonMain/resources/ai-prompts/recipe-system.md`
  Loaded as classpath resources at runtime (the planner picks the cleanest KMP idiom — likely `getResourceAsStream` or a precompiled `expect/actual` reader). Versioned in git, reviewable in PRs. Locale defaults to `de` (the app's current UI locale); future English UI flips the value at the call site without changing the prompt files.
- **D-18-16 (Static skeleton + Cancel + 60s timeout):** During an in-flight generation:
  - **Static skeleton placeholder** — render shimmer rows matching the eventual layout (workout: N exercise rows; recipe: N ingredient rows). On response, swap to real content in one frame. No streaming, no progressive token reveal — stays inside ROADMAP scope.
  - **Cancel button** — aborts the in-flight Ktor request. App-side, that resolves as a "user cancelled" state (no error toast).
  - **Hard request timeout = 60s** at the Ktor client level. A timeout surfaces error class 1 per D-18-08.
  - **Hard response-size limit** — Claude's discretion (lean: 32 KB or 64 KB; large enough for 6 templates, small enough to bound memory).

### Claude's Discretion

- Exact placement of the AI button in the Workout tab and the Nutrition tab (toolbar action icon, FAB-adjacent button, or a row in the existing list — pick what reads as a primary action without crowding existing buttons).
- Exact range of the F6 "exercise count" input (3–8 is a reasonable default).
- Whether F8 has any form fields beyond the implicit "fill remaining macros" — likely none, but a "diet style" preference (quick / no-cook / high-protein focus / leftovers) could surface as an optional dropdown.
- Default model names per provider preset (consult provider docs at planning time; for grading reliability, pick well-supported defaults: `gpt-4o-mini` for OpenAI, `meta-llama/Llama-3.3-70B-Instruct-Turbo` for Together.AI, `meta-llama/llama-3.3-70b-instruct` for Groq, etc.).
- Exact secure-storage idiom: `expect class SecureKeyStore` with `actual` implementations using `Keychain Services` on iOS (`SecItemAdd` etc. wrapped via cinterop or a thin Swift bridge) and `androidx.security:security-crypto` `EncryptedSharedPreferences` on Android.
- Exact Ktor client extension shape — extend the existing `HttpClientFactory.kt` `expect/actual` rather than creating a parallel client; share connection pooling with `OpenFoodFactsApi`.
- Exact Compose Multiplatform vs platform-specific decision for the AI Settings screen — given Phase 15/16/17 convention, ship Compose Material 3 on Android + a SwiftUI handoff for iOS. The AI Settings UI is simple enough that a Compose Multiplatform iOS UI is tempting; **resist** unless the planner has a strong reason. Default to the existing convention.
- Whether to show a token-count or cost estimate on the preview sheet (recommended: NO, it's deferred per ROADMAP scope).
- Whether to add a daily request soft-cap (recommended: NO for prototype; documented as a future polish item).
- Preview-sheet UI shape — full-screen sheet vs modal bottom sheet vs new-screen-with-discard-confirm. Pick what reads natively for each platform.
- Skeleton shimmer details — exact row count to render (use `count` from form), animation timing, color treatment.
- Whether F6 generates a default template name or asks the LLM to name it (recommended: ask the LLM via the schema, with a fallback to "AI workout — {date}" if the field is empty).
- Whether the `source` column type is a free-form string vs a Room-converted enum. Recommended: free-form `String?` to keep AutoMigration trivial; treat unknown values as `USER` on read.
- Cascading-source semantics: when an AI-created Recipe is later edited by the user — does the Recipe's `source` flip to `USER`? (Recommended: keep `AI` as the provenance — it tracks origin, not current state. Edits don't rewrite history.)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements & design baseline
- `.planning/REQUIREMENTS-ai-features.md` — REQ-AI-01 … REQ-AI-08 with UAT criteria. **MUST read before planning.** F6 + F8 acceptance, BYOK requirements, schema validation contract.
- `.planning/notes/ai-features-design-decisions.md` — D-AI-01 … D-AI-08 baseline rationale. Locks "MCP/API = HTTPS transport, not agentic", single OpenAI-compatible BYOK, on-device deferred, F6+F8 in same phase, minimal prompt-safety scope, multi-template chained generation in scope.
- `.planning/seeds/SEED-002-on-device-gemma-llamatik.md` — On-device LLM v2 path. Trigger conditions for revisiting.

### Roadmap & state
- `.planning/ROADMAP.md` §"Phase 18" — Phase entry text + dependency chain (no code dep on Phase 17, sequencing only).
- `.planning/PROJECT.md` §"Tech stack" — Kotlin 2.3.20, Compose Multiplatform 1.10 (Android), SwiftUI iOS, Room KMP v9 (this phase bumps to v10), Koin 4.2, kotlinx-datetime, KMPNativeCoroutinesAsync, DataStore Preferences, Ktor Client (CIO/OkHttp Android, Darwin iOS).
- `.planning/STATE.md` §"Current Position" / §"Untracked Drift" — Phase 17 just completed; nutrition + theming shipped post-v1.5 outside GSD.

### Phase 16 (nutrition goals — for F8 remaining-macro logic)
- `.planning/phases/16-set-nutrition-goals-kcal-protein-carbs-fat-per-day-surface-p/16-CONTEXT.md` — D-16-15 keeps `±10%` tolerance; F8 acceptance per REQ-AI-04 reads through the same predicate.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/NutritionGoalDayPolicy.kt` — read-only call from F8 acceptance check ("are remaining macros worth generating against?").
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/nutrition/CalculateDailyMacrosUseCase.kt` — existing pipeline that yields today's totals; remaining = goals − totals.

### Phase 17 (KMP convention reference)
- `.planning/phases/17-progress-pic-feature-with-biometric-locked-gallery-post-work/17-CONTEXT.md` — D-17-18 (KMP shared VM + native UI) and D-17-19 (`expect/actual` for platform I/O) are the patterns this phase follows for the secure-key store, the shared `OpenAICompatibleClient`, and the F6/F8 ViewModels.

### Existing data models (target shapes for JSON schemas)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/WorkoutTemplate.kt` — `WorkoutTemplate` + `TemplateExercise` data classes. F6 schema mirrors this shape.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Recipe.kt` — `Recipe` + `RecipeIngredient` (currently `foodId: String + amountGrams: Double`). F8 schema emits `food: Food + amountGrams`; app resolves to `foodId`.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Food.kt` — `Food` data class with per-100g macros + `FoodUnit`. F8 schema's inline-food shape mirrors this.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/Exercise.kt` — `Exercise` with `MuscleGroup` enum. F6 schema's new-exercise emit shape mirrors this; primaryMuscles must be valid `MuscleGroup` values.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleGroup.kt` (or wherever the enum lives — planner verifies) — enum values constrain the LLM's `primaryMuscles` field.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/FoodUnit.kt` — enum constraining the LLM's `unit` field.
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/NutritionGoals.kt` — F8's daily-target reference.

### Existing repositories (persistence path)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/TemplateRepository.kt` — F6 persistence target. Verify create-template + create-exercise APIs at planning time.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/ExerciseRepository.kt` — Used by F6 when emitting new Exercise entries.
- Recipe repository (path Claude verifies during research) — F8 persistence target.
- `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepository.kt` — DataStore key-value pattern. **NOT for the API key** (Keychain / EncryptedSharedPreferences instead per REQ-AI-06). Used for the provider preset, base URL, and model preference (these aren't secrets).

### HTTP client (extend, don't fork)
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/HttpClientFactory.kt` — `expect fun createHttpClient(): HttpClient`. Extend this for the AI client; reuse the platform-specific `actual` implementations (`HttpClientFactory.android.kt`, `HttpClientFactory.ios.kt`).
- `shared/src/commonMain/kotlin/com/pumpernickel/data/api/OpenFoodFactsApi.kt` — Existing Ktor client pattern to mirror (request shape, error handling, JSON deserialization). The AI client follows the same shape with auth headers and request bodies.

### Anatomy picker reuse (F6 form)
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegionPaths.kt` — Shared coordinate data feeding both Android Canvas and SwiftUI Path renderers (Phase 14 / post-v1.5 commonMain refactor).
- Android `AnatomyPickerSheet` (under `androidApp/.../ui/screens/` — planner verifies path) — Compose component with multi-select mode.
- iOS `AnatomyPicker` SwiftUI view (under `iosApp/iosApp/Views/` — planner verifies path) — corresponding SwiftUI port.

### Schema migration anchor
- `shared/src/commonMain/kotlin/com/pumpernickel/data/db/AppDatabase.kt` — currently `version = 9` with `AutoMigration(8, 9)` (Phase 17). This phase bumps to `version = 10`, adds `AutoMigration(9, 10)`, modifies the four entities listed in D-18-11.
- Existing `AutoMigration` examples: `AutoMigration(7, 8)` from Phase 15 and `AutoMigration(8, 9)` from Phase 17 — both additive, both reference precedents for D-18-11's shape.

### DI & KoinHelper convention
- `shared/src/commonMain/kotlin/com/pumpernickel/di/SharedModule.kt` — Koin wiring; this phase adds an `AiModule.kt` (or folds into an existing module — planner's call) for the new `OpenAICompatibleClient`, `SecureKeyStore` (expect/actual), AI ViewModels, and prompt loaders.
- `shared/src/iosMain/kotlin/com/pumpernickel/di/AchievementGalleryKoinHelper.kt` (and the Phase-17 progress-gallery helpers) — canonical KoinHelper shape (one helper per VM, no caching). New helpers per AI VM.

### Settings UI integration points
- Existing Android `SettingsScreen` / `SettingsSheet` (Compose) — gains an "AI" row navigating to a new AI Settings sub-screen.
- Existing iOS `SettingsView.swift` — gains a corresponding "AI" navigation link.

### Workout / Nutrition tab integration points
- Android `MainScreen.kt` Workout tab + Nutrition tab — each gains an AI button entry into `AiWorkoutGenRoute` / `AiMealGenRoute` (new routes in `Routes.kt`).
- iOS `MainTabView.swift` corresponding Workout / Nutrition tabs — SwiftUI handoff doc spells out the entry-point shape.

### Reference (no direct port)
- `/Users/olli/schenanigans/gymtracker` — Firmware reference. No AI logic to port.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **Ktor in the stack** — `ktor.client.core`, `ktor.client.content.negotiation`, `ktor.serialization.json`, `ktor.client.okhttp` (Android), `ktor.client.darwin` (iOS) are already declared in `shared/build.gradle.kts`. The AI client extends this — no new transport dep.
- **kotlinx-serialization** — already on the classpath for nav routes and the existing `OpenFoodFactsDto.kt`. `@Serializable` data classes for the AI request/response payloads cost nothing extra.
- **`HttpClientFactory.kt` `expect/actual`** — established pattern for sharing a single `HttpClient` instance across features. AI client requests through the same factory.
- **`OpenFoodFactsApi.kt`** — direct precedent for a Ktor-backed shared API client with JSON deserialization, custom headers, and error surfacing. The AI client follows the same shape with `Authorization: Bearer ${apiKey}` instead of `User-Agent`.
- **`AnatomyPickerSheet` (Android Compose + iOS SwiftUI)** — directly reused for F6 muscle selection. Multi-select mode already supported via `MuscleRegionPaths`.
- **`CalculateDailyMacrosUseCase` + `NutritionGoals`** — F8's "what's left today?" reads through the existing pipeline; no new domain code for the macro math.
- **`NutritionGoalDayPolicy.isGoalDay(...)`** — F8 acceptance ("are remaining macros worth a recipe?") reads the same ±10% predicate as Phase 16's gamification.
- **`SettingsRepository`** — provider preset / base URL / model fields persisted here (these are configuration, not secrets — the API key uses platform-secure storage).
- **DataStore precedent** — established for non-secret settings; the API key explicitly **does not** go here.

### Established Patterns

- **KMP shared VM + native UI** (Phase 15/16/17 convention, MEMORY.md user-android-experience entry): shared `commonMain` VMs + `StateFlow`, Material 3 Compose on Android, hand-written SwiftUI on iOS via a `18-IOS-HANDOFF.md` doc. **No Compose Multiplatform iOS UI.**
- **`expect/actual` for platform-specific I/O** — `createDataStore.ios.kt`, `HttpClientFactory.{android,ios}.kt`, and Phase 17's `PhotoVault` / `BiometricGate` set the shape. New `expect class SecureKeyStore` / `actual class SecureKeyStore` follow the same pattern.
- **One KoinHelper per iOS VM** — `AchievementGalleryKoinHelper`, `RanksAndAchievementsKoinHelper`, `ProgressGalleryKoinHelper`. New AI VMs each get a helper.
- **Shared VM + StateFlow + KMPNativeCoroutines** — `@NativeCoroutinesState val uiState: StateFlow<...>` collected via `collectAsState()` on Android and `asyncSequence` on iOS.
- **Room KMP additive AutoMigration** — Phases 15 (7→8), 17 (8→9) precedent. Phase 18 follows with 9→10.
- **iOS handoff doc convention** — Phase 15.1 `15.1-IOS-HANDOFF.md`, Phase 17 `17-IOS-HANDOFF.md`. This phase emits `18-IOS-HANDOFF.md`.
- **German UI copy** — app is German-first; AI prompt files are English (per D-18-15) but instruct the model to emit user-visible strings in German via the `{locale}` placeholder.

### Integration Points

- **Workout tab** (`MainScreen.kt` Workout host on Android, `MainTabView.swift` Workout tab on iOS) — gains an AI action entry. New `AiWorkoutGenRoute` (Android) + corresponding SwiftUI navigation.
- **Nutrition tab** (similar shape) — gains an AI action entry. New `AiMealGenRoute` + SwiftUI nav.
- **Settings sheet/screen** (Android Compose + iOS SwiftUI) — gains an "AI" row → new AI Settings sub-screen with API key + provider/base URL + model fields.
- **Room migration** — `AppDatabase.kt` bumps `version = 10`; `AutoMigration(9, 10)` registered; `WorkoutTemplateEntity`, `ExerciseEntity`, `RecipeEntity`, `FoodEntity` each gain a nullable `source: String?` column.
- **Domain mappers** — `WorkoutTemplateEntity.toDomain` / `ExerciseEntity.toDomain` / `RecipeEntity.toDomain` / `FoodEntity.toDomain` propagate the new `source` field through to the domain models (also need the new `source` field added there).
- **New shared services** (commonMain): `OpenAICompatibleClient` (uses `HttpClientFactory`); `SecureKeyStore` (`expect/actual` over Keychain / EncryptedSharedPreferences); `AiPromptCatalog` (loads `workout-system.md` / `recipe-system.md` from resources, substitutes `{locale}`); `WorkoutAiSchema` / `RecipeAiSchema` (`@Serializable` data classes); `WorkoutAiUseCase` / `RecipeAiUseCase` (orchestrates: build prompt → call client → validate → resolve Foods/Exercises → return preview-ready domain models).
- **New shared VMs** (commonMain): `WorkoutAiViewModel` (form state, generate, preview, save), `RecipeAiViewModel` (form state — likely just diet-style optional, generate, preview, save), `AiSettingsViewModel` (key, provider, base URL, model, save, clear).
- **New Android screens / routes**: `AiWorkoutGenScreen.kt`, `AiMealGenScreen.kt`, `AiSettingsScreen.kt`, `AiPreviewSheet.kt` — added to `Routes.kt` and `MainScreen.kt`.
- **iOS handoff** (`18-IOS-HANDOFF.md`): SwiftUI surfaces for `AiWorkoutGenView`, `AiMealGenView`, `AiSettingsView`, `AiPreviewSheet`, plus Workout/Nutrition tab AI button additions and SettingsView "AI" row.
- **Manifest / Info.plist** — no new permissions (HTTPS only, no camera / biometric / storage additions). iOS `Info.plist` may need an ATS exception only if a custom-base-URL provider uses non-HTTPS (which would violate REQ-AI-06's HTTPS-only premise — disallow non-HTTPS at the Settings input layer instead).

</code_context>

<specifics>
## Specific Ideas

- **"Symmetric AI buttons" is the user's framing.** Workout tab → workout gen, Nutrition tab → meal gen. Don't collapse this into a single "AI" tab or a single "AI" entry on Overview. The pairing is intentional — generation lives next to the data it produces.
- **"LLM has authoring rights" is load-bearing.** The model creates new Exercise + Food entries when needed. The system prompt MUST encode the schemas. Don't fall back to fuzzy-match-or-error on catalog misses — that was explicitly rejected in favor of giving the LLM enough context to do the right thing.
- **Preview-then-save is the commitment boundary.** Until the user taps Save, no DB writes happen — including the new Foods and Exercises emitted inline. Save is a transactional commit: new Foods + new Exercises + Template/Recipe land together or not at all.
- **Per-class error copy with Retry is non-negotiable.** REQ-AI-08's "distinct, actionable user message" gates UAT. A single generic error sheet doesn't pass.
- **English prompts + locale instruction.** The user explicitly chose the templated path — output language follows the app's UI locale via a `{locale}` placeholder substituted at call time.
- **Static skeleton, not streaming.** The user picked the "static skeleton" interpretation when given the streaming-vs-no-streaming clarifier. Streaming stays out of scope per ROADMAP.md.
- **No daily request soft-cap.** Out of scope for prototype; documented as a future polish item.
- **No prompt-injection hardening.** Per D-AI-07 / REQ-AI scope — schema-validate, length-cap user inputs sent into prompts, don't log the key. Anything beyond that is deferred.

</specifics>

<deferred>
## Deferred Ideas

### From design-decisions.md (carried forward)
- **On-device Gemma via Llamatik** — see SEED-002. Trigger: post-deadline polish milestone or BYOK pain (cost / latency / no-network).
- **Multi-provider abstraction layer** — Phase 18 ships a single `OpenAICompatibleClient`. Extracting an `LlmProvider` interface waits for a second concrete implementation (likely on-device).
- **MCP server implementation** — explicitly rejected (D-AI-01). The "MCP/API" wording is satisfied by HTTPS API.
- **Tool-use / agent loops** — out of scope. The LLM never gets tools.
- **AI editing of existing templates / recipes (Update via AI)** — F6 is Create-only for v1. Defer to a v2 phase.
- **Streaming responses** — out of scope per ROADMAP. Static skeleton + Cancel is the loading UX.
- **Cost / token-count UI** — out of scope. No spend visibility in v1.
- **Prompt-injection hardening / jailbreak audits / output-content moderation** — explicitly out of scope per D-AI-07.
- **RAG, embeddings, fine-tuning** — out of scope.

### Surfaced during this discussion
- **Daily request soft-cap (cost guardrail)** — out of scope for prototype. Could ship as a Settings toggle in a future polish phase.
- **Allergies / dietary preferences for F8** — REQ-AI-04 hints at "user-configured allergies/dietary preferences (if such preferences exist; otherwise no constraint)". No such preferences exist today; adding them is a separate feature. Defer.
- **Token-count or cost estimate on the preview sheet** — same scope as the cost UI deferral.
- **Test-connection button in AI Settings** — useful, but adds complexity (a stub call against the configured provider). Defer to a polish phase if first-run friction surfaces.
- **Inline key entry on the F6/F8 form** — rejected in favor of the empty-state-with-Settings-deep-link (D-18-07). Could ride later as an alternate flow if Settings-trip-friction surfaces.
- **Streaming skeleton (progressive token reveal)** — explicitly rejected during the scope-clarifier. Stays in the streaming deferral.
- **Diet-style preference dropdown on F8** — Claude's discretion at planning time. If included, Claude leans toward a small optional dropdown (Quick / No-cook / High-protein focus / Use leftovers).
- **AI badge on AI-authored entries in lists** — would consume the new `source` field. Out of phase scope but trivially follow-up-shippable.

### Reviewed Todos (not folded)
- **`2026-05-06-retroactive-progress-photo-attach-from-history.md`** — matched on "progress" keyword (score 0.9) but is clearly a Phase 17 follow-up (retroactive progress photo attach from History detail). Not AI-related. Keep on the todo list, route to a Phase-17 follow-up phase rather than this one.

</deferred>

---

*Phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op*
*Context gathered: 2026-05-07*

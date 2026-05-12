# Requirements — AI Features (F6 + F8)

**Phase:** 18 (planned)
**Created:** 2026-05-06
**Source:** Lastenheft F6 (Workout Gen) + F8 (Meal Gen). Surfaced via `/gsd-explore` voice memo on 2026-05-06.
**Confidence:** MEDIUM — Lastenheft wording is loose. Reading and scope decisions captured in `.planning/notes/ai-features-design-decisions.md`.

## Lastenheft Excerpts (verbatim)

> **F6 — Workout Gen:** MCP/API für CRU(D) Operationen. Systemprompt, vordefinierte Struktur. UI Interface. LLM. D manuell möglich.
>
> **F8 — Meal Gen:** KI querien für Mahlzeiten.

## Categories

### AI Workout Generation (F6)

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| REQ-AI-01 | User can generate one or more `WorkoutTemplate`s from a small input form (target muscles, exercise count, optional split style e.g. Push/Pull/Legs). The app calls a configured BYOK LLM with a system prompt + JSON schema and persists each returned template via the existing `WorkoutTemplate` repository. | Must | (1) From the input form, the user generates a single template — it appears in the template list, opens in the existing editor, can be launched into a workout session unchanged. (2) When a "split" option is selected (e.g. PPL), multiple templates are generated and all appear in the template list. (3) Generated templates use only `Exercise` IDs that exist in the catalog (or fall back gracefully to user-creatable exercises). (4) Schema-invalid LLM responses surface a clear error and never write partial templates to the DB. (5) Delete remains a manual user action — the AI never deletes templates. |
| REQ-AI-02 | A "predefined Struktur" (JSON schema) governs the LLM's workout-template output. The schema must encode: name, optional description, ordered list of exercises with sets-per-exercise, target rep ranges, and optional rest seconds — matching the existing `WorkoutTemplate` data model. | Must | The schema lives next to the workout-AI feature code, is validated against actual generations from at least 2 OpenAI-compatible providers, and rejects malformed/missing fields with a descriptive error. |
| REQ-AI-03 | A system prompt for workout generation is checked into the repo, versioned, and reads as the source of truth for generation behavior (tone, default rest, default sets, anti-injury heuristics, refusal of non-fitness inputs). | Must | The system prompt file exists in source control, is referenced by the workout-AI client, and changes to it are reviewable in PRs. |

### AI Meal Generation (F8)

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| REQ-AI-04 | User can request a recipe suggestion that fits their *remaining* daily macros. The app reads today's `ConsumptionEntry` log against the user's `NutritionGoals`, computes the remaining kcal / protein / carbs / fat / sugar, and prompts the BYOK LLM with those values + a recipe JSON schema. The returned recipe is persisted via the existing `Recipe` entity (or shown as a suggestion the user can accept). | Must | (1) From a "Fill remaining macros" entry point, the user receives a recipe whose macros are within ±10% of remaining targets (matching the Phase 15 nutrition tolerance). (2) The recipe is saveable to the existing recipe collection. (3) When remaining macros are negative or near-zero, the UI explains *why* a recipe wasn't generated rather than calling the LLM with nonsense. (4) Generated recipes never reference foods that violate any user-configured allergies/dietary preferences (if such preferences exist; otherwise no constraint). |
| REQ-AI-05 | A system prompt and JSON schema for recipe generation are checked into the repo, structured to match the existing `Recipe` / `Food` data model (ingredients with quantity/unit + per-100g macros + steps). | Must | The schema validates recipe JSON, the prompt file lives in source control, and the schema's macro rollups are consistent with the user-facing macro displays elsewhere. |

### LLM Provider & Settings

| ID | Requirement | Priority | UAT Criteria |
|----|-------------|----------|--------------|
| REQ-AI-06 | The app supports a single, OpenAI-compatible HTTPS LLM provider via Bring-Your-Own-API-Key. A Settings screen accepts an API key + base URL (e.g., `https://api.openai.com/v1`, `https://api.together.xyz/v1`, `https://openrouter.ai/api/v1`). The key is stored in platform-secure storage (Keychain on iOS, EncryptedSharedPreferences on Android — *not* DataStore). | Must | (1) User can enter a key + base URL, save, and successfully run a generation. (2) The key is never written to plaintext logs, plaintext storage, or crash reports. (3) An empty / invalid key surfaces a clear error UI before any generation attempt. (4) Settings allows clearing the key. (5) Optional: model name field defaults sensibly per provider. |
| REQ-AI-07 | All generation calls validate response shape against the JSON schema before persisting. Invalid responses produce user-facing errors and are not written to the DB. The user is never shown a half-formed template / recipe. | Must | A deliberately broken response (e.g., missing required field) surfaces an error message and writes nothing. |
| REQ-AI-08 | Generation requests have a hard request timeout, a hard response-size limit, and gracefully present errors for: timeout, network failure, 4xx (key/quota), 5xx (provider issue), and content-policy refusals. | Must | Each error class produces a distinct, actionable user message. App does not crash on any of them. |

### Out of Scope (this milestone)

- On-device / local LLM inference (see `.planning/seeds/SEED-002-on-device-gemma-llamatik.md`).
- A multi-provider abstraction layer with multiple concrete clients (single OpenAI-compatible client only — Together.AI, OpenAI, OpenRouter, Groq all share that shape).
- An MCP server implementation (the "MCP/API" wording in F6 is satisfied by HTTPS API; see D-AI-01 in the design-decisions note).
- Tool-use / agent loops where the LLM calls back into the app to mutate data.
- Prompt-injection hardening, jailbreak audits, or output-content moderation pipelines (see D-AI-07).
- RAG, embeddings, fine-tuning.
- AI editing of *existing* templates / recipes (Update via AI). F6 is interpreted as Create-only for v1; Update via AI may be a v2 enhancement.
- Streaming responses (a spinner during the generation is acceptable for v1).
- Cost tracking / token counting in the UI.

## Cross-References

- Design decisions: `.planning/notes/ai-features-design-decisions.md`
- Future on-device path: `.planning/seeds/SEED-002-on-device-gemma-llamatik.md`
- Roadmap entry: `.planning/ROADMAP.md` → Phase 18
- Existing data models leveraged: `WorkoutTemplate`, `Recipe`, `Food`, `ConsumptionEntry`, `NutritionGoals` (all from Post-v1.5 untracked drift; see `MILESTONES.md`)

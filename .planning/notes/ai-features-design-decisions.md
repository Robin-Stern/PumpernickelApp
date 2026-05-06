---
title: AI Features (F6 Workout Gen + F8 Meal Gen) — design decisions
date: 2026-05-06
context: Captured from /gsd-explore session before Phase 18 planning. Baseline rationale for the AI-features phase so future decisions don't relitigate the same forks.
related:
  - .planning/seeds/SEED-002-on-device-gemma-llamatik.md
  - .planning/REQUIREMENTS-ai-features.md
  - .planning/ROADMAP.md (Phase 18)
---

# AI Features — Design Decisions (pre-plan)

## Lastenheft anchors

- **F6 — Workout Gen:** "MCP/API für CRU(D) Operationen. Systemprompt, vordefinierte Struktur. UI Interface. LLM. D manuell möglich."
- **F8 — Meal Gen:** "KI querien für Mahlzeiten."

## Decisions

### D-AI-01 — "MCP/API für CRU(D)" reads as `generate → JSON → save`

The Lastenheft phrase is loose. Two valid readings existed:

1. **Agentic / tool-use** — give the LLM Create/Read/Update tools (function-calling or MCP server) and let it modify the local DB.
2. **Simple `generate → JSON → save`** — user taps generate, app calls LLM with a system prompt + schema, parses the JSON response, persists it via the *existing* `WorkoutTemplate` / `Recipe` repositories.

**Chosen: 2.** The "MCP/API" wording is the *transport* for the LLM call, not an agent loop. CRU(D) refers to the existing app-level CRUD on templates/recipes, which is already implemented. Delete stays manual (D-AI-02).

**Why:** Massively lower complexity — no MCP server to host, no tool-loop debugging, no security surface around an LLM mutating local data. Frontier-grade structured-output (OpenAI-compatible `response_format: json_schema`) is already reliable.

**How to apply:** During Phase 18 planning, the LLM never gets tools. It receives a system prompt + user prompt + JSON schema and must return a single object/array. The app validates and writes through the existing repositories.

### D-AI-02 — Delete is always manual

Per F6's literal wording ("D manuell möglich"). The AI never deletes user-created workout templates or recipes. This is also the safest interpretation for grading.

### D-AI-03 — Single BYOK provider, OpenAI-compatible HTTPS

The voice memo wanted four providers (MCP, OpenAI BYOK, Together.AI BYOK, on-device Gemma). Lastenheft requires only "**an** LLM" (singular). Under a 4-week deadline, **one provider that ships beats four that don't**.

**Chosen:** OpenAI-compatible HTTPS BYOK. Settings screen with API key + base URL. Together.AI, Groq, OpenAI itself, OpenRouter, etc. all expose the same `/v1/chat/completions` shape — same code works against any of them by changing the base URL.

**Why:** ~1–2 days of implementation (Ktor client + a Settings field). No abstraction tax. Multi-provider can be added in v2 without breaking changes since the OpenAI shape is the lingua franca.

**How to apply:** No `LlmProvider` interface yet. Single `OpenAICompatibleClient` class. Trait-extracting an interface only happens when a second concrete implementation arrives.

### D-AI-04 — On-device Gemma deferred to a v2 seed

See `SEED-002-on-device-gemma-llamatik.md`. Research findings:

- Mature iOS path is `llama.cpp + GGUF + Metal` — Google has deprecated MediaPipe LLM Inference for iOS in favor of LiteRT-LM (which is itself early).
- One viable KMP wrapper exists: **Llamatik** (community llama.cpp KMP binding).
- Hardware floor: Gemma 3n E2B Q4 ≈ 1.5 GB RAM (iPhone 12+); E4B Q4 ≈ 5 GB (iPhone 15 Pro+).
- llama.cpp has **GBNF grammar-constrained decoding** for reliable on-device JSON.
- Verdict for 4-week deadline: *risky-but-doable*, but a 1–2 week rabbit hole *before* any app code. Not worth it under deadline pressure when BYOK ships in 1–2 days.

**Decision:** Park as SEED-002. Trigger condition: post-deadline / v2 polish milestone.

### D-AI-05 — F6 and F8 in a single phase (Phase 18)

They share ~80% of code: BYOK settings, HTTP client, prompt-engineering pattern, JSON-schema validation, error/cost UX. Splitting into two phases would double the GSD ceremony for marginal isolation gain.

**Why:** Avoids two BYOK settings rollouts, two error surfaces, and risk of drift between the two flows. Plan-level wave structure can still parallelize the two feature flows internally.

**How to apply:** During plan-phase, expect at least these waves: (1) BYOK plumbing + settings UI, (2) shared LLM client + JSON-schema scaffolding, (3) F6 workout-AI flow, (4) F8 meal-AI flow.

### D-AI-06 — AI work scheduled *after* Phase 17 (after Gamification + Photos)

Phases 15 (gamification), 15.1 (ranks browser), 16 (nutrition goals editor), 17 (progress-pic gallery) are shipped or near-ship. Phase 18 = AI Features comes next.

**Why:** AI introduces an external dependency (network, third-party API key, latency, cost). Putting it last means none of the existing local-only features ride on a cloud dependency for grading.

### D-AI-07 — Prompt safety scope: minimal but present

Lastenheft does not call for prompt-injection hardening. For a uni prototype:

- **In scope:** strict JSON-schema validation on response, size caps on user inputs sent into prompts, content-length limits on response, sane error UX when the model refuses or returns invalid JSON, BYOK key never logged or persisted to anywhere except the platform's secure storage.
- **Out of scope (this milestone):** prompt-injection defenses, jailbreak-resistance audits, output-content moderation pipelines, RAG, fine-tuning.

If the prof asks "how would you secure this?" — the answer is: schema-validate, length-limit, key-in-keychain, document the trust boundary. Don't build it.

### D-AI-08 — Multi-template "chained" generation (PPL split) is in scope, decided at plan-phase

Voice memo wanted: not one workout, but a whole split (e.g., Push/Pull/Legs = 3 templates from one questionnaire). This is a UX/prompting decision, not architectural. Mark it as *in scope* for Phase 18 but defer the specific shape (single big prompt returning array vs. N sequential calls) to plan-phase.

## What this phase intentionally is NOT

- A multi-provider abstraction layer
- An MCP server implementation
- An on-device LLM integration
- A RAG/embeddings system
- An agent loop with tool use
- A nutrition data overhaul (the substrate already exists)

## Open questions for plan-phase

- Where in the UI does the user trigger workout generation? Template list "+ AI" button vs. dedicated tab vs. inside template editor.
- "Fill remaining macros" button placement — Nutrition home, Overview tab, or somewhere else.
- Single prompt returning an array of templates vs. N sequential calls for the PPL chain.
- Cost/latency UX — show a spinner vs. streaming the response vs. progressive reveal.
- Does the user pre-select target muscle groups via the existing `AnatomyPickerSheet`? (Reuse vs. new questionnaire.)
- Quota / cost guardrails — soft cap on requests per day to protect users from runaway BYOK bills?

These belong in `/gsd-discuss-phase` for Phase 18, not here.

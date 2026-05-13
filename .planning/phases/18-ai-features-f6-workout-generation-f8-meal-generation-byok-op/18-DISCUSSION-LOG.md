# Phase 18: AI Features — F6 Workout Generation + F8 Meal Generation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `18-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
**Areas discussed:** Entry points & form UX, Settings/BYOK & error UX, Schema bridging — exercises & foods, Generation shape & prompts

---

## Entry points & form UX

### Q1 — Where should the F6 entry point live?

| Option | Description | Selected |
|--------|-------------|----------|
| Templates tab — primary FAB / button (Recommended) | Add an 'AI generate' affordance to TemplateListScreen alongside the existing 'New template' button. | |
| Template editor — inside an empty template | User taps 'New template' first, then sees an 'Or generate with AI' option. | |
| New 'AI' tab in bottom nav | Dedicated tab hosting both F6 + F8 entry points. | |
| Prompt card on Overview | Mirror Phase 17's progress-pic card pattern. | |
| **Other (free text)** | "an AI button on both workouts and nutrition tabs, one triggers workout gen, other triggers meal gen" | ✓ |

**User's choice:** Symmetric AI buttons on Workout + Nutrition tabs.
**Notes:** Generation lives next to the data it produces. This decision also folds in the F8 entry-point question (a single symmetric pattern).

### Q2 — F6 muscle picker

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse AnatomyPickerSheet from Phase 14 (Recommended) | Tap the body diagram, multi-select via existing MuscleRegionPaths. | ✓ |
| Chip-based muscle group selector | Horizontal scroll of MuscleGroup chips. | |
| Free-text 'describe your goal' | LLM parses freely. | |
| Skip muscle selection — split-type only | LLM's system prompt encodes which muscles each split targets. | |

**User's choice:** Reuse `AnatomyPickerSheet`.
**Notes:** Visual, consistent, no new UI to design. Both Android Compose component and iOS SwiftUI port already exist from Phase 14.

### Q3 — F6 form fields

| Option | Description | Selected |
|--------|-------------|----------|
| Exercise count + split style (Recommended) | Number-of-exercises (3–8) + Split type selector (None / PPL / UL / Full-Body). | ✓ |
| Exercise count + difficulty | Beginner/Intermediate/Advanced. | |
| Exercise count + free-text 'goal' | Open text box. | |
| Exercise count only — single template per call | Drop split-type entirely. | |

**User's choice:** Count + split style.
**Notes:** Closest to the requirements doc. PPL/UL trigger multi-template generation per D-18-13.

### Q4 — F8 CTA placement

| Option | Description | Selected |
|--------|-------------|----------|
| AI button in Nutrition tab toolbar (Recommended) | Symmetric with the Workout tab AI button. | ✓ |
| Bottom of DailyLog when remaining > 0 | Conditional CTA. | |
| Recipe list — 'Generate' alongside 'New recipe' | Lives in RecipeListScreen. | |
| Both: Nutrition tab button + DailyLog conditional banner | More surface area. | |

**User's choice:** Nutrition tab toolbar AI button.
**Notes:** Symmetric with Workout tab. Persistent, doesn't depend on remaining-macro state.

---

## Settings, BYOK & error UX

### Q1 — Settings location

| Option | Description | Selected |
|--------|-------------|----------|
| Section inside existing SettingsSheet/Screen (Recommended) | New 'AI' section, sub-screen for fields. | ✓ |
| Dedicated top-level AI Settings screen | Separate Settings reachable from a top-level entry. | |
| Inline disclosure on F6/F8 surfaces | Key entry directly on form. | |

**User's choice:** Section inside existing Settings.

### Q2 — Provider configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Provider preset dropdown + override (Recommended) | OpenAI / Together / OpenRouter / Groq / Custom. | ✓ |
| Free-form base URL + model only | Power-user vibe. | |
| OpenAI only — just an API key field | Loses provider flexibility. | |
| Provider preset dropdown, no override | No future-provider escape hatch. | |

**User's choice:** Preset dropdown + override.

### Q3 — No-key UX

| Option | Description | Selected |
|--------|-------------|----------|
| Empty-state card on the form: 'Add API key in Settings' (Recommended) | Form opens, fields disabled, deep-link to AI Settings. | ✓ |
| Inline key entry on the form | Mixes plumbing and feature use. | |
| Toast + Settings auto-navigation | Aggressive redirect. | |
| Disable AI buttons entirely | Hides the feature. | |

**User's choice:** Empty-state card on form with deep-link.

### Q4 — Error granularity

| Option | Description | Selected |
|--------|-------------|----------|
| Distinct copy + retry per class (Recommended) | 5 error states with tailored copy. | ✓ |
| Two buckets: transient vs permanent | Simpler, less informative. | |
| Single generic error sheet with raw error code | Smallest copy surface. | |
| Distinct copy, no retry button | Manual re-tap. | |

**User's choice:** Distinct copy + retry per class.
**Notes:** Required by REQ-AI-08's "distinct, actionable user message" UAT.

---

## Schema bridging — exercises & foods

### Q1 — F6 exercise catalog matching

| Option | Description | Selected |
|--------|-------------|----------|
| Constrain prompt to the catalog (Recommended) | LLM picks only from a curated catalog list. | |
| Fuzzy match + fall-through to custom Exercise | App fuzzy-matches LLM output. | |
| Hard error on catalog miss | Strict, brittle. | |
| AI-only template flag, no Exercise.id link | Decouples but breaks PB pipeline. | |
| **Other (free text)** | "id say this is extermely free. because the catalog may not contain all exercises i thought of the feature that the LLM can also create new exercises. that means that the systemprompt needs to set context about the data struture, what fields and inputs are available so it can build proper entries. same for workouts and nutritions" | ✓ |

**User's choice:** LLM has authoring rights over the Exercise catalog. System prompts encode the schemas so the model can emit valid new entries.
**Notes:** Same logic applies to Food entries (extended into Q2 below). This is the load-bearing decision shaping how F6 and F8 actually work — there's no fuzzy match or fallback hack; the LLM sees enough schema context to do the right thing.

### Q2 — F8 Recipe ↔ Food integration

| Option | Description | Selected |
|--------|-------------|----------|
| LLM emits inline Food + ingredient pair (Recommended) | LLM returns each ingredient as { food: { name, per100g, unit }, amountGrams }. | ✓ |
| Schema extension: inline-macro RecipeIngredient (no Food.id) | Schema migration to bypass Food rows. | |
| Match-by-name, hard error on miss | Strictest, breaks easily. | |
| Hybrid — match existing first, fallback to inline-macros | Mixed model. | |

**User's choice:** Inline Food + ingredient pair, persisted as new Food rows.
**Notes:** Symmetric with the Q1 decision (LLM authors new entries when needed). Foods accumulate in the user's pantry.

### Q3 — Provenance metadata

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — add 'source' field via Room v9→v10 (Recommended) | Nullable 'source' on Template/Recipe/Exercise/Food. | ✓ |
| Yes — source on Templates/Recipes only | Smaller migration. | |
| No — don't track provenance | No schema change. | |
| Logical only — store in a side table | Side table avoids touching existing entities. | |

**User's choice:** Add `source` via Room v9 → v10 to all four entities.

### Q4 — Save flow

| Option | Description | Selected |
|--------|-------------|----------|
| Preview sheet, user taps Save (Recommended) | Result preview before commitment. | ✓ |
| Auto-save then route to existing editor | Result lands in DB immediately. | |
| Auto-save with toast + undo | Material 3 idiomatic. | |
| Generate → mandatory edit — land in editor with Save still required | Reuses existing editor entirely. | |

**User's choice:** Preview sheet → Save.
**Notes:** Until Save, nothing is written — including new Foods and Exercises. Transactional commit.

---

## Generation shape & prompts

### Q1 — PPL multi-template shape

| Option | Description | Selected |
|--------|-------------|----------|
| Single prompt returning array of N templates (Recommended) | One round-trip, schema = { templates: [...] }. | ✓ |
| N sequential calls, one template each | 3× cost, per-template error isolation. | |
| N parallel calls, one template each | Fastest latency, rate-limit risk. | |
| Hybrid — single call with named per-day slots | Schema names days explicitly. | |

**User's choice:** Single prompt returning array.

### Q2 — Structured-output enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| response_format: json_schema with validate-and-retry fallback (Recommended) | Frontier reliability when supported, graceful degradation when not. | ✓ |
| Strict response_format only — fail on unsupported providers | Cleaner code path, smaller provider list. | |
| Schema-validate-and-retry only (no response_format) | Most provider-agnostic. | |
| response_format: json_object (loose) + schema validate | Cheapest mode, weakest guarantees. | |

**User's choice:** json_schema with retry fallback.
**Notes:** One auto-retry on schema-violation falls back to embedded-in-prompt + json_object mode. Second failure surfaces the schema-invalid error class.

### Q3 — Prompt language and storage

| Option | Description | Selected |
|--------|-------------|----------|
| English prompts, in shared module as .txt/.md resources (Recommended) | Resources at shared/src/commonMain/resources/ai-prompts/. | |
| English prompts as Kotlin string constants | Same content, harder to read. | |
| German prompts (matching app UI) | Risk of weaker instruction-following. | |
| English prompts + per-output-locale instruction (template-driven) | Templated `{locale}` placeholder. | ✓ |

**User's choice:** English prompts with `{locale}` placeholder.
**Notes:** Files live in shared resources, output language follows the app UI locale via runtime substitution.

### Q4 — Loading UX

| Option | Description | Selected (initial → final) |
|--------|-------------|----------|
| Indeterminate spinner + Cancel button (Recommended) | Modal/sheet with spinner + Cancel + 60s timeout. | |
| Skeleton preview that fills in | Progressive reveal — implies streaming. | initial ✓ |
| Progress bar with stage labels | Connecting / Generating / Validating. | |
| Spinner only, no Cancel | Smallest code surface. | |

### Q4-clarifier — Streaming was deferred in ROADMAP.md

| Option | Description | Selected |
|--------|-------------|----------|
| Static skeleton, swap on response (Recommended) | Render shimmer placeholders, swap to real content on response. No streaming. | ✓ |
| Streaming skeleton (re-open scope) | Re-includes streaming in Phase 18 scope. | |
| Drop skeleton — spinner only | Reverts to plain spinner. | |

**User's choice (final):** Static skeleton + Cancel + 60s timeout.
**Notes:** Initial pick implied streaming; clarifier confirmed the user wants the static-skeleton interpretation that stays inside ROADMAP scope.

---

## Claude's Discretion

- Exact placement of the AI button in the Workout tab and Nutrition tab toolbars.
- Range of the F6 "exercise count" input.
- Whether F8 has any form fields beyond the implicit "fill remaining macros" (likely none, possibly an optional diet-style preference).
- Default model name per provider preset.
- Exact `expect class SecureKeyStore` shape and platform `actual` implementations.
- Whether to ship a Compose Multiplatform AI Settings screen vs the standard split-platform convention. Default to Material 3 Compose on Android + SwiftUI handoff on iOS.
- Preview-sheet UI shape (full-screen sheet vs modal bottom sheet vs new-screen).
- Skeleton shimmer details (row count, animation timing).
- Whether F6 generates a default template name or asks the LLM to name it.
- Cascading-source semantics on edit (recommended: keep `AI` as origin, edits don't rewrite history).

## Deferred Ideas

- Daily request soft-cap (cost guardrail).
- Allergies / dietary preferences for F8.
- Token-count or cost estimate on preview sheet.
- Test-connection button in AI Settings.
- Inline key entry on F6/F8 form.
- Streaming skeleton.
- AI badge on AI-authored entries.
- Diet-style preference dropdown on F8 (Claude's discretion at planning time).
- All standing deferrals from `.planning/notes/ai-features-design-decisions.md` (on-device LLM, multi-provider, MCP, agents, AI editing, RAG, prompt-injection hardening).

### Reviewed Todos (not folded)
- `2026-05-06-retroactive-progress-photo-attach-from-history.md` — matched on "progress" keyword (score 0.9) but is a Phase 17 follow-up, not AI-related.

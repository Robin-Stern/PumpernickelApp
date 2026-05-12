---
id: SEED-002
status: dormant
planted: 2026-05-06
planted_during: /gsd-explore — Phase 18 AI Features ideation
trigger_when: A v2 / post-deadline polish milestone opens, OR the BYOK-only AI implementation surfaces user complaints about cost, latency, or offline use, OR a milestone explicitly mentions on-device LLM, privacy, or offline-AI.
scope: Medium
---

# SEED-002: On-device Gemma via Llamatik (privacy/cost/wow story for v2)

## Why This Matters

Phase 18 ships F6/F8 with a single OpenAI-compatible BYOK transport — see `.planning/notes/ai-features-design-decisions.md` D-AI-03. That's the right call for the May 2026 deadline, but it leaves three real user pain points untouched:

- **Cost:** every generation hits a metered API. A user generating a 6-template PPL split pays for 6 calls. Bulk generation discourages the "feature" the voice memo was excited about.
- **Privacy:** workout history + nutrition macros + body anatomy + future progress photos make a sensitive profile. Even if the LLM provider isn't told everything, BYOK ships user data off-device.
- **Offline:** at the gym with bad signal, BYOK doesn't work.

On-device Gemma fixes all three and has a strong demo story ("the AI runs entirely on your phone"). It's also a credible v2 differentiator on top of a uni-grade prototype.

## Research Findings (2026-05-06)

Compiled during the /gsd-explore session that produced Phase 18.

### Runtime options on iOS

- **MediaPipe LLM Inference (iOS)** — Google has *deprecated* the Android/iOS path and recommends migrating to LiteRT-LM. **Avoid as primary path.**
- **llama.cpp + GGUF + Metal** — most mature and active. 15–30 tok/s on A17 Pro. Vision input not exposed in the iOS framework, but text generation is solid.
- **Core ML / MLX** — Apple-native, INT4 palettization, ~250 MB RAM, but requires conversion work from a Gemma checkpoint. **Not KMP-friendly** (Swift/Obj-C only).

### KMP integration story

- **Llamatik** ([github.com/ferranpons/llamatik](https://github.com/ferranpons/llamatik)) — community KMP binding over llama.cpp targeting Android / iOS / Desktop / JVM / WASM. **Closest to drop-in.**
- Otherwise: cinterop on Kotlin/Native generates bindings from llama.cpp C headers; expose a single `LlmEngine` via `expect/actual`. Working examples on dev.to and 2BAB blog (cited below).
- **No first-party Google KMP wrapper** for MediaPipe / LiteRT.

### Hardware floor & sizes (Gemma 3n)

| Variant | Quant | RAM | Min iPhone |
|--|--|--|--|
| Gemma 3n E2B | Q4_K_M | ~1.5 GB | iPhone 12+ |
| Gemma 3n E4B | Q4_K_M | ~5 GB | iPhone 15 Pro+ / 16 |

Q4_K_M is the sweet spot. Below Q3 = quality cliff. Thermal throttling on sustained generation is real on older devices.

### Structured output

- llama.cpp supports **GBNF grammar-constrained decoding** — reliable JSON on-device, no "validate and pray" loop.
- MediaPipe iOS: no documented grammar/structured-output API.
- Frontier cloud quality still beats 2B on-device for arbitrary schemas, but a tightly-defined `WorkoutTemplate` / `Recipe` schema is well within Gemma 3n E2B's capability under GBNF.

### 4-week verdict (why this is a v2 seed, not Phase 18)

> **Risky-but-doable but not recommended for May deadline.**
>
> Deciding factor: a KMP + llama.cpp + cinterop + Metal + model-bundling + GBNF stack is a 1–2 week rabbit hole *before* you write app code, and ships a ~30 MB+ model in the bundle. Versus BYOK = 1–2 days.

## When to Surface

**Trigger conditions** (any of):

1. A v2 / post-deadline polish milestone opens — present this seed during `/gsd:new-milestone` requirement gathering.
2. The shipped BYOK flow surfaces user feedback about cost, latency, missing-network, or "I don't want to give an API key" friction.
3. A milestone scope mentions: on-device LLM, offline AI, privacy hardening for AI features, or "no API key required."

When triggered, this seed should be promoted to a phase via `/gsd-add-phase`.

## Scope Estimate

**Medium** — probably 1.5–2 weeks of focused work on top of the existing BYOK plumbing.

Breakdown:

- **Llamatik integration:** add dependency, wire iOS + Android, get a tiny Gemma model loading and generating on-simulator.
- **Model distribution:** decide bundled-in-app (~30 MB ipa size hit, good UX) vs. first-launch download (smaller binary, worse offline UX, complicates app review). For v2 with grade-impressing in mind: bundled.
- **GBNF grammar generation:** convert the JSON Schemas defined in Phase 18 into GBNF for grammar-constrained decoding. Likely a small build-time codegen step.
- **Provider abstraction:** Phase 18 ships a single `OpenAICompatibleClient`. This is where the abstraction earns its keep — extract `LlmProvider` interface, add `OnDeviceLlmProvider`, route by user choice in Settings.
- **Settings UX:** "AI source" picker with BYOK / On-device options; show RAM + storage cost transparently; gate E4B model behind device-capability check.
- **Performance UX:** show tok/s, allow cancellation of long generations, warn on thermal throttle.
- **QA:** test on a low-end supported device (iPhone 12) for thermal + RAM behavior.

## Strong References

- [LLM Inference for iOS — Google AI Edge (deprecation notice)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/ios)
- [Llamatik — KMP llama.cpp bindings (GitHub)](https://github.com/ferranpons/llamatik)
- [On-Device LLM Inference via KMP and llama.cpp (dev.to)](https://dev.to/software_mvp-factory/on-device-llm-inference-via-kmp-and-llamacpp-4pec)
- [Adapting MediaPipe Demos for KMP — LLM Inference (2BAB)](https://2bab.me/en/blog/2024-09-01-on-device-model-integration-kmp/)
- [Run Gemma and VLMs on mobile with llama.cpp (Medium)](https://farmaker47.medium.com/run-gemma-and-vlms-on-mobile-with-llama-cpp-dbb6e1b19a93)
- [Gemma 3n / 4 for Edge Deployment — E2B/E4B on Phones (MindStudio)](https://www.mindstudio.ai/blog/gemma-4-edge-deployment-e2b-e4b-models)

## Risks to Watch

- **Llamatik bus factor:** single-maintainer community project. If it stalls, fall back to hand-rolled cinterop (the dev.to / 2BAB references show working examples).
- **App Store review:** large bundled models can trigger Apple review questions. Have a story ready ("local AI inference for offline use").
- **Battery / thermal:** sustained generation on older devices is rough. UI must surface this honestly.
- **Schema drift:** if Phase 18 evolves the workout/recipe JSON schema, the GBNF grammar must follow — codegen the grammar from the schema to avoid manual sync.

# Multi-Model Eval Results — 2026-05-10

## TL;DR

- **Most consistent for workout:** **Gemma 4 31B** (mean **4.00 / 4**, stdev 0.00) — tied with Qwen3-235B and Llama-3.3-70B-Turbo on perfect runs, but Gemma is the only model that *also* nails recipe.
- **Most consistent for recipe:** **Gemma 4 31B** (mean **4.00 / 4**, stdev 0.00) — every other model fell to ≤ 2.67/4 mean, often with a structural failure mode that won't go away across re-runs.
- **Overall recommendation:** **`google/gemma-4-31B-it`** for both prompts. It is the only model in this set that gave 12/12 across both suites with zero variance, at sane latency (~9–14s/test), and with the user's already-paid token credits.

## Methodology

- 5 models × 2 suites × 3 reps each (planned). One model (Llama 4 Maverick) was unreachable on Together's serverless tier with reproducible 503 — substituted with **Llama-3.3-70B-Instruct-Turbo** for fair Llama representation.
- Per-run config: `temperature: 0.7`, `max_tokens: 4096`, `response_format: { type: 'json_object' }`.
- All runs with `--no-cache` to force fresh API calls (without it, promptfoo would serve the same cached completion 3× and report fake 0-stdev).
- Each `(model, suite, rep)` tuple writes its own JSON to `runs/<suite>-<model>-r<n>.json`. Aggregated metrics in `runs/aggregate.json`.
- Sweep driver: `evals/multi-model.mjs`.
- **Total tokens consumed: 308,910** (~1.4× over briefing's 750k upper estimate's lower bound; some models terminated early via the broken-model guard).

### Final ranking metric

`consistencyScore = mean − stdev` (penalizes models that occasionally pass but flicker run-to-run).

## Workout suite results

| Model | Run 1 | Run 2 | Run 3 | Mean | Stdev | Score | Notes |
|---|---|---|---|---|---|---|---|
| `google/gemma-4-31B-it` | 4/4 | 4/4 | 4/4 | **4.00** | 0.00 | **4.00** | Perfect, every test, every run |
| `Qwen/Qwen3-235B-A22B-Instruct-2507-tput` | 4/4 | 4/4 | 4/4 | **4.00** | 0.00 | **4.00** | Perfect on workout (note: collapses on recipe) |
| `meta-llama/Llama-3.3-70B-Instruct-Turbo` | 4/4 | 4/4 | 4/4 | **4.00** | 0.00 | **4.00** | Perfect on workout (note: weak on recipe) |
| `openai/gpt-oss-20b` | 2/4 | 3/4 | 4/4 | 3.00 | 0.82 | 2.18 | High variance — `Thinking:` trace eats budget |
| `openai/gpt-oss-120b` | 3/4 | 3/4 | 2/4 | 2.67 | 0.47 | 2.20 | UPPER_LOWER + 1-template tests fail consistently |
| `meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8` | err | — | — | n/a | n/a | n/a | Together 503 on every request — not actually serverless? |

### Per-test breakdown — workout

| Test | gpt-oss-20b | gemma-4-31B | gpt-oss-120b | Qwen3-235B | Llama-3.3-70B |
|---|---|---|---|---|---|
| `single template · 5 ex · chest+shoulders` | 1/3 | 3/3 | 2/3 | 3/3 | 3/3 |
| `Push/Pull/Legs · 4 per template` | 2/3 | 3/3 | 3/3 | 3/3 | 3/3 |
| `Upper/Lower · 6 per template` | 3/3 | 3/3 | **1/3** | 3/3 | 3/3 |
| `minimal · 1 exercise · biceps` | 3/3 | 3/3 | 2/3 | 3/3 | 3/3 |

**What this surfaces:**
- The briefing's `UPPER_LOWER → 6 templates instead of 2` hypothesis turns out to be *only* a `gpt-oss-120b` problem in this run; the failure reason was `templates is not an array`, meaning 120b sometimes emitted a single template object rather than wrapping it in an array. Gemma, Qwen, and Llama-3.3 handle UPPER_LOWER cleanly.
- `gpt-oss-20b` has truly random variance on the 1-template / PPL tests because it sometimes runs out of `max_tokens` mid-`Thinking:` and never emits the JSON.

## Recipe suite results

| Model | Run 1 | Run 2 | Run 3 | Mean | Stdev | Score | Notes |
|---|---|---|---|---|---|---|---|
| `google/gemma-4-31B-it` | 4/4 | 4/4 | 4/4 | **4.00** | 0.00 | **4.00** | Perfect, every test, every run |
| `openai/gpt-oss-120b` | 3/4 | 3/4 | 2/4 | 2.67 | 0.47 | 2.20 | `Thinking:` trace truncates JSON on 2 of 4 tests |
| `meta-llama/Llama-3.3-70B-Instruct-Turbo` | 1/4 | 0/4 | 0/4 | 0.33 | 0.47 | -0.14 | Macros consistently off-target by >15% |
| `openai/gpt-oss-20b` | 0/4 | — | — | 0.00 | 0.00 | 0.00 | All 4 truncated mid-`Thinking:` (no JSON ever emitted); broken-model guard skipped reps 2–3 |
| `Qwen/Qwen3-235B-A22B-Instruct-2507-tput` | 0/4 | — | — | 0.00 | 0.00 | 0.00 | Mode collapse: emits same Hähnchen-Reis-Bowl regardless of target macros |
| `meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8` | err | — | — | n/a | n/a | n/a | Together 503 |

### Per-test breakdown — recipe (only models that produced data)

| Test | gpt-oss-20b | gemma-4-31B | gpt-oss-120b | Qwen3-235B | Llama-3.3-70B |
|---|---|---|---|---|---|
| `small · breakfast · 600 kcal` | 0/1 | 3/3 | 3/3 | 0/1 | 0/3 |
| `medium · standard · 1100 kcal` | 0/1 | 3/3 | **1/3** | 0/1 | 0/3 |
| `large · bulk · 2000 kcal` | 0/1 | 3/3 | **1/3** | 0/1 | 0/3 |
| `high protein · low fat · 800 kcal` | 0/1 | 3/3 | 3/3 | 0/1 | 1/3 |

(`gpt-oss-20b` and `Qwen3` show 0/1 because the broken-model guard halted them after rep 1.)

**What this surfaces:**
- The briefing's "small kcal targets get refused" hypothesis is **not** the dominant pattern. The `600 kcal` case is actually one of the *easier* ones — gpt-oss-120b nails it 3/3, and only Llama-3.3 trips on it (with macros, not refusal). The harder cases are the *medium and large* budgets where models routinely undershoot.
- Llama-3.3 fails recipe by **systematically getting macros wrong**, not by refusing or producing invalid JSON. `protein 47g vs 30g`, `kcal 1258 vs 2000` — it produces a valid recipe, just not the one the prompt asked for. This is genuine prompt-following failure, not a structural issue.

## Failure mode patterns

Three structurally distinct failure types showed up — they need different fixes.

### 1. Reasoning-trace token starvation (`gpt-oss-20b` and partially `gpt-oss-120b`)
Together's `togetherai` provider concatenates `"Thinking: " + reasoning + content` into the response string. With `max_tokens: 4096`, the *combined* budget includes the reasoning trace. On long recipe prompts, `gpt-oss-20b` regularly burns the entire 4096 on reasoning and never emits the JSON content — output is just `Thinking: We need...` truncated mid-thought.

Validators correctly report `JSON parse failed: Unexpected token 'T'`. The bracket-matcher can't find any `{...}` block because none was emitted. This is **not** a validator bug — it's a budget issue specific to reasoning models on this task.

`gpt-oss-120b` hits this less often because it reasons more efficiently, but still fails ~2/3 on the medium/large recipe cases.

### 2. Schema-shape inconsistency (`gpt-oss-120b` workout)
On 2 of 3 runs, `gpt-oss-120b` produces `{ "name": "...", "exercises": [...] }` — a **single template object** — instead of `{ "templates": [{ ... }] }`. The validator correctly rejects with `templates is not an array`. The prompt does say "always return a `templates` array of length N", but the model occasionally collapses single-template results into a bare object. This is consistent within the model — fixing it requires a stricter schema reminder, not just retries.

### 3. Mode collapse on recipe (`Qwen3-235B`)
Qwen produces *valid JSON*, but it's the same recipe ("Hähnchen-Reis-Bowl mit Brokkoli und Avocado") for `600 kcal`, `1100 kcal`, and `2000 kcal` targets. The output gets re-scaled, but the macros end up off-target by more than 15% (e.g., 731 vs 600 kcal, 73g vs 55g protein). The model is treating "produce a recipe" as the dominant constraint and "match these specific macros" as a soft hint. This pattern is robust across very different inputs — it won't go away with re-runs.

### 4. Macro arithmetic weakness (`Llama-3.3-70B`)
Llama-3.3 produces sensible, varied recipes (not mode collapse), but the macros are systematically *under* on kcal and *over* on protein. It picks reasonable ingredients but doesn't actually do the multi-ingredient sum that lands within ±15% of the target. The prompt explicitly states the tolerance — Llama-3.3 just doesn't compute precisely enough.

## Recommended prompt changes

Based on the failure patterns above (do **not** apply these without re-running the eval — that's the whole point of the harness):

1. **Workout prompt — restate the templates-is-an-array contract at the END of the system prompt**, not just at the top. `gpt-oss-120b` is paying attention to the tail of the prompt; restating the wrapper there should fix the bare-object failures (Failure mode #2).

2. **Recipe prompt — add an explicit worked example near the end** showing macro arithmetic for one target. Something like:
   > Example reasoning: target 1100 kcal / 55g protein → pick 200g chicken breast (330 kcal, 62g protein) + 150g rice (200 kcal, 4g protein) + 100g broccoli (35 kcal, 3g protein) + 1 tbsp olive oil (120 kcal) + 80g mango (50 kcal) → totals 735 kcal, 69g protein. Adjust quantities until kcal and protein land within ±15% of target.

   This nudges Llama-3.3 toward actually doing the sum (Failure mode #4) and may also reduce Qwen's mode collapse (Failure mode #3) by giving it a concrete macro-driven decision pattern.

3. **Don't rely on reasoning models for recipe with `max_tokens: 4096`.** `gpt-oss-20b` is structurally unsuited — most of the budget goes to thinking. Either bump to `max_tokens: 8192+`, set `reasoning: { effort: "low" }`, or just don't use reasoning models for this task. (Stick with non-reasoning instructs like Gemma / Llama / Qwen for production.)

4. **Add a hard refusal hook.** Even Gemma occasionally got a refusal in earlier sessions; the `refusal` field is in the validator but only Gemma seems to use it correctly. The prompt should be explicit: "If you cannot satisfy the constraints, return `{\"refusal\": \"reason\"}`. Do not return a partial or off-target recipe."

## Recommended model

**`google/gemma-4-31B-it`** for both prompts.

| Criterion | Gemma 4 31B vs alternatives |
|---|---|
| **Consistency** | Only model with 12/12 across both suites at 0 stdev |
| **Cost** | Mid-tier on Together's serverless. User has paid credits. |
| **Latency** | ~9–14s/test, no reasoning-trace overhead |
| **Determinism** | Pure instruct, no thinking tokens — same prompt → equivalent output reliably |
| **Risk** | Low — failure mode (if any) wasn't observed in 24 attempts |

**Runners-up by use-case:**
- If only the workout prompt matters (cheaper backup): **`Qwen/Qwen3-235B-A22B-Instruct-2507-tput`** — 4/4 perfect, no mode-collapse on workout, and Qwen3 is currently a strong free-tier option. **Do not** use Qwen for recipe.
- For Llama preference (e.g., self-host later): **`meta-llama/Llama-3.3-70B-Instruct-Turbo`** — 4/4 perfect on workout, but expect to fix the macro arithmetic before recipe is usable. Llama 4 Maverick is currently broken on Together's serverless tier (reproducible 503 — possibly a deployment outage on 2026-05-10) and Scout requires a dedicated endpoint.
- **Do not** use `openai/gpt-oss-*` for recipe in this prompt + max_tokens shape. Reasoning models are wrong-tool-for-job here.

## Caveats

- Llama 4 Maverick was unreachable on Together's serverless tier with reproducible 503 over multiple retries. Llama-3.3-70B-Turbo was substituted as the Llama representative. If Maverick comes back online before the deadline, re-run the harness against it; the briefing's expectation that "Llama 4 = current flagship" should hold but couldn't be measured today.
- 3 reps per `(model, suite)` is enough to distinguish "perfect" from "flickery" but doesn't bound the long tail of 1-in-50 failures. Gemma's 12/12 here is encouraging but not a guarantee.
- All variance numbers are stdev of pass *count* (raw integer 0–4), not pass *rate*. Multiply by 25 to convert to percentage points.
- The `gpt-oss-20b` recipe and `Qwen3-235B` recipe rows are short because the broken-model guard halted them after rep 1's 0/4. The pattern was structural enough that more reps wouldn't have changed the conclusion.

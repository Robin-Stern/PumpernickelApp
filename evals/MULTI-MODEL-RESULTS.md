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

---

# B.1 — Workout-Prompt Wrapper-Restatement (2026-05-11)

## Edit applied

Commit `54167d7` appended two sentences to the `## Final reminder` block in `shared/src/commonMain/resources/workout-system-prompt.md`:

> The top-level shape is always `{ "templates": [...], "inlineNewExercises": [...] }`.
> Even if `templatesExpected = 1`, `templates` MUST still be an array containing one object — NEVER a bare template object.

Goal: fix `gpt-oss-120b`'s "templates is not an array" failure mode (baseline: 2.67/4 mean, hit `templates is not an array` on UPPER_LOWER 2/3 + single-template 1/3).

## Re-eval (workout suite × 5 models × 3 reps, 108,781 tokens)

Aggregate: `runs/aggregate-B1.json`.

| Model | Baseline | B.1 | Δ mean | Δ stdev | Verdict |
|---|---|---|---|---|---|
| `openai/gpt-oss-20b` | 3.00 (σ 0.82) | **3.33** (σ 0.47) | **+0.33** | **-0.35** | improved |
| `google/gemma-4-31B-it` | 4.00 (σ 0.00) | 4.00 (σ 0.00) | 0 | 0 | unchanged (already perfect) |
| `openai/gpt-oss-120b` | 2.67 (σ 0.47) | **2.00** (σ 0.00) | **-0.67** | -0.47 | **REGRESSED** |
| `meta-llama/Llama-4-Maverick…FP8` | n/a (503) | n/a (503) | — | — | still unreachable on Together serverless |
| `Qwen/Qwen3-235B-A22B-Instruct-2507-tput` | 4.00 (σ 0.00) | 4.00 (σ 0.00) | 0 | 0 | unchanged (perfect) |

### Per-test breakdown vs. baseline (workout) — only changed cells shown

| Test | gpt-oss-20b | gpt-oss-120b |
|---|---|---|
| `single template · 5 ex · chest+shoulders` | 1/3 → 3/3 ✓ | **1/3 → 0/3 ✗** ⚠️ |
| `Push/Pull/Legs · 4 per template` | 2/3 → 2/3 | 3/3 → 3/3 |
| `Upper/Lower · 6 per template` | 3/3 → 3/3 | 1/3 → 1/3 |
| `minimal · 1 exercise · biceps` | 3/3 → 2/3 (one JSON-parse fail, see below) | 2/3 → 2/3 |

## What the regression actually looks like

The targeted failure (`templates is not an array`) **did not move**:
- UPPER_LOWER on gpt-oss-120b: still fails 2/3 (unchanged).
- Single-template on gpt-oss-120b: was 1/3 failures → now **3/3 failures**. All three runs report exactly `templates is not an array (and no refusal field)` — the model still emits a bare template object at the top level.

Plausible mechanism: the added sentence explicitly names `templatesExpected = 1` as the danger case. For a reasoning model that pattern-matches phrases from the system prompt into its "thinking" trace, the call-out makes it MORE likely to dwell on the single-template branch, decide that's "the important shape," and emit it directly. The intended de-biasing turned into a salience prime.

Counter-evidence: `gpt-oss-20b` improved (3.00 → 3.33) on the same prompt, including 3/3 on single-template (vs. 1/3 baseline). So the new wording *helps* some models and *hurts* others. The 120b-specific regression isn't fully explained by salience alone — could also be ordinary variance compounded by Together's serverless temperature today (see Gemma latency note below).

## Side observations

- **Gemma 4 31B latency spike on Together today:** ~196–268s per run (vs. ~9–14s in baseline). Pass rate unaffected (4/4 every time). Likely Together's serverless tier was congested at 22:09–22:17 UTC on 2026-05-11; not prompt-related.
- **Llama 4 Maverick still 503-ing.** Outage at minimum 25h+ now.
- **One JSON-parse failure on gpt-oss-20b minimal-biceps in rep 2.** Same `Thinking:` truncation pattern as baseline recipe runs — reasoning trace eats the budget on what should be a trivial 1-exercise template. Not a prompt issue; this is the structural max_tokens issue called out for B.3.

## Conclusion — verdict on B.1 edit

**Mixed-to-negative.** The edit did not fix the targeted `gpt-oss-120b` failure mode, and it made the simple-case fail rate worse on that model. The production model (`gemma-4-31B-it`) is unaffected — still 12/12 — so end users running the default config see no change. But the eval-driven hypothesis ("restate the wrapper at the end fixes 120b") does not hold.

Three reasonable next moves:
1. **Revert `54167d7`.** B.1 was wrong; move on to B.2 (recipe worked-example).
2. **Try a different formulation in B.1b** that doesn't name the dangerous case explicitly. E.g. front-load the wrapper restatement near the schema example rather than appending it at the end; or use only the first new sentence (drop the "Even if templatesExpected = 1" call-out).
3. **Accept and proceed.** 120b isn't a recommended production model anyway (Gemma is). If we're not shipping 120b, the regression is academic.

**Resolution:** option 1 chosen. Commit `54167d7` reverted in `501c31d`. The workout prompt is back to baseline.

---

# B.2 — Recipe Worked-Example for Macro Arithmetic (2026-05-11)

## Edit applied

Commit `380d8d2` inserted a new `## Worked example — hitting the macro target` section into `shared/src/commonMain/resources/recipe-system-prompt.md`, between the existing `## Composition guidance` and `## Refusal` sections. The example demonstrates one full iteration for `remaining = { kcal: 1100, protein: 55g }`:

> 1. First draft with 4 ingredients (Hähnchenbrust 200g + Reis 200g + Brokkoli 150g + Olivenöl 10g) → sum (729 kcal, 71.6g protein)
> 2. Tolerance check: kcal short by 34% → off-target
> 3. Adjustment: bump Reis to 550g → +455 kcal
> 4. Re-sum (1068.9 kcal, 59.35g protein) → both inside ±10% ✓
>
> Closing rule: "Calculate, check, adjust until both kcal AND protein land inside ±10%. Don't settle for 'close enough.'"

Goal: address Llama-3.3-70B-Turbo's macro-arithmetic weakness (baseline 0.33/4 mean — produced sensible recipes but macros off-target by >15%) and Qwen3-235B's mode collapse (baseline 0/4 — emitted the same Hähnchen-Reis-Bowl regardless of target).

## Re-eval (recipe suite, 5 standard models × 3 reps + Llama-3.3 × 3 reps, 220k + ~90k = ~310k tokens)

Aggregate: `runs/aggregate-B2.json` (standard 5 models) and `runs/recipe-llama33-B2-r{1,2,3}.json` (Llama-3.3 separately).

| Model | Baseline | B.2 | Δ mean | Δ stdev | Verdict |
|---|---|---|---|---|---|
| `openai/gpt-oss-20b` | 0.00* (σ 0.00) | 0.67 (σ 0.94) | +0.67 | +0.94 | improved but unstable |
| `google/gemma-4-31B-it` | 4.00 (σ 0.00) | **4.00** (σ 0.00) | 0 | 0 | **unchanged — production model still perfect** ✓ |
| `openai/gpt-oss-120b` | 2.67 (σ 0.47) | 2.67 (σ 0.47) | 0 | 0 | unchanged |
| `meta-llama/Llama-4-Maverick…FP8` | n/a (503) | n/a (503) | — | — | still 503 |
| `Qwen/Qwen3-235B-A22B-Instruct-2507-tput` | 0.00* (σ 0.00) | **2.00** (σ 0.00) | **+2.00** | 0 | **major improvement** ✓ |
| `meta-llama/Llama-3.3-70B-Instruct-Turbo` | 0.33 (σ 0.47) | **1.67** (σ 0.47) | **+1.33** | 0 | **major improvement on B.2's primary target** ✓ |

*The `0.00*` for `gpt-oss-20b` and `Qwen` in the baseline column reflects rep-1-only data (the broken-model guard halted further runs in the original sweep). The Δ for those rows is conservative.

### Per-test breakdown — recipe, B.2 vs. baseline (only models with movement shown)

| Test | gpt-oss-20b | Qwen3-235B | Llama-3.3-70B |
|---|---|---|---|
| `small · 600 kcal` | 0/1 → 1/3 | 0/1 → 0/3 | 0/3 → 0/3 |
| `medium · 1100 kcal` | 0/1 → 1/3 | 0/1 → **3/3** ✓ | 0/3 → **3/3** ✓ |
| `large · 2000 kcal` | 0/1 → 0/3 | 0/1 → 0/3 | 0/3 → 0/3 |
| `high protein · 800 kcal` | 0/1 → 0/3 | 0/1 → **3/3** ✓ | 1/3 → 2/3 |

## What worked (and what the data tells us about the mechanism)

**The `1100 kcal` test went from 0/3 to 3/3 on both Qwen and Llama-3.3.** That's not a coincidence — `1100 kcal` is the exact target value used in the worked-example block. Models learned the pattern shown to them. The `high protein · 800 kcal` test also flipped to 3/3 on Qwen and 2/3 on Llama-3.3 — proximity (in target magnitude and protein focus) seems to be enough for transfer.

**The `600 kcal` and `2000 kcal` extremes stayed broken.** Looking at the failure reasons:

- Llama-3.3 `small 600 kcal`: still gets protein 37–47g vs target 30g — closer than baseline (47g) but still over the 15% tolerance.
- Llama-3.3 `large 2000 kcal`: still undershoots kcal (1354–1410 vs 2000) — same direction as baseline (1258), only slightly closer.
- Qwen `small/large`: same protein-over / kcal-under pattern.

The worked-example uses *middle-of-the-range* numbers (1100 kcal, 55g protein) and didn't transfer well to ends of the range. The models are doing one-shot pattern matching, not generalized arithmetic.

## What didn't change

`gpt-oss-120b` is unchanged (2.67/4 both before and after). Its remaining failures are still "Thinking: ..." truncation on the medium/large recipe targets — a structural max_tokens issue, not a prompt-content issue. B.3 (max_tokens tuning for reasoning models) is the planned fix.

`gpt-oss-20b` improved from "always 0" to "sometimes 1–2/4" but the stdev jumped to 0.94 (the highest in this run). Same root cause as 120b — reasoning trace eats the budget — and the longer recipe prompt (now with the worked example) makes the budget pressure marginally worse. Don't read the +0.67 as a real fix; the variance says it's mostly noise around the same structural failure.

## What's notable about the Llama-3.3 result

Llama-3.3-70B was **the primary target** of B.2 — the only model from the baseline that produced valid recipes with wrong macros (the "macro arithmetic weakness" failure mode #4). Going from 0.33 to 1.67 (5× improvement) on that exact failure mode is the strongest single-edit signal we've measured. The pattern is also clean: the test case whose target matched the worked example transferred cleanly; the extremes didn't.

This validates the hypothesis from the original report (recommendation #2 — "Add an explicit worked example") in a way the B.1 hypothesis did not.

## Side note — Together latency

Gemma 4 31B took 195–600s per recipe run today (vs. ~14s for workout last sweep). Same model, same prompt structure, just heavier traffic on Together's serverless tier this evening. Pass rate unaffected (4/4 every run). For production: prefer paid/dedicated endpoints if response latency matters; the consumer experience would be miserable on the current serverless tier at this hour.

## Verdict on B.2 edit

**Keep the edit.** Gemma is unaffected (production-safe), Qwen and Llama-3.3 both improved on their target failure modes, gpt-oss-20b shows directional improvement, and no model regressed. The `~310k` tokens spent on B.2 measurement were a clear positive return.

Next: B.3 (reasoning-model max_tokens tuning to fix the `Thinking:` truncation on gpt-oss-*) and B.4 (refusal-hook formulation).

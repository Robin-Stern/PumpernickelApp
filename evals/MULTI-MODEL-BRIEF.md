# Multi-Model Consistency Eval — Briefing for Fresh Claude

You are continuing work on **PumpernickelApp**, a Kotlin Multiplatform fitness
tracking app. A previous session built an evaluation harness using
[promptfoo](https://promptfoo.dev) at `evals/`. The workout-AI suite passed
7/8 (87.5%) on a single 2-model run — now we want to know **which model
gives the most consistent results across repeated runs**, because a one-shot
87.5% from a probabilistic system isn't enough to trust in production.

## Your mission

1. Run **both eval suites** (workout + recipe) against **5 different Together
   models**, executing each suite **3 times per model** to measure variance.
2. Aggregate pass rates per `model × suite`, compute mean + standard
   deviation, identify which model is the **most consistent** (highest mean,
   lowest stdev) for each suite.
3. Document model-specific failure modes — does Model X always miss the
   UPPER_LOWER case? Does Model Y refuse on small kcal targets? Patterns
   matter as much as raw numbers.
4. Write a markdown report `evals/MULTI-MODEL-RESULTS.md` with a comparison
   table, ranked recommendations for each suite, and concrete prompt
   improvements suggested by the failure patterns you observe.
5. Commit the report.

You should be **autonomous** — the user wants you to run this end-to-end
without back-and-forth on methodology. Optimize for clean comparable data,
not perfect results.

## Models to test

Verified against Together's live serverless catalog as of **2026-05-10**
(see `https://www.together.ai/models` and
`https://docs.together.ai/docs/serverless-models`). Older lineups (Llama-3.x,
Gemma 2/3, Qwen 2.5) are deprecated or not in the serverless tier — do NOT
substitute them.

In this exact order (cheapest/smallest first, so token budget burns from
the bottom):

```yaml
- togetherai:openai/gpt-oss-20b                                  # free baseline, reasoning, ~20B
- togetherai:google/gemma-4-31B-it                               # 31B, instruct — user bought tokens for this
- togetherai:openai/gpt-oss-120b                                 # 120B reasoning — A/B against the 20b sibling
- togetherai:meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8   # Llama 4 MoE (~400B sparse), instruct
- togetherai:Qwen/Qwen3-235B-A22B-Instruct-2507-tput             # 235B MoE, pure instruct (no reasoning trace)
```

**Why this set, given the eval target (deterministic JSON output):**
- `gpt-oss-20b` and `gpt-oss-120b` — both are **reasoning models** (emit a
  thinking trace before the answer). Bracket-matcher in the validators
  handles this. Comparing 20b vs 120b directly tells us if scale fixes
  prompt-following bugs (the UPPER_LOWER → 6 templates failure).
- `gemma-4-31B-it` — pure instruct, no reasoning trace. Should be the
  most consistent if the prompt is clear; if it underperforms the
  reasoning models, that's a prompt issue.
- `Llama-4-Maverick-17B-128E-Instruct-FP8` — current-gen Llama flagship
  (replaces the deprecated Llama-3.3-70B-Instruct-Turbo I had earlier).
  17B active params, 128 experts, ~400B sparse total.
- `Qwen3-235B-A22B-Instruct-2507-tput` — Qwen3 instruct (NOT thinking
  variant). Strong on structured output. The `-tput` suffix is the
  throughput-optimized endpoint (~22B active per token).

**Models considered and skipped — and why:**
- `google/gemma-2-27b-it`, `google/gemma-3-27b-it` — superseded by
  gemma-4-31B. Gemma 3 is still listed but we have direct access to 4.
- `meta-llama/Llama-3.3-70B-Instruct-Turbo` — superseded by Llama 4.
- `deepseek-ai/DeepSeek-V4-Pro`, `DeepSeek-R1` — reasoning models, very
  slow on free/serverless, prior session confirmed timeouts.
- `Qwen/Qwen2.5-7B-Instruct-Turbo` — too small, prior session showed
  reasoning loops.
- `moonshotai/Kimi-K2.6` (1T), `zai-org/GLM-5` (744B), `Qwen3.5-397B-A17B`
  — capable but expensive. Add as a stretch goal if the first 5 finish
  cleanly and the user wants more data.
- `MiniMaxAI/MiniMax-M2.7`, `deepcogito/cogito-v2-1-671b`,
  `nvidia/Nemotron-3-Super` — interesting but less proven on
  strict-JSON tasks. Out of scope for this run.

If you find a model consistently broken (refuses everything / parses 0/4)
after run 1, log it and skip remaining repetitions to save tokens.

## Repository state — what already works

Working dir: `/Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/`

**Eval harness location:** `evals/`

**Layout:**
```
evals/
├── prepare.mjs              # generates prompts/*.json from shared/.../resources/*.md
├── promptfoo.workout.yaml   # workout suite config
├── promptfoo.recipe.yaml    # recipe suite config
├── cases/
│   ├── workout.json         # 4 workout test cases (templatesExpected, exerciseCount, etc.)
│   ├── workout-validator.cjs
│   ├── recipe.json          # 4 recipe test cases (remainingKcal, remainingProtein, ...)
│   └── recipe-validator.cjs
├── package.json             # type=module — validators MUST be .cjs
├── run.sh                   # convenience wrapper
└── README.md                # full doc
```

**System prompts** (read-only — these are the things being evaluated):
- `shared/src/commonMain/resources/workout-system-prompt.md`
- `shared/src/commonMain/resources/recipe-system-prompt.md`

`prepare.mjs` substitutes `{locale}` → `de` and writes JSON chat templates
to `evals/prompts/`. Re-run it any time the source `.md` changes.

## Setup commands

```bash
# 1. Working directory + path
cd /Users/olli/Studium/semester_6/mobile_app_dev/PumpernickelApp/evals
export PATH=/opt/homebrew/bin:$PATH   # use system node v25, NOT nvm node

# 2. Install (only first run)
rm -rf node_modules package-lock.json   # nuclear, but avoids ABI mismatch
/opt/homebrew/bin/npm install

# 3. Get API key — ASK THE USER for a fresh TOGETHER_API_KEY.
#    The previous one was leaked in chat and rotated. Do NOT proceed
#    without a new one. Once received:
export TOGETHER_API_KEY=<their-fresh-key>

# 4. Single eval run
./node_modules/.bin/promptfoo eval -c promptfoo.workout.yaml --no-cache

# 5. Filter to one provider (for debugging or repeat runs):
./node_modules/.bin/promptfoo eval -c promptfoo.workout.yaml \
    --filter-providers <regex-matching-model-id> --no-cache
```

`--no-cache` is **important** for variance measurement — without it,
promptfoo serves the same response three times and you'll see zero
variance for the wrong reason.

## Methodology

For each `(model, suite)` pair:

1. Edit the YAML to include only that one model under `providers:`.
2. Run `promptfoo eval -c <suite>.yaml --no-cache` three times in a row.
3. After each run, capture the eval ID from the output and run
   `promptfoo export eval <id> -o /tmp/run-<n>.json`.
4. Parse the JSON, extract per-test pass/fail + the `reason` field.
5. Move to next pair.

**Token budget:** ~25k tokens per full 8-test run × 5 models × 3 reps × 2
suites ≈ 750k tokens. The user has paid Together credits but be sensible —
abort early if a model is clearly broken.

**Script suggestion:** write a small Node helper at `evals/multi-model.mjs`
that drives the whole sweep + aggregates. Don't run 30 commands manually.

## What "consistent" means

For each `(model, suite)`:
- **Mean pass rate** across 3 runs (e.g. 6/8, 7/8, 8/8 → mean 87.5%)
- **Stdev** (raw count, not %) — 0 stdev with non-zero pass = perfect
  consistency
- **Per-test pass count** — does test #3 fail 3/3 times? That's a
  prompt issue. Does it fail 1/3 times? That's model variance.

Final ranking metric: `mean - stdev` (penalizes inconsistency).

## Already-known issues — don't re-discover these

Bugs the previous session found and fixed (do NOT touch unless you
identify a regression):

1. **Validators must be `.cjs`** — `package.json` is `"type": "module"`.
   If you see "Custom function must return ..." errors, the validator was
   loaded as ESM and `module.exports` no-op'd.
2. **Validator returns must include `score` AND `reason`**. Promptfoo's
   `GradingResult` accepts `{pass, score, reason}` but rejects `{pass, score}`
   at runtime despite the docs saying `reason` is optional. Always include
   all three fields.
3. **`extractJsonObject` picks the LONGEST balanced `{...}` block**, not
   the first. Reasoning models quote the prompt's refusal-example JSON
   inside their thinking trace, so a first-match strategy parses the wrong
   object.
4. **The workout prompt previously had a "max 8 working sets per muscle
   group" rule** that caused refusals on small workouts. Already removed.
5. **Together returns reasoning text in the `reasoning` field**, but
   promptfoo's `togetherai` provider concatenates `"Thinking: " + reasoning + content`
   into the output string passed to validators. The bracket-matcher handles
   this — don't try to "fix" the prefix.

## Known prompt weaknesses to confirm via this eval

The previous run on gpt-oss-20b showed:
- **UPPER_LOWER split** sometimes produces 6 templates instead of 2 — the
  prompt may not bind `splitStyle` strictly enough. If multiple models hit
  this, propose a prompt fix in your report.
- **Recipe macros at small remaining values** (e.g. 600 kcal) sometimes get
  refused or under-shoot — report whether this is universal or model-specific.

Suggesting prompt edits in your report is welcome but **do not apply them
until the user reviews**. The harness exists precisely so prompt changes
can be measured.

## Deliverable — `evals/MULTI-MODEL-RESULTS.md`

Structure:

```markdown
# Multi-Model Eval Results — <date>

## TL;DR
- Most consistent for workout: <model> (mean X.X/8, stdev Y)
- Most consistent for recipe: <model> (mean X.X/8, stdev Y)
- Overall recommendation: <model + reasoning>

## Methodology
- 5 models × 2 suites × 3 runs each
- temperature 0.7, max_tokens 4096
- All --no-cache fresh API calls
- Total tokens consumed: <number>

## Workout suite results

| Model | Run 1 | Run 2 | Run 3 | Mean | Stdev | Notes |
|-------|-------|-------|-------|------|-------|-------|
| ... |

### Per-test breakdown
(which tests passed N/3 times — surface prompt vs model issues)

## Recipe suite results

(same table format)

## Failure mode patterns

(prose — what broke, in what models, what it suggests about prompts)

## Recommended prompt changes
(numbered list — concrete proposed edits to the .md prompts)

## Recommended model
<which model + why, taking into account cost, latency, consistency>
```

Commit with message:
`feat(evals): multi-model consistency report — <best model> wins`

## Final notes

- Your previous self ran a partial sweep on Together's gpt-oss-20b and
  Gemma 4 31B; do NOT trust those numbers as your baseline (different runs,
  cached responses). Run everything fresh.
- The user is patient with token spend but values an honest report —
  if a model is broken or you can't get clean data, say so explicitly
  rather than fabricating numbers.
- If you change the validators, run `node -e` against a known-good
  cached response first to make sure your change still passes that case.
- The user's original request: *"versuche mit dem gemma 4 31b und ein
  paar versch modellen, dann reflektieren welches modell konsistente
  ergebnisse erzielt"*. Reflection > raw numbers.
- **Don't trust hardcoded model lists** (including the one above). Before
  starting, sanity-check each ID with a one-shot call:
  `curl -sS -H "Authorization: Bearer $TOGETHER_API_KEY" \
   https://api.together.xyz/v1/models | jq -r '.[].id' | grep -i <name>`.
  If a listed ID returns 404 on first eval call, replace it from the
  live catalog rather than skipping.

Good luck.

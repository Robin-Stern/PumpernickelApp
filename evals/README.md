# AI Prompt Evaluation Harness

Reproducible quality testing for the Workout-AI and Recipe-AI system prompts.
Runs the actual prompts (`shared/src/commonMain/resources/*-system-prompt.md`)
against any OpenAI-compatible provider (Together, OpenAI, OpenRouter, Groq) and
checks each response against a JS assertion that mirrors what the app's
validators expect.

## Quick start

```bash
cd evals
export TOGETHER_API_KEY=<your-key>
npm install
./run.sh           # runs both workout + recipe suites against Together
npx promptfoo view # opens an HTML report in the browser
```

The first run downloads `promptfoo` (~50 MB Node deps). Subsequent runs are
fast — the prompts and test inputs only change when you edit them in
`prompts/` or `cases/`.

## Files

| File | Purpose |
|---|---|
| `prepare.mjs` | Copies the `.md` system prompts from `shared/...` into `prompts/`, substitutes `{locale}` → `de`. Re-run when you edit the source prompts. |
| `promptfoo.workout.yaml` | Workout-AI eval config: prompt + 4 input cases + JS assertions. |
| `promptfoo.recipe.yaml` | Recipe-AI eval config: prompt + 4 input cases + JS assertions. |
| `cases/workout.json` | Test inputs for workout (target muscles, exercise counts, split styles, available exercises). |
| `cases/recipe.json` | Test inputs for recipe (remaining macros). |
| `run.sh` | Runs prepare + both suites + opens viewer. |

## Switching providers / models

Edit the `providers:` array in either `promptfoo.*.yaml`. Each provider entry
points to an OpenAI-compatible endpoint via `apiBaseUrl` and reads the key
from `apiKeyEnvar`. Add as many models as you want to A/B compare them — the
HTML viewer aligns rows so you can see which prompt+model pair passes.

Examples shipped:
- `together-gpt-oss-20b` (free, recommended baseline)
- `together-gemma-4-31b` (newer, may need higher max_tokens for reasoning)

To add OpenAI:

```yaml
- id: openai-4o-mini
  config:
    apiBaseUrl: https://api.openai.com/v1
    apiKeyEnvar: OPENAI_API_KEY
    model: gpt-4o-mini
    max_tokens: 4096
    temperature: 0.7
    response_format: { type: json_object }
```

## Assertions

Each test case has a `javascript` assertion that:

1. Strips ` ```json ... ``` ` fences (LLMs often wrap JSON in them).
2. JSON-parses the cleaned output.
3. Checks structural invariants matching the app's validators:
   - **Workout**: `templates.length === templatesExpected`, every exercise has
     `targetSets ∈ 3..4`, `targetReps ∈ 8..12`, `restPeriodSec ∈ 60..120`,
     etc.
   - **Recipe**: macros within ±15 % of `remaining` for kcal and protein,
     `sugar ≤ carbohydrates`, valid `unit` enum, ≥ 4 ingredients.

A failed assertion shows the exact reason in the viewer alongside the raw
output, so you can iterate the prompt and re-run.

## Iterating on a prompt

1. Edit `shared/src/commonMain/resources/workout-system-prompt.md`.
2. `./run.sh` — re-runs `prepare.mjs` then evaluates.
3. Look at the HTML report. If a test fails, the assertion message tells you
   which invariant the model violated. Tighten the prompt and repeat.

The assertion code lives in `cases/workout-validator.js` and
`cases/recipe-validator.js` — keep it in sync with the app validators in
`WorkoutAiUseCase.validateResponse()` and `RecipeAiUseCase.validateResponse()`.

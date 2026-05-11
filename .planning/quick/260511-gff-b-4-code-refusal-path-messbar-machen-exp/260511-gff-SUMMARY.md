---
phase: 260511-gff
plan: 01
subsystem: evals
tags: [evals, refusal, prompts, validators]
requirements:
  - B4-CODE-refusal-path-measurable
key-files:
  modified:
    - evals/cases/workout-validator.cjs
    - evals/cases/recipe-validator.cjs
    - evals/cases/workout.json
    - evals/cases/recipe.json
    - shared/src/commonMain/resources/workout-system-prompt.md
    - shared/src/commonMain/resources/recipe-system-prompt.md
metrics:
  duration: ~5min
  completed: 2026-05-11
---

# Phase 260511-gff Plan 01: Refusal Path Measurable Summary

Refusal handling is now a first-class, observable axis in the eval harness: both validators honor `vars.expectRefusal`, both suites gained 2 impossible-scenario cases (now 6 cases each), and both system prompts have sharpened Refusal sections that prefer refusal over degraded output.

## Commits Created

| # | Hash      | Subject                                                                                |
| - | --------- | -------------------------------------------------------------------------------------- |
| 1 | `636c4cc` | feat(evals): validators honor vars.expectRefusal for refusal-path testing              |
| 2 | `bbda296` | test(evals): add 2 impossible-scenario refusal cases to workout suite                  |
| 3 | `9952406` | test(evals): add 2 impossible-scenario refusal cases to recipe suite                   |
| 4 | `e33ef0d` | feat(prompts): sharpen Refusal sections — prefer refusal over degraded output          |

## What Changed

**Validators (workout-validator.cjs, recipe-validator.cjs):**
- Replaced the single-line `if (parsed.refusal) → fail` block with a 3-branch handler placed at the same location (after JSON parse, before schema checks).
- Branch A: `parsed.refusal` present + `expectRefusal=true` → pass.
- Branch B: `parsed.refusal` present + no `expectRefusal` → fail (preserves existing behavior).
- Branch C: no refusal but `expectRefusal=true` → fail (model failed to refuse).
- Backward-compat: existing 4 cases per suite have no `expectRefusal`, so Branch A is bypassed and Branch B preserves prior failure semantics. Branch C never fires for them.

**Cases (workout.json, recipe.json):**
- workout.json: appended 2 cases — `"non-fitness request · expect refusal"` (Hawaii vacation prompt, omits fitness vars on purpose) and `"unsafe loading on stated injury · expect refusal"` (herniated disc + deadlift-heavy `existingExercises`).
- recipe.json: appended 2 cases — `"impossibly low remaining kcal · expect refusal"` (50 kcal target) and `"impossibly high remaining kcal · expect refusal"` (20000 kcal target). Both use the existing `protein_g`/`fat_g`/etc. YAML format matched from the file (not the brief's `protein`/`fat`/etc. — see "Notes" below).

**Prompts:**
- workout-system-prompt.md: Refusal trigger broadened to include "cannot be safely satisfied with the given `existingExercises`". Added explicit "Prefer a refusal over a partial or unsafe workout. Never silently produce a degraded response when constraints cannot be met."
- recipe-system-prompt.md: Replaced the single-trigger ≤100 kcal rule with a 3-trigger list (≤100 kcal, >5000 kcal, can't land within ±15% kcal AND protein). Removed hardcoded German refusal message and replaced with the `<kurze Erklärung in {locale}>` placeholder that matches the rest of the prompt. Added "Don't ship an off-target recipe — refuse it."

## Confirmation — Original Descriptions Preserved

**workout.json** (indices 0-3, unchanged):
1. `single template · 5 exercises · chest+shoulders`
2. `Push/Pull/Legs split · 4 per template · full body`
3. `Upper/Lower split · 6 per template`
4. `minimal · 1 exercise · biceps`

**recipe.json** (indices 0-3, unchanged):
1. `small remaining · breakfast portion · 600 kcal`
2. `medium remaining · standard meal · 1100 kcal`
3. `large remaining · bulk meal · 2000 kcal`
4. `high protein · low fat · 800 kcal`

All 8 original descriptions verified preserved at their original indices via `jq` checks.

## No Eval / No prepare.mjs

- **No eval was run** — would have burned ~250k tokens, the orchestrator will trigger the re-eval.
- **No `prepare.mjs` invocation** — orchestrator re-bakes prompts into Kotlin sources.
- Only the 6 files in the plan's `files_modified` frontmatter were touched. Verified via `git diff --cached --name-only` before each of the 4 commits.

## Deviations from Plan

**None.** Plan executed exactly as written.

The plan itself anticipated one possible-deviation point (recipe.json YAML format `protein_g` vs `protein`) and pre-empted it with explicit guidance — the executor honored the file's existing `protein_g`/`fat_g`/`carbohydrates_g`/`sugar_g` convention as instructed. This was a follow-the-plan moment, not a deviation.

## Verification Sweep Results

Final sweep at end of Task 4:

```
node -c evals/cases/workout-validator.cjs    ✓
node -c evals/cases/recipe-validator.cjs     ✓
jq '. | length' workout.json == 6            ✓
jq '. | length' recipe.json == 6             ✓
2 expectRefusal=true in workout.json         ✓
2 expectRefusal=true in recipe.json          ✓
≥2 'expectRefusal' in workout-validator.cjs  ✓ (4 occurrences)
≥2 'expectRefusal' in recipe-validator.cjs   ✓ (4 occurrences)
'Prefer a refusal' x1 in workout prompt      ✓
"Don't ship an off-target recipe" x1 in recipe prompt  ✓
Old hardcoded German message removed         ✓
```

All 11 verification gates passed.

## Self-Check: PASSED

- All 4 expected commits exist on branch (`636c4cc`, `bbda296`, `9952406`, `e33ef0d`)
- All 6 files in `files_modified` have been modified
- No files outside the plan's scope were staged in any commit

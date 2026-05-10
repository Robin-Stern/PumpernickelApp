// Mirrors RecipeAiUseCase.validateResponse() in shared/.../RecipeAiUseCase.kt
// plus the targeting policy from recipe-system-prompt.md (kcal+protein within
// ±15% of remaining; we relax from ±10% in the prompt to ±15% here so a
// "close enough" recipe doesn't flunk evals — the app itself doesn't reject
// based on macro fit, only flags via MacrosFitIndicator).

// Find the LARGEST balanced {...} block — the model's reasoning often
// quotes prompt-example JSON snippets like {"refusal":"..."} which would
// trip up first-match extraction. The actual answer is the longest block.
function extractJsonObject(raw) {
  let t = raw.trim();
  if (t.startsWith("```")) {
    const firstNl = t.indexOf("\n");
    if (firstNl >= 0) {
      const inner = t.slice(firstNl + 1);
      const closeIdx = inner.lastIndexOf("```");
      t = (closeIdx >= 0 ? inner.slice(0, closeIdx) : inner).trim();
    }
  }
  const blocks = [];
  let depth = 0, openIdx = -1, inString = false, escape = false;
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (escape) { escape = false; continue; }
    if (ch === "\\" && inString) { escape = true; continue; }
    if (ch === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (ch === "{") { if (depth === 0) openIdx = i; depth++; }
    else if (ch === "}") {
      depth--;
      if (depth === 0 && openIdx >= 0) { blocks.push(t.slice(openIdx, i + 1)); openIdx = -1; }
      if (depth < 0) depth = 0;
    }
  }
  if (blocks.length === 0) return t;
  return blocks.sort((a, b) => b.length - a.length)[0];
}

const ALLOWED_UNITS = new Set(["GRAM", "MILLILITER"]);

module.exports = function (output, { vars }) {
  const cleaned = extractJsonObject(output);
  let parsed;
  try { parsed = JSON.parse(cleaned); }
  catch (e) {
    return {
      pass: false,
      score: 0,
      reason: `JSON parse failed: ${e.message}\n\nFirst 300 chars of raw output:\n${String(output).slice(0, 300)}`
    };
  }

  if (parsed.refusal) {
    return { pass: false, score: 0, reason: `LLM refused: ${parsed.refusal}` };
  }
  if (!parsed.name || typeof parsed.name !== "string") {
    return { pass: false, score: 0, reason: "recipe.name missing" };
  }
  if (!Array.isArray(parsed.ingredients) || parsed.ingredients.length === 0) {
    return { pass: false, score: 0, reason: "recipe.ingredients empty" };
  }

  let totals = { kcal: 0, protein: 0, fat: 0, carbs: 0, sugar: 0 };

  for (const [i, ing] of parsed.ingredients.entries()) {
    const f = ing.food;
    if (!f) return { pass: false, score: 0, reason: `ingredients[${i}].food missing` };
    if (!f.name) return { pass: false, score: 0, reason: `ingredients[${i}].food.name missing` };
    if (!ALLOWED_UNITS.has(f.unit))
      return { pass: false, score: 0, reason: `ingredients[${i}] unknown unit: ${f.unit}` };
    for (const k of ["calories", "protein", "fat", "carbohydrates", "sugar"]) {
      if (typeof f[k] !== "number" || f[k] < 0)
        return { pass: false, score: 0, reason: `ingredients[${i}].food.${k} invalid: ${f[k]}` };
    }
    if (f.sugar > f.carbohydrates)
      return { pass: false, score: 0, reason: `ingredients[${i}] sugar > carbs (${f.sugar} > ${f.carbohydrates})` };
    if (typeof ing.amountGrams !== "number" || ing.amountGrams < 0)
      return { pass: false, score: 0, reason: `ingredients[${i}].amountGrams invalid: ${ing.amountGrams}` };

    const factor = ing.amountGrams / 100.0;
    totals.kcal    += f.calories      * factor;
    totals.protein += f.protein       * factor;
    totals.fat     += f.fat           * factor;
    totals.carbs   += f.carbohydrates * factor;
    totals.sugar   += f.sugar         * factor;
  }

  if (!Array.isArray(parsed.steps) || parsed.steps.length === 0) {
    return { pass: false, score: 0, reason: "recipe.steps empty" };
  }

  // Macro fit check: kcal AND protein within ±15% of remaining.
  const within = (actual, target, pct) =>
    target <= 0 ? true : Math.abs((actual - target) / target) <= pct;

  if (!within(totals.kcal, vars.remainingKcal, 0.15)) {
    return {
      pass: false,
      score: 0,
      reason: `kcal off-target: ${totals.kcal.toFixed(0)} vs ${vars.remainingKcal} (>15%)`
    };
  }
  if (!within(totals.protein, vars.remainingProtein, 0.15)) {
    return {
      pass: false,
      score: 0,
      reason: `protein off-target: ${totals.protein.toFixed(0)}g vs ${vars.remainingProtein}g (>15%)`
    };
  }

  return { pass: true, score: 1, reason: "all checks passed" };
};

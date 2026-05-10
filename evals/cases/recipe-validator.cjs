// Mirrors RecipeAiUseCase.validateResponse() in shared/.../RecipeAiUseCase.kt
// plus the targeting policy from recipe-system-prompt.md (kcal+protein within
// ±15% of remaining; we relax from ±10% in the prompt to ±15% here so a
// "close enough" recipe doesn't flunk evals — the app itself doesn't reject
// based on macro fit, only flags via MacrosFitIndicator).

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
  const start = t.indexOf("{");
  if (start < 0) return t;
  let depth = 0, inString = false, escape = false;
  for (let i = start; i < t.length; i++) {
    const ch = t[i];
    if (escape) { escape = false; continue; }
    if (ch === "\\" && inString) { escape = true; continue; }
    if (ch === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) return t.slice(start, i + 1);
    }
  }
  return t.slice(start);
}

const ALLOWED_UNITS = new Set(["GRAM", "MILLILITER"]);

module.exports = function (output, { vars }) {
  const cleaned = extractJsonObject(output);
  let parsed;
  try { parsed = JSON.parse(cleaned); }
  catch (e) {
    return {
      pass: false,
      reason: `JSON parse failed: ${e.message}\n\nFirst 300 chars of raw output:\n${output.slice(0, 300)}`
    };
  }

  if (parsed.refusal) {
    return { pass: false, reason: `LLM refused: ${parsed.refusal}` };
  }
  if (!parsed.name || typeof parsed.name !== "string") {
    return { pass: false, reason: "recipe.name missing" };
  }
  if (!Array.isArray(parsed.ingredients) || parsed.ingredients.length === 0) {
    return { pass: false, reason: "recipe.ingredients empty" };
  }

  let totals = { kcal: 0, protein: 0, fat: 0, carbs: 0, sugar: 0 };

  for (const [i, ing] of parsed.ingredients.entries()) {
    const f = ing.food;
    if (!f) return { pass: false, reason: `ingredients[${i}].food missing` };
    if (!f.name) return { pass: false, reason: `ingredients[${i}].food.name missing` };
    if (!ALLOWED_UNITS.has(f.unit))
      return { pass: false, reason: `ingredients[${i}] unknown unit: ${f.unit}` };
    for (const k of ["calories", "protein", "fat", "carbohydrates", "sugar"]) {
      if (typeof f[k] !== "number" || f[k] < 0)
        return { pass: false, reason: `ingredients[${i}].food.${k} invalid: ${f[k]}` };
    }
    if (f.sugar > f.carbohydrates)
      return { pass: false, reason: `ingredients[${i}] sugar > carbs (${f.sugar} > ${f.carbohydrates})` };
    if (typeof ing.amountGrams !== "number" || ing.amountGrams < 0)
      return { pass: false, reason: `ingredients[${i}].amountGrams invalid: ${ing.amountGrams}` };

    const factor = ing.amountGrams / 100.0;
    totals.kcal    += f.calories      * factor;
    totals.protein += f.protein       * factor;
    totals.fat     += f.fat           * factor;
    totals.carbs   += f.carbohydrates * factor;
    totals.sugar   += f.sugar         * factor;
  }

  if (!Array.isArray(parsed.steps) || parsed.steps.length === 0) {
    return { pass: false, reason: "recipe.steps empty" };
  }

  // Macro fit check: kcal AND protein within ±15% of remaining.
  const within = (actual, target, pct) =>
    target <= 0 ? true : Math.abs((actual - target) / target) <= pct;

  if (!within(totals.kcal, vars.remainingKcal, 0.15)) {
    return {
      pass: false,
      reason: `kcal off-target: ${totals.kcal.toFixed(0)} vs ${vars.remainingKcal} (>15%)`
    };
  }
  if (!within(totals.protein, vars.remainingProtein, 0.15)) {
    return {
      pass: false,
      reason: `protein off-target: ${totals.protein.toFixed(0)}g vs ${vars.remainingProtein}g (>15%)`
    };
  }

  return { pass: true };
};

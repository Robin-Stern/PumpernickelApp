// Mirrors WorkoutAiUseCase.validateResponse() in shared/.../WorkoutAiUseCase.kt.
// Run from inside a promptfoo `javascript` assertion. The whole file is
// inlined into the YAML via `file://` reference.
//
// Inputs (provided by promptfoo):
//   - output:  string returned by the LLM
//   - context: { vars: { templatesExpected, exerciseCount, ... } }
// Returns: { pass: boolean, score?: number, reason?: string }

// Strip markdown fences, then extract the first balanced {...} object.
// Models often emit reasoning prose followed by the actual JSON; this
// finds the JSON regardless of where it is in the response. If no balanced
// object is found, returns the original (parse will then fail loudly).
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

  let depth = 0;
  let inString = false;
  let escape = false;
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

const ALLOWED_MUSCLES = new Set([
  "chest","shoulders","biceps","triceps","forearms","traps","lats","neck",
  "quadriceps","hamstrings","glutes","calves","adductors","abdominals",
  "obliques","lower back"
]);

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

  if (!Array.isArray(parsed.templates)) {
    return { pass: false, reason: "templates is not an array" };
  }
  if (parsed.templates.length !== vars.templatesExpected) {
    return { pass: false, reason: `expected ${vars.templatesExpected} templates, got ${parsed.templates.length}` };
  }
  for (const [i, t] of parsed.templates.entries()) {
    if (!t.name || typeof t.name !== "string") return { pass: false, reason: `template[${i}].name missing` };
    if (!Array.isArray(t.exercises) || t.exercises.length === 0) {
      return { pass: false, reason: `template[${i}].exercises empty` };
    }
    if (t.exercises.length !== vars.exerciseCount) {
      return { pass: false, reason: `template[${i}] has ${t.exercises.length} exercises, expected ${vars.exerciseCount}` };
    }
    for (const [j, e] of t.exercises.entries()) {
      if (!e.exerciseName) return { pass: false, reason: `template[${i}].exercises[${j}].exerciseName missing` };
      if (!(e.targetSets >= 1 && e.targetSets <= 10))
        return { pass: false, reason: `targetSets ${e.targetSets} out of 1..10 (template ${i}, ex ${j})` };
      if (!(e.targetReps >= 1 && e.targetReps <= 50))
        return { pass: false, reason: `targetReps ${e.targetReps} out of 1..50 (template ${i}, ex ${j})` };
      if (!(e.restPeriodSec >= 0 && e.restPeriodSec <= 600))
        return { pass: false, reason: `restPeriodSec ${e.restPeriodSec} out of 0..600 (template ${i}, ex ${j})` };
    }
  }

  if (parsed.inlineNewExercises) {
    if (!Array.isArray(parsed.inlineNewExercises)) {
      return { pass: false, reason: "inlineNewExercises is not an array" };
    }
    for (const [k, ex] of parsed.inlineNewExercises.entries()) {
      if (!ex.name) return { pass: false, reason: `inlineNewExercises[${k}].name missing` };
      if (!Array.isArray(ex.primaryMuscles) || ex.primaryMuscles.length === 0)
        return { pass: false, reason: `inlineNewExercises[${k}].primaryMuscles empty` };
      for (const m of ex.primaryMuscles) {
        if (!ALLOWED_MUSCLES.has(m))
          return { pass: false, reason: `inlineNewExercises[${k}] unknown muscle: ${m}` };
      }
      if (!Array.isArray(ex.instructions) || ex.instructions.length === 0)
        return { pass: false, reason: `inlineNewExercises[${k}].instructions empty` };
    }
  }

  return { pass: true };
};

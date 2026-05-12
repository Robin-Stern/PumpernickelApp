// Mirrors WorkoutAiUseCase.validateResponse() in shared/.../WorkoutAiUseCase.kt.
// Run from inside a promptfoo `javascript` assertion. The whole file is
// inlined into the YAML via `file://` reference.
//
// Inputs (provided by promptfoo):
//   - output:  string returned by the LLM
//   - context: { vars: { templatesExpected, exerciseCount, ... } }
// Returns: { pass: boolean, score?: number, reason?: string }

// Strip markdown fences, then extract the LAST balanced {...} object.
// Reasoning models often quote partial JSON examples ({"refusal":...} from
// the prompt) inside their thinking trace; the actual final answer is the
// last `{...}` block. Returns the longest balanced object found.
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

  // Walk from the end: find the matching `{` for the last `}`.
  // Use bracket-counting in reverse, tracking strings.
  const blocks = [];
  let depth = 0;
  let openIdx = -1;
  let inString = false;
  let escape = false;
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (escape) { escape = false; continue; }
    if (ch === "\\" && inString) { escape = true; continue; }
    if (ch === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (ch === "{") {
      if (depth === 0) openIdx = i;
      depth++;
    } else if (ch === "}") {
      depth--;
      if (depth === 0 && openIdx >= 0) {
        blocks.push(t.slice(openIdx, i + 1));
        openIdx = -1;
      }
      if (depth < 0) depth = 0;
    }
  }
  if (blocks.length === 0) return t;
  // Pick the longest block — that's almost always the actual answer
  // (a quoted refusal example is short, the answer with templates/ingredients is long).
  return blocks.sort((a, b) => b.length - a.length)[0];
}

const ALLOWED_MUSCLES = new Set([
  "chest","shoulders","biceps","triceps","forearms","traps","lats","neck",
  "quadriceps","hamstrings","glutes","calves","adductors","abdominals",
  "obliques","lower back"
]);

module.exports = function (output, ctx) {
  const vars = ctx && ctx.vars ? ctx.vars : {};
  const cleaned = extractJsonObject(String(output));
  let parsed;
  try { parsed = JSON.parse(cleaned); }
  catch (e) {
    return {
      pass: false,
      score: 0,
      reason: `JSON parse failed: ${e.message}\n\nFirst 300 chars of raw output:\n${String(output).slice(0, 300)}`
    };
  }

  // Refusal handling — runs before schema validation so refusal cases
  // (vars.expectRefusal === true) don't trip on missing templates/etc.
  if (parsed && typeof parsed.refusal === 'string') {
    if (vars.expectRefusal === true) {
      return { pass: true, score: 1, reason: `refused as expected: ${parsed.refusal.slice(0, 100)}` };
    }
    return { pass: false, score: 0, reason: `model refused unexpectedly: ${parsed.refusal.slice(0, 100)}` };
  }

  // expectRefusal was true but no refusal field present → model failed to refuse
  if (vars.expectRefusal === true) {
    return { pass: false, score: 0, reason: `expected refusal but got workout response` };
  }

  if (!Array.isArray(parsed.templates)) {
    return { pass: false, score: 0, reason: "templates is not an array (and no refusal field)" };
  }
  if (parsed.templates.length !== vars.templatesExpected) {
    return { pass: false, score: 0, reason: `expected ${vars.templatesExpected} templates, got ${parsed.templates.length}` };
  }
  for (const [i, t] of parsed.templates.entries()) {
    if (!t.name || typeof t.name !== "string") return { pass: false, score: 0, reason: `template[${i}].name missing` };
    if (!Array.isArray(t.exercises) || t.exercises.length === 0) {
      return { pass: false, score: 0, reason: `template[${i}].exercises empty` };
    }
    if (t.exercises.length !== vars.exerciseCount) {
      return { pass: false, score: 0, reason: `template[${i}] has ${t.exercises.length} exercises, expected ${vars.exerciseCount}` };
    }
    for (const [j, e] of t.exercises.entries()) {
      if (!e.exerciseName) return { pass: false, score: 0, reason: `template[${i}].exercises[${j}].exerciseName missing` };
      if (!(e.targetSets >= 1 && e.targetSets <= 10))
        return { pass: false, score: 0, reason: `targetSets ${e.targetSets} out of 1..10 (template ${i}, ex ${j})` };
      if (!(e.targetReps >= 1 && e.targetReps <= 50))
        return { pass: false, score: 0, reason: `targetReps ${e.targetReps} out of 1..50 (template ${i}, ex ${j})` };
      if (!(e.restPeriodSec >= 0 && e.restPeriodSec <= 600))
        return { pass: false, score: 0, reason: `restPeriodSec ${e.restPeriodSec} out of 0..600 (template ${i}, ex ${j})` };
    }
  }

  if (parsed.inlineNewExercises) {
    if (!Array.isArray(parsed.inlineNewExercises)) {
      return { pass: false, score: 0, reason: "inlineNewExercises is not an array" };
    }
    for (const [k, ex] of parsed.inlineNewExercises.entries()) {
      if (!ex.name) return { pass: false, score: 0, reason: `inlineNewExercises[${k}].name missing` };
      if (!Array.isArray(ex.primaryMuscles) || ex.primaryMuscles.length === 0)
        return { pass: false, score: 0, reason: `inlineNewExercises[${k}].primaryMuscles empty` };
      for (const m of ex.primaryMuscles) {
        if (!ALLOWED_MUSCLES.has(m))
          return { pass: false, score: 0, reason: `inlineNewExercises[${k}] unknown muscle: ${m}` };
      }
      if (!Array.isArray(ex.instructions) || ex.instructions.length === 0)
        return { pass: false, score: 0, reason: `inlineNewExercises[${k}].instructions empty` };
    }
  }

  return { pass: true, score: 1, reason: "all checks passed" };
};

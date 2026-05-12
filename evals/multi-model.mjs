#!/usr/bin/env node
// Multi-model consistency sweep.
// Runs each (model × suite × rep) via promptfoo, captures per-test pass/reason,
// writes raw per-run JSON + an aggregated summary (default: runs/aggregate).
//
// Required env: TOGETHER_API_KEY
//
// Usage (run from evals/):
//   node multi-model.mjs                                 # full sweep: 5 models × 2 suites × 3 reps
//   node multi-model.mjs --smoke                         # 1 model × 1 suite × 1 rep
//   node multi-model.mjs --suite workout                 # workout suite only
//   node multi-model.mjs --models gpt-oss-20b,maverick   # comma-list substring match
//   node multi-model.mjs --reps 1                        # one rep per (model, suite)
//   node multi-model.mjs --output /tmp/sweep.json        # custom aggregate path
// Notes:
//   - --smoke overrides --suite/--models/--reps (compat with prior behavior)
//   - --output only affects the aggregate file; per-run JSON paths are unchanged

import { spawnSync } from 'node:child_process';
import { writeFileSync, readFileSync, mkdirSync, existsSync, unlinkSync } from 'node:fs';
import { dirname } from 'node:path';

function parseArgs(argv) {
  const out = { smoke: false, suite: undefined, output: undefined, models: undefined, reps: undefined, unknownFlags: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--smoke')       { out.smoke = true; }
    else if (a === '--suite')  { out.suite  = argv[++i]; }
    else if (a === '--output') { out.output = argv[++i]; }
    else if (a === '--models') { out.models = (argv[++i] ?? '').split(',').map((s) => s.trim()).filter(Boolean); }
    else if (a === '--reps') {
      const n = Number.parseInt(argv[++i], 10);
      if (Number.isFinite(n) && n > 0) out.reps = n;
      else console.error(`[warn] --reps requires a positive integer; falling back to default`);
    }
    else { out.unknownFlags.push(a); }
  }
  return out;
}

const MODELS = [
  'openai/gpt-oss-20b',
  'google/gemma-4-31B-it',
  'openai/gpt-oss-120b',
  'meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8',
  'Qwen/Qwen3-235B-A22B-Instruct-2507-tput',
];

const SUITES = [
  {
    name: 'workout',
    prompt: './prompts/workout-system.json',
    validator: './cases/workout-validator.cjs',
    tests: './cases/workout.json',
  },
  {
    name: 'recipe',
    prompt: './prompts/recipe-system.json',
    validator: './cases/recipe-validator.cjs',
    tests: './cases/recipe.json',
  },
];

const REPS = 3;
const RUN_TIMEOUT_MS = 10 * 60 * 1000;

const args = parseArgs(process.argv.slice(2));
for (const f of args.unknownFlags) console.error(`[warn] unknown flag: ${f}`);

const SMOKE = args.smoke;
const OUTPUT_PATH = args.output ?? 'runs/aggregate.json';

function filterModels(needles) {
  for (const n of needles) {
    if (!MODELS.some((m) => m.toLowerCase().includes(n.toLowerCase()))) {
      console.error(`[warn] --models entry matched zero models: ${n}`);
    }
  }
  return MODELS.filter((m) => needles.some((n) => m.toLowerCase().includes(n.toLowerCase())));
}

const ACTIVE_MODELS = SMOKE ? MODELS.slice(0, 1) : (args.models ? filterModels(args.models) : MODELS);
const ACTIVE_SUITES = SMOKE ? SUITES.slice(0, 1)
  : args.suite === 'workout' ? SUITES.filter((s) => s.name === 'workout')
  : args.suite === 'recipe'  ? SUITES.filter((s) => s.name === 'recipe')
  : SUITES; // 'both' or undefined
const ACTIVE_REPS = SMOKE ? 1 : (args.reps ?? REPS);

if (ACTIVE_MODELS.length === 0) { console.error('[error] --models filter matched no models'); process.exit(1); }
if (ACTIVE_SUITES.length === 0) { console.error(`[error] --suite filter matched no suites (got: ${args.suite})`); process.exit(1); }

mkdirSync('runs', { recursive: true });
mkdirSync(dirname(OUTPUT_PATH), { recursive: true });

const safeId = (m) => m.replace(/[\/.]/g, '_');

function buildYaml(suite, modelId) {
  return `description: ${suite.name} ${modelId}
prompts:
  - file://${suite.prompt}
providers:
  - id: togetherai:${modelId}
    label: ${modelId}
    config:
      temperature: 0.7
      max_tokens: 4096
      response_format: { type: 'json_object' }
defaultTest:
  assert:
    - type: javascript
      value: file://${suite.validator}
tests: file://${suite.tests}
`;
}

function runOne(suite, modelId, rep, brokenModels) {
  if (brokenModels.has(modelId)) {
    return { skipped: true, reason: 'model marked broken in earlier rep' };
  }
  const cfgPath = `_sweep_${suite.name}_${safeId(modelId)}.yaml`;
  const outPath = `runs/${suite.name}-${safeId(modelId)}-r${rep}.json`;
  writeFileSync(cfgPath, buildYaml(suite, modelId));

  const startedAt = Date.now();
  const proc = spawnSync(
    './node_modules/.bin/promptfoo',
    ['eval', '-c', cfgPath, '--no-cache', '-o', outPath],
    {
      stdio: ['ignore', 'pipe', 'pipe'],
      env: process.env,
      timeout: RUN_TIMEOUT_MS,
      encoding: 'utf-8',
    },
  );
  const durationMs = Date.now() - startedAt;
  try { unlinkSync(cfgPath); } catch {}

  // promptfoo exits non-zero when assertions fail — that's expected.
  // Only treat as error if no output file was written.
  if (!existsSync(outPath)) {
    return {
      skipped: false, error: true,
      stderr: (proc.stderr || '').slice(-1000), stdout: (proc.stdout || '').slice(-500),
      exitCode: proc.status, signal: proc.signal, durationMs,
    };
  }
  const raw = JSON.parse(readFileSync(outPath, 'utf-8'));
  const tests = (raw.results?.results || []).map((r) => ({
    description: r.testCase?.description || `test#${r.testIdx}`,
    success: !!r.success,
    score: r.score,
    reason: (r.gradingResult?.componentResults || [])
      .map((c) => c.reason)
      .filter(Boolean)
      .join(' | '),
    failureReason: r.failureReason,
    promptTokens: r.response?.tokenUsage?.prompt,
    completionTokens: r.response?.tokenUsage?.completion,
  }));
  const passed = tests.filter((t) => t.success).length;
  const total = tests.length;
  const totalTokens = raw.results?.stats?.tokenUsage?.total ?? 0;
  return { skipped: false, error: false, passed, total, tests, totalTokens, durationMs, evalId: raw.evalId };
}

function meanAndStdev(values) {
  if (values.length === 0) return { mean: 0, stdev: 0 };
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const variance =
    values.reduce((a, b) => a + (b - mean) ** 2, 0) / values.length;
  return { mean, stdev: Math.sqrt(variance) };
}

const log = (...args) =>
  console.log(`[${new Date().toISOString()}]`, ...args);

const aggregate = { runs: [], summary: [], generatedAt: new Date().toISOString() };
const brokenModels = new Set();

for (const modelId of ACTIVE_MODELS) {
  for (const suite of ACTIVE_SUITES) {
    const runs = [];
    for (let rep = 1; rep <= ACTIVE_REPS; rep++) {
      log(`>>> ${modelId} | ${suite.name} | rep ${rep}/${ACTIVE_REPS}`);
      const result = runOne(suite, modelId, rep, brokenModels);
      runs.push({ rep, ...result });
      aggregate.runs.push({ modelId, suite: suite.name, rep, ...result });
      if (result.skipped) {
        log(`    skipped (${result.reason})`);
      } else if (result.error) {
        log(`    ERROR after ${(result.durationMs/1000).toFixed(1)}s: ${result.stderr.slice(0, 200)}`);
      } else {
        log(`    ${result.passed}/${result.total} passed in ${(result.durationMs/1000).toFixed(1)}s, ${result.totalTokens} tokens`);
      }
      if (rep === 1 && !result.skipped && !result.error && result.passed === 0) {
        log(`    !!! 0/${result.total} on rep 1 — marking ${modelId} as broken, skipping remaining reps`);
        brokenModels.add(modelId);
      }
      writeFileSync(OUTPUT_PATH, JSON.stringify(aggregate, null, 2));
    }
    const passed = runs.filter((r) => !r.skipped && !r.error).map((r) => r.passed);
    const total = runs.find((r) => r.total)?.total || 0;
    const { mean, stdev } = meanAndStdev(passed);
    const perTest = {};
    for (const r of runs) {
      if (r.skipped || r.error || !r.tests) continue;
      for (const t of r.tests) {
        if (!perTest[t.description]) perTest[t.description] = { pass: 0, fail: 0, reasons: [] };
        if (t.success) perTest[t.description].pass++;
        else {
          perTest[t.description].fail++;
          perTest[t.description].reasons.push(`r${r.rep}: ${t.reason}`);
        }
      }
    }
    aggregate.summary.push({
      modelId,
      suite: suite.name,
      reps: runs.length,
      runs: runs.map((r) => ({ rep: r.rep, passed: r.passed, total: r.total, error: r.error, skipped: r.skipped })),
      mean,
      stdev,
      consistencyScore: mean - stdev,
      perTest,
      total,
    });
    log(`=== ${modelId} | ${suite.name}: mean ${mean.toFixed(2)}/${total}, stdev ${stdev.toFixed(2)}, score ${(mean - stdev).toFixed(2)}`);
    writeFileSync(OUTPUT_PATH, JSON.stringify(aggregate, null, 2));
  }
}

aggregate.totalTokens = aggregate.runs.reduce(
  (a, r) => a + (r.totalTokens || 0),
  0,
);
writeFileSync(OUTPUT_PATH, JSON.stringify(aggregate, null, 2));

log('=== DONE ===');
log(`Total tokens: ${aggregate.totalTokens}`);
log('Summary:');
for (const s of aggregate.summary) {
  log(`  ${s.modelId.padEnd(60)} ${s.suite.padEnd(7)} mean ${s.mean.toFixed(2)} stdev ${s.stdev.toFixed(2)} score ${s.consistencyScore.toFixed(2)}`);
}

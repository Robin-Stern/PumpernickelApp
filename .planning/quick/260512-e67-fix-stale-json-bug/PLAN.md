---
quick_id: 260512-e67
slug: fix-stale-json-bug
title: Fix stale per-run JSON bug in multi-model.mjs
status: in-progress
---

# Fix stale per-run JSON bug in multi-model.mjs

## Goal

In `evals/multi-model.mjs` → `runOne()`: add `try { unlinkSync(outPath); } catch {}`
BEFORE the `spawnSync` call so stale JSONs from previous sweeps don't contaminate
aggregate results when promptfoo fails (e.g. API 503/502).

## Context

- `unlinkSync` is already imported on line 20
- Insertion point: after `writeFileSync(cfgPath, buildYaml(...))` (line 122), before `const startedAt` (line 124)
- Root cause: if promptfoo exits without writing `outPath`, the helper reads whatever
  leftover file is there → injects data from a different sweep/track into the aggregate

## Tasks

- [ ] T1: Edit `evals/multi-model.mjs` — insert `try { unlinkSync(outPath); } catch {}`
       after line 122, before line 124
- [ ] T2: Commit with message `fix(evals): delete stale per-run JSON before promptfoo eval`

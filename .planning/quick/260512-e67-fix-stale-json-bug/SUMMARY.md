---
quick_id: 260512-e67
slug: fix-stale-json-bug
status: complete
---

# Summary

Inserted `try { unlinkSync(outPath); } catch {}` in `runOne()` of
`evals/multi-model.mjs` directly before the `spawnSync` call (after
writing the temp YAML config). This mirrors the existing cleanup pattern
for `cfgPath` and prevents stale per-rep JSON files from contaminating
aggregate results when promptfoo exits without producing output.

**Commit:** `31edfcd`

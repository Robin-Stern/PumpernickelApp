---
quick_id: 260512-e6s
slug: add-llama33-to-models
status: complete
---

# Summary

Appended `'meta-llama/Llama-3.3-70B-Instruct-Turbo'` to the MODELS array in
`evals/multi-model.mjs` as the 6th entry after Qwen3-235B. Maverick remains
in position 4 — the broken-model guard (0/total on rep 1 → skip remaining)
handles Together 503 transparently without any code change needed.

**Commit:** `ecfc90a`

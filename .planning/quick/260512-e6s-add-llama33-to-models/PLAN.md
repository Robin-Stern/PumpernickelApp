---
quick_id: 260512-e6s
slug: add-llama33-to-models
title: Add Llama-3.3-70B-Instruct-Turbo to MODELS array
status: in-progress
---

# Add Llama-3.3-70B to MODELS array in multi-model.mjs

## Goal

Add `meta-llama/Llama-3.3-70B-Instruct-Turbo` as 6th entry in the MODELS array
in `evals/multi-model.mjs`. Maverick stays (broken-model guard handles 503s).

## Rationale

Llama-3.3 was already tested separately in B.2 + B.4 via ad-hoc CLI runs.
Adding it to the MODELS array makes future sweeps include it automatically
without needing separate commands.

## Tasks

- [ ] T1: Edit `evals/multi-model.mjs` — append `'meta-llama/Llama-3.3-70B-Instruct-Turbo'`
       to MODELS array after Qwen3-235B entry
- [ ] T2: Commit

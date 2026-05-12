---
quick_id: 260512-ehp
slug: ios-picker-qwen3-suggestion
title: A.6 — Qwen3-235B als zweite Together-Empfehlung im iOS Picker
status: in-progress
---

# A.6 — iOS Picker: Qwen3-235B als zweite Together-Empfehlung

## Goal

`iosApp/iosApp/Views/AI/AISettingsView.swift` → `modelSuggestions["together"]`:
Qwen3-235B-A22B-Instruct-2507-tput als neue Position 2 einfügen (nach Gemma).

## Eval-Begründung (B.4 + B.4b, Commit 2109f15 + fa84e1a)

- Qwen3-235B ist das einzige Modell mit 6/6 Workout (σ 0) inklusive 3/3 beim
  schwierigen Injury-Refusal-Case
- Gemma: 5.33/6 Workout (injury refusal 1/3), 6/6 Recipe (σ 0) — stärkste Gesamtleistung
- Note muss ehrlich sein, kein Marketing

## Tasks

- [ ] T1: Edit `iosApp/iosApp/Views/AI/AISettingsView.swift` — neue Zeile nach Gemma:
       `ModelSuggestion(name: "Qwen/Qwen3-235B-A22B-Instruct-2507-tput", note: "Stärkstes Refusal · 6/6 Workout in Eval")`
- [ ] T2: Commit

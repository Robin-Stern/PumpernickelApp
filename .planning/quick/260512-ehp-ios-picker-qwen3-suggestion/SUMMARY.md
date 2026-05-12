---
quick_id: 260512-ehp
slug: ios-picker-qwen3-suggestion
status: complete
---

# Summary

Eingefügt in `iosApp/iosApp/Views/AI/AISettingsView.swift` →
`modelSuggestions["together"]` als Position 2 (nach Gemma):

```swift
ModelSuggestion(name: "Qwen/Qwen3-235B-A22B-Instruct-2507-tput",
                note: "Stärkstes Refusal · 6/6 Workout in Eval")
```

Eval-Grundlage: B.4 (Commit 2109f15) + B.4b-Korrektur (fa84e1a).
Note ist ehrlich — kein Marketing, kein übertreibendes Superlativ.

`No such module 'Shared'` in Xcode-Diagnose ist umgebungsbedingt (KMP nicht gebuildet),
nicht durch diesen Edit verursacht.

**Commit:** `3d4f8a6`

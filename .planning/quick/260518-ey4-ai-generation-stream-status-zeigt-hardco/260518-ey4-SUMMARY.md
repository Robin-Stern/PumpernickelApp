---
quick_id: 260518-ey4
title: AI Generation Stream-Status zeigt hardcoded 5 Übungen
status: complete
date: 2026-05-18
commit: 59e5a67
---

# Quick Task 260518-ey4 — Summary

## Problem

Beim AI-Workout-Generation-Streaming zeigte die UI-Headline ("KI denkt nach… N Übungen") immer `5`, unabhängig vom User-Input. Wenn der User z.B. 2 Übungen anforderte, stand trotzdem "5" während des Streams; das Modell lieferte am Ende dann korrekt 2 Übungen → reiner Display-Bug.

## Root Cause

`shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt:113` hardcoded:
```kotlin
_uiState.value = WorkoutAiUiState.Generating(skeletonRowCount = 5)
```

`AiGenerationState.Generating(type, originatingData)` trägt bereits das `WorkoutAiForm` mit User-Input (`exerciseCount`) — der Wert wurde aber ignoriert.

## Fix

Cast `originatingData` als `WorkoutAiForm` und nimm `exerciseCount`. Fallback-Kette:
1. `form.exerciseCount` (aktueller Generate-Run)
2. Aktueller `Form`-State (Edge-Case)
3. 5 als last-resort default

## Files Modified
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt` (+3/-1)

## Cross-Platform
Single-file shared change — iOS (`AIWorkoutGenView.swift`) + Android (`AiWorkoutGenScreen.kt`) konsumieren beide `state.skeletonRowCount` und sind damit automatisch korrigiert.

## Verification
- `./gradlew :shared:compileAndroidMain` → BUILD SUCCESSFUL

## Manual UAT pending
- Generate AI Workout mit Setting "2 Übungen" → Status zeigt "2 Übungen" (nicht 5)
- Selbiger Test mit 7 Übungen → "7 Übungen"

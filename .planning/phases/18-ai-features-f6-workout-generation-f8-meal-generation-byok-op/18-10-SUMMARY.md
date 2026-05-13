---
phase: 18-ai-features-f6-workout-generation-f8-meal-generation-byok-op
plan: "10"
subsystem: ios-handoff
tags: [ios, swiftui, handoff, ai, byok, koinhelper, f6, f8]
dependency_graph:
  requires:
    - 18-05 (AiSettingsViewModel + AiSettingsKoinHelper)
    - 18-06 (WorkoutAiViewModel + WorkoutAiKoinHelper — Wave 4)
    - 18-07 (Android workout AI screen — reference for German copy)
    - 18-08 (RecipeAiViewModel + RecipeAiKoinHelper — Wave 6)
    - 18-09 (Android meal AI screen — reference for German copy)
  provides:
    - 18-IOS-HANDOFF.md — complete SwiftUI spec for iOS AI surfaces
  affects:
    - User implements AiSettingsView, AiWorkoutGenView, AiMealGenView, AiPreviewSheet
    - User modifies SettingsView.swift (KI section) and MainTabView.swift (sparkles toolbar items)
tech_stack:
  added: []
  patterns:
    - KoinHelper class-style acquisition (ClassName().method()) per Phase 15.1 / 17 convention
    - asyncSequence(for: viewModel.xxxFlow) per KMPNativeCoroutines project convention
    - AiPreviewContent Swift enum bridges WorkoutAiPreview and RecipeAiPreview into one sheet
    - sealed UiState flat-export naming (WorkoutAiUiStateForm, etc.) per Phase 15 STATE.md decision
key_files:
  created:
    - .planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md
  modified: []
decisions:
  - "AiPreviewSheet uses a Swift enum (AiPreviewContent) rather than two separate sheet views — one sheet handles both F6 and F8 preview branches"
  - "German UI strings extracted directly from AiSettingsScreen.kt Android source and CONTEXT.md D-18-08 error copy table"
  - "Sealed UiState Swift type names documented as flat-export convention (WorkoutAiUiStateForm, not WorkoutAiUiState.Form) per Phase 15 precedent"
  - "Security guardrail section explicitly mirrors the 5 Android T-18-05-xx mitigations in SwiftUI terms"
metrics:
  duration: "~15 min"
  completed: "2026-05-08"
  tasks: 1
  files_created: 1
  files_modified: 0
---

# Phase 18 Plan 10: iOS Handoff — Summary

**One-liner:** iOS SwiftUI implementation spec covering 4 new views (AiSettingsView, AiWorkoutGenView, AiMealGenView, AiPreviewSheet) and 2 modifications (SettingsView + MainTabView), with full Kotlin API contracts for all 3 KoinHelpers, sealed UiState branches, action methods, German error copy per AiError class, and pbxproj bundle-resource concerns.

## Files Created

- `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` — 581-line iOS SwiftUI spec

## Handoff Doc Coverage Summary

| Section | Content |
|---------|---------|
| What you are building | 4 new + 2 modified surfaces table |
| pbxproj concerns | Prompt .md bundle inclusion, flat naming, grep verification commands, free_exercise_db.json precedent |
| No new Info.plist permissions | HTTPS-only, no camera/biometric/photo |
| AiSettingsView contract | 4 StateFlows + 5 actions + provider preset defaults table + Form layout snippet |
| AiWorkoutGenView contract | 1 StateFlow, 6 sealed branches, 8 action methods, WorkoutAiSplit enum table, per-state UI structure |
| AiMealGenView contract | 1 StateFlow, 8 sealed branches, 6 action methods, RemainingMacros fields, per-state UI structure |
| AiPreviewSheet contract | AiPreviewContent Swift enum, WorkoutAiPreview fields, RecipeAiPreview fields, MacrosFitIndicator, sheet layout snippet |
| Per-class error copy | 5-row table (Timeout / Network / AuthOrQuota / Provider / SchemaInvalid) with German copy + action |
| SettingsView modification | Section("KI") snippet with NavigationLink |
| MainTabView modifications | Both tab sparkles toolbar snippet |
| Security guardrails | 5 explicit rules mirroring Android T-18-05-xx |
| Already shipped | 6 items not to re-implement |
| Reference table | Android screen → Kotlin VM per surface |
| Acceptance checklist | 15 UAT items |

## Deviations from Plan

None — plan executed exactly as written. The plan's Task 1 action block contained a nearly-complete template of the handoff doc; the executor verified all Kotlin API contracts against the actual shipped VM files (Plans 02-05) and planned VM specs (Plans 06-08 PLAN.md files, not yet executed), then authored the final doc matching the 17-IOS-HANDOFF.md structure.

## Self-Check: PASSED

- `.planning/phases/18-ai-features-f6-workout-generation-f8-meal-generation-byok-op/18-IOS-HANDOFF.md` — FOUND (581 lines)
- Commit d42b9d2 (docs(18-10): create 18-IOS-HANDOFF.md) — FOUND on branch worktree-agent-a2c15e924e1b108b2

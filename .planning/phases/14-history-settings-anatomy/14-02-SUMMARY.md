---
plan: 14-02
phase: 14-history-settings-anatomy
status: complete
started: 2026-04-01
completed: 2026-04-01
---

# Plan 14-02: Anatomy Picker — Summary

## What Was Built

Extracted all 73 muscle region SVG path entries (40 front + 33 back) plus body outline paths from iOS `MuscleRegionPaths.swift` into shared KMP module `MuscleRegionPaths.kt`. Built `AnatomyPickerSheet` composable with Compose Canvas-drawn front/back body maps, touch-based region selection, and wired into both exercise screens.

## Key Files

### Created
- `shared/src/commonMain/kotlin/com/pumpernickel/domain/model/MuscleRegionPaths.kt` (308 lines) — shared path data
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnatomyPickerSheet.kt` (196 lines) — Canvas anatomy picker

### Modified
- `androidApp/.../ExerciseCatalogScreen.kt` — anatomy picker button + sheet
- `androidApp/.../ExercisePickerScreen.kt` — anatomy picker button + sheet

## Commits
- `c69ae6c`: feat(14-02): extract muscle region paths to shared KMP and create AnatomyPickerSheet
- `b4a351c`: feat(14-02): wire AnatomyPickerSheet into ExerciseCatalogScreen and ExercisePickerScreen

## Deviations
- Path data converted programmatically (sed) instead of manual copying due to 45K token SVG data size
- Executed inline by orchestrator instead of subagent due to output token limit

## Self-Check: PASSED
- MuscleRegionPaths.kt has 73 region entries (40 front + 33 back) + outline paths
- AnatomyPickerSheet uses Canvas, PathParser, detectTapGestures, ModalBottomSheet
- Both ExerciseCatalogScreen and ExercisePickerScreen have anatomy picker wired
- Build compiles successfully

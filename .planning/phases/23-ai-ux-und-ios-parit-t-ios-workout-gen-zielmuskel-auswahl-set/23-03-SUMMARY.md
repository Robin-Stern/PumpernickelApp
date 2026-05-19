---
phase: 23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set
plan: "03"
subsystem: documentation
tags:
  - ios-handoff
  - swiftui
  - documentation
  - ai-ux
dependency_graph:
  requires:
    - "23-01 (shared KMP — WorkoutAiUiState.Form.setsPerExercise, WorkoutAiViewModel.onSetsPerExerciseChanged)"
    - "23-02 (Android-UI — Referenz für SwiftUI-Spiegelung)"
  provides:
    - "23-IOS-HANDOFF.md — vollständige SwiftUI-Spec für User-Implementation"
  affects:
    - "iosApp/iosApp/Views/AI/AIWorkoutGenView.swift (User-Änderung nach Handoff)"
    - "iosApp/iosApp/Views/MainTabView.swift (User-Änderung nach Handoff)"
tech_stack:
  added: []
  patterns:
    - "asyncSequence(for:) KMPNativeCoroutinesAsync Observation-Pattern"
    - "safeAreaInset(edge: .bottom) für persistente Mini-Bar über Tab-Bar"
    - "SwiftUI Stepper mit KMP-Binding via Int32-Bridging"
key_files:
  created:
    - ".planning/phases/23-ai-ux-und-ios-parit-t-ios-workout-gen-zielmuskel-auswahl-set/23-IOS-HANDOFF.md"
  modified: []
decisions:
  - "D-23-12: User implementiert iOS selbst — Agent liefert vollständige Spec (23-IOS-HANDOFF.md)"
  - "safeAreaInset(edge: .bottom) empfohlen statt VStack-Wrapper — idiomatischer iOS 15+ Weg"
  - "AiGenerationKoinHelper als neue iosMain-Datei dokumentiert (fehlt noch im Codebase)"
  - "Tab-Switch selectedTab zuerst, dann Navigation — Pitfall aus MainTabView-Analyse"
metrics:
  duration: "15 min"
  completed: "2026-05-19"
  tasks_completed: 1
  tasks_total: 1
  files_created: 1
  files_modified: 0
---

# Phase 23 Plan 03: iOS Handoff Spec — Summary

iOS Handoff-Dokument erstellt: SwiftUI-Stepper für `setsPerExercise` (Range 1–6) in `AIWorkoutGenView` + `AiGenerationMiniBarView` über der Tab-Bar in `MainTabView`, mit asyncSequence-Observer-Pattern, Pitfalls und Acceptance Criteria.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create 23-IOS-HANDOFF.md with Sets-Stepper + Mini-Bar SwiftUI spec | 661eeed | `.planning/phases/23-.../23-IOS-HANDOFF.md` |

## What Was Built

`23-IOS-HANDOFF.md` (498 Zeilen) enthält:

1. **Sets-Stepper Spec** — Konkreter SwiftUI-Snippet basierend auf dem bestehenden `exerciseCount`-Stepper-Pattern in `AIWorkoutGenView.swift` (Zeilen 169–179). Binding gegen `viewModel.onSetsPerExerciseChanged(count: Int32($0))`, Range `1...6`, Position zwischen "Anzahl Übungen" und "Aufteilung".

2. **AiGenerationManager-Zugang** — Dokumentation des fehlenden `AiGenerationKoinHelper.kt` (Pattern identisch zu `WorkoutAiKoinHelper.kt`), das der User anlegen muss.

3. **Mini-Bar Integration** — `safeAreaInset(edge: .bottom)` auf `TabView` in `MainTabView.swift`. Vollständiger `AiGenerationMiniBarView`-Snippet inkl. `TypingIndicatorDots`, `visible`-Computed-Property, Sealed-Class-Casting-Pattern.

4. **asyncSequence-Observer** — Pattern aus `PumpernickelApp.swift` und `AIWorkoutGenView.swift` übernommen; kein neues ObservableObject nötig, `@State + .task` reicht.

5. **Tab-Navigation** — Drei Optionen für Mini-Bar-Tap dokumentiert (Tab-Wechsel only, programmatischer Push, NotificationCenter). Option 2 (nur Tab-Wechsel) als empfohlener initialer Ansatz.

6. **Pitfalls** — 5 Pitfalls dokumentiert: state.idle/error Bar-Sichtbarkeit, Tab-Switch-Reihenfolge, KMP-Sealed-Class-Casting, safeAreaInset-Semantik, fehlender AiGenerationKoinHelper.

## Deviations from Plan

### Auto-ergänzte Erkenntnisse

**1. [Rule 2 - Missing Info] AiGenerationKoinHelper fehlt in iosMain**
- **Found during:** Task 1 — Suche nach `AiGenerationManager`-Zugang in iOS
- **Issue:** Kein `AiGenerationKoinHelper.kt` in `shared/src/iosMain/kotlin/com/pumpernickel/di/` vorhanden (nur `WorkoutAiKoinHelper`, `RecipeAiKoinHelper`, etc.)
- **Fix:** Kotlin-Snippet für die fehlende Datei im Handoff-Dokument dokumentiert (Section 2, "Shared Hook")
- **Kein Code geändert:** Reine Dokumentation — User oder Plan 23-01-Folge-Task muss Datei anlegen

**2. [Adaptation] safeAreaInset statt VStack**
- **Found during:** Task 1 — Analyse von `MainTabView.swift`
- **Anpassung:** Plan-Spec hatte `VStack { miniBar; TabView }` als Ansatz erwähnt. Codebase-Analyse zeigte: `MainTabView` nutzt `TabView` direkt ohne äußeren `VStack`. `safeAreaInset(edge: .bottom)` ist der idiomatischere iOS 15+-Weg und vermeidet Tab-Bar-Layout-Probleme.

**3. [Adaptation] Tab-Navigation via ToolbarItem statt NavController**
- **Found during:** Task 1 — Lektüre von `MainTabView.swift`
- **Anpassung:** Android hat `navController.navigate(AiWorkoutGenRoute)`. iOS `MainTabView` nutzt keinen eigenen NavController für die AI-Screens — diese sind als `ToolbarItem`-Links in den Tab-Root-Views eingebettet. Drei Optionen im Handoff dokumentiert.

## Known Stubs

Keine — das Handoff-Dokument ist vollständig. User muss `AiGenerationKoinHelper.kt` anlegen (dokumentiert im Handoff).

## Threat Flags

Keine neuen Security-relevanten Surfaces — Plan 03 erstellt ausschließlich ein `.planning/`-Dokumentations-File ohne Code-Änderungen. Threat-Modell aus Plan-Frontmatter gilt (T-23-09 / T-23-10: accept).

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| `.planning/.../23-IOS-HANDOFF.md` existiert | FOUND |
| Commit `661eeed` existiert | FOUND |

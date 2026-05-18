---
slug: ios-undertrained-dialog
status: complete
completed: 2026-05-12
---

# iOS Parity: 3 Features implementiert

## Was gemacht wurde

### 1. Undertrained Muscles Alert (WorkoutSessionView)
- `showUndertrainedDialog` + `undertrainedMuscles` State-Variablen hinzugefügt
- `observeUndertrainedMuscles()` Observer auf `undertrainedMusclesFlow`
- `.alert("Vernachlässigte Muskeln")` mit Muskelliste als Message
- Commit: `de8edc7`

### 2. RankLadder Screen + Navigation (OverviewRankStrip / OverviewView)
- Neue `RankLadderView.swift` in `Views/Gamification/` (alle 10 Ränge, PASSED/CURRENT/LOCKED)
- `OverviewRankStrip` mit Chevron-Indikator versehen
- `OverviewView`: Strip in `NavigationLink(destination: RankLadderView())` eingebettet
- Commit: `4025ad8`

### 3. Tutorial Overlay + Settings-Button
- Neue `TutorialOverlayView.swift` in `Views/Onboarding/` (9 Seiten, TabView .page)
- `PumpernickelApp.swift`: `hasSeenTutorialFlow` beobachten, `.fullScreenCover` beim ersten Start
- `SettingsView.swift`: "Tutorial erneut anzeigen" Button in neuer "Hilfe"-Section
- Commit: `0004059`

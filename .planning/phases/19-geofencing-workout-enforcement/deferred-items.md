# Deferred Items — Phase 19 Plan 03

## Pre-existing xcodebuild Errors (Out-of-scope for Plan 03)

These errors existed in the worktree base commit (1f8e165) BEFORE Plan 03 execution.
Confirmed by: `git stash` + `xcodebuild` showing same errors on original HEAD.

1. `iosApp/iosApp/Views/Overview/OverviewView.swift:23` — `cannot find 'RankLadderView' in scope`
2. `iosApp/iosApp/PumpernickelApp.swift` — `cannot find 'TutorialOverlayView' in scope`

These will be fixed by a future Wave (likely Wave 4 or when the gamification views are merged).
The xcodebuild acceptance criterion in Plan 03 Task 4 cannot pass until these are resolved.

All other Task 4 acceptance criteria pass (plist, AppDelegate, PumpernickelApp.swift).

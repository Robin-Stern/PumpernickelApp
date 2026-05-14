---
status: partial
phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
source:
  - 15-01-SUMMARY.md
  - 15-02-SUMMARY.md
  - 15-03-SUMMARY.md
  - 15-04-SUMMARY.md
  - 15-05-SUMMARY.md
  - 15-06-SUMMARY.md
  - 15-07-SUMMARY.md
  - 15-08-SUMMARY.md
  - 15-09-SUMMARY.md
started: 2026-04-22T18:04:41Z
updated: 2026-04-22T18:12:00Z
---

## Current Test

[testing stopped — single root cause blocks all iOS-observable tests; routed to gap planning]

## Tests

### 1. Cold Start Smoke Test
expected: |
  Kill the app completely. Launch from a cold state. The app boots without
  crash. Room migration v7 → v8 completes (existing workouts + nutrition data
  still intact). `GamificationStartup.run()` fires on first launch after
  upgrade (Android: Dispatchers.IO / iOS: GlobalScope). Overview tab opens
  and renders the rank strip (either Unranked literal or a Ranked row if
  retroactive replay already awarded XP from history).
result: issue
reported: "fresh build on my iphone, deleted all data beforehand frsh everything, no xp rank intro. i cant even find it anywhere in the ui"
severity: major
root_cause: |
  Plans 08 and 09 declared iOS Swift views as user-implemented per
  `<ios_integration_contract>`. The shared Kotlin VM + Koin factories
  (`GamificationUiKoinHelper`, `AchievementGalleryKoinHelper`) ship, but
  nothing in `iosApp/iosApp/` references them. No `Views/Gamification/`
  directory. `OverviewView.swift` has no rank-strip child.
  `SettingsView.swift` has no Gamification section / Achievements
  NavigationLink. `MainTabView.swift` has no `.fullScreenCover` for the
  unlock modal. Verified via:
    grep -rn "GamificationUiKoinHelper\|AchievementGalleryKoinHelper\|\
    rankStateFlow\|unlockEventsFlow\|getGamificationViewModel\|\
    getAchievementGalleryViewModel\|OverviewRankStrip\|UnlockModal\|\
    AchievementGallery" iosApp/  →  0 results.

### 2. Overview Rank Strip — Unranked State
expected: |
  On a truly fresh install (no prior workouts), open the Overview tab. At the
  top of the scrollable content — above the Muscle Activity card — a rank
  strip appears showing a lock icon and the literal text
  "Unranked — complete a workout to unlock Silver" (D-11 copy).
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Observed during Test 1 — iOS OverviewRankStrip.swift per plan 08 integration contract never written. No way to observe Unranked literal on iOS."

### 3. Overview Rank Strip — Ranked State After Workout
expected: |
  Complete and save a workout from a template. Return to the Overview tab.
  The rank strip now shows: a military/tier icon, the current rank display
  name (e.g. "SILVER"), "current XP / next-rank threshold XP", and a
  LinearProgressIndicator filling proportionally toward the next rank.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires OverviewRankStrip.swift — not written."

### 4. XP Awarded on Workout Save
expected: |
  Before saving, note the current totalXp (from rank strip). Save a workout
  containing a few sets. After save, Overview rank strip totalXp increases
  by `floor(sum(reps × weightKg) / 100)` (D-02 formula). Example: 10 reps
  × 100 kg across one set → +10 XP.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "XP visibility depends on the rank strip; Kotlin engine executes but no iOS surface to observe totalXp delta."

### 5. Unlock Modal — Rank Promotion
expected: |
  Save a workout whose XP award crosses a rank threshold (e.g., first-ever
  workout crosses SILVER → SILVER_ELITE at 500 XP if enough volume). After
  save completes, an AlertDialog appears showing rank promotion (fromRank →
  toRank, totalXp, flavour copy). A short haptic vibration (~50ms) fires on
  appearance. Dismissing the modal returns to normal UI.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires UnlockModalView.swift at MainTabView level — not written."

### 6. Unlock Modal — Achievement Unlock
expected: |
  Trigger an achievement threshold (e.g., complete 10 total workouts →
  consistency-total-workouts BRONZE). After the triggering workout save,
  an AlertDialog appears showing the achievement name, tier badge (Bronze/
  Silver/Gold), and flavour copy. Haptic fires.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires UnlockModalView.swift — not written."

### 7. Unlock Modal — Queue Behavior (Multiple Unlocks)
expected: |
  Trigger a save that unlocks 2+ items simultaneously (e.g. a workout that
  crosses a rank threshold AND an achievement). The modals appear one at a
  time, never stacked. Dismissing the first shows the second. Each modal
  has its own haptic.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires UnlockModalView.swift queue logic — not written."

### 8. Achievement Gallery — Navigate from Settings
expected: |
  On the Workout tab, open the Settings sheet. A "Gamification" section is
  present with an "Achievements" row (trophy leading icon, chevron trailing).
  Tapping the row closes the sheet and navigates to the AchievementGallery
  screen (workout-tab NavHost).
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires SettingsView.swift Gamification section + NavigationLink — not written."

### 9. Achievement Gallery — 36 Tiles Grouped by Category
expected: |
  The gallery shows 4 category sections in fixed order: Volume, Consistency,
  PR Hunter, Exercise Variety. Within each, tiles render in a 2-column
  LazyVerticalGrid. Tier order within each family: Bronze → Silver → Gold.
  Total tile count = 36 (12 families × 3 tiers). Category headers span full
  row width.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires AchievementGalleryView.swift — not written."

### 10. Achievement Gallery — Locked Tile Rendering
expected: |
  Locked achievement tiles render at reduced opacity (~45% alpha), show a
  lock icon, and display "currentProgress / threshold" as a footer (e.g.
  "3 / 10" for a 10-workout achievement with 3 completed).
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires AchievementGalleryView.swift tile rendering — not written."

### 11. Achievement Gallery — Unlocked Tile Rendering
expected: |
  Unlocked tiles render at full opacity, show a trophy (EmojiEvents) icon,
  have a tier-coloured border (bronze/silver/gold), and display
  "Unlocked YYYY-MM-DD" footer using the unlock date.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Requires AchievementGalleryView.swift tile rendering — not written."

### 12. PR Detection Awards 50 XP
expected: |
  In a new workout, lift a weight × reps combo that exceeds the prior best
  for that exercise (a new PR). Save the workout. The rank strip total XP
  gains an additional +50 on top of volume XP (PR_XP constant). Only one
  +50 PR award per exercise per workout, regardless of how many sets
  exceeded the PR.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Kotlin engine fires, but XP delta not observable without rank strip."

### 13. Nutrition Goal Day Awards 25 XP
expected: |
  With nutrition goals configured (calories/protein/fat/carbs), log
  consumption entries whose daily totals land within ±10% of each goal
  (unset goals = 0 are skipped). Open the Overview tab (triggers
  GoalDayTrigger.maybeTrigger). +25 XP awarded for the goal-day. Repeating
  the same day does not double-award (dedup via event key goalday:YYYY-MM-DD).
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Kotlin engine fires (GoalDayTrigger from OverviewViewModel.refresh()), but goal-day XP not observable without rank strip."

### 14. Streak Bonus XP (3-Day Workout Threshold)
expected: |
  Complete workouts on 3 consecutive calendar days (backdate the DB or use
  real days). On the 3rd day's save, an additional +25 XP streak bonus is
  awarded (STREAK_WORKOUT_3D). Rank strip reflects the bonus. Event key
  "streak:workout:3:<runStartEpochDay>" prevents re-award across saves on
  the same run.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Kotlin engine fires, but streak bonus not observable without rank strip."

### 15. Retroactive Walker — First Launch After Upgrade
expected: |
  On the first launch after v7 → v8 migration (fresh install of this build
  over prior data), GamificationStartup triggers RetroactiveWalker. It
  processes all prior CompletedWorkouts chronologically (PR detection using
  point-in-time PBs) and all prior nutrition goal-days. The rank strip shows
  the resulting cumulative XP on return to Overview. `retroactiveApplied`
  sentinel flips to true; subsequent launches do NOT re-replay. Achievement
  gallery reflects unlocks earned from historical data.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Replay runs on iOS startup but outcome is only observable via rank strip + gallery, neither implemented. User's fresh install had no prior data to replay in any case."

### 16. XP Idempotency — No Double Award on Retry
expected: |
  Simulate a save retry: trigger `gamificationEngine.onWorkoutSaved(id)`
  twice for the same workoutId (e.g., via crash-between-save-and-clear
  recovery). The ledger awards XP only once. No duplicate rank promotion
  modal. `(source, eventKey)` unique index on xp_ledger enforces this at
  DB level.
result: blocked
blocked_by: ios-ui-not-implemented
reason: "Dedup holds at DB level (verified by plan 01 unique index), but 'no duplicate modal' half requires UnlockModalView.swift."

## Summary

total: 16
passed: 0
issues: 1
pending: 0
skipped: 0
blocked: 15

## Gaps

- truth: "Phase 15 gamification is user-visible on iOS (the project's primary platform per CLAUDE.md)"
  status: failed
  reason: "User reported: fresh build on my iphone, deleted all data beforehand frsh everything, no xp rank intro. i cant even find it anywhere in the ui"
  severity: major
  test: 1
  verified_root_cause: |
    Plans 08 and 09 declared the iOS Swift presentation layer as
    user-implemented per `<ios_integration_contract>`. The Kotlin side
    is complete (ViewModels, SharedFlow, Koin factories, engine
    pipeline), but no Swift view observes the shared flows or renders
    gamification UI.

    Verified by searching iosApp/ for every symbol the contract would
    reference:
      grep -rn "GamificationUiKoinHelper\|AchievementGalleryKoinHelper\|
      rankStateFlow\|unlockEventsFlow\|getGamificationViewModel\|
      getAchievementGalleryViewModel\|OverviewRankStrip\|UnlockModal\|
      AchievementGallery" iosApp/  →  zero matches.

    Additionally:
      ls iosApp/iosApp/Views/Gamification/  →  no such directory
      ls iosApp/iosApp/Views/Overview/  →  only OverviewView.swift (no
        OverviewRankStrip.swift)

    Conclusion: the iOS UI surface never shipped.
  artifacts_missing:
    - iosApp/iosApp/Views/Overview/OverviewRankStrip.swift
    - iosApp/iosApp/Views/Gamification/UnlockModalView.swift
    - iosApp/iosApp/Views/Gamification/AchievementGalleryView.swift
  artifacts_modified_needed:
    - iosApp/iosApp/Views/Overview/OverviewView.swift (insert OverviewRankStrip as first VStack child)
    - iosApp/iosApp/Views/Settings/SettingsView.swift (add Gamification section with Achievements NavigationLink)
    - iosApp/iosApp/Views/MainTabView.swift (attach .fullScreenCover for UnlockModalView)
  contract_references:
    - "15-08-PLAN.md <ios_integration_contract>"
    - "15-09-PLAN.md <ios_integration_contract>"
  missing: []

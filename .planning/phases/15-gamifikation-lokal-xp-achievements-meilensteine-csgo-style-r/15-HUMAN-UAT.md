---
status: partial
phase: 15-gamifikation-lokal-xp-achievements-meilensteine-csgo-style-r
source: [15-VERIFICATION.md]
started: 2026-04-23T00:00:00Z
updated: 2026-04-23T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Cold start smoke test — fresh install, Room v7→v8 migration, rank strip visible
expected: App boots without crash. v7 data is intact. Overview tab shows rank strip (Unranked literal on fresh install, or Ranked state if retroactive walker processed prior workouts).
result: [pending]

### 2. Overview rank strip — Unranked state renders D-11 literal
expected: On fresh install, Overview tab shows a lock icon and exactly: "Unranked — complete a workout to unlock Silver"
result: [pending]

### 3. Overview rank strip — Ranked state after completing a workout
expected: After saving a workout, the rank strip updates to show the current rank display name, totalXp / nextRankThreshold XP, and a proportional ProgressView.
result: [pending]

### 4. XP awarded on workout save — formula verified (D-02)
expected: Save a workout with N sets. Rank strip totalXp increases by floor(sum(reps × weightKg) / 100).
result: [pending]

### 5. Unlock modal — rank promotion fires with haptic
expected: When a rank threshold is crossed, a full-screen modal appears with the correct rank name, totalXp, and flavour copy. A success haptic fires on modal appear. Dismissal works.
result: [pending]

### 6. Unlock modal — achievement unlock fires with haptic
expected: Triggering an achievement threshold shows the achievement name, tier badge, and flavour copy with a haptic.
result: [pending]

### 7. Unlock modal queue — multiple unlocks present one at a time (CR-01 fix)
expected: When two events fire simultaneously (rank promo + achievement), modals appear sequentially. Dismissing the first shows the second. Each modal fires its own haptic. No event is dropped.
result: [pending]

### 8. Achievement gallery — navigate from Settings Gamification section
expected: Opening Settings shows a Gamification section with an Achievements row (trophy icon, chevron). Tapping navigates to AchievementGalleryView.
result: [pending]

### 9. Achievement gallery — 36 tiles grouped by category
expected: Gallery shows Volume / Consistency / PR Hunter / Exercise Variety categories in that order. 2-column LazyVGrid. 36 total tiles (12 families × 3 tiers).
result: [pending]

### 10. Achievement gallery — locked tile rendering
expected: Locked tiles at 0.45 opacity with lock icon and "currentProgress / threshold" footer.
result: [pending]

### 11. Achievement gallery — unlocked tile rendering
expected: Unlocked tiles at full opacity with trophy icon, tier-colored border, and "Unlocked YYYY-MM-DD" footer.
result: [pending]

### 12. PR detection awards 50 XP (D-03)
expected: Setting a new PR in a workout causes the rank strip to show an additional +50 XP on top of volume XP.
result: [pending]

### 13. Nutrition goal-day awards 25 XP (D-04/D-05)
expected: Logging macros within ±10% of goals on Overview tab appearance triggers GoalDayTrigger; +25 XP awarded. Repeat same day does not double-award.
result: [pending]

### 14. Streak bonus 3-day workout threshold (D-06)
expected: Workouts on 3 consecutive days trigger +25 streak bonus XP. Event key prevents re-award on same streak run.
result: [pending]

### 15. Retroactive walker — first launch after upgrade (D-12/D-13)
expected: On first launch over prior v7 data, RetroactiveWalker replays history. Rank strip shows cumulative XP. retroactiveApplied sentinel flips to true. Subsequent launches do not re-replay.
result: [pending]

### 16. XP idempotency — no double award on duplicate event key (D-13)
expected: Calling onWorkoutSaved(id) twice does not double XP. DB unique index on (source, eventKey) enforces this. No duplicate modal.
result: [pending]

## Summary

total: 16
passed: 0
issues: 0
pending: 16
skipped: 0
blocked: 0

## Gaps

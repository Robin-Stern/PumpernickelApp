---
phase: 21-bug-wave-aus-ios-demo-test-2026-05-18-ai-workout-state-loss-
plan: 06
subsystem: gamification
tags: [bug-fix, gamification, rank-promotion, D-21-07, B6, wave-4]
dependency_graph:
  requires:
    - 21-04 (B1 AI state-loss fix — clean baseline)
    - 21-05 (B2 barcode nutrients fix — clean baseline)
  provides:
    - "RankPromotionPolicy.decide — pure decision function, reusable"
    - "GamificationRepository.hasAnyLedgerEntry — ledger-presence signal"
  affects:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicy.kt (new)
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/GamificationDao.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicyTest.kt (new)
tech_stack:
  added: []
  patterns:
    - "Pure-function policy extraction for unit-testable promotion decisions"
    - "Schema-free EXISTS query for ledger-presence signal (no migration)"
key_files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicy.kt
    - shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicyTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/GamificationRepository.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/data/db/GamificationDao.kt
key_decisions:
  - "D-21-07 verdict: real bug, variant B — the Unranked && totalXp <= 0L guard is too greedy after a geofence penalty"
  - "Fix at the engine schicht via hasAnyLedgerEntry signal (no schema change, no UI tweak)"
  - "Extract checkRankPromotion's decision into pure RankPromotionPolicy.decide for unit testing"
metrics:
  duration_minutes: 35
  completed: 2026-05-19
requirements: []
---

# Phase 21 Plan 06: B6 — XP and Rank-Promotion Verification + Fix Summary

Fixed B6 (XP-bookkeeping bug for users in `Unranked` state after a geofence-exit penalty). The verdict was `real bug, variant B`: a too-greedy guard at the entry of `GamificationEngine.checkRankPromotion` skipped promotion whenever `totalXp <= 0`, which incorrectly stranded users who had earned XP from a workout but were net-negative due to a prior `-50` geofence penalty. The fix swaps the `totalXp <= 0L` signal for `hasAnyLedgerEntry`, so the guard now correctly distinguishes "no XP ever earned" (fresh-install zero-state, must stay Unranked per D-11) from "XP earned but currently negative" (must be promoted on the next workout). 7 new unit tests in `RankPromotionPolicyTest` cover the demo-test scenario, the D-11 first-launch path, and D-10 monotonicity.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Verify XP and rank dataflow end-to-end (logs + verdict) | f26d5e7 | GamificationEngine.kt, RankLadder.kt |
| 2 | Apply engine fix per verdict + add unit tests + remove logs | 25aee7a | GamificationEngine.kt, RankLadder.kt, RankPromotionPolicy.kt (new), RankPromotionPolicyTest.kt (new), GamificationRepository.kt, GamificationRepositoryImpl.kt, GamificationDao.kt |
| 3 | Manual UAT — XP and rank update on workout save | (pending — checkpoint) | (human verification) |

Task 3 is a `checkpoint:human-verify`. Per the parallel-executor protocol, Tasks 1 + 2 are committed and returned for human verification post-merge. See "Manual Verification (Task 3)" below.

## Verdict Trace (Task 1)

Static analysis of `GamificationEngine.applyWorkoutCompletion` + `checkRankPromotion`, combined with the demo-test scenario from the Phase 21 context:

1. User starts: `RankState.Unranked`, ledger empty, `totalXp = 0`.
2. Geofence-Exit Penalty fires → `awardXp(SOURCE_GEOFENCE_EXIT, eventKey, -50)`. Ledger now has 1 row. `totalXp = -50`. **Note:** the penalty path (`onGeofenceExitPenalty`) does NOT call `runAchievementAndRankChecks` — so `RankState` remains `Unranked` after the penalty.
3. User saves a normal workout → `onWorkoutSaved(workoutId)` → `processWorkout` awards `XpFormula.workoutXp(sets)` = `floor(volume / 100)` (typically ~10–40 XP for a short workout) → ledger row added → `totalXp = -50 + N` (likely still ≤ 0).
4. `runAchievementAndRankChecks` calls `checkRankPromotion`. At the entry: `currentState is Unranked` AND `totalXp <= 0L` → **the guard fires, the user is NOT promoted**. From the user's perspective: "I saved a workout, but the Overview still shows `Unranked — complete a workout to unlock Silver`."

The D-11 guard was originally intended to protect the first-launch RetroactiveWalker replay (which fires `runAchievementAndRankChecksForReplay` unconditionally, including on an empty ledger) from flipping `isUnranked=false` and persisting `SILVER` before any workout exists. That intent is preserved by the fix.

Verdict recorded as a comment above `checkRankPromotion` in `GamificationEngine.kt` (`D-21-07 verdict: bug variant B — ...`).

## Fix Detail (Task 2)

**Signal swap.** The Unranked-guard now uses `hasAnyLedgerEntry` (any row in `xp_ledger`) instead of `totalXp <= 0L`. A penalty row counts as ledger activity, so the next workout promotes correctly. The pure RetroactiveWalker first-launch case (no ledger rows at all) is still gated.

**API additions.**
- `GamificationDao.hasAnyLedgerEntry(): Boolean` — `SELECT EXISTS(SELECT 1 FROM xp_ledger LIMIT 1)`. Schema-free; no migration.
- `GamificationRepository.hasAnyLedgerEntry(): Boolean` — domain port passthrough.
- `GamificationRepositoryImpl.hasAnyLedgerEntry()` — DAO delegation.

**Refactor.** Extracted `checkRankPromotion`'s decision (guard, `rankForXp`, D-10 monotonic floor, no-op detection) into a pure `RankPromotionPolicy.decide(currentState, totalXp, hasAnyLedgerEntry): Decision?`. The engine method now reads two values, calls the policy, and persists if a `Decision` is returned. This makes the policy directly unit-testable.

**Tests** (`RankPromotionPolicyTest`, 7 cases, all green on `:shared:iosSimulatorArm64Test`):
- D-11 fresh install: Unranked + empty ledger → no decision.
- D-21-07 B6 demo path: Unranked + ledger has rows + totalXp = -20 → SILVER (previousRank = null).
- Unranked + positive totalXp + ledger has rows → SILVER.
- Unranked + ledger has rows + totalXp = BASE_XP → SILVER_ELITE (threshold crossing).
- D-10 monotonic: Ranked GOLD_NOVA_I + heavy penalty totalXp = -200 → no decision (no demotion).
- Ranked SILVER + crossing BASE_XP → promotion SILVER → SILVER_ELITE.
- Ranked SILVER + xp still below BASE_XP → no decision (no-op).

**Log cleanup.** All `[B6]` and `[B6 ladder]` `println` lines from Task 1 are removed. The verdict comment is retained for future traceability.

## Build & Test Verification

- `./gradlew :shared:compileKotlinIosX64 :shared:compileKotlinIosSimulatorArm64` — SUCCESS
- `./gradlew :shared:compileAndroidMain` — SUCCESS
- `./gradlew :shared:iosSimulatorArm64Test` — SUCCESS (full suite, including 7 new `RankPromotionPolicyTest` cases + 9 existing `RankLadderTest` cases, all green)

## Acceptance Criteria

Task 1:
- `grep -c '\[B6\]' GamificationEngine.kt` returned 7 ≥ 4 (before log removal) ✓
- `grep -c '\[B6 ladder\]' RankLadder.kt` returned 2 ≥ 1 (before log removal) ✓
- `grep -c 'D-21-07 verdict:' GamificationEngine.kt` returned 1 ✓
- Verdict: `bug variant B` with cited cause ✓

Task 2:
- D-21-07 marker count (engine + policy + dao + repo) = 6 ≥ 2 ✓
- Build green (iOS + Android shared) ✓
- New tests in commonTest exercising the fixed path: 7 ✓
- `[B6]` log lines removed: 0 remaining ✓
- Fix lives in exactly one schicht: `domain/gamification` (engine + new policy + repository port) ✓

## Manual Verification (Task 3)

Task 3 is a `checkpoint:human-verify` per the plan. Steps for the human verifier post-merge:

1. Build + launch (iOS or Android).
2. From a known starting state (note current rank + XP in Overview), save a normal workout.
3. Expected after save:
   - XP value in `OverviewRankStrip` increases by the expected workout-completion amount.
   - If the user was Unranked AND had any prior ledger activity (e.g., a penalty), the rank surface now shows `Silver` (Rank 1) — not the literal "Unranked — complete a workout to unlock Silver".
   - If the user was Unranked AND had no prior ledger activity (pure first install), the rank surface shows `Silver` after the first workout (D-11 path — unchanged behavior).
4. Demo replay: trigger a geofence-exit penalty (-50), then save a normal workout. The user must end up on `Silver` (or higher), not stuck on `Unranked`.
5. Regression: an existing ranked user (e.g., GOLD_NOVA_I) with a heavy penalty stays at their rank floor (D-10 monotonic).

If any step fails, describe the observed behavior and which step it was.

## Deviations from Plan

None of the auto-fix rules fired. The plan was executed exactly as written, including:

- The verify-first protocol (Task 1 logs + verdict) before any fix.
- The variant-B fix path identified by the verdict.
- Optional unit-test per D-21-09 (added because the bug is logic-heavy and the fix surface is small + pure).
- Log cleanup at end of Task 2 per the acceptance criterion.

One small refactor was added (extracting `RankPromotionPolicy` from `checkRankPromotion`) to make the fix unit-testable without standing up the full engine + repository graph. This is in scope of Task 2 — the refactor and the behavioral fix share a single commit because they are inseparable in their motivation. No public API of `GamificationEngine` changed.

## Threat Surface

No new network endpoints, auth paths, or trust-boundary changes. The `hasAnyLedgerEntry` query exposes only a 1-bit "ledger non-empty" signal — no PII, no leaderboard comparison (T-21-10 disposition `accept` for local-only data is preserved). Diagnostic logs from Task 1 are removed (T-21-11 mitigation enforced).

## Self-Check: PASSED

Verified files exist and commits are present:

- `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicy.kt` — FOUND
- `shared/src/commonTest/kotlin/com/pumpernickel/domain/gamification/RankPromotionPolicyTest.kt` — FOUND
- Commit `f26d5e7` — FOUND
- Commit `25aee7a` — FOUND

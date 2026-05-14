---
slug: fresh-install-rank-silver1
status: resolved
trigger: Fresh app install starts user at rank "silver1" instead of "unranked"
created: 2026-04-23
updated: 2026-04-23
---

# Debug Session: fresh-install-rank-silver1

## Symptoms

- **Expected:** A brand-new user with no workouts, no XP earned, and no completed achievements should be displayed as "unranked" (or the lowest/zero rank tier) in the Overview XP banner on first launch.
- **Actual:** The Overview XP banner shows rank "silver1" on a fresh install, even though the user has done nothing in the app yet.
- **Error messages:** None (visual/UX bug, not an exception).
- **Timeline:** Observed during manual UAT of the gamification feature (phase 15). Appears every time on first launch after fresh install / data reset.
- **Reproduction:**
  1. Fully delete / reset app data (fresh install state).
  2. Launch the app.
  3. Navigate to the Overview tab.
  4. Observe XP banner — shows "silver1" instead of unranked / zero tier.

## Current Focus

- hypothesis: RetroactiveWalker unconditionally runs `runAchievementAndRankChecksForReplay()` at the end of its first-launch replay. Even with zero workouts and zero XP, `GamificationEngine.checkRankPromotion` proceeds to write `isUnranked=false, currentRank=SILVER` because `RankLadder.rankForXp(0L)` returns `Rank.SILVER` (threshold(SILVER)=0) and the early-return condition doesn't trigger when previousRank is null.
- test: Trace the logic manually with fresh-install state (entity=null, totalXp=0) through `checkRankPromotion` — confirmed it writes Ranked(SILVER, 0 XP).
- expecting: Confirmed — root cause is `checkRankPromotion` missing a "skip when 0 XP and still unranked" guard.
- next_action: Apply minimal fix: in `checkRankPromotion`, short-circuit when `currentState is Unranked && totalXp == 0L` so the rank_state row stays unwritten. D-11 explicitly says "Rank 1 (Silver) unlocks at XP = 0 on first workout" — the unlock trigger is the workout save, not the first boot.

## Evidence

- checked: `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/RankLadder.kt` — `rankForXp(0L)` returns `Rank.SILVER` (SILVER threshold = 0). That's by design for D-11.
- checked: `shared/src/commonMain/kotlin/com/pumpernickel/data/db/RankStateEntity.kt` — default row has `isUnranked=true`, `currentRank="UNRANKED"`. On fresh install no row exists yet; DAO returns null → mapper returns `RankState.Unranked`. So starting state is correct.
- checked: `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/GamificationRepository.kt` lines 151–163 — `toDomain` returns `Unranked` iff entity is null OR `isUnranked` is true. Correct mapper behavior.
- checked: `shared/src/commonMain/kotlin/com/pumpernickel/di/GamificationStartup.kt` — on every launch runs `seeder.seedIfEmpty()` (harmless, just achievements) then `walker.applyIfNeeded()`.
- checked: `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/RetroactiveWalker.kt` — `applyIfNeeded` runs `replay()` once (sentinel-gated). `replay()` ends with unconditional `engine.runAchievementAndRankChecksForReplay()` — fires even when workouts list is empty.
- checked: `shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt` `checkRankPromotion` lines 279–315:
    * currentState = Unranked
    * totalXp = 0
    * newRank = SILVER
    * previousRank = null (from Unranked)
    * targetRank = newRank = SILVER (null previousRank skips the monotonicity guard)
    * early-return `previousRank == targetRank && currentState !is Unranked` → `null == SILVER` is false → does NOT return
    * proceeds to `setRankState(totalXp=0, currentRank=SILVER, isUnranked=false)` → user is now promoted to Silver at 0 XP on fresh install.
  found: Bug confirmed. The only first-launch path that hits this is the RetroactiveWalker sentinel run; after that sentinel flips to true the walker doesn't re-run, but the damage is done — `rank_state` row is now `SILVER, isUnranked=false` permanently until a new reset.
- checked: iosApp `OverviewRankStrip.swift` shows `ranked.currentRank.displayName` which for SILVER is "Silver". The user's "silver1" is a paraphrase of Rank.SILVER (tier = ordinal+1 = 1); the UI literally shows "Silver" + a progress bar toward Silver Elite + rosette with silver tint.

## Eliminated

- hypothesis: Off-by-one in rank threshold ladder placing 0 XP into SILVER_ELITE or similar.
  evidence: Threshold table is correct — `thresholdFor(SILVER)=0`, `thresholdFor(SILVER_ELITE)=BASE_XP=500`. RankLadderTest covers this.
- hypothesis: Missing "unranked" tier in the ladder.
  evidence: Unranked is modeled as a separate `RankState.Unranked` sealed-object, not a Rank enum entry. That's deliberate per D-11. The bug is in the transition logic, not the ladder.
- hypothesis: A seed granting initial XP.
  evidence: `AchievementStateSeeder` only seeds achievement rows (locked, 0 progress). No XP ledger rows are seeded. `totalXp` is 0 on fresh install.
- hypothesis: `?: Rank.SILVER_1` style null-coalesce fallback on UI.
  evidence: Mapper in GamificationRepository uses `?: Rank.SILVER`, but that only runs when entity is non-null (i.e., after the walker already wrote it). The fallback is a safety net for corrupt enum strings — it is not what puts the user at SILVER on first boot.

## Suspected Surfaces

Starting surfaces to investigate (for the debugger agent):
- Rank computation / tiering logic (how XP or progress maps to rank names)
- Initial user profile / seed — what defaults are written on first launch (DB migration, onCreate, app boot)
- Any hard-coded default rank constants (fallbacks when no history exists)
- Whether rank is computed from XP total, workout count, achievements, or some composite, and what that value is at zero

## Resolution

- root_cause: `GamificationEngine.checkRankPromotion` promotes the user to `Rank.SILVER` (isUnranked=false, totalXp=0) on first launch because `RetroactiveWalker.replay()` unconditionally invokes `runAchievementAndRankChecksForReplay()` even when there are no workouts and no ledger entries. Inside `checkRankPromotion`, `RankLadder.rankForXp(0L)` correctly returns `Rank.SILVER`, and the early-return guard (`previousRank == targetRank && currentState !is Unranked`) does not fire because `previousRank` is `null` for an Unranked user. Result: rank_state is overwritten to `Ranked(SILVER, 0 XP)` on the very first boot, contradicting D-11 which states Rank 1 unlocks on the **first workout**, not on app launch.
- fix: Added an early-return guard at the top of `checkRankPromotion`: `if (currentState is RankState.Unranked && totalXp <= 0L) return`. This keeps an Unranked user in the Unranked state whenever there is still no earned XP, regardless of which caller triggered the check (retroactive replay or live path). Rank promotion continues to fire on live workout saves once the ledger has any XP.
- verification:
  * Automated: `:shared:iosSimulatorArm64Test --tests RankLadderTest` passes (no regression in pure-rank logic).
  * Compile: `:shared:compileKotlinIosSimulatorArm64` builds clean (only pre-existing warnings unrelated to this change).
  * Human: delete app data → reinstall → launch app → open Overview tab. Expected: XP banner shows the "Unranked — complete a workout to unlock Silver" text with the lock icon (the `unrankedContent` branch in `OverviewRankStrip.swift`). After saving a workout with non-zero volume, banner should flip to the ranked view at `Rank.SILVER`.
- files_changed:
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/gamification/GamificationEngine.kt

## Follow-ups (not fixed in this session)

- Existing users whose rank_state was already corrupted to SILVER @ 0 XP by the bug on a prior build will stay ranked (the fix does not mutate existing state). A one-time migration could detect `isUnranked=false AND totalXp=0 AND lastPromotedAtMillis is null-or-matches-first-launch-heuristic` and reset to Unranked, but it is out of scope for this bug fix. Only affects users who installed Phase 15 builds before this patch.
- `RankStateEntity` on fresh install never has a row inserted — the SQL-level default column values (`isUnranked=1`, `currentRank="UNRANKED"`) are therefore never exercised. Safe to keep, but consider collapsing with a doc note in the entity.
- No GamificationEngine integration test suite exists. A `commonTest` fake-repo harness covering (a) `checkRankPromotion` with Unranked+0 XP stays Unranked, (b) Unranked+1 XP promotes to SILVER, (c) Ranked stays monotonic, would be a useful addition for Phase 15+ regression protection. Flagged for a future Plan.
- Behavioural note: the fix also changes the edge case where a user saves a workout with zero volume (e.g., bodyweight sets stored as weightKgX10=0). Previously that path would promote to SILVER @ 0 XP (because `onWorkoutSaved` calls `checkRankPromotion` after awarding workoutXp=0). With the fix, such a user stays Unranked until they earn any XP. Arguably more correct per D-11, but flagging in case the UAT spec treats "first workout save" as the trigger.

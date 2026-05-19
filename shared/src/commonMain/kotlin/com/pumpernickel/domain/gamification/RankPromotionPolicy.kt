package com.pumpernickel.domain.gamification

/**
 * Pure decision function for rank promotion. Extracted from
 * GamificationEngine.checkRankPromotion (D-21-07) so the policy can be
 * unit-tested without standing up the full engine + repository graph.
 *
 * D-11: Rank 1 (Silver) unlocks at XP = 0 on the FIRST workout — NOT on app
 * launch. The RetroactiveWalker fires `runAchievementAndRankChecksForReplay`
 * unconditionally, including on a fresh install with an empty ledger — that
 * path must NOT flip `isUnranked=false` until at least one ledger row exists.
 *
 * D-21-07: Distinguish "no XP ever earned" (must stay Unranked) from "XP
 * earned but currently net-negative due to penalties" (must be promoted on
 * the next workout). The previous implementation used `totalXp <= 0L` as the
 * skip-condition, which incorrectly bailed for the Phase-21 demo scenario:
 *   1. User Unranked, totalXp=0
 *   2. Geofence-Exit Penalty credits -50 → totalXp=-50
 *   3. Normal workout credits +N (typically < 50 for short workouts)
 *   4. totalXp still <= 0, guard fires, user remains Unranked despite saving a
 *      complete workout.
 * The fix uses `hasAnyLedgerEntry` to detect zero-state: a penalty row counts
 * as ledger activity, so the next workout will promote.
 *
 * D-10: monotonic — rank can only increase. A user demoted by penalty keeps
 * their previous rank floor.
 */
object RankPromotionPolicy {

    data class Decision(
        val previousRank: Rank?,
        val targetRank: Rank
    )

    /**
     * @return the promotion decision, or null if no update should be persisted
     *         (either fresh-install zero-state, or already at the correct rank).
     */
    fun decide(
        currentState: RankState,
        totalXp: Long,
        hasAnyLedgerEntry: Boolean
    ): Decision? {
        // D-11 / D-21-07 — fresh-install guard. Only Unranked + zero ledger
        // activity stays Unranked. A user with ANY ledger row (positive or
        // negative) must be evaluated for promotion.
        if (currentState is RankState.Unranked && !hasAnyLedgerEntry) return null

        val newRank = RankLadder.rankForXp(totalXp)

        val previousRank: Rank? = when (currentState) {
            is RankState.Unranked -> null
            is RankState.Ranked -> currentState.currentRank
        }

        // D-10: rank is monotonically non-decreasing.
        val targetRank = if (previousRank != null && newRank.ordinal < previousRank.ordinal) {
            previousRank
        } else {
            newRank
        }

        // No-op when nothing changes AND we are already in Ranked state.
        if (previousRank == targetRank && currentState !is RankState.Unranked) return null

        return Decision(previousRank = previousRank, targetRank = targetRank)
    }
}

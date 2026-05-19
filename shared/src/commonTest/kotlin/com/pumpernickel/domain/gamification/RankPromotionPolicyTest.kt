package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-function tests for the D-21-07 rank-promotion fix.
 * Covers the Phase-21 B6 demo scenario (Unranked + penalty + workout)
 * and the D-11 first-launch RetroactiveWalker zero-state guard.
 */
class RankPromotionPolicyTest {

    // ----- D-11: fresh-install zero-state stays Unranked -----

    @Test fun unrankedWithEmptyLedgerStaysUnranked() {
        val decision = RankPromotionPolicy.decide(
            currentState = RankState.Unranked,
            totalXp = 0L,
            hasAnyLedgerEntry = false
        )
        assertNull(decision, "Fresh install with no ledger rows must NOT promote (D-11)")
    }

    // ----- D-21-07: B6 demo scenario — penalty then workout -----

    @Test fun unrankedAfterPenaltyAndWorkoutPromotesToSilver() {
        // User had Penalty (-50) and then logged a small workout (+30) →
        // totalXp = -20, but ledger has entries.
        val decision = RankPromotionPolicy.decide(
            currentState = RankState.Unranked,
            totalXp = -20L,
            hasAnyLedgerEntry = true
        )
        // SILVER threshold is 0, so rankForXp(-20) = SILVER (floor). D-21-07
        // fix means the Unranked-guard does NOT fire here.
        assertEquals(Rank.SILVER, decision?.targetRank)
        assertNull(decision?.previousRank, "previousRank must be null when leaving Unranked")
    }

    @Test fun unrankedWithPositiveXpPromotesToSilver() {
        val decision = RankPromotionPolicy.decide(
            currentState = RankState.Unranked,
            totalXp = 100L,
            hasAnyLedgerEntry = true
        )
        assertEquals(Rank.SILVER, decision?.targetRank)
        assertNull(decision?.previousRank)
    }

    @Test fun unrankedWithExactlyBaseXpPromotesToSilverElite() {
        val decision = RankPromotionPolicy.decide(
            currentState = RankState.Unranked,
            totalXp = RankLadder.BASE_XP,
            hasAnyLedgerEntry = true
        )
        // crossing BASE_XP threshold → SILVER_ELITE
        assertEquals(Rank.SILVER_ELITE, decision?.targetRank)
    }

    // ----- D-10: monotonic — penalty cannot demote a ranked user -----

    @Test fun rankedUserStaysAtRankFloorAfterPenalty() {
        val ranked = RankState.Ranked(
            currentRank = Rank.GOLD_NOVA_I,
            totalXp = 800L,
            currentRankThreshold = RankLadder.thresholdFor(Rank.GOLD_NOVA_I),
            nextRank = Rank.GOLD_NOVA_II,
            nextRankThreshold = RankLadder.thresholdFor(Rank.GOLD_NOVA_II),
            lastPromotedAtMillis = 0L
        )
        // Now user gets -1000 XP (heavy penalty) → totalXp=-200,
        // RankLadder would return SILVER, but D-10 monotonic keeps GOLD_NOVA_I.
        val decision = RankPromotionPolicy.decide(
            currentState = ranked,
            totalXp = -200L,
            hasAnyLedgerEntry = true
        )
        // previousRank == targetRank == GOLD_NOVA_I and state is Ranked →
        // decision is null (no-op).
        assertNull(decision, "Penalty must NOT demote a ranked user (D-10)")
    }

    @Test fun rankedUserPromotesWhenCrossingThreshold() {
        val ranked = RankState.Ranked(
            currentRank = Rank.SILVER,
            totalXp = 100L,
            currentRankThreshold = 0L,
            nextRank = Rank.SILVER_ELITE,
            nextRankThreshold = RankLadder.BASE_XP,
            lastPromotedAtMillis = 0L
        )
        val decision = RankPromotionPolicy.decide(
            currentState = ranked,
            totalXp = RankLadder.BASE_XP + 50L,
            hasAnyLedgerEntry = true
        )
        assertEquals(Rank.SILVER, decision?.previousRank)
        assertEquals(Rank.SILVER_ELITE, decision?.targetRank)
    }

    @Test fun rankedUserNoOpWhenAlreadyAtTargetRank() {
        val ranked = RankState.Ranked(
            currentRank = Rank.SILVER,
            totalXp = 100L,
            currentRankThreshold = 0L,
            nextRank = Rank.SILVER_ELITE,
            nextRankThreshold = RankLadder.BASE_XP,
            lastPromotedAtMillis = 0L
        )
        val decision = RankPromotionPolicy.decide(
            currentState = ranked,
            totalXp = 200L,   // still below BASE_XP, still SILVER
            hasAnyLedgerEntry = true
        )
        assertNull(decision, "No promotion needed when already at the correct rank")
    }
}

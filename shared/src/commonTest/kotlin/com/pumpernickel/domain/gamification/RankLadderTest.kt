package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RankLadderTest {
    @Test fun silverThresholdIsZero() {
        assertEquals(0L, RankLadder.thresholdFor(Rank.SILVER))
    }

    @Test fun silverEliteThresholdIsBaseXp() {
        assertEquals(RankLadder.BASE_XP, RankLadder.thresholdFor(Rank.SILVER_ELITE))
    }

    @Test fun goldNovaIThresholdIsBaseTimesOnePointFive() {
        assertEquals((RankLadder.BASE_XP * 1.5).toLong(), RankLadder.thresholdFor(Rank.GOLD_NOVA_I))
    }

    @Test fun rankForZeroXpIsSilver() {
        assertEquals(Rank.SILVER, RankLadder.rankForXp(0L))
    }

    @Test fun rankForMaxXpIsGlobalElite() {
        assertEquals(Rank.GLOBAL_ELITE, RankLadder.rankForXp(Long.MAX_VALUE))
    }

    @Test fun rankForBoundaryXpHitsNextRank() {
        assertEquals(Rank.SILVER_ELITE, RankLadder.rankForXp(RankLadder.BASE_XP))
    }

    @Test fun nextRankOfGlobalEliteIsNull() {
        assertNull(RankLadder.nextRank(Rank.GLOBAL_ELITE))
    }

    @Test fun nextRankOfSilverIsSilverElite() {
        assertEquals(Rank.SILVER_ELITE, RankLadder.nextRank(Rank.SILVER))
    }

    @Test fun tenRanksTotal() {
        assertEquals(10, Rank.values().size)
    }
}

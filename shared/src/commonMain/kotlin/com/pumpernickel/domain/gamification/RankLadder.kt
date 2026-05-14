package com.pumpernickel.domain.gamification

import kotlin.math.pow

/**
 * Exponential rank-threshold curve. See D-09.
 * threshold(n) = BASE_XP × 1.5^(n-2) for n ≥ 2 (rank 2 = SILVER_ELITE).
 * Rank 1 (Silver) unlocks at XP = 0 on first workout (D-11).
 */
object RankLadder {
    // TODO(tuning): re-anchor BASE_XP after play-testing per D-07 / D-09 Claude-discretion.
    // 500 chosen so rank 2 is reachable within ~2 hard workouts (sum(reps × kg) / 100 ≈ 500
    // is plausible for a full-body session at moderate load).
    const val BASE_XP: Long = 500L

    private const val GROWTH: Double = 1.5

    /** XP threshold required to reach `rank`. Rank 1 (SILVER) returns 0. */
    fun thresholdFor(rank: Rank): Long = when (rank) {
        Rank.SILVER -> 0L
        else -> {
            // rank 2 → exponent 0 → BASE_XP
            // rank 3 → exponent 1 → BASE_XP × 1.5
            val exponent = (rank.ordinal - 1).toDouble()
            (BASE_XP * GROWTH.pow(exponent)).toLong()
        }
    }

    /** Resolve the highest rank a user qualifies for at the given XP total. */
    fun rankForXp(xp: Long): Rank {
        // Walk ranks from top down; return the first whose threshold we meet.
        val ranks = Rank.values()
        for (i in ranks.indices.reversed()) {
            if (xp >= thresholdFor(ranks[i])) return ranks[i]
        }
        return Rank.SILVER
    }

    /** Next rank after `current`, or null if already at GLOBAL_ELITE. */
    fun nextRank(current: Rank): Rank? {
        val next = current.ordinal + 1
        return if (next < Rank.values().size) Rank.values()[next] else null
    }
}

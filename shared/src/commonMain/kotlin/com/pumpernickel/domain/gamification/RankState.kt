package com.pumpernickel.domain.gamification

/**
 * UI-facing rank state. Maps from `RankStateEntity` in GamificationRepository.
 * D-11: `Unranked` is the initial state until the first workout is saved.
 */
sealed class RankState {
    data object Unranked : RankState()
    data class Ranked(
        val currentRank: Rank,
        val totalXp: Long,
        val currentRankThreshold: Long,   // thresholdFor(currentRank)
        val nextRank: Rank?,              // null at GLOBAL_ELITE
        val nextRankThreshold: Long?,     // null at GLOBAL_ELITE
        val lastPromotedAtMillis: Long?
    ) : RankState()
}

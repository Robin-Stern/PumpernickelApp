package com.pumpernickel.domain.gamification

/**
 * Events the UI presents as a modal + haptic. See D-19 and D-20 (queued presentation).
 */
sealed class UnlockEvent {
    data class RankPromotion(
        val fromRank: Rank?,   // null if first promotion from Unranked state
        val toRank: Rank,
        val totalXp: Long,
        val flavourCopy: String
    ) : UnlockEvent()

    data class AchievementTierUnlocked(
        val achievementId: String,
        val displayName: String,
        val tier: Tier,
        val flavourCopy: String
    ) : UnlockEvent()
}

package com.pumpernickel.domain.repository

import com.pumpernickel.domain.gamification.AchievementProgress
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.domain.gamification.RankState
import com.pumpernickel.domain.gamification.XpLedgerRecord
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer port for gamification state. Read-side exposes Flow<domain>,
 * write-side is suspend. All write methods return Booleans/Units that indicate
 * whether a dedupe-IGNORE fired (so callers can early-exit cascades).
 *
 * Plan 20-08 closed the Plan 20-04 Smell 3 leak by switching
 * [getPrLedgerEntries] from the Room `XpLedgerEntity` to the domain
 * [XpLedgerRecord] projection — `GamificationEngine` is now fully Room-free.
 */
interface GamificationRepository {
    val totalXp: Flow<Long>
    val rankState: Flow<RankState>
    val achievements: Flow<List<AchievementProgress>>

    /**
     * Insert a ledger row. Returns true if written, false if the unique
     * (source, eventKey) index fired (dedupe). See D-13.
     */
    suspend fun awardXp(
        source: String,
        eventKey: String,
        amount: Int,
        awardedAtMillis: Long,
        retroactive: Boolean = false
    ): Boolean

    suspend fun hasLedgerEntry(source: String, eventKey: String): Boolean

    /**
     * D-21-07 — true if at least one XP ledger row exists for the user.
     * Used by the engine's rank-promotion guard to distinguish "no XP ever
     * earned" (must stay Unranked per D-11 first-launch protection) from
     * "XP earned but currently net-negative due to penalties" (must be
     * promoted on the next workout).
     */
    suspend fun hasAnyLedgerEntry(): Boolean

    /** Upserts rank_state singleton (id = 1). */
    suspend fun setRankState(
        totalXp: Long,
        currentRank: Rank?,
        lastPromotedAtMillis: Long?,
        isUnranked: Boolean
    )

    suspend fun getRankStateSnapshot(): RankState

    /** Updates current progress value for an achievement (without unlocking). */
    suspend fun setAchievementProgress(achievementId: String, progress: Long)

    /** Unlocks an achievement tier (writes unlockedAtMillis + progress). */
    suspend fun unlockAchievement(achievementId: String, unlockedAtMillis: Long, progress: Long)

    suspend fun getAchievementProgressSnapshot(id: String): AchievementProgress?

    // ----- Blocker 3 / Blocker 4 ledger-scan passthroughs -----
    /** Returns ISO dates where a nutrition_goal_day XP row exists (ASC). */
    suspend fun getGoalDayIsoDates(): List<String>

    /**
     * Returns all PR-source ledger entries as domain records (ASC by
     * awardedAtMillis). Plan 20-08: switched from `XpLedgerEntity` to
     * [XpLedgerRecord] so the engine no longer imports `data.db.*` (Smell 3).
     */
    suspend fun getPrLedgerEntries(): List<XpLedgerRecord>
}

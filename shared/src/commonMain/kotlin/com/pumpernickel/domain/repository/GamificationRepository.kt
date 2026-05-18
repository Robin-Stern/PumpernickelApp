package com.pumpernickel.domain.repository

import com.pumpernickel.data.db.XpLedgerEntity
import com.pumpernickel.domain.gamification.AchievementProgress
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.domain.gamification.RankState
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer port for gamification state. Read-side exposes Flow<domain>,
 * write-side is suspend. All write methods return Booleans/Units that indicate
 * whether a dedupe-IGNORE fired (so callers can early-exit cascades).
 *
 * NOTE (Smell-3 follow-up): [getPrLedgerEntries] still returns the Room
 * [XpLedgerEntity] type. This Entity-in-domain leak is intentionally retained
 * in Plan 20-04 and will be addressed in Plan 20-09 (GamificationEngine
 * refactor) via a domain projection on the repository.
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

    /** Returns all PR-source ledger entries (ASC). */
    suspend fun getPrLedgerEntries(): List<XpLedgerEntity>
}

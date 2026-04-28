package com.pumpernickel.data.repository

import com.pumpernickel.data.db.AchievementStateEntity
import com.pumpernickel.data.db.GamificationDao
import com.pumpernickel.data.db.RankStateEntity
import com.pumpernickel.data.db.XpLedgerEntity
import com.pumpernickel.domain.gamification.AchievementCatalog
import com.pumpernickel.domain.gamification.AchievementDef
import com.pumpernickel.domain.gamification.AchievementProgress
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.domain.gamification.RankLadder
import com.pumpernickel.domain.gamification.RankState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Data-layer repository for gamification state. Read-side exposes Flow<domain>,
 * write-side is suspend. All write methods return Booleans/Units that indicate
 * whether a dedupe-IGNORE fired (so callers can early-exit cascades).
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

class GamificationRepositoryImpl(
    private val dao: GamificationDao
) : GamificationRepository {

    override val totalXp: Flow<Long> = dao.totalXpFlow()

    override val rankState: Flow<RankState> = combine(
        dao.rankStateFlow(),
        dao.totalXpFlow()
    ) { entity, xp -> entity.toDomain(xp) }

    override val achievements: Flow<List<AchievementProgress>> =
        dao.achievementStateFlow().map { rows ->
            rows.mapNotNull { row -> row.toDomain() }
        }

    override suspend fun awardXp(
        source: String,
        eventKey: String,
        amount: Int,
        awardedAtMillis: Long,
        retroactive: Boolean
    ): Boolean {
        val entry = XpLedgerEntity(
            source = source,
            eventKey = eventKey,
            xpAmount = amount,
            awardedAtMillis = awardedAtMillis,
            retroactive = retroactive
        )
        val rowId = dao.insertLedgerEntry(entry)
        return rowId != -1L
    }

    override suspend fun hasLedgerEntry(source: String, eventKey: String): Boolean =
        dao.findLedgerEntry(source, eventKey) != null

    override suspend fun setRankState(
        totalXp: Long,
        currentRank: Rank?,
        lastPromotedAtMillis: Long?,
        isUnranked: Boolean
    ) {
        val entity = RankStateEntity(
            id = 1L,
            totalXp = totalXp,
            currentRank = currentRank?.name ?: "UNRANKED",
            lastPromotedAtMillis = lastPromotedAtMillis,
            isUnranked = isUnranked
        )
        dao.upsertRankState(entity)
    }

    override suspend fun getRankStateSnapshot(): RankState {
        val entity = dao.getRankState()
        return entity.toDomain(entity?.totalXp ?: 0L)
    }

    override suspend fun setAchievementProgress(achievementId: String, progress: Long) {
        dao.updateAchievementProgress(achievementId, progress)
    }

    override suspend fun unlockAchievement(
        achievementId: String,
        unlockedAtMillis: Long,
        progress: Long
    ) {
        dao.unlockAchievement(achievementId, unlockedAtMillis, progress)
    }

    override suspend fun getAchievementProgressSnapshot(id: String): AchievementProgress? =
        dao.getAchievementState(id)?.toDomain()

    override suspend fun getGoalDayIsoDates(): List<String> = dao.getGoalDayIsoDates()

    override suspend fun getPrLedgerEntries(): List<XpLedgerEntity> = dao.getPrLedgerEntries()
}

// ----- Mappers -----

/**
 * Map RankStateEntity -> RankState. D-11: if the row is null OR isUnranked = true,
 * the Overview strip shows Unranked; otherwise we resolve the Ranked data.
 */
private fun RankStateEntity?.toDomain(totalXpFromLedger: Long): RankState {
    if (this == null || this.isUnranked) return RankState.Unranked
    val rank = runCatching { Rank.valueOf(this.currentRank) }.getOrNull() ?: Rank.SILVER
    val next = RankLadder.nextRank(rank)
    return RankState.Ranked(
        currentRank = rank,
        totalXp = totalXpFromLedger,
        currentRankThreshold = RankLadder.thresholdFor(rank),
        nextRank = next,
        nextRankThreshold = next?.let { RankLadder.thresholdFor(it) },
        lastPromotedAtMillis = this.lastPromotedAtMillis
    )
}

/** Join achievement_state row with its AchievementCatalog definition. */
private fun AchievementStateEntity.toDomain(): AchievementProgress? {
    val def: AchievementDef = AchievementCatalog.findById(this.achievementId) ?: return null
    return AchievementProgress(
        def = def,
        currentProgress = this.currentProgress,
        unlockedAtMillis = this.unlockedAtMillis
    )
}

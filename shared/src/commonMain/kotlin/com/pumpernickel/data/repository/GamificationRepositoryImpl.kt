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
import com.pumpernickel.domain.gamification.XpLedgerRecord
import com.pumpernickel.domain.repository.GamificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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

    override suspend fun getPrLedgerEntries(): List<XpLedgerRecord> =
        dao.getPrLedgerEntries().map { it.toRecord() }
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

/**
 * Plan 20-08 (Smell 3 closure): project the Room ledger row into the engine-
 * facing domain record so `GamificationRepository.getPrLedgerEntries` no
 * longer leaks `XpLedgerEntity`.
 */
private fun XpLedgerEntity.toRecord(): XpLedgerRecord = XpLedgerRecord(
    source = source,
    eventKey = eventKey,
    xpAmount = xpAmount,
    awardedAtMillis = awardedAtMillis,
    retroactive = retroactive
)

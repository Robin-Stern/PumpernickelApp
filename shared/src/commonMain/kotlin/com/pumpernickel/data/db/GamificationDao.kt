package com.pumpernickel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GamificationDao {

    // ----- XP ledger -----
    /**
     * Inserts a ledger row. Returns the new row id on success, or -1L if the
     * unique (source, eventKey) index fires (dedupe). Engine treats -1L as
     * "already awarded, skip all downstream effects."
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerEntry(entry: XpLedgerEntity): Long

    @Query("SELECT * FROM xp_ledger WHERE source = :src AND eventKey = :key LIMIT 1")
    suspend fun findLedgerEntry(src: String, key: String): XpLedgerEntity?

    @Query("SELECT COALESCE(SUM(xpAmount), 0) FROM xp_ledger")
    fun totalXpFlow(): Flow<Long>

    @Query("SELECT * FROM xp_ledger ORDER BY awardedAtMillis DESC")
    fun allEntriesFlow(): Flow<List<XpLedgerEntity>>

    // ----- Blocker 3: nutrition-streak derivation from ledger -----
    /**
     * Returns the list of ISO dates (YYYY-MM-DD) for which a goal-day XP row
     * exists. The engine + walker convert each to epochDay (via LocalDate.parse
     * on commonMain) and feed it to StreakCalculator. Ordered ASC so retroactive
     * replay can walk in chronological order.
     *
     * Why ISO dates, not parsed numbers: the ledger's eventKey encodes the ISO
     * date as "goalday:YYYY-MM-DD" — we extract via substr rather than storing
     * a separate column (which would need a schema bump).
     */
    @Query(
        "SELECT substr(eventKey, 9) FROM xp_ledger " +
        "WHERE source = 'nutrition_goal_day' AND eventKey LIKE 'goalday:%' " +
        "ORDER BY awardedAtMillis ASC"
    )
    suspend fun getGoalDayIsoDates(): List<String>

    // ----- Blocker 4: PR-hunter snapshot derivation from ledger -----
    /**
     * All PR-award rows in chronological order. Engine parses each eventKey
     * via EventKeys.parsePr to extract (exerciseId, workoutId) and computes:
     *   - totalPrsSet = size
     *   - distinctExercisesWithPr = distinct(exerciseId)
     *   - bestPrsInSingleSession = max(count grouped by workoutId)
     */
    @Query("SELECT * FROM xp_ledger WHERE source = 'pr' ORDER BY awardedAtMillis ASC")
    suspend fun getPrLedgerEntries(): List<XpLedgerEntity>

    /**
     * PR-award rows for a specific workoutId (used when the engine wants to
     * know "how many PRs in THIS workout" without scanning the whole ledger).
     * Matches eventKey pattern "pr:<exerciseId>:<workoutId>" via a LIKE suffix.
     */
    @Query("SELECT * FROM xp_ledger WHERE source = 'pr' AND eventKey LIKE '%:' || :workoutId")
    suspend fun getPrLedgerEntriesForWorkout(workoutId: Long): List<XpLedgerEntity>

    // ----- Rank state (singleton) -----
    @Query("SELECT * FROM rank_state WHERE id = 1")
    fun rankStateFlow(): Flow<RankStateEntity?>

    @Query("SELECT * FROM rank_state WHERE id = 1")
    suspend fun getRankState(): RankStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRankState(state: RankStateEntity)

    // ----- Achievement state -----
    @Query("SELECT * FROM achievement_state")
    fun achievementStateFlow(): Flow<List<AchievementStateEntity>>

    @Query("SELECT * FROM achievement_state WHERE achievementId = :id LIMIT 1")
    suspend fun getAchievementState(id: String): AchievementStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievementStateIfMissing(state: AchievementStateEntity): Long

    @Query("UPDATE achievement_state SET currentProgress = :progress WHERE achievementId = :id")
    suspend fun updateAchievementProgress(id: String, progress: Long)

    @Query("UPDATE achievement_state SET unlockedAtMillis = :ts, currentProgress = :progress WHERE achievementId = :id")
    suspend fun unlockAchievement(id: String, ts: Long, progress: Long)

    // ----- Transactional bulk apply (used by RetroactiveWalker — D-13) -----
    /**
     * Applies a batch of ledger entries + optional rank/achievement updates inside
     * a single Room transaction. Dedupe via the unique index on xp_ledger means
     * partial re-runs are safe after rollback.
     */
    @Transaction
    suspend fun applyRetroactive(
        entries: List<XpLedgerEntity>,
        updatedRankState: RankStateEntity,
        unlockedAchievements: List<AchievementStateEntity>
    ) {
        entries.forEach { insertLedgerEntry(it) }
        upsertRankState(updatedRankState)
        unlockedAchievements.forEach { state ->
            unlockAchievement(
                id = state.achievementId,
                ts = state.unlockedAtMillis ?: 0L,
                progress = state.currentProgress
            )
        }
    }
}

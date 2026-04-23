package com.pumpernickel.presentation.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.domain.gamification.RankLadder
import com.pumpernickel.domain.gamification.RankState
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * UI state for the Phase 15.1 rank ladder screen. Pure data, Kotlin types
 * only — no platform-specific types. Locale-aware XP formatting happens on
 * the view layer (Compose / SwiftUI), not here (D-151-18).
 *
 * Derived purely from GamificationRepository.rankState + .totalXp per
 * D-151-07. Initial value is `isLoading = true` with empty rows; the stateIn
 * pipeline replaces it with a built state on first emission.
 */
data class RankLadderUiState(
    val rows: List<RankRow> = emptyList(),
    val totalXp: Long = 0L,
    val currentRank: Rank? = null,         // null when Unranked
    val isUnranked: Boolean = true,
    val isLoading: Boolean = true
)

/**
 * One row per tier in the 10-rank CSGO ladder, ordered by Rank.values()
 * (SILVER first → GLOBAL_ELITE last). `xpToReach` is null for PASSED and
 * CURRENT rows; for LOCKED rows it carries `max(0, threshold - totalXp)`.
 * The Unranked SILVER row is a special case: LOCKED but with
 * `xpToReach = 0` so the view can render the D-11 "First workout unlocks"
 * hint instead of a stale "X XP to unlock" label.
 */
data class RankRow(
    val rank: Rank,
    val threshold: Long,
    val status: RankRowStatus,
    val xpToReach: Long?
)

enum class RankRowStatus { PASSED, CURRENT, LOCKED }

/**
 * Shared VM for the Phase 15.1 rank ladder surface. Owns ONLY the rank
 * ladder state — the achievement half of the browser reuses
 * AchievementGalleryViewModel (Phase 15-09) per D-151-03 / D-151-05.
 *
 * Android consumers: obtain via `koinViewModel<RanksAndAchievementsViewModel>()`.
 * iOS consumers: obtain via `RanksAndAchievementsKoinHelper().getRanksAndAchievementsViewModel()`.
 */
class RanksAndAchievementsViewModel(
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    /**
     * D-151-05 / D-151-08: combined rank-state + total-XP flow. Both upstream
     * flows are already exposed by GamificationRepository. Uses
     * `SharingStarted.WhileSubscribed(5_000)` to match the project-wide
     * 5-second keep-alive (AchievementGalleryViewModel and GamificationViewModel
     * both use this value).
     */
    @NativeCoroutinesState
    val rankLadderState: StateFlow<RankLadderUiState> = combine(
        gamificationRepository.rankState,
        gamificationRepository.totalXp
    ) { rankState, totalXp -> buildUiState(rankState, totalXp) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RankLadderUiState(isLoading = true)
        )

    /**
     * Pure derivation — D-151-07 rules:
     *   - Unranked → all rows LOCKED. SILVER gets xpToReach=0 (D-11 first-workout hint);
     *     other rows get xpToReach = threshold.
     *   - Ranked → rows below currentRank.ordinal = PASSED (xpToReach=null),
     *     row at currentRank.ordinal = CURRENT (xpToReach=null),
     *     rows above = LOCKED with xpToReach = max(0, threshold - totalXp).
     * Rows ordered by Rank.values() — SILVER first → GLOBAL_ELITE last.
     */
    private fun buildUiState(rankState: RankState, totalXp: Long): RankLadderUiState {
        val allRanks = Rank.values().toList()
        return when (rankState) {
            is RankState.Unranked -> {
                val rows = allRanks.map { rank ->
                    val threshold = RankLadder.thresholdFor(rank)
                    val xpToReach = if (rank == Rank.SILVER) 0L else threshold
                    RankRow(
                        rank = rank,
                        threshold = threshold,
                        status = RankRowStatus.LOCKED,
                        xpToReach = xpToReach
                    )
                }
                RankLadderUiState(
                    rows = rows,
                    totalXp = totalXp,
                    currentRank = null,
                    isUnranked = true,
                    isLoading = false
                )
            }
            is RankState.Ranked -> {
                val currentOrdinal = rankState.currentRank.ordinal
                val rows = allRanks.map { rank ->
                    val threshold = RankLadder.thresholdFor(rank)
                    val status = when {
                        rank.ordinal < currentOrdinal -> RankRowStatus.PASSED
                        rank.ordinal == currentOrdinal -> RankRowStatus.CURRENT
                        else -> RankRowStatus.LOCKED
                    }
                    val xpToReach = if (status == RankRowStatus.LOCKED) {
                        (threshold - totalXp).coerceAtLeast(0L)
                    } else null
                    RankRow(
                        rank = rank,
                        threshold = threshold,
                        status = status,
                        xpToReach = xpToReach
                    )
                }
                RankLadderUiState(
                    rows = rows,
                    totalXp = totalXp,
                    currentRank = rankState.currentRank,
                    isUnranked = false,
                    isLoading = false
                )
            }
        }
    }
}

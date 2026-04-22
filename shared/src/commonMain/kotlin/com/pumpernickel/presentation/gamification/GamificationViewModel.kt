package com.pumpernickel.presentation.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.domain.gamification.GamificationEngine
import com.pumpernickel.domain.gamification.RankState
import com.pumpernickel.domain.gamification.UnlockEvent
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Shared ViewModel consumed by BOTH the Overview rank strip (D-18) and the
 * root-level unlock-modal host (D-19 + D-20). Exposing these flows from a
 * single VM means a rank promotion that updates `rankState` is observed by
 * the Overview strip simultaneously with the modal firing from
 * `unlockEvents` — no divergence possible.
 *
 * Android consumers: obtain via `koinViewModel<GamificationViewModel>()`.
 * iOS consumers: obtain via `GamificationUiKoinHelper().getGamificationViewModel()`.
 */
class GamificationViewModel(
    private val gamificationRepository: GamificationRepository,
    private val gamificationEngine: GamificationEngine
) : ViewModel() {

    /**
     * Current rank + XP state for the Overview strip. Defaults to Unranked
     * so the fresh-install / not-yet-walked case shows the correct D-11
     * copy ("Unranked — complete a workout to unlock Silver") without a
     * brief flash of stale data.
     */
    @NativeCoroutinesState
    val rankState: StateFlow<RankState> = gamificationRepository
        .rankState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            RankState.Unranked
        )

    /**
     * One-shot unlock events for the modal queue. Not a StateFlow — each
     * event fires exactly once when the engine detects a promotion or
     * achievement tier unlock. The UI (MainScreen / MainTabView) buffers
     * these into a queue so multiple unlocks from one save show
     * sequentially (D-20).
     */
    @NativeCoroutines
    val unlockEvents: SharedFlow<UnlockEvent> = gamificationEngine.unlockEvents
}

package com.pumpernickel.presentation.progresspic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.db.GamificationDao
import com.pumpernickel.data.db.NutritionDao
import com.pumpernickel.data.repository.ProgressPictureRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.domain.gamification.NutritionGoalDayPolicy
import com.pumpernickel.domain.progresspic.ProgressGalleryTile
import com.pumpernickel.domain.model.NutritionGoals
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Shared VM for the gallery surface. One tile per workout that has at least one
 * photo (D-17-11). Tile carries enriched stats (volume kg, PR count, goal-day
 * flag — D-17-12); empty stats (zero PR, not a goal-day) are surfaced as flags
 * but the UI side drops them (D-17-12 final paragraph).
 *
 * **Auth lives in the viewer VM, NOT here.** Gallery taps simply emit
 * `NavEvent.OpenViewer(workoutId)`; the actual `BiometricGate.requestUnlock`
 * call happens inside `ProgressViewerViewModel.requestUnlock()` (the viewer)
 * on first composition. This keeps T-BIOMETRIC-BYPASS cleanly mitigated —
 * there is exactly ONE place auth state lives:
 * `ProgressViewerViewModel.unlockedWorkoutId`.
 *
 * **Enrichment (Option A pinned at 17-02):** the repository emits placeholder
 * `prCount = 0` / `isGoalDay = false`. This VM enriches per-tile by:
 *   - reading the per-workout PR ledger via `GamificationDao.getPrLedgerEntriesForWorkout`
 *   - computing whether the workout's calendar day was a goal-day via
 *     `NutritionGoalDayPolicy.isGoalDay(entries, goals)` — entries come from
 *     `NutritionDao.getAllEntries()` filtered by ISO date; goals come from
 *     `SettingsRepository.nutritionGoals.first()`. This mirrors the live engine
 *     path in `GamificationEngine.evaluateGoalDay` (lines 61-70).
 *
 * **N+1 note:** the per-tile DAO call is a known hot-path cost. The gallery
 * is bounded by photographed workouts (typically <50 in prototype scope) so
 * the cost is acceptable; revisit with a single aggregating query if the
 * gallery ever grows large.
 *
 * **Deviation (Rule 3) from plan ctor pin:** the plan pins exactly 3 ctor
 * params (repository, gamificationDao, nutritionGoalDayPolicy) but assumes a
 * `nutritionGoalDayPolicy.isGoalDay(workoutDate)` per-date overload that does
 * not exist on the actual `NutritionGoalDayPolicy` object — the real signature
 * is `isGoalDay(entries: List<ConsumptionEntryEntity>, goals: NutritionGoals)`.
 * Adding `nutritionDao` + `settingsRepository` as the minimum dependencies
 * required to call the real API is the smallest correct fix; documented in
 * the 17-06 SUMMARY.
 */
class ProgressGalleryViewModel(
    private val repository: ProgressPictureRepository,
    private val gamificationDao: GamificationDao,
    private val nutritionGoalDayPolicy: NutritionGoalDayPolicy,
    private val nutritionDao: NutritionDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @NativeCoroutinesState
    val uiState: StateFlow<GalleryUiState> = combine(
        repository.observeGalleryTiles(),
        // REVIEW M-03 — compose Flows instead of suspending on `first()`.
        // `onStart` emits a default so the gallery is never blocked on a
        // cold-start DataStore read; the real goals replace the default
        // as soon as the underlying Flow emits.
        settingsRepository.nutritionGoals.onStart { emit(NutritionGoals()) },
        // Reactive entries Flow (NutritionDao.observeAllEntries — added in M-03)
        // means the goal-day chip flips live when the user logs food, instead
        // of only re-evaluating when the gallery list itself changes.
        nutritionDao.observeAllEntries()
    ) { rawTiles, goals, allEntries ->
        rawTiles.map { tile ->
            val prCount = gamificationDao.getPrLedgerEntriesForWorkout(tile.workoutId).size
            val workoutIsoDate = Instant.fromEpochMilliseconds(tile.startTimeMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
            val entriesForDate = allEntries.filter { entry ->
                Instant.fromEpochMilliseconds(entry.timestampMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
                    .toString() == workoutIsoDate
            }
            val isGoalDay = nutritionGoalDayPolicy.isGoalDay(entriesForDate, goals)
            tile.copy(prCount = prCount, isGoalDay = isGoalDay)
        }
    }
        .map { tiles -> GalleryUiState(tiles = tiles, isLoading = false) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GalleryUiState(tiles = emptyList(), isLoading = true)
        )

    private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

    @NativeCoroutines
    val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

    /**
     * Emits `NavEvent.OpenViewer(workoutId)` unconditionally — the actual
     * biometric prompt fires inside `ProgressViewerViewModel.requestUnlock()`
     * on viewer first composition (D-17-14 per-tile every-tap). This keeps the
     * single auth gate (`ProgressViewerViewModel.unlockedWorkoutId`) as the
     * sole place auth state lives — T-BIOMETRIC-BYPASS mitigation.
     */
    fun onTileTapped(workoutId: Long) {
        viewModelScope.launch {
            _navEvents.emit(NavEvent.OpenViewer(workoutId))
        }
    }
}

data class GalleryUiState(
    val tiles: List<ProgressGalleryTile>,
    val isLoading: Boolean
)

sealed class NavEvent {
    data class OpenViewer(val workoutId: Long) : NavEvent()
}

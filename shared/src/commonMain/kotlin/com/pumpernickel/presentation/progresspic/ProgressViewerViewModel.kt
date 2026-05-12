package com.pumpernickel.presentation.progresspic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ProgressPictureRepository
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.ProgressPicture
import com.pumpernickel.domain.progresspic.UnlockResult
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared VM for the photo viewer (one workout's carousel).
 *
 * **T-BIOMETRIC-BYPASS mitigation:** the `unlockedWorkoutId: Long?` field is
 * the SINGLE explicit gate state in this VM. It defaults to `null`. It only
 * becomes the workoutId on a fresh `BiometricGate.requestUnlock` returning
 * `UnlockResult.Success` (which already covers D-17-16's no-credential case
 * via the actual class — Success is emitted without challenge). Recompose
 * reads from this state. Closing the viewer calls `relock()` which resets
 * `unlockedWorkoutId = null` so the tile re-blurs in the grid (D-17-13 /
 * D-17-14).
 *
 * Inside the viewer, paging across photos does NOT call requestUnlock again
 * (D-17-14: per-tile, NOT per-photo). The carousel reads from `photos`
 * regardless of paging position.
 */
class ProgressViewerViewModel(
    private val workoutId: Long,
    private val repository: ProgressPictureRepository,
    private val biometricGate: BiometricGate
) : ViewModel() {

    private val photosFlow = repository.observePicturesForWorkout(workoutId)
    private val _unlockedWorkoutId = MutableStateFlow<Long?>(null)
    private val _busy = MutableStateFlow(false)

    /**
     * Exposed for state observers / tests. The screen reads `uiState.unlockedWorkoutId`
     * but anyone wanting to observe just the gate state can subscribe here.
     */
    val unlockedWorkoutId: StateFlow<Long?> = _unlockedWorkoutId

    @NativeCoroutinesState
    val uiState: StateFlow<ViewerUiState> = combine(
        photosFlow,
        _unlockedWorkoutId,
        _busy
    ) { photos, unlocked, busy ->
        ViewerUiState(
            photos = photos,
            unlockedWorkoutId = unlocked,
            busy = busy
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ViewerUiState(photos = emptyList(), unlockedWorkoutId = null, busy = false)
    )

    /**
     * Called by the host (entry from gallery's NavEvent collector). Triggers
     * fresh OS auth. On Success, sets unlockedWorkoutId so the photos render
     * un-blurred. On any other outcome (Cancelled / Failed / Error), keeps
     * unlockedWorkoutId = null and the host shows the still-blurred placeholder.
     */
    fun requestUnlock() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val reason = "Fortschrittsbild entsperren"
                when (biometricGate.requestUnlock(reason)) {
                    is UnlockResult.Success -> _unlockedWorkoutId.value = workoutId
                    else -> {
                        // D-17-17 silent failure; unlockedWorkoutId stays null.
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * D-17-13 / D-17-14: tile re-blurs when viewer closes. Host calls this in
     * its onDispose / popBackStack handler.
     */
    fun relock() {
        _unlockedWorkoutId.value = null
    }

    /**
     * Optional per-photo delete (CONTEXT line 102). Cascades through the
     * repository's `deletePicture` which removes both row and file
     * (T-DELETE-ORPHAN — already mitigated in 17-02).
     */
    fun deletePhoto(picture: ProgressPicture) {
        viewModelScope.launch {
            repository.deletePicture(picture.id, picture.relativePath)
        }
    }
}

data class ViewerUiState(
    val photos: List<ProgressPicture>,
    val unlockedWorkoutId: Long?,
    val busy: Boolean
)

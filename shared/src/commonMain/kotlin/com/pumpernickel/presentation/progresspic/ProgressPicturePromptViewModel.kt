package com.pumpernickel.presentation.progresspic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.repository.ProgressPictureRepository
import com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncher
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Shared VM for the post-workout photo prompt. Driven by a `workoutId` injected
 * via Koin's `parametersOf(workoutId)`.
 *
 * Per D-17-01 the prompt is non-blocking — the host screen's Done button stays
 * enabled. This VM never blocks the workout-completion flow; capture errors
 * surface only via the `error` field on the UI state, which the host renders
 * inline (no toast, no error dialog).
 *
 * Per D-17-04 the only entry point is via `parametersOf(workoutId)` at the
 * moment the Finished screen renders — no `loadHistorical` / retro-add path.
 */
class ProgressPicturePromptViewModel(
    private val workoutId: Long,
    private val repository: ProgressPictureRepository,
    private val launcher: PhotoCaptureLauncher
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _dismissed = MutableStateFlow(false)

    @NativeCoroutinesState
    val uiState: StateFlow<PromptUiState> = combine(
        repository.observePhotoCount(workoutId),
        _busy,
        _error,
        _dismissed
    ) { photoCount, busy, error, dismissed ->
        PromptUiState(
            workoutId = workoutId,
            photoCount = photoCount,
            busy = busy,
            error = error,
            dismissed = dismissed
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PromptUiState(
            workoutId = workoutId,
            photoCount = 0,
            busy = false,
            error = null,
            dismissed = false
        )
    )

    fun onTakePhotoClick() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val bytes = launcher.captureFromCamera()
                if (bytes != null) {
                    saveBytes(bytes)
                }
                // null = user cancelled; not an error per D-17-17 framing.
            } catch (t: Throwable) {
                _error.value = t.message ?: "Capture failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun onPickFromLibraryClick() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val bytes = launcher.pickFromLibrary()
                if (bytes != null) {
                    saveBytes(bytes)
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Pick failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * D-17-01 / D-17-04 — Skip is a no-op that just dismisses the card.
     * The host screen's Done button stays enabled regardless; this only
     * hides the prompt UI for the rest of the Finished screen lifetime.
     */
    fun onSkipClick() {
        _dismissed.value = true
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun saveBytes(bytes: ByteArray) {
        val id = Uuid.random().toString()
        val now = Clock.System.now().toEpochMilliseconds()
        // sortOrder = current photoCount; new photo lands at the end of the
        // existing list. Read fresh from the StateFlow's current value so the
        // sort key tracks live state without an extra DAO call.
        val sortOrder = uiState.value.photoCount
        repository.savePicture(
            id = id,
            workoutId = workoutId,
            bytes = bytes,
            capturedAtMillis = now,
            sortOrder = sortOrder
        )
        // Live photoCount Flow will tick automatically via the repository's
        // observePhotoCount; no manual update needed.
    }
}

data class PromptUiState(
    val workoutId: Long,
    val photoCount: Int,
    val busy: Boolean,
    val error: String?,
    val dismissed: Boolean
) {
    /**
     * D-17-02: header copy switches once the user has saved at least one photo.
     */
    val showAddAnother: Boolean get() = photoCount > 0
}

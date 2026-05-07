package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.ai.WorkoutAiForm
import com.pumpernickel.domain.ai.WorkoutAiPreview
import com.pumpernickel.domain.ai.WorkoutAiSplit
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.domain.model.MuscleGroup
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * D-18-07 / D-18-08 / D-18-12 / D-18-13 / D-18-16 — F6 Workout AI flow VM.
 *
 * State machine:
 * - NoKey (when SecureKeyStore.readApiKey() == null at init)
 * - Form (default, edits muscles/count/split)
 * - Generating (in-flight LLM call; Cancel button visible)
 * - Preview (LLM returned a valid response; preview sheet shown; Save / Discard)
 * - Error (terminal until retry / dismiss; carries an AiError for per-class copy)
 * - Saved (commit() succeeded; templateIds returned for nav back to TemplateList)
 */
class WorkoutAiViewModel(
    private val useCase: WorkoutAiUseCase,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutAiUiState>(
        WorkoutAiUiState.Form(
            targetMuscles = emptyList(),
            exerciseCount = 5,                    // Claude's discretion (D-18-03 — range 3-8)
            splitStyle = WorkoutAiSplit.NONE
        )
    )

    @NativeCoroutinesState
    val uiState: StateFlow<WorkoutAiUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            if (secureKeyStore.readApiKey() == null) {
                _uiState.value = WorkoutAiUiState.NoKey
            }
        }
    }

    fun onMusclesChanged(muscles: List<MuscleGroup>) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(targetMuscles = muscles)
    }

    fun onExerciseCountChanged(count: Int) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(exerciseCount = count.coerceIn(1, 12))
    }

    fun onSplitStyleChanged(split: WorkoutAiSplit) {
        val current = _uiState.value as? WorkoutAiUiState.Form ?: return
        _uiState.value = current.copy(splitStyle = split)
    }

    fun generate() {
        val form = (_uiState.value as? WorkoutAiUiState.Form) ?: return
        if (form.targetMuscles.isEmpty()) return  // form-side validation; UI also disables button

        _uiState.value = WorkoutAiUiState.Generating(skeletonRowCount = form.exerciseCount)
        generationJob = viewModelScope.launch {
            try {
                val preview = useCase.invoke(
                    WorkoutAiForm(
                        targetMuscles = form.targetMuscles,
                        exerciseCount = form.exerciseCount,
                        splitStyle = form.splitStyle
                    )
                )
                _uiState.value = WorkoutAiUiState.Preview(preview, originatingForm = form)
            } catch (ce: CancellationException) {
                // User-cancel — return to Form, no error UI per D-18-16.
                _uiState.value = form
                throw ce
            } catch (ai: AiError) {
                _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = form)
            } catch (t: Throwable) {
                _uiState.value = WorkoutAiUiState.Error(
                    AiError.fromThrowable(t),
                    originatingForm = form
                )
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
    }

    fun save() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        viewModelScope.launch {
            try {
                val ids = useCase.commit(preview.preview)
                _uiState.value = WorkoutAiUiState.Saved(templateIds = ids)
            } catch (ce: CancellationException) {
                throw ce
            } catch (ai: AiError) {
                _uiState.value = WorkoutAiUiState.Error(ai, originatingForm = preview.originatingForm)
            } catch (t: Throwable) {
                _uiState.value = WorkoutAiUiState.Error(
                    AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                    originatingForm = preview.originatingForm
                )
            }
        }
    }

    fun discardPreview() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        _uiState.value = preview.originatingForm
    }

    fun retryFromError() {
        val error = (_uiState.value as? WorkoutAiUiState.Error) ?: return
        _uiState.value = error.originatingForm
    }
}

/**
 * Sealed UiState — exported to Swift via the KMPNativeCoroutines flat-export
 * convention (Phase 15 STATE.md). Preview/Error carry the originating Form
 * so the user can return to it without re-entering muscles/count/split.
 */
sealed class WorkoutAiUiState {
    object NoKey : WorkoutAiUiState()

    data class Form(
        val targetMuscles: List<MuscleGroup>,
        val exerciseCount: Int,
        val splitStyle: WorkoutAiSplit
    ) : WorkoutAiUiState()

    data class Generating(val skeletonRowCount: Int) : WorkoutAiUiState()

    data class Preview(
        val preview: WorkoutAiPreview,
        val originatingForm: Form
    ) : WorkoutAiUiState()

    data class Error(
        val error: AiError,
        val originatingForm: Form
    ) : WorkoutAiUiState()

    data class Saved(val templateIds: List<Long>) : WorkoutAiUiState()
}

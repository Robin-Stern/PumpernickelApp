package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.AiGenerationManager
import com.pumpernickel.domain.ai.AiGenerationState
import com.pumpernickel.domain.ai.AiType
import com.pumpernickel.domain.ai.ApiKeyState
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.ai.StreamingText
import com.pumpernickel.domain.ai.WorkoutAiForm
import com.pumpernickel.domain.ai.WorkoutAiPreview
import com.pumpernickel.domain.ai.WorkoutAiSplit
import com.pumpernickel.domain.ai.WorkoutAiUseCase
import com.pumpernickel.domain.model.MuscleGroup
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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
    private val secureKeyStore: SecureKeyStore,
    private val generationManager: AiGenerationManager
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

    @NativeCoroutinesState
    val streamingText: StateFlow<StreamingText> = generationManager.streamingText

    /**
     * 260518-eny — one-shot dismiss signal for iOS-View after successful `save()`.
     * Replaces the previous "persistent `Saved` UI-state -> auto-dismiss" pattern,
     * which left the screen "burned" on re-entry (Saved state survived navigation
     * because the Koin-resolved VM instance is effectively long-lived on iOS).
     * Android continues to react to the (very short-lived) `Saved` UI-state in its
     * existing `LaunchedEffect(uiState)`-based pop logic.
     */
    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @NativeCoroutines
    val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

    private val defaultForm = WorkoutAiUiState.Form(
        targetMuscles = emptyList(),
        exerciseCount = 5,
        splitStyle = WorkoutAiSplit.NONE
    )

    init {
        // Bootstrap ApiKeyState from Keychain on first init (read updates the flow).
        viewModelScope.launch { secureKeyStore.readApiKey() }
        // Live-react to key changes
        viewModelScope.launch {
            ApiKeyState.configured.collect { hasKey ->
                val current = _uiState.value
                when {
                    !hasKey && current !is WorkoutAiUiState.Generating
                            && current !is WorkoutAiUiState.Preview
                            && current !is WorkoutAiUiState.Saved -> {
                        _uiState.value = WorkoutAiUiState.NoKey
                    }
                    hasKey && current is WorkoutAiUiState.NoKey -> {
                        _uiState.value = defaultForm
                    }
                    else -> {}
                }
            }
        }

        // Observe global generation state
        viewModelScope.launch {
            generationManager.state.collect { genState ->
                val current = _uiState.value
                when (genState) {
                    is AiGenerationState.Idle -> {
                        if (current is WorkoutAiUiState.Generating) {
                            _uiState.value = defaultForm
                        }
                    }
                    is AiGenerationState.Generating -> {
                        if (genState.type == AiType.WORKOUT) {
                            val form = genState.originatingData as? WorkoutAiForm
                            val rows = form?.exerciseCount ?: (current as? WorkoutAiUiState.Form)?.exerciseCount ?: 5
                            _uiState.value = WorkoutAiUiState.Generating(skeletonRowCount = rows)
                        }
                    }
                    is AiGenerationState.Success -> {
                        if (genState.type == AiType.WORKOUT) {
                            val preview = genState.preview as WorkoutAiPreview
                            val form = genState.originatingData as? WorkoutAiForm
                            _uiState.value = WorkoutAiUiState.Preview(
                                preview = preview,
                                originatingForm = if (form != null) {
                                    WorkoutAiUiState.Form(form.targetMuscles, form.exerciseCount, form.splitStyle)
                                } else {
                                    (current as? WorkoutAiUiState.Preview)?.originatingForm ?: defaultForm
                                }
                            )
                        }
                    }
                    is AiGenerationState.Error -> {
                        if (genState.type == AiType.WORKOUT) {
                            val error = if (genState.exception is AiError) genState.exception else AiError.fromThrowable(genState.exception)
                            val form = genState.originatingData as? WorkoutAiForm
                            _uiState.value = WorkoutAiUiState.Error(
                                error = error,
                                originatingForm = if (form != null) {
                                    WorkoutAiUiState.Form(form.targetMuscles, form.exerciseCount, form.splitStyle)
                                } else {
                                    (current as? WorkoutAiUiState.Error)?.originatingForm ?: defaultForm
                                }
                            )
                        }
                    }
                }
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
        if (form.targetMuscles.isEmpty()) return

        val started = generationManager.startWorkoutGeneration(
            WorkoutAiForm(
                targetMuscles = form.targetMuscles,
                exerciseCount = form.exerciseCount,
                splitStyle = form.splitStyle
            )
        )
        if (!started) {
            _uiState.value = WorkoutAiUiState.Error(
                error = AiError.SchemaInvalid("Bereits eine Generierung aktiv. Bitte warten."),
                originatingForm = form
            )
        }
    }

    fun cancel() {
        generationManager.clear()
    }

    fun save() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        viewModelScope.launch {
            try {
                val ids = useCase.commit(preview.preview)
                generationManager.clear()
                // Android: LaunchedEffect(uiState) sees `Saved` once and pops.
                _uiState.value = WorkoutAiUiState.Saved(templateIds = ids)
                // iOS: one-shot SharedFlow triggers dismiss() in the View.
                _savedEvent.tryEmit(Unit)
                // After both observers had a chance to react, fall back to the
                // default Form so a re-entry into this screen does NOT replay the
                // Saved state (260518-eny — fixes iOS "burned screen" bug).
                yield()
                _uiState.value = defaultForm
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

    /**
     * 260518-eny — idempotent reset called from iOS `.onAppear` to guarantee the
     * AI workout screen always shows a fresh Form view, even if the long-lived VM
     * instance carries a stale `Saved` / `Preview` / `Error` state from a prior
     * navigation cycle. Does NOT cancel in-flight generations (would break the
     * Phase-19 BackgroundTaskManager flow); the `Generating` guard preserves that.
     */
    fun reset() {
        if (_uiState.value is WorkoutAiUiState.Generating) return
        _uiState.value = defaultForm
    }

    fun discardPreview() {
        val preview = (_uiState.value as? WorkoutAiUiState.Preview) ?: return
        _uiState.value = preview.originatingForm
        generationManager.clear()
    }

    fun retryFromError() {
        val error = (_uiState.value as? WorkoutAiUiState.Error) ?: return
        _uiState.value = error.originatingForm
        generationManager.clear()
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

    /**
     * 260518-eny — As of this fix, `Saved` is only emitted for a single coroutine
     * tick inside `WorkoutAiViewModel.save()` to give Android's
     * `LaunchedEffect(uiState)`-based pop logic a chance to fire. The iOS View
     * uses the one-shot `WorkoutAiViewModel.savedEvent` SharedFlow instead and
     * defensively ignores the `Saved` UI-state (no `dismiss()` from `onAppear`).
     * The data class is retained for shared-framework binary stability.
     */
    data class Saved(val templateIds: List<Long>) : WorkoutAiUiState()
}

package com.pumpernickel.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.ApiKeyState
import com.pumpernickel.domain.ai.RecipeAiPreview
import com.pumpernickel.domain.ai.RecipeAiUseCase
import com.pumpernickel.domain.ai.RemainingMacros
import com.pumpernickel.domain.ai.SecureKeyStore
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * D-18-04 / D-18-08 / D-18-12 / D-18-16 — F8 Recipe AI flow VM.
 *
 * State machine:
 * - Loading (transient init state while checking key + remaining)
 * - NoKey (when SecureKeyStore.readApiKey() == null at init)
 * - RemainingExhausted (when remaining kcal <= 100)
 * - Form (default — F8 has no required form fields per CONTEXT.md; tap "Generieren" to fire)
 * - Generating (in-flight LLM call; Cancel button visible)
 * - Preview (LLM returned a valid response; preview sheet shown)
 * - Error (per-class user copy)
 * - Saved (commit succeeded; recipe is in user's collection)
 */
class RecipeAiViewModel(
    private val useCase: RecipeAiUseCase,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeAiUiState>(RecipeAiUiState.Loading)

    @NativeCoroutinesState
    val uiState: StateFlow<RecipeAiUiState> = _uiState.asStateFlow()

    /** See WorkoutAiViewModel.streamingText. */
    private val _streamingText = MutableStateFlow(StreamingText())
    @NativeCoroutinesState
    val streamingText: StateFlow<StreamingText> = _streamingText.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch { secureKeyStore.readApiKey() }
        viewModelScope.launch {
            ApiKeyState.configured.collect { hasKey ->
                val current = _uiState.value
                when {
                    !hasKey && current !is RecipeAiUiState.Generating
                            && current !is RecipeAiUiState.Preview
                            && current !is RecipeAiUiState.Saved -> {
                        _uiState.value = RecipeAiUiState.NoKey
                    }
                    hasKey && (current is RecipeAiUiState.NoKey
                            || current is RecipeAiUiState.Loading) -> {
                        // Re-enter Loading then refresh remaining macros to land in Form.
                        _uiState.value = RecipeAiUiState.Loading
                        refreshRemaining()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Re-reads today's totals + goals; transitions to RemainingExhausted or Form.
     * Called from init and from manual refresh in the UI.
     */
    fun refreshRemaining() {
        viewModelScope.launch {
            try {
                val remaining = useCase.computeRemaining()
                _uiState.value = if (remaining.isExhausted) {
                    RecipeAiUiState.RemainingExhausted(remaining)
                } else {
                    RecipeAiUiState.Form(remaining)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.SchemaInvalid("computeRemaining failed: ${t.message ?: "unknown"}"),
                    originatingRemaining = null
                )
            }
        }
    }

    fun generate() {
        val form = (_uiState.value as? RecipeAiUiState.Form) ?: return
        val remaining = form.remaining
        _uiState.value = RecipeAiUiState.Generating
        _streamingText.value = StreamingText()
        generationJob = viewModelScope.launch {
            try {
                val preview = useCase.invoke(remaining) { content, reasoning ->
                    _streamingText.value = StreamingText(content, reasoning)
                }
                _uiState.value = RecipeAiUiState.Preview(preview, originatingRemaining = remaining)
            } catch (ce: CancellationException) {
                _uiState.value = RecipeAiUiState.Form(remaining)
                throw ce
            } catch (ai: AiError) {
                _uiState.value = RecipeAiUiState.Error(ai, originatingRemaining = remaining)
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.fromThrowable(t),
                    originatingRemaining = remaining
                )
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
    }

    fun save() {
        val preview = (_uiState.value as? RecipeAiUiState.Preview) ?: return
        viewModelScope.launch {
            try {
                useCase.commit(preview.preview)
                _uiState.value = RecipeAiUiState.Saved
            } catch (ce: CancellationException) {
                throw ce
            } catch (ai: AiError) {
                _uiState.value = RecipeAiUiState.Error(ai, originatingRemaining = preview.originatingRemaining)
            } catch (t: Throwable) {
                _uiState.value = RecipeAiUiState.Error(
                    AiError.SchemaInvalid("Save failed: ${t.message ?: "unknown"}"),
                    originatingRemaining = preview.originatingRemaining
                )
            }
        }
    }

    fun discardPreview() {
        val preview = (_uiState.value as? RecipeAiUiState.Preview) ?: return
        _uiState.value = RecipeAiUiState.Form(preview.originatingRemaining)
    }

    /**
     * Called from the iOS view's `.onAppear` so the remaining-macros panel is
     * always fresh when the screen returns to foreground (after the user logged
     * food elsewhere). State-guarded: an in-flight Generating call or an open
     * Preview is preserved — only Form / RemainingExhausted / Error states are
     * re-derived from the latest day's totals.
     */
    fun onAppearRefresh() {
        when (_uiState.value) {
            is RecipeAiUiState.Form,
            is RecipeAiUiState.RemainingExhausted,
            is RecipeAiUiState.Error -> refreshRemaining()
            else -> {} // NoKey / Loading / Generating / Preview / Saved — leave alone
        }
    }

    fun retryFromError() {
        val error = (_uiState.value as? RecipeAiUiState.Error) ?: return
        if (error.originatingRemaining == null) {
            // computeRemaining failure path — re-run the refresh.
            refreshRemaining()
        } else {
            _uiState.value = RecipeAiUiState.Form(error.originatingRemaining)
        }
    }
}

sealed class RecipeAiUiState {
    object Loading : RecipeAiUiState()
    object NoKey : RecipeAiUiState()
    data class RemainingExhausted(val remaining: RemainingMacros) : RecipeAiUiState()
    data class Form(val remaining: RemainingMacros) : RecipeAiUiState()
    object Generating : RecipeAiUiState()
    data class Preview(val preview: RecipeAiPreview, val originatingRemaining: RemainingMacros) : RecipeAiUiState()
    data class Error(val error: AiError, val originatingRemaining: RemainingMacros?) : RecipeAiUiState()
    object Saved : RecipeAiUiState()
}

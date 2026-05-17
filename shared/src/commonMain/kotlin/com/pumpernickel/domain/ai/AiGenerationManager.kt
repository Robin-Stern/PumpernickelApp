package com.pumpernickel.domain.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Live LLM token stream — both ai-content (the answer) and reasoning (chain-of-thought, reasoning models only). */
data class StreamingText(val content: String = "", val reasoning: String = "")

/**
 * Singleton manager to orchestrate AI generation across the app.
 * Survives UI navigation and backgrounding.
 */
class AiGenerationManager(
    private val workoutUseCase: WorkoutAiUseCase,
    private val recipeUseCase: RecipeAiUseCase,
    private val notificationService: NotificationService,
    private val backgroundTaskManager: BackgroundTaskManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val state: StateFlow<AiGenerationState> = _state.asStateFlow()

    private val _streamingText = MutableStateFlow(StreamingText())
    val streamingText: StateFlow<StreamingText> = _streamingText.asStateFlow()

    /**
     * @return true if generation started, false if already busy.
     */
    fun startWorkoutGeneration(form: WorkoutAiForm): Boolean {
        if (_state.value is AiGenerationState.Generating) return false

        _state.value = AiGenerationState.Generating(AiType.WORKOUT, form)
        _streamingText.value = StreamingText()
        scope.launch {
            val taskId = backgroundTaskManager.beginTask()
            try {
                val preview = workoutUseCase.invoke(form) { content, reasoning ->
                    _streamingText.value = StreamingText(content, reasoning)
                }
                _state.value = AiGenerationState.Success(AiType.WORKOUT, preview, form)
                notificationService.showNotification(
                    "Workout fertig!",
                    "Dein KI-generiertes Workout ist bereit."
                )
            } catch (e: Exception) {
                _state.value = AiGenerationState.Error(AiType.WORKOUT, e, form)
                notificationService.showNotification(
                    "Fehler bei der Generierung",
                    "Das Workout konnte nicht erstellt werden: ${e.message}"
                )
            } finally {
                backgroundTaskManager.endTask(taskId)
            }
        }
        return true
    }

    /**
     * @return true if generation started, false if already busy.
     */
    fun startRecipeGeneration(remaining: RemainingMacros): Boolean {
        if (_state.value is AiGenerationState.Generating) return false

        _state.value = AiGenerationState.Generating(AiType.RECIPE, remaining)
        _streamingText.value = StreamingText()
        scope.launch {
            val taskId = backgroundTaskManager.beginTask()
            try {
                val preview = recipeUseCase.invoke(remaining) { content, reasoning ->
                    _streamingText.value = StreamingText(content, reasoning)
                }
                _state.value = AiGenerationState.Success(AiType.RECIPE, preview, remaining)
                notificationService.showNotification(
                    "Rezept fertig!",
                    "Dein KI-generiertes Rezept ist bereit."
                )
            } catch (e: Exception) {
                _state.value = AiGenerationState.Error(AiType.RECIPE, e, remaining)
                notificationService.showNotification(
                    "Fehler bei der Generierung",
                    "Das Rezept konnte nicht erstellt werden: ${e.message}"
                )
            } finally {
                backgroundTaskManager.endTask(taskId)
            }
        }
        return true
    }

    fun clear() {
        _state.value = AiGenerationState.Idle
        _streamingText.value = StreamingText()
    }
}

sealed class AiGenerationState {
    object Idle : AiGenerationState()
    data class Generating(val type: AiType, val originatingData: Any) : AiGenerationState()
    data class Success(val type: AiType, val preview: Any, val originatingData: Any) : AiGenerationState()
    data class Error(val type: AiType, val exception: Throwable, val originatingData: Any?) : AiGenerationState()
}

enum class AiType {
    WORKOUT, RECIPE
}

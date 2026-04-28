package com.pumpernickel.presentation.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.domain.gamification.GoalDayTrigger
import com.pumpernickel.domain.gamification.RankState
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.model.UserPhysicalStats
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Training intensity level for a muscle group, derived from
 * RIR-weighted scoring of completed sets in the last 7 days.
 *
 * Scoring: each set contributes a base score based on its RIR value,
 * multiplied by a muscle-role factor (primary = 1.0, secondary = 0.5).
 *
 * RIR multipliers:
 *   RIR 4+ → 0.5×
 *   RIR 2-3 → 1.0×
 *   RIR 1   → 1.5×
 *   RIR 0   → 2.0×
 *
 * Weekly score thresholds:
 *   <5   → LOW   (under-trained)
 *   5-12 → MODERATE (maintenance)
 *   13+  → HIGH  (growth stimulus)
 */
enum class TrainingIntensity {
    /** No sets recorded */
    NONE,
    /** Weighted score < 5 */
    LOW,
    /** Weighted score 5-12 */
    MODERATE,
    /** Weighted score 13+ */
    HIGH;

    companion object {
        fun fromWeightedScore(score: Double): TrainingIntensity = when {
            score <= 0.0 -> NONE
            score < 5.0  -> LOW
            score <= 12.0 -> MODERATE
            else -> HIGH
        }

        /** RIR-based multiplier for a single set. */
        fun rirMultiplier(rir: Int): Double = when {
            rir >= 4 -> 0.5
            rir >= 2 -> 1.0
            rir == 1 -> 1.5
            else     -> 2.0  // RIR 0
        }
    }
}

data class OverviewUiState(
    val muscleLoad: Map<MuscleGroup, TrainingIntensity> = emptyMap(),
    val todayMacros: RecipeMacros = RecipeMacros(),
    val nutritionGoals: NutritionGoals = NutritionGoals(),
    val isLoading: Boolean = true
)

class OverviewViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val loadConsumptionsForDate: LoadConsumptionsForDateUseCase,
    private val calculateDailyMacros: CalculateDailyMacrosUseCase,
    private val gamificationRepository: GamificationRepository,
    private val goalDayTrigger: GoalDayTrigger
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())

    @NativeCoroutinesState
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    @NativeCoroutinesState
    val nutritionGoals: StateFlow<NutritionGoals> = settingsRepository
        .nutritionGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionGoals())

    /**
     * D-18: rank + XP for the Overview strip. Unranked until first workout save.
     */
    @NativeCoroutinesState
    val rankState: StateFlow<RankState> = gamificationRepository
        .rankState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RankState.Unranked)

    /**
     * D-16-11 — null until the user has saved a complete `UserPhysicalStats` once.
     * The editor opens with placeholder defaults (UI-SPEC) when null.
     */
    @NativeCoroutinesState
    val userPhysicalStats: StateFlow<UserPhysicalStats?> = settingsRepository
        .userPhysicalStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * D-16-13 / D-16-14 — true while the "set personal goals" banner should be shown.
     * Negation of the persisted dismissed flag; starts true on first launch.
     */
    @NativeCoroutinesState
    val nutritionGoalsBannerVisible: StateFlow<Boolean> = settingsRepository
        .nutritionGoalsBannerDismissed
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.nutritionGoals.collect { goals ->
                _uiState.update { it.copy(nutritionGoals = goals) }
            }
        }
    }

    fun refresh() {
        // D-22: fire goal-day evaluation in its own coroutine so it does not
        // block the muscle/macro UI refresh. Idempotent — safe to call on every tab appearance.
        viewModelScope.launch {
            try {
                goalDayTrigger.maybeTrigger()
            } catch (t: Throwable) {
                println("GoalDayTrigger failed: ${t.message}")
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load goals
            val goals = settingsRepository.nutritionGoals.first()

            // Load today's nutrition
            val today = today()
            val entries = loadConsumptionsForDate(today)
            val macros = calculateDailyMacros(entries)

            // Load 7-day muscle training load (RIR-weighted scoring)
            val sevenDaysAgoMillis = Clock.System.now().toEpochMilliseconds() - 7 * 24 * 60 * 60 * 1000L
            val exerciseSetRirs = workoutRepository.getExerciseSetRirSince(sevenDaysAgoMillis)

            // Group per-set RIR data by exerciseId
            val rirsByExercise = exerciseSetRirs.groupBy { it.exerciseId }

            // Compute weighted muscle scores
            val muscleScores = mutableMapOf<MuscleGroup, Double>()
            for ((exerciseId, setRows) in rirsByExercise) {
                val exercise = exerciseRepository.getExerciseById(exerciseId).first()
                    ?: continue

                // Sum up weighted contributions for this exercise
                val exerciseScore = setRows.sumOf { row ->
                    TrainingIntensity.rirMultiplier(row.rir)
                }

                // Primary muscles: full weight (1.0×)
                for (group in exercise.primaryMuscles) {
                    muscleScores[group] = (muscleScores[group] ?: 0.0) + exerciseScore
                }
                // Secondary muscles: half weight (0.5×)
                for (group in exercise.secondaryMuscles) {
                    muscleScores[group] = (muscleScores[group] ?: 0.0) + exerciseScore * 0.5
                }
            }

            val muscleLoad = muscleScores.mapValues { (_, score) ->
                TrainingIntensity.fromWeightedScore(score)
            }

            _uiState.value = OverviewUiState(
                muscleLoad = muscleLoad,
                todayMacros = macros,
                nutritionGoals = goals,
                isLoading = false
            )
        }
    }

    fun updateNutritionGoals(goals: NutritionGoals) {
        viewModelScope.launch {
            settingsRepository.setNutritionGoals(goals)
            // D-16-14: any successful save also dismisses the discoverability banner.
            settingsRepository.setNutritionGoalsBannerDismissed(true)
            _uiState.update { it.copy(nutritionGoals = goals) }
        }
    }

    /**
     * D-16-11 — persist the user's stats so the calculator remembers them.
     * Triggered by the editor on Save (alongside `updateNutritionGoals`).
     */
    fun updateUserPhysicalStats(stats: UserPhysicalStats) {
        viewModelScope.launch {
            settingsRepository.setUserPhysicalStats(stats)
        }
    }

    /**
     * D-16-13 / D-16-14 — flip the banner-dismissed sentinel from the "×" tap.
     * `updateNutritionGoals` also flips it on save, so this is only reached on explicit dismiss.
     */
    fun dismissBanner() {
        viewModelScope.launch {
            settingsRepository.setNutritionGoalsBannerDismissed(true)
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

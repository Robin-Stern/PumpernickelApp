package com.pumpernickel.domain.workout

import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.domain.repository.WorkoutRepository
import com.pumpernickel.domain.model.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Recovery-based assessment of which muscles are due for a stimulus.
 *
 * Replaces the previous fixed 7-day RIR-score window. The new model looks at:
 *  - **Time since last qualifying set** (RIR ≤ 3 — RIR 4+ ignored as warm-up).
 *  - **Frequency over the last 28 days** (qualifying sets / 4).
 *
 * Only muscles that were *ever* trained (primary or secondary, any RIR) are considered —
 * muscles you've never touched aren't surfaced as "neglected".
 */
class GetUndertrainedMusclesUseCase(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository
) {
    companion object {
        private const val QUALIFYING_RIR_MAX = 3
        private const val WINDOW_DAYS = 28L
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        // Severity thresholds in days since last qualifying stimulus.
        private const val NEEDS_ATTENTION_MIN_DAYS = 5
        private const val OVERDUE_MIN_DAYS = 10
        private const val NEGLECTED_MIN_DAYS = 21

        // Frequency thresholds in qualifying sets per week (28-day window / 4).
        private const val LOW_FREQUENCY_PER_WEEK = 1.0
        private const val VERY_LOW_FREQUENCY_PER_WEEK = 0.5
    }

    suspend operator fun invoke(): List<UndertrainedMuscle> {
        // Find every muscle that has ever appeared in a completed workout.
        val allTimeSets = workoutRepository.getExerciseSetRirSince(0L)
        if (allTimeSets.isEmpty()) return emptyList()

        // Cache exercise lookups — many rows share the same exerciseId.
        val exerciseCache = mutableMapOf<String, com.pumpernickel.domain.model.Exercise?>()
        suspend fun exerciseFor(id: String) =
            exerciseCache.getOrPut(id) { exerciseRepository.getExerciseById(id).first() }

        val everTrainedMuscles = mutableSetOf<MuscleGroup>()
        for (exerciseId in allTimeSets.map { it.exerciseId }.toSet()) {
            val exercise = exerciseFor(exerciseId) ?: continue
            everTrainedMuscles += exercise.primaryMuscles
            everTrainedMuscles += exercise.secondaryMuscles
        }
        if (everTrainedMuscles.isEmpty()) return emptyList()

        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val windowStart = nowMillis - WINDOW_DAYS * MILLIS_PER_DAY

        // For each muscle, track the most recent qualifying set + count of qualifying sets in window.
        val lastQualifyingMillis = mutableMapOf<MuscleGroup, Long>()
        val qualifyingSetsInWindow = mutableMapOf<MuscleGroup, Int>()

        for (set in allTimeSets) {
            if (set.rir > QUALIFYING_RIR_MAX) continue
            val exercise = exerciseFor(set.exerciseId) ?: continue
            val touched = exercise.primaryMuscles + exercise.secondaryMuscles
            for (group in touched) {
                val prev = lastQualifyingMillis[group]
                if (prev == null || set.startTimeMillis > prev) {
                    lastQualifyingMillis[group] = set.startTimeMillis
                }
                if (set.startTimeMillis >= windowStart) {
                    qualifyingSetsInWindow[group] = (qualifyingSetsInWindow[group] ?: 0) + 1
                }
            }
        }

        val results = everTrainedMuscles.mapNotNull { group ->
            val lastMillis = lastQualifyingMillis[group]
            val daysSince = lastMillis?.let { ((nowMillis - it) / MILLIS_PER_DAY).toInt() }
            val freq = (qualifyingSetsInWindow[group] ?: 0) / (WINDOW_DAYS / 7.0)
            val severity = classify(daysSince, freq) ?: return@mapNotNull null
            UndertrainedMuscle(
                group = group,
                daysSinceLast = daysSince,
                weeklyFrequency = freq,
                severity = severity
            )
        }

        return results
            .sortedWith(
                compareByDescending<UndertrainedMuscle> { severityOrder(it.severity) }
                    .thenByDescending { it.daysSinceLast ?: Int.MAX_VALUE }
                    .thenBy { it.group.displayName }
            )
    }

    private fun classify(daysSince: Int?, weeklyFrequency: Double): UndertrainedSeverity? {
        // Never qualified: maximally neglected.
        if (daysSince == null) return UndertrainedSeverity.NEGLECTED

        if (daysSince >= NEGLECTED_MIN_DAYS) return UndertrainedSeverity.NEGLECTED
        if (daysSince >= OVERDUE_MIN_DAYS) return UndertrainedSeverity.OVERDUE
        if (weeklyFrequency < VERY_LOW_FREQUENCY_PER_WEEK) return UndertrainedSeverity.OVERDUE

        if (daysSince >= NEEDS_ATTENTION_MIN_DAYS &&
            weeklyFrequency < LOW_FREQUENCY_PER_WEEK) {
            return UndertrainedSeverity.NEEDS_ATTENTION
        }

        // Trained recently enough and often enough — not surfaced.
        return null
    }

    private fun severityOrder(s: UndertrainedSeverity): Int = when (s) {
        UndertrainedSeverity.NEGLECTED -> 2
        UndertrainedSeverity.OVERDUE -> 1
        UndertrainedSeverity.NEEDS_ATTENTION -> 0
    }
}

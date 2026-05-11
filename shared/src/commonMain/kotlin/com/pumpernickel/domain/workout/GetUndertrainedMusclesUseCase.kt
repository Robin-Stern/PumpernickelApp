package com.pumpernickel.domain.workout

import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.presentation.overview.TrainingIntensity
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class GetUndertrainedMusclesUseCase(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository
) {
    suspend operator fun invoke(): List<MuscleGroup> {
        // Determine which muscles have ever been trained
        val allTimeSets = workoutRepository.getExerciseSetRirSince(0L)
        if (allTimeSets.isEmpty()) return emptyList()

        val everTrainedMuscles = mutableSetOf<MuscleGroup>()
        for (exerciseId in allTimeSets.map { it.exerciseId }.toSet()) {
            val exercise = exerciseRepository.getExerciseById(exerciseId).first() ?: continue
            everTrainedMuscles += exercise.primaryMuscles
            everTrainedMuscles += exercise.secondaryMuscles
        }
        if (everTrainedMuscles.isEmpty()) return emptyList()

        // Compute RIR-weighted scores for the last 7 days
        val sevenDaysAgoMillis = Clock.System.now().toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
        val recentSets = workoutRepository.getExerciseSetRirSince(sevenDaysAgoMillis)
        val muscleScores = mutableMapOf<MuscleGroup, Double>()
        for ((exerciseId, setRows) in recentSets.groupBy { it.exerciseId }) {
            val exercise = exerciseRepository.getExerciseById(exerciseId).first() ?: continue
            val score = setRows.sumOf { TrainingIntensity.rirMultiplier(it.rir) }
            for (group in exercise.primaryMuscles) {
                muscleScores[group] = (muscleScores[group] ?: 0.0) + score
            }
            for (group in exercise.secondaryMuscles) {
                muscleScores[group] = (muscleScores[group] ?: 0.0) + score * 0.5
            }
        }

        // Only return muscles that were trained before but are NONE or LOW in the last 7 days
        return everTrainedMuscles
            .filter { muscle ->
                val intensity = TrainingIntensity.fromWeightedScore(muscleScores[muscle] ?: 0.0)
                intensity == TrainingIntensity.NONE || intensity == TrainingIntensity.LOW
            }
            .sortedBy { it.displayName }
    }
}

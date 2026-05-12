package com.pumpernickel.di

import com.pumpernickel.presentation.ai.WorkoutAiViewModel
import org.koin.mp.KoinPlatform

class WorkoutAiKoinHelper {
    fun getWorkoutAiViewModel(): WorkoutAiViewModel =
        KoinPlatform.getKoin().get()
}

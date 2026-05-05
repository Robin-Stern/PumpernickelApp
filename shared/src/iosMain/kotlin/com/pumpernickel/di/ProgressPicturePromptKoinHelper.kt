package com.pumpernickel.di

import com.pumpernickel.presentation.progresspic.ProgressPicturePromptViewModel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the Phase 17 post-workout prompt VM.
 *
 * Swift callers:
 * `ProgressPicturePromptKoinHelper().getProgressPicturePromptViewModel(workoutId: 42)`.
 * Instantiated by the Finished view on iOS (see 17-08 iOS handoff).
 *
 * One helper per VM, no caching (D-151-10 convention). The `workoutId: Long`
 * is forwarded to Koin via `parametersOf`, mirroring the Viewer helper.
 */
class ProgressPicturePromptKoinHelper {
    fun getProgressPicturePromptViewModel(workoutId: Long): ProgressPicturePromptViewModel =
        KoinPlatform.getKoin().get { parametersOf(workoutId) }
}

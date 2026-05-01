package com.pumpernickel.di

import com.pumpernickel.presentation.progresspic.ProgressViewerViewModel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the Phase 17 viewer VM.
 *
 * Swift callers: `ProgressViewerKoinHelper().getProgressViewerViewModel(workoutId: 42)`.
 * The `workoutId: Long` is forwarded to Koin via `parametersOf` — same shape as
 * the Android `koinViewModel { parametersOf(workoutId) }` consumption.
 *
 * One helper per VM, no caching (D-151-10 convention). Swift retains the
 * returned VM in `@State` so its lifecycle is bound to the View — closing
 * the view fires the VM's relock() through the SwiftUI onDisappear hook.
 */
class ProgressViewerKoinHelper {
    fun getProgressViewerViewModel(workoutId: Long): ProgressViewerViewModel =
        KoinPlatform.getKoin().get { parametersOf(workoutId) }
}

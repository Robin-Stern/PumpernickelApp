package com.pumpernickel.di

import com.pumpernickel.presentation.progresspic.ProgressGalleryViewModel
import org.koin.mp.KoinPlatform

/**
 * iOS-side factory for the Phase 17 gallery VM.
 *
 * Swift callers: `ProgressGalleryKoinHelper().getProgressGalleryViewModel()`.
 * Mirrors the Phase 15 / 15.1 one-helper-per-VM convention
 * (AchievementGalleryKoinHelper, RanksAndAchievementsKoinHelper) per D-151-10
 * — class, not object; no params; no caching; one getter.
 *
 * The underlying `viewModel { ProgressGalleryViewModel(...) }` factory in
 * progressGalleryModule produces a fresh VM instance per call. SwiftUI is
 * responsible for retaining the returned VM in `@State` so its lifecycle is
 * bound to the View.
 */
class ProgressGalleryKoinHelper {
    fun getProgressGalleryViewModel(): ProgressGalleryViewModel =
        KoinPlatform.getKoin().get()
}

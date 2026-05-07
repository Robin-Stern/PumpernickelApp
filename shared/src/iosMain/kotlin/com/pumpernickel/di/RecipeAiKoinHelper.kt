package com.pumpernickel.di

import com.pumpernickel.presentation.ai.RecipeAiViewModel
import org.koin.mp.KoinPlatform

class RecipeAiKoinHelper {
    fun getRecipeAiViewModel(): RecipeAiViewModel =
        KoinPlatform.getKoin().get()
}

package com.pumpernickel.di

import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import com.pumpernickel.presentation.templates.TemplateListViewModel
import org.koin.mp.KoinPlatform

object KoinHelper {
    fun getExerciseCatalogViewModel(): ExerciseCatalogViewModel =
        KoinPlatform.getKoin().get()

    fun getExerciseDetailViewModel(): ExerciseDetailViewModel =
        KoinPlatform.getKoin().get()

    fun getCreateExerciseViewModel(): CreateExerciseViewModel =
        KoinPlatform.getKoin().get()

    fun getTemplateListViewModel(): TemplateListViewModel =
        KoinPlatform.getKoin().get()

    fun getTemplateEditorViewModel(): TemplateEditorViewModel =
        KoinPlatform.getKoin().get()
}

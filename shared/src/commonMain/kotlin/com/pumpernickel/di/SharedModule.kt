package com.pumpernickel.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.DatabaseSeeder
import com.pumpernickel.data.db.ExerciseDao
import com.pumpernickel.data.db.WorkoutSessionDao
import com.pumpernickel.data.db.WorkoutTemplateDao
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.ExerciseRepositoryImpl
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.data.repository.TemplateRepositoryImpl
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.data.repository.WorkoutRepositoryImpl
import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import com.pumpernickel.presentation.templates.TemplateListViewModel
import com.pumpernickel.presentation.history.WorkoutHistoryViewModel
import com.pumpernickel.presentation.settings.SettingsViewModel
import com.pumpernickel.presentation.workout.WorkoutSessionViewModel
import com.pumpernickel.readResourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.context.startKoin

val sharedModule = module {
    // Database -- build from platform-provided Builder
    single<AppDatabase> {
        get<RoomDatabase.Builder<AppDatabase>>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<ExerciseDao> { get<AppDatabase>().exerciseDao() }
    single<WorkoutTemplateDao> { get<AppDatabase>().workoutTemplateDao() }
    single<WorkoutSessionDao> { get<AppDatabase>().workoutSessionDao() }
    single<CompletedWorkoutDao> { get<AppDatabase>().completedWorkoutDao() }

    // Seeder
    single<DatabaseSeeder> { DatabaseSeeder { readResourceFile("free_exercise_db.json") } }

    // Repositories
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepository(get()) }

    // ViewModels
    viewModel { ExerciseCatalogViewModel(get()) }
    viewModel { ExerciseDetailViewModel(get()) }
    viewModel { CreateExerciseViewModel(get()) }
    viewModel { TemplateListViewModel(get()) }
    viewModel { TemplateEditorViewModel(get(), get()) }
    viewModel { WorkoutSessionViewModel(get(), get(), get()) }
    viewModel { WorkoutHistoryViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
}

// Common init function
fun initKoin(additionalModules: List<Module> = emptyList()) {
    startKoin {
        modules(sharedModule + platformModule + additionalModules)
    }
}

expect val platformModule: Module

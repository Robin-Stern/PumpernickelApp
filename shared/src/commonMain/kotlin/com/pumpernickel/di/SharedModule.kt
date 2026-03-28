package com.pumpernickel.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.DatabaseSeeder
import com.pumpernickel.data.db.ExerciseDao
import com.pumpernickel.data.db.WorkoutTemplateDao
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.ExerciseRepositoryImpl
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.data.repository.TemplateRepositoryImpl
import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single<ExerciseDao> { get<AppDatabase>().exerciseDao() }
    single<WorkoutTemplateDao> { get<AppDatabase>().workoutTemplateDao() }

    // Seeder
    single<DatabaseSeeder> { DatabaseSeeder { readResourceFile("free_exercise_db.json") } }

    // Repositories
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }

    // ViewModels
    viewModel { ExerciseCatalogViewModel(get()) }
    viewModel { ExerciseDetailViewModel(get()) }
    viewModel { CreateExerciseViewModel(get()) }
}

// Common init function
fun initKoin(additionalModules: List<Module> = emptyList()) {
    startKoin {
        modules(sharedModule + platformModule + additionalModules)
    }
}

expect val platformModule: Module

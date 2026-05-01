package com.pumpernickel.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.api.OpenFoodFactsApi
import com.pumpernickel.data.api.createHttpClient
import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.DatabaseSeeder
import com.pumpernickel.data.db.ExerciseDao
import com.pumpernickel.data.db.NutritionDao
import com.pumpernickel.data.db.NutritionDataSeeder
import com.pumpernickel.data.db.WorkoutSessionDao
import com.pumpernickel.data.db.WorkoutTemplateDao
import com.pumpernickel.data.repository.ExerciseRepository
import com.pumpernickel.data.repository.ExerciseRepositoryImpl
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.data.repository.FoodRepositoryImpl
import com.pumpernickel.data.repository.TemplateRepository
import com.pumpernickel.data.repository.TemplateRepositoryImpl
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.data.repository.WorkoutRepository
import com.pumpernickel.data.repository.WorkoutRepositoryImpl
import com.pumpernickel.domain.nutrition.AddFoodUseCase
import com.pumpernickel.domain.nutrition.CalculateDailyMacrosUseCase
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.DeleteConsumptionUseCase
import com.pumpernickel.domain.nutrition.DeleteFoodUseCase
import com.pumpernickel.domain.nutrition.LoadConsumptionsForDateUseCase
import com.pumpernickel.domain.nutrition.LoadFoodsUseCase
import com.pumpernickel.domain.nutrition.LogConsumptionUseCase
import com.pumpernickel.domain.nutrition.LookupBarcodeUseCase
import com.pumpernickel.domain.nutrition.SearchFoodsRemoteUseCase
import com.pumpernickel.domain.nutrition.UpdateFoodUseCase
import com.pumpernickel.domain.nutrition.ValidateFoodInputUseCase
import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import com.pumpernickel.presentation.exercises.ExerciseCatalogViewModel
import com.pumpernickel.presentation.exercises.ExerciseDetailViewModel
import com.pumpernickel.presentation.nutrition.DailyLogViewModel
import com.pumpernickel.presentation.nutrition.FoodEntryViewModel
import com.pumpernickel.presentation.nutrition.RecipeCreationViewModel
import com.pumpernickel.presentation.nutrition.RecipeListViewModel
import com.pumpernickel.presentation.templates.TemplateEditorViewModel
import com.pumpernickel.presentation.templates.TemplateListViewModel
import com.pumpernickel.presentation.history.WorkoutHistoryViewModel
import com.pumpernickel.presentation.overview.OverviewViewModel
import com.pumpernickel.presentation.settings.SettingsViewModel
import com.pumpernickel.presentation.workout.WorkoutSessionViewModel
import com.pumpernickel.readResourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.context.startKoin
import org.koin.core.KoinApplication

val sharedModule = module {
    // Gamification feature modules (plan 03) -- mounted here once; each plan
    // adds its bindings to its own feature module file.
    includes(
        gamificationModule,
        gamificationEngineModule,
        gamificationUiModule,
        achievementGalleryModule
    )

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
    single<NutritionDao> { get<AppDatabase>().nutritionDao() }

    // Seeder
    single<DatabaseSeeder> { DatabaseSeeder { readResourceFile("free_exercise_db.json") } }

    // Repositories
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(get(), get()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepository(get()) }
    single<FoodRepository> { FoodRepositoryImpl(get(), get()) }

    // Nutrition: API + Seeder
    single { createHttpClient() }
    single { OpenFoodFactsApi(get()) }
    single { NutritionDataSeeder(get<NutritionDao>()) }

    // Nutrition: Use Cases
    single { ValidateFoodInputUseCase() }
    single { LoadFoodsUseCase(get()) }
    single { AddFoodUseCase(get(), get()) }
    single { UpdateFoodUseCase(get(), get()) }
    single { DeleteFoodUseCase(get()) }
    single { CalculateRecipeMacrosUseCase() }
    single { LookupBarcodeUseCase(get(), get()) }
    single { SearchFoodsRemoteUseCase(get()) }
    single { LogConsumptionUseCase(get()) }
    single { LoadConsumptionsForDateUseCase(get()) }
    single { DeleteConsumptionUseCase(get()) }
    single { CalculateDailyMacrosUseCase() }

    // ViewModels -- Workout
    viewModel { ExerciseCatalogViewModel(get()) }
    viewModel { ExerciseDetailViewModel(get()) }
    viewModel { CreateExerciseViewModel(get()) }
    viewModel { TemplateListViewModel(get()) }
    viewModel { TemplateEditorViewModel(get(), get()) }
    viewModel { WorkoutSessionViewModel(get(), get(), get(), get(), get()) }
    viewModel { WorkoutHistoryViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { OverviewViewModel(get(), get(), get(), get(), get(), get(), get()) }

    // ViewModels -- Nutrition
    viewModel { FoodEntryViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RecipeListViewModel(get(), get()) }
    viewModel { RecipeCreationViewModel(get(), get(), get()) }
    viewModel { DailyLogViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

// Common init function
// appDeclaration allows platform-specific config (e.g. androidContext() on Android)
fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) {
    startKoin {
        appDeclaration()
        modules(sharedModule + platformModule)
    }
}

expect val platformModule: Module

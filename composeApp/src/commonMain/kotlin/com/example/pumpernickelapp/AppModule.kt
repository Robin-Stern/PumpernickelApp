package com.example.pumpernickelapp

import com.example.pumpernickelapp.food.data.FoodRepositoryImpl
import com.example.pumpernickelapp.food.data.api.OpenFoodFactsApi
import com.example.pumpernickelapp.food.data.api.createHttpClient
import com.example.pumpernickelapp.food.data.seedDemoDataIfEmpty
import com.example.pumpernickelapp.food.domain.AddFoodUseCase
import com.example.pumpernickelapp.food.domain.DeleteFoodUseCase
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.LoadFoodsUseCase
import com.example.pumpernickelapp.food.domain.LookupBarcodeUseCase
import com.example.pumpernickelapp.food.domain.CalculateRecipeMacrosUseCase
import com.example.pumpernickelapp.food.domain.UpdateFoodUseCase
import com.example.pumpernickelapp.food.domain.ValidateFoodInputUseCase
import com.example.pumpernickelapp.food.ui.entry.FoodEntryViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeCreationViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<FoodRepository> { FoodRepositoryImpl().also { seedDemoDataIfEmpty(it) } }
    single { ValidateFoodInputUseCase() }
    single { LoadFoodsUseCase(get()) }
    single { AddFoodUseCase(get(), get()) }
    single { DeleteFoodUseCase(get()) }
    single { UpdateFoodUseCase(get(), get()) }
    single { CalculateRecipeMacrosUseCase() }
    single { createHttpClient() }
    single { OpenFoodFactsApi(get()) }
    single { LookupBarcodeUseCase(get(), get()) }
    viewModel { FoodEntryViewModel(get(), get(), get(), get(), get()) }
    viewModel { RecipeListViewModel(get(), get()) }
    viewModel { RecipeCreationViewModel(get(), get()) }
}

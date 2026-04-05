package com.example.pumpernickelapp

import com.example.pumpernickelapp.food.data.FoodRepositoryImpl
import com.example.pumpernickelapp.food.data.seedDemoDataIfEmpty
import com.example.pumpernickelapp.food.domain.AddFoodUseCase
import com.example.pumpernickelapp.food.domain.DeleteFoodUseCase
import com.example.pumpernickelapp.food.domain.FoodRepository
import com.example.pumpernickelapp.food.domain.LoadFoodsUseCase
import com.example.pumpernickelapp.food.domain.UpdateFoodUseCase
import com.example.pumpernickelapp.food.domain.ValidateFoodInputUseCase
import com.example.pumpernickelapp.food.ui.entry.FoodEntryViewModel
import com.example.pumpernickelapp.food.ui.recipe.RecipeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<FoodRepository> { FoodRepositoryImpl().also { seedDemoDataIfEmpty(it) } }
    single { ValidateFoodInputUseCase() }
    single { LoadFoodsUseCase(get()) }
    single { AddFoodUseCase(get(), get()) }
    single { DeleteFoodUseCase(get()) }
    single { UpdateFoodUseCase(get(), get()) }
    viewModel { FoodEntryViewModel(get(), get(), get(), get()) }
    viewModel { RecipeViewModel(get()) }
}

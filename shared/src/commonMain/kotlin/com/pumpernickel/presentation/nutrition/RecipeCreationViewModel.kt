package com.pumpernickel.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.FoodRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.model.calculateMacros
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LookupBarcodeUseCase
import com.pumpernickel.domain.nutrition.SearchFoodsRemoteUseCase
import com.pumpernickel.domain.nutrition.SelectFoodUseCase
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientEntry(val food: Food, val amountGrams: String)

data class SearchResultItem(
    val food: Food,
    val brand: String? = null,
    val nutriScore: String? = null
)

data class RecipeCreationUiState(
    val recipeName: String = "",
    val searchQuery: String = "",
    val searchResults: List<SearchResultItem> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val ingredients: List<IngredientEntry> = emptyList(),
    val totals: RecipeMacros = RecipeMacros(),
    val errorMessage: String? = null,
    val editingRecipeId: String? = null,
    val editingIsFavorite: Boolean = false
)

sealed interface RecipeCreationEvent {
    data class OnRecipeNameChanged(val value: String) : RecipeCreationEvent
    data class OnSearchQueryChanged(val value: String) : RecipeCreationEvent
    data class OnFoodSelected(val food: Food) : RecipeCreationEvent
    data class OnSearchFocused(val focused: Boolean) : RecipeCreationEvent
    data class OnIngredientAmountChanged(val index: Int, val value: String) : RecipeCreationEvent
    data class OnIngredientRemoved(val index: Int) : RecipeCreationEvent
    data class OnIngredientMoved(val fromIndex: Int, val toIndex: Int) : RecipeCreationEvent
    data object OnSaveClicked : RecipeCreationEvent
    data class OnBarcodeScanned(val barcode: String) : RecipeCreationEvent
}

class RecipeCreationViewModel(
    private val repository: FoodRepository,
    private val calculateRecipeMacros: CalculateRecipeMacrosUseCase,
    private val lookupBarcode: LookupBarcodeUseCase,
    private val searchFoodsRemote: SearchFoodsRemoteUseCase,
    private val selectFood: SelectFoodUseCase
) : ViewModel() {

    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    private var searchJob: Job? = null

    private val _creationState = MutableStateFlow(RecipeCreationUiState())
    @NativeCoroutinesState
    val creationState: StateFlow<RecipeCreationUiState> = _creationState.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @NativeCoroutines
    val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            _foods.value = repository.loadFoods()
            _creationState.update { it.copy(searchResults = recentFoods()) }
        }
    }

    fun reset() {
        viewModelScope.launch {
            _foods.value = repository.loadFoods()
            _creationState.value = RecipeCreationUiState(searchResults = recentFoods())
        }
    }

    fun loadRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val allFoods = repository.loadFoods()
            _foods.value = allFoods
            val foodMap = allFoods.associateBy { it.id }
            val entries = recipe.ingredients.mapNotNull { ingredient ->
                val food = foodMap[ingredient.foodId] ?: return@mapNotNull null
                val amountStr = if (ingredient.amountGrams == ingredient.amountGrams.toLong().toDouble())
                    ingredient.amountGrams.toLong().toString() else ingredient.amountGrams.toString()
                IngredientEntry(food, amountStr)
            }
            _creationState.value = RecipeCreationUiState(
                recipeName = recipe.name,
                searchResults = recentFoods(),
                ingredients = entries,
                totals = calcTotals(entries),
                editingRecipeId = recipe.id,
                editingIsFavorite = recipe.isFavorite
            )
        }
    }

    fun onEvent(event: RecipeCreationEvent) {
        when (event) {
            is RecipeCreationEvent.OnRecipeNameChanged ->
                _creationState.update { it.copy(recipeName = event.value) }

            is RecipeCreationEvent.OnSearchQueryChanged -> {
                _creationState.update { it.copy(searchQuery = event.value, isSearchingRemote = false) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    val freshFoods = repository.loadFoods()
                    _foods.value = freshFoods
                    val query = event.value.trim()
                    val localResults = if (query.isBlank()) recentFoods()
                        else freshFoods.filter { it.name.contains(query, ignoreCase = true) }.map { SearchResultItem(it) }
                    if (localResults.isNotEmpty() || query.isBlank()) {
                        _creationState.update { it.copy(searchResults = localResults) }
                    } else {
                        _creationState.update { it.copy(searchResults = emptyList(), isSearchingRemote = true) }
                        when (val remote = searchFoodsRemote(query)) {
                            is SearchFoodsRemoteUseCase.Result.Success -> {
                                val remoteItems = remote.foods.map { r ->
                                    SearchResultItem(
                                        food = Food(name = r.name, calories = r.calories, protein = r.protein,
                                            fat = r.fat, carbohydrates = r.carbs, sugar = r.sugar,
                                            source = "openfoodfacts"),
                                        brand = r.brand,
                                        nutriScore = r.nutriScore
                                    )
                                }
                                _creationState.update { it.copy(searchResults = remoteItems, isSearchingRemote = false) }
                            }
                            else -> _creationState.update { it.copy(searchResults = emptyList(), isSearchingRemote = false) }
                        }
                    }
                }
            }

            is RecipeCreationEvent.OnFoodSelected -> viewModelScope.launch {
                val food = selectFood(event.food)
                _foods.value = repository.loadFoods()
                _creationState.update { state ->
                    val newIngredients = if (state.ingredients.none { it.food.id == food.id })
                        state.ingredients + IngredientEntry(food, "100")
                    else state.ingredients
                    state.withIngredients(newIngredients)
                        .copy(searchResults = state.searchResults.filter { it.food.id != food.id })
                }
            }

            is RecipeCreationEvent.OnSearchFocused -> Unit

            is RecipeCreationEvent.OnIngredientAmountChanged -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList()
                    .also { it[event.index] = it[event.index].copy(amountGrams = event.value) }
                state.withIngredients(newIngredients)
            }

            is RecipeCreationEvent.OnIngredientRemoved -> _creationState.update { state ->
                val newIngredients = state.ingredients.toMutableList().also { it.removeAt(event.index) }
                state.withIngredients(newIngredients)
            }

            is RecipeCreationEvent.OnIngredientMoved -> _creationState.update { state ->
                val list = state.ingredients.toMutableList()
                val item = list.removeAt(event.fromIndex)
                list.add(event.toIndex, item)
                state.copy(ingredients = list)
            }

            RecipeCreationEvent.OnSaveClicked -> saveRecipe()

            is RecipeCreationEvent.OnBarcodeScanned -> viewModelScope.launch {
                _creationState.update { it.copy(errorMessage = null) }
                when (val result = lookupBarcode(event.barcode)) {
                    is LookupBarcodeUseCase.Result.FoundLocally ->
                        onEvent(RecipeCreationEvent.OnFoodSelected(result.food))
                    is LookupBarcodeUseCase.Result.FoundRemote -> {
                        val food = Food(
                            name = result.name, calories = result.calories,
                            protein = result.protein, fat = result.fat,
                            carbohydrates = result.carbs, sugar = result.sugar,
                            barcode = event.barcode
                        )
                        repository.saveFood(food)
                        _foods.value = repository.loadFoods()
                        onEvent(RecipeCreationEvent.OnFoodSelected(food))
                    }
                    is LookupBarcodeUseCase.Result.NotFound ->
                        _creationState.update { it.copy(errorMessage = "Produkt nicht gefunden.") }
                    is LookupBarcodeUseCase.Result.Error ->
                        _creationState.update { it.copy(errorMessage = "Fehler: ${result.message}") }
                }
            }
        }
    }

    private fun recentFoods() = _foods.value.takeLast(5).reversed().map { SearchResultItem(it) }

    private fun RecipeCreationUiState.withIngredients(newIngredients: List<IngredientEntry>) = copy(
        ingredients = newIngredients, totals = calcTotals(newIngredients)
    )

    private fun calcTotals(ingredients: List<IngredientEntry>): RecipeMacros {
        val pairs = ingredients.mapNotNull { entry ->
            val factor = entry.amountGrams.toDoubleOrNull() ?: return@mapNotNull null
            entry.food to (factor / 100.0)
        }
        return calculateMacros(pairs)
    }

    private fun saveRecipe() {
        val state = _creationState.value
        val error = when {
            state.recipeName.isBlank() -> "Rezeptname darf nicht leer sein."
            state.ingredients.isEmpty() -> "Mindestens eine Zutat hinzufuegen."
            state.ingredients.any { it.amountGrams.toDoubleOrNull()?.let { v -> v <= 0 } != false } ->
                "Alle Mengenangaben muessen eine Zahl > 0 sein."
            else -> null
        }
        if (error != null) {
            _creationState.update { it.copy(errorMessage = error) }
            return
        }
        viewModelScope.launch {
            val recipeIngredients = state.ingredients.map { entry ->
                RecipeIngredient(foodId = entry.food.id, amountGrams = entry.amountGrams.toDouble())
            }
            if (state.editingRecipeId != null) {
                repository.updateRecipe(Recipe(
                    id = state.editingRecipeId,
                    name = state.recipeName.trim(),
                    ingredients = recipeIngredients,
                    isFavorite = state.editingIsFavorite
                ))
            } else {
                repository.saveRecipe(Recipe(name = state.recipeName.trim(), ingredients = recipeIngredients))
            }
            _savedEvent.tryEmit(Unit)
        }
    }
}

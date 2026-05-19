package com.pumpernickel.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.domain.repository.FoodRepository
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.Recipe
import com.pumpernickel.domain.model.RecipeIngredient
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.domain.model.calculateMacros
import com.pumpernickel.domain.nutrition.CalculateRecipeMacrosUseCase
import com.pumpernickel.domain.nutrition.LookupBarcodeUseCase
import com.pumpernickel.domain.nutrition.RemoteFoodResult
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

data class RecipeCreationUiState(
    val recipeName: String = "",
    val searchQuery: String = "",
    val searchResults: List<Food> = emptyList(),
    val remoteSearchResults: List<RemoteFoodResult> = emptyList(),
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
    data class OnRemoteFoodSelected(val result: RemoteFoodResult) : RecipeCreationEvent
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
                _creationState.update { it.copy(searchQuery = event.value, remoteSearchResults = emptyList(), isSearchingRemote = false) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    val freshFoods = repository.loadFoods()
                    _foods.value = freshFoods
                    val query = event.value.trim()
                    val localResults = if (query.isBlank()) recentFoods()
                        else freshFoods.filter { it.name.contains(query, ignoreCase = true) }
                    _creationState.update { it.copy(searchResults = localResults) }
                    if (query.length >= 3) {
                        _creationState.update { it.copy(isSearchingRemote = true) }
                        when (val remote = searchFoodsRemote(query)) {
                            is SearchFoodsRemoteUseCase.Result.Success ->
                                _creationState.update { it.copy(remoteSearchResults = remote.foods, isSearchingRemote = false) }
                            else ->
                                _creationState.update { it.copy(remoteSearchResults = emptyList(), isSearchingRemote = false) }
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
                    state.withIngredients(newIngredients).copy(
                        searchQuery = "",
                        searchResults = recentFoods(),
                        remoteSearchResults = emptyList(),
                        isSearchingRemote = false
                    )
                }
            }

            is RecipeCreationEvent.OnRemoteFoodSelected -> viewModelScope.launch {
                val food = Food(
                    name = event.result.name,
                    calories = event.result.calories,
                    protein = event.result.protein,
                    fat = event.result.fat,
                    carbohydrates = event.result.carbs,
                    sugar = event.result.sugar,
                    source = "openfoodfacts"
                )
                val savedFood = selectFood(food)
                _foods.value = repository.loadFoods()
                _creationState.update { state ->
                    val newIngredients = if (state.ingredients.none { it.food.id == savedFood.id })
                        state.ingredients + IngredientEntry(savedFood, "100")
                    else state.ingredients
                    state.withIngredients(newIngredients).copy(
                        searchQuery = "",
                        searchResults = recentFoods(),
                        remoteSearchResults = emptyList(),
                        isSearchingRemote = false
                    )
                }
            }

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
                // D-21-03 root-cause: ViewModel / UseCase silently persisted Food with
                // zero macros when (a) OFF dataset had no nutriments AND (b) the product
                // name didn't match any FALLBACKS keyword in LookupBarcodeUseCase. The
                // FoundRemote branch unconditionally built a Food and saved it without
                // checking whether any meaningful macros came through. D-21-03 fix below:
                // surface a UI hint instead of silently persisting a zero-macro Food;
                // pre-fill the name so the user can edit it from the foods list.
                when (val result = lookupBarcode(event.barcode)) {
                    is LookupBarcodeUseCase.Result.FoundLocally ->
                        onEvent(RecipeCreationEvent.OnFoodSelected(result.food))
                    is LookupBarcodeUseCase.Result.FoundRemote -> {
                        // D-21-03 fix — detect the "OFF zero AND fallback empty" case.
                        // A single calorie threshold of 1 kcal is enough: every real food
                        // has at least a kcal value (water sits at 0 but is rarely
                        // barcoded as an ingredient). Protein/carbs/fat are checked too
                        // to defend against producers who fill only one nutrient.
                        val hasNoMacros = result.calories < 1.0 &&
                            result.protein <= 0.0 && result.carbs <= 0.0 && result.fat <= 0.0
                        val food = Food(
                            name = result.name, calories = result.calories,
                            protein = result.protein, fat = result.fat,
                            carbohydrates = result.carbs, sugar = result.sugar,
                            barcode = event.barcode
                        )
                        repository.saveFood(food)
                        _foods.value = repository.loadFoods()
                        if (hasNoMacros) {
                            // D-21-03 fix — honest UI hint instead of silent zero-row.
                            // The Food still persists (with name + barcode preserved) so
                            // the user can edit the macros manually from the foods list.
                            _creationState.update {
                                it.copy(
                                    errorMessage = "OpenFoodFacts hat für '${result.name}' " +
                                        "keine Nährwerte. Bitte manuell ergänzen."
                                )
                            }
                        }
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

    private fun recentFoods() = _foods.value.takeLast(5).reversed()

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

package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pumpernickel.android.R
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.nutrition.RemoteFoodResult
import com.pumpernickel.presentation.nutrition.RecipeCreationEvent
import com.pumpernickel.presentation.nutrition.RecipeCreationViewModel
import com.pumpernickel.presentation.nutrition.RecipeListViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private val ColorPrimary = Color(0xFF0A9D8B)
private val ColorPrimaryDark = Color(0xFF078072)
private val ColorSurfaceMuted = Color(0xFFF7F6F3)
private val ColorBorder = Color(0xFFECECEA)
private val ColorBorderSoft = Color(0xFFF1F0ED)
private val ColorText = Color(0xFF1A1A1A)
private val ColorTextMuted = Color(0xFF6F6F6F)
private val ColorTextFaint = Color(0xFF9A9A9A)
private val ColorSurfaceLavenderSoft = Color(0xFFF8EDF4)
private val ColorFocusRing = Color(0xFFE6F5F2)

@Composable
fun NutritionRecipeCreationScreen(
    listViewModel: RecipeListViewModel,
    navController: NavController,
    recipeId: String? = null,
    viewModel: RecipeCreationViewModel = koinViewModel()
) {
    val state by viewModel.creationState.collectAsStateWithLifecycle()
    val recipes by listViewModel.recipes.collectAsStateWithLifecycle()
    var searchFocused by remember { mutableStateOf(false) }
    val isSearchMode = searchFocused || state.searchQuery.isNotBlank()

    LaunchedEffect(recipeId, recipes) {
        if (recipeId == null) {
            if (state.editingRecipeId != null) viewModel.reset()
        } else if (state.editingRecipeId != recipeId) {
            recipes.firstOrNull { it.id == recipeId }?.let(viewModel::loadRecipe)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.savedEvent.collect {
            listViewModel.refresh()
            navController.popBackStack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // ── Topbar ──
        RecipeTopBar(
            title = if (recipeId != null) "Rezept bearbeiten" else "Neues Rezept",
            onBack = { navController.popBackStack() }
        )

        // ── Search bar ──
        SearchBar(
            query = state.searchQuery,
            focused = searchFocused,
            onQueryChange = { viewModel.onEvent(RecipeCreationEvent.OnSearchQueryChanged(it)) },
            onFocusChange = { searchFocused = it },
            onBarcodeScan = { viewModel.onEvent(RecipeCreationEvent.OnBarcodeScanned(it)) }
        )

        // ── Hint line ──
        if (isSearchMode) {
            Row(
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                Text("2,8 Mio. Produkte · OpenFoodFacts", fontSize = 11.sp, color = ColorTextFaint)
            }
        }

        // ── Main content ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (isSearchMode) {
                // ── Search mode ──
                item {
                    SearchSectionHeader(
                        query = state.searchQuery,
                        resultCount = state.searchResults.size + state.remoteSearchResults.size,
                        isLoading = state.isSearchingRemote
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Lokale Ergebnisse
                itemsIndexed(state.searchResults) { _, food ->
                    SearchResultCard(
                        food = food,
                        onAdd = {
                            viewModel.onEvent(RecipeCreationEvent.OnFoodSelected(food))
                            searchFocused = false
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Remote-Ergebnisse (OpenFoodFacts)
                if (state.searchQuery.length >= 3) {
                    if (state.isSearchingRemote) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ColorPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else if (state.remoteSearchResults.isNotEmpty()) {
                        item {
                            Text(
                                "OPENFOODFACTS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = ColorTextFaint,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        itemsIndexed(state.remoteSearchResults) { _, result ->
                            RemoteSearchResultCard(
                                result = result,
                                onAdd = {
                                    viewModel.onEvent(RecipeCreationEvent.OnRemoteFoodSelected(result))
                                    searchFocused = false
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        item {
                            Text(
                                "Daten von openfoodfacts.org",
                                fontSize = 11.sp,
                                color = ColorTextFaint,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (state.searchResults.isEmpty()) {
                        item { SearchEmptyState() }
                    }
                } else if (state.searchResults.isEmpty() && state.searchQuery.isNotBlank()) {
                    item { SearchEmptyState() }
                }
            } else {
                // ── Idle mode ──

                // Section 1 — Aus Datenbank wählen
                item {
                    Spacer(Modifier.height(4.dp))
                    IdleSectionHeader(
                        eyebrow = "ZULETZT HINZUGEFÜGT",
                        title = "Aus Datenbank wählen",
                        action = "${state.searchResults.size} Einträge"
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ColorSurfaceMuted, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                    ) {
                        state.searchResults.forEachIndexed { index, food ->
                            if (index > 0) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorBorderSoft))
                            }
                            RecentFoodItem(
                                food = food,
                                onAdd = { viewModel.onEvent(RecipeCreationEvent.OnFoodSelected(food)) }
                            )
                        }
                        if (state.searchResults.isEmpty()) {
                            Text(
                                "Noch keine Lebensmittel vorhanden",
                                fontSize = 13.sp,
                                color = ColorTextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // Section 2 — Zutaten
                if (state.ingredients.isNotEmpty()) {
                    item {
                        IdleSectionHeader(
                            eyebrow = "IM REZEPT",
                            title = "Zutaten",
                            action = "${state.ingredients.size} · ${state.totals.calories.roundToInt()} kcal"
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    itemsIndexed(state.ingredients) { index, entry ->
                        val amount = entry.amountGrams.toDoubleOrNull() ?: 100.0
                        val factor = amount / 100.0
                        IngredientCard(
                            name = entry.food.name,
                            kcal = (entry.food.calories * factor).roundToInt(),
                            amountGrams = amount.roundToInt(),
                            protein = entry.food.protein * factor,
                            fat = entry.food.fat * factor,
                            carbs = entry.food.carbohydrates * factor,
                            sugar = entry.food.sugar * factor,
                            onMinus = {
                                val newAmount = maxOf(10, amount.roundToInt() - 10)
                                viewModel.onEvent(RecipeCreationEvent.OnIngredientAmountChanged(index, newAmount.toString()))
                            },
                            onPlus = {
                                val newAmount = amount.roundToInt() + 10
                                viewModel.onEvent(RecipeCreationEvent.OnIngredientAmountChanged(index, newAmount.toString()))
                            },
                            onRemove = { viewModel.onEvent(RecipeCreationEvent.OnIngredientRemoved(index)) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Gesamt block
                    item {
                        Spacer(Modifier.height(8.dp))
                        Gesamt(
                            kcal = state.totals.calories.roundToInt(),
                            protein = state.totals.protein,
                            fat = state.totals.fat,
                            carbs = state.totals.carbs,
                            sugar = state.totals.sugar
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Error + Save
                item {
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    SaveButton(
                        label = if (recipeId != null) "Rezept aktualisieren" else "Rezept speichern",
                        onClick = { viewModel.onEvent(RecipeCreationEvent.OnSaveClicked) }
                    )
                }
            }
        }
    }
}

// ── Topbar ──

@Composable
private fun RecipeTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("‹", fontSize = 26.sp, color = ColorPrimary, fontWeight = FontWeight.W300, lineHeight = 26.sp)
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorText, letterSpacing = (-0.3).sp)
    }
}

// ── Search bar ──

@Composable
private fun SearchBar(
    query: String,
    focused: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onBarcodeScan: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(
                if (focused) Color.White else ColorSurfaceMuted,
                RoundedCornerShape(12.dp)
            )
            .then(
                if (focused) Modifier.border(1.dp, ColorPrimary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🔍", fontSize = 14.sp)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(fontSize = 13.5.sp, color = ColorText),
            cursorBrush = SolidColor(ColorPrimary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFocusChange(false) }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Lebensmittel suchen oder Barcode scannen…", fontSize = 13.5.sp, color = ColorTextMuted)
                }
                inner()
            }
        )
        if (query.isEmpty()) {
            BarcodeScannerButton(
                onBarcodeScanned = onBarcodeScan,
                showAsIcon = true,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Text("×", fontSize = 18.sp, color = ColorTextMuted)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

// ── Section headers ──

@Composable
private fun IdleSectionHeader(eyebrow: String, title: String, action: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text(eyebrow, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = ColorTextFaint)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText)
        }
        Text(action, fontSize = 12.sp, color = ColorTextMuted)
    }
}

@Composable
private fun SearchSectionHeader(query: String, resultCount: Int, isLoading: Boolean) {
    val title = if (query.isBlank()) "Vorschläge" else "\"$query\""
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text("ERGEBNISSE", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = ColorTextFaint)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText)
        }
        if (!isLoading) Text("$resultCount Treffer", fontSize = 12.sp, color = ColorTextMuted)
    }
}

// ── Recent food item (idle section 1) ──

@Composable
private fun RecentFoodItem(food: Food, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    food.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text("${food.calories.roundToInt()} kcal/100g", fontSize = 11.5.sp, color = ColorTextMuted)
            }
            Spacer(Modifier.height(7.dp))
            MacroRow(
                protein = food.protein,
                fat = food.fat,
                carbs = food.carbohydrates,
                sugar = food.sugar
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.5.dp, ColorPrimary, CircleShape)
                .background(Color.White)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 20.sp, color = ColorPrimary, lineHeight = 20.sp)
        }
    }
}

// ── Search result card ──

@Composable
private fun SearchResultCard(food: Food, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                food.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorText,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text("${food.calories.roundToInt()} kcal/100g", fontSize = 11.sp, color = ColorTextMuted)
            Spacer(Modifier.height(7.dp))
            MacroRow(
                protein = food.protein,
                fat = food.fat,
                carbs = food.carbohydrates,
                sugar = food.sugar
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.5.dp, ColorPrimary, CircleShape)
                .background(Color.White)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 18.sp, color = ColorPrimary, lineHeight = 18.sp)
        }
    }
}

// ── Remote search result card (OpenFoodFacts) ──

@Composable
private fun RemoteSearchResultCard(
    result: RemoteFoodResult,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorText,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            result.brand?.let {
                Text(it, fontSize = 11.sp, color = ColorTextMuted, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text("${result.calories.roundToInt()} kcal/100g", fontSize = 11.sp, color = ColorTextMuted)
            Spacer(Modifier.height(7.dp))
            MacroRow(protein = result.protein, fat = result.fat, carbs = result.carbs, sugar = result.sugar)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.5.dp, ColorPrimary, CircleShape)
                .background(Color.White)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 18.sp, color = ColorPrimary, lineHeight = 18.sp)
        }
    }
}

// ── Nutri-Score badge ──

@Composable
private fun NutriScoreBadge(score: String) {
    val grades = listOf("A", "B", "C", "D", "E")
    val colors = mapOf(
        "A" to Color(0xFF1E8F3A),
        "B" to Color(0xFF7CB518),
        "C" to Color(0xFFF0C419),
        "D" to Color(0xFFE8821A),
        "E" to Color(0xFFE1442E)
    )
    Row(
        modifier = Modifier
            .border(1.dp, ColorBorder, RoundedCornerShape(5.dp))
            .background(Color.White, RoundedCornerShape(5.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        grades.forEach { grade ->
            val isActive = grade == score
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (isActive) (colors[grade] ?: Color.Gray) else Color(0xFFE5E5E3),
                        RoundedCornerShape(3.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    grade,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 9.5.sp
                )
            }
        }
    }
}

// ── Ingredient card (section 2) ──

@Composable
private fun IngredientCard(
    name: String,
    kcal: Int,
    amountGrams: Int,
    protein: Double,
    fat: Double,
    carbs: Double,
    sugar: Double,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorText)
                Text("$kcal kcal · pro Portion", fontSize = 12.sp, color = ColorTextMuted)
            }
            // Stepper
            Row(
                modifier = Modifier
                    .border(1.dp, ColorBorder, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(width = 30.dp, height = 32.dp).clickable(onClick = onMinus),
                    contentAlignment = Alignment.Center
                ) { Text("−", fontSize = 16.sp, color = ColorText) }
                Box(
                    modifier = Modifier.width(56.dp).height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text("$amountGrams", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(" g", fontSize = 14.sp, fontWeight = FontWeight.Normal, color = ColorTextMuted)
                    }
                }
                Box(
                    modifier = Modifier.size(width = 30.dp, height = 32.dp).clickable(onClick = onPlus),
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 16.sp, color = ColorText) }
            }
            // Remove
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("×", fontSize = 18.sp, color = ColorTextFaint) }
        }
        Spacer(Modifier.height(10.dp))
        MacroRow(protein = protein, fat = fat, carbs = carbs, sugar = sugar)
    }
}

// ── Gesamt block ──

@Composable
private fun Gesamt(kcal: Int, protein: Double, fat: Double, carbs: Double, sugar: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurfaceLavenderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GESAMT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = ColorTextMuted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$kcal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorPrimary, letterSpacing = (-0.3).sp)
                Text("kcal", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ColorTextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        MacroRow(protein = protein, fat = fat, carbs = carbs, sugar = sugar)
    }
}

// ── Save button ──

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorPrimary)
            .border(
                width = 2.dp,
                color = ColorPrimaryDark,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ── Empty state ──

@Composable
private fun SearchEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurfaceMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Nichts gefunden — als eigenes Produkt anlegen?",
            fontSize = 13.sp,
            color = ColorTextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class MacroPillColors(val background: Color, val text: Color, val dot: Color)

data class NutritionColors(
    val protein: MacroPillColors,
    val fat: MacroPillColors,
    val carbs: MacroPillColors,
    val sugar: MacroPillColors,
    val favoriteStar: Color,
    val favoriteBackground: Color
) {
    companion object {
        val Light = NutritionColors(
            protein = MacroPillColors(Color(0xFFFDE4D8), Color(0xFFC95A30), Color(0xFFE26A35)),
            fat = MacroPillColors(Color(0xFFF6ECC8), Color(0xFFA07A18), Color(0xFFC79628)),
            carbs = MacroPillColors(Color(0xFFD6ECD2), Color(0xFF2E7D3A), Color(0xFF3C9645)),
            sugar = MacroPillColors(Color(0xFFEAD7EF), Color(0xFF7A3F9C), Color(0xFF9B54BF)),
            favoriteStar = Color(0xFFFBC02D),
            favoriteBackground = Color(0xFFFFF9C4)
        )
        val Dark = NutritionColors(
            protein = MacroPillColors(Color(0xFF4A2018), Color(0xFFFF8A50), Color(0xFFFF6B35)),
            fat = MacroPillColors(Color(0xFF3E3000), Color(0xFFFFD54F), Color(0xFFC79628)),
            carbs = MacroPillColors(Color(0xFF1A3320), Color(0xFF81C784), Color(0xFF4CAF50)),
            sugar = MacroPillColors(Color(0xFF2E1A40), Color(0xFFCE93D8), Color(0xFFAB47BC)),
            favoriteStar = Color(0xFFFFD54F),
            favoriteBackground = Color(0xFF3E3000)
        )
    }
}

@Composable
fun nutritionColors(): NutritionColors {
    val surface = MaterialTheme.colorScheme.surface
    val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
    return if (luminance < 0.5f) NutritionColors.Dark else NutritionColors.Light
}

@Composable
fun MacroRow(protein: Double, fat: Double, carbs: Double, sugar: Double) {
    val colors = nutritionColors()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MacroChip("P", protein, colors.protein)
        MacroChip("F", fat, colors.fat)
        MacroChip("KH", carbs, colors.carbs)
        MacroChip("Z", sugar, colors.sugar)
    }
}

@Composable
private fun MacroChip(label: String, value: Double, colors: MacroPillColors) {
    Row(
        modifier = Modifier.background(colors.background, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(colors.dot, CircleShape))
        Text(
            text = "$label ${value.roundToInt()}g",
            style = MaterialTheme.typography.labelSmall,
            color = colors.text,
            maxLines = 1,
            softWrap = false
        )
    }
}

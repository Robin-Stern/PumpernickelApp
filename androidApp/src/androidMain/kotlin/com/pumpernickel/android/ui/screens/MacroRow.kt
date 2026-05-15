package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

data class NutritionColors(
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    val sugar: Color,
    val favoriteStar: Color,
    val favoriteBackground: Color
) {
    companion object {
        val Light = NutritionColors(
            protein = Color(0xFFE65100),
            fat = Color(0xFFC29107),
            carbs = Color(0xFF2E7D32),
            sugar = Color(0xFF6A1B9A),
            favoriteStar = Color(0xFFFBC02D),
            favoriteBackground = Color(0xFFFFF9C4)
        )
        val Dark = NutritionColors(
            protein = Color(0xFFFF8A50),
            fat = Color(0xFFFFD54F),
            carbs = Color(0xFF81C784),
            sugar = Color(0xFFCE93D8),
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
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroChip("P", protein, colors.protein, Modifier.weight(1f))
            MacroChip("F", fat, colors.fat, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroChip("KH", carbs, colors.carbs, Modifier.weight(1f))
            MacroChip("Z", sugar, colors.sugar, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroChip(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = "$label ${value.roundToInt()}g",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

package com.example.pumpernickelapp.food.ui.components

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
import com.example.pumpernickelapp.food.ui.AppColors
import kotlin.math.roundToInt

@Composable
fun MacroRow(protein: Double, fat: Double, carbs: Double, sugar: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MacroChip(label = "P",  value = protein, color = AppColors.protein)
        MacroChip(label = "F",  value = fat,     color = AppColors.fat)
        MacroChip(label = "KH", value = carbs,   color = AppColors.carbs)
        MacroChip(label = "Z",  value = sugar,   color = AppColors.sugar)
    }
}

@Composable
private fun MacroChip(label: String, value: Double, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            "$label ${value.roundToInt()}g",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

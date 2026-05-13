package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.ai.MacrosFitIndicator
import com.pumpernickel.domain.ai.RecipeAiPreview
import com.pumpernickel.domain.ai.StagedExercise
import com.pumpernickel.domain.ai.StagedFood
import com.pumpernickel.domain.ai.StagedRecipeIngredient
import com.pumpernickel.domain.ai.StagedTemplate
import com.pumpernickel.domain.ai.StagedTemplateExercise
import com.pumpernickel.domain.ai.WorkoutAiPreview
import kotlin.math.abs

/**
 * Mirror of iOS AIPreviewSheet.swift. Polymorphic preview body rendered inside
 * a ModalBottomSheet hosted by the parent screen. The parent supplies
 * onSaveAll / onDiscard callbacks that drive the backing ViewModel transition.
 */
sealed interface AiPreviewContent {
    data class Workout(val preview: WorkoutAiPreview) : AiPreviewContent
    data class Recipe(val preview: RecipeAiPreview) : AiPreviewContent
}

@Composable
fun AiPreviewSheetBody(
    content: AiPreviewContent,
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AiPreviewHeader(onSaveAll = onSaveAll, onDiscard = onDiscard)
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (content) {
                is AiPreviewContent.Workout -> workoutBody(content.preview)
                is AiPreviewContent.Recipe -> recipeBody(content.preview)
            }
        }
    }
}

@Composable
private fun AiPreviewHeader(onSaveAll: () -> Unit, onDiscard: () -> Unit) {
    Surface(tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onDiscard) {
                Text(
                    text = "Verwerfen",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Vorschau",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onSaveAll) {
                Text("Alle speichern")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.workoutBody(
    preview: WorkoutAiPreview
) {
    items(count = preview.templates.size) { idx ->
        StagedTemplateCard(template = preview.templates[idx])
    }
    if (preview.inlineNewExercises.isNotEmpty()) {
        item {
            Text(
                text = "Neu generierte Übungen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        items(count = preview.inlineNewExercises.size) { idx ->
            InlineNewItemRow(name = preview.inlineNewExercises[idx].name)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recipeBody(
    preview: RecipeAiPreview
) {
    item {
        Text(
            text = preview.recipe.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
    item { SectionHeader("Zutaten") }
    items(count = preview.recipe.ingredients.size) { idx ->
        IngredientRow(preview.recipe.ingredients[idx])
    }
    item { SectionHeader("Zubereitung") }
    items(count = preview.recipe.steps.size) { idx ->
        StepRow(index = idx + 1, text = preview.recipe.steps[idx])
    }
    item {
        SectionHeader("Passt zu deinen Zielen?")
        FitsIndicatorCard(preview.fitsIndicator)
    }
    if (preview.inlineNewFoods.isNotEmpty()) {
        item { SectionHeader("Neue Lebensmittel") }
        items(count = preview.inlineNewFoods.size) { idx ->
            InlineNewFoodRow(preview.inlineNewFoods[idx])
        }
    }
}

@Composable
private fun StagedTemplateCard(template: StagedTemplate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            template.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            template.exercises.forEach { ex ->
                StagedTemplateExerciseRow(ex)
            }
        }
    }
}

@Composable
private fun StagedTemplateExerciseRow(ex: StagedTemplateExercise) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${ex.targetSets}×${ex.targetReps}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = ex.exerciseName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${ex.restPeriodSec}s Pause",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InlineNewItemRow(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun IngredientRow(ingredient: StagedRecipeIngredient) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${ingredient.amountGrams.toInt()}g",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = ingredient.foodName,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StepRow(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FitsIndicatorCard(indicator: MacrosFitIndicator) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MacroFitRow("Kalorien", indicator.deltaKcalPercent)
            MacroFitRow("Protein", indicator.deltaProteinPercent)
            MacroFitRow("Fett", indicator.deltaFatPercent)
            MacroFitRow("Kohlenhydrate", indicator.deltaCarbsPercent)
            MacroFitRow("Zucker", indicator.deltaSugarPercent)
            Spacer(modifier = Modifier.height(4.dp))
            val summaryColor = if (indicator.fitsAll) Color(0xFF2E7D32) else Color(0xFFE65100)
            Text(
                text = if (indicator.fitsAll)
                    "Passt zu deinen verbleibenden Zielen."
                else
                    "Weicht von deinen verbleibenden Zielen ab.",
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MacroFitRow(label: String, deltaPercent: Double) {
    val isHigh = abs(deltaPercent) > 10.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        val sign = if (deltaPercent >= 0) "+" else ""
        Text(
            text = "$sign${deltaPercent.toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHigh) Color(0xFFE65100)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineNewFoodRow(food: StagedFood) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = food.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${food.calories.toInt()} kcal/100g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

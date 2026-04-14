package com.pumpernickel.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.MuscleRegionPath
import com.pumpernickel.domain.model.MuscleRegionPaths
import com.pumpernickel.domain.model.NutritionGoals
import com.pumpernickel.domain.model.RecipeMacros
import com.pumpernickel.presentation.overview.OverviewUiState
import com.pumpernickel.presentation.overview.OverviewViewModel
import com.pumpernickel.presentation.overview.TrainingIntensity
import org.koin.compose.viewmodel.koinViewModel

// ── Intensity colors (RIR-weighted) ──

private val IntensityNone = Color(0xFF3A3A3A)
private val IntensityLow = Color(0xAAFF6B6B)      // Red — under-trained
private val IntensityModerate = Color(0xAAFFD54F)  // Yellow — maintenance
private val IntensityHigh = Color(0xFF66BB6A)      // Green — growth stimulus

private fun intensityColor(intensity: TrainingIntensity): Color = when (intensity) {
    TrainingIntensity.NONE -> IntensityNone
    TrainingIntensity.LOW -> IntensityLow
    TrainingIntensity.MODERATE -> IntensityModerate
    TrainingIntensity.HIGH -> IntensityHigh
}

// ── Ring colors ──

private val CalorieRingColor = Color(0xFFFF6B6B)
private val ProteinRingColor = Color(0xFF4FC3F7)
private val CarbRingColor = Color(0xFFFFD54F)
private val FatRingColor = Color(0xFFFF8A65)
private val SugarRingColor = Color(0xFFBA68C8)

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Übersicht", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Muscle Activity Section ──
                MuscleActivityCard(uiState)

                // ── Nutrition Rings Section ──
                NutritionRingsCard(uiState)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Muscle Activity Card
// ══════════════════════════════════════════════════════════════

@Composable
private fun MuscleActivityCard(uiState: OverviewUiState) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Muskelbelastung · 7 Tage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Scoring info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OverviewAnatomyCanvas(
                    outlinePaths = MuscleRegionPaths.frontOutline,
                    regions = MuscleRegionPaths.frontRegions,
                    muscleLoad = uiState.muscleLoad,
                    modifier = Modifier.weight(1f)
                )
                OverviewAnatomyCanvas(
                    outlinePaths = MuscleRegionPaths.backOutline,
                    regions = MuscleRegionPaths.backRegions,
                    muscleLoad = uiState.muscleLoad,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                LegendItem("Niedrig", IntensityLow)
                LegendItem("Mittel", IntensityModerate)
                LegendItem("Hoch", IntensityHigh)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("RIR-basierte Bewertung") },
            text = {
                Text(
                    "Jeder Satz wird nach dem RIR-Wert (Reps in Reserve) gewichtet:\n\n" +
                    "• RIR 4+ → 0,5×\n" +
                    "• RIR 2-3 → 1,0×\n" +
                    "• RIR 1 → 1,5×\n" +
                    "• RIR 0 → 2,0×\n\n" +
                    "Primäre Muskeln: volle Wertung\n" +
                    "Sekundäre Muskeln: halbe Wertung\n\n" +
                    "Wochenbewertung:\n" +
                    "🔴 Niedrig: Score < 5\n" +
                    "🟡 Mittel: Score 5-12\n" +
                    "🟢 Hoch: Score 13+"
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverviewAnatomyCanvas(
    outlinePaths: List<String>,
    regions: List<MuscleRegionPath>,
    muscleLoad: Map<MuscleGroup, TrainingIntensity>,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    val parsedOutlines = remember(outlinePaths) {
        outlinePaths.mapNotNull { svgData ->
            runCatching { PathParser().parsePathString(svgData).toPath() }.getOrNull()
        }
    }

    val parsedRegions = remember(regions) {
        regions.mapNotNull { region ->
            runCatching {
                region to PathParser().parsePathString(region.pathData).toPath()
            }.getOrNull()
        }
    }

    var canvasWidth by remember { mutableFloatStateOf(0f) }

    val scaledOutlines = remember(parsedOutlines, canvasWidth) {
        if (canvasWidth <= 0f) return@remember emptyList()
        val scale = canvasWidth / MuscleRegionPaths.VIEW_BOX_WIDTH
        val matrix = Matrix().apply { scale(scale, scale) }
        parsedOutlines.map { path ->
            Path().apply { addPath(path); transform(matrix) }
        }
    }

    val scaledRegions = remember(parsedRegions, canvasWidth) {
        if (canvasWidth <= 0f) return@remember emptyList()
        val scale = canvasWidth / MuscleRegionPaths.VIEW_BOX_WIDTH
        val matrix = Matrix().apply { scale(scale, scale) }
        parsedRegions.map { (data, path) ->
            data to Path().apply { addPath(path); transform(matrix) }
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(MuscleRegionPaths.VIEW_BOX_WIDTH / MuscleRegionPaths.VIEW_BOX_HEIGHT)
    ) {
        canvasWidth = size.width

        scaledOutlines.forEach { path ->
            drawPath(path, outlineColor)
        }

        scaledRegions.forEach { (data, path) ->
            val group = MuscleGroup.fromDbName(data.groupName)
            val intensity = if (group != null) muscleLoad[group] ?: TrainingIntensity.NONE else TrainingIntensity.NONE
            drawPath(path, intensityColor(intensity))
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Nutrition Rings Card
// ══════════════════════════════════════════════════════════════

@Composable
private fun NutritionRingsCard(uiState: OverviewUiState) {
    val macros = uiState.todayMacros
    val goals = uiState.nutritionGoals

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ernährung · Heute",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Main calorie ring
            CalorieRing(
                current = macros.calories,
                goal = goals.calorieGoal.toDouble(),
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Macro rings row
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                MacroRingItem(
                    label = "Protein",
                    current = macros.protein,
                    goal = goals.proteinGoal.toDouble(),
                    unit = "g",
                    color = ProteinRingColor
                )
                MacroRingItem(
                    label = "Kohlenh.",
                    current = macros.carbs,
                    goal = goals.carbGoal.toDouble(),
                    unit = "g",
                    color = CarbRingColor
                )
                MacroRingItem(
                    label = "Fett",
                    current = macros.fat,
                    goal = goals.fatGoal.toDouble(),
                    unit = "g",
                    color = FatRingColor
                )
                MacroRingItem(
                    label = "Zucker",
                    current = macros.sugar,
                    goal = goals.sugarGoal.toDouble(),
                    unit = "g",
                    color = SugarRingColor
                )
            }
        }
    }
}

// ── Calorie ring (large, centered with text) ──

@Composable
private fun CalorieRing(
    current: Double,
    goal: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1.5f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "calorie_progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background track
            drawArc(
                color = CalorieRingColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = CalorieRingColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = current.toInt().toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CalorieRingColor
            )
            Text(
                text = "/ ${goal.toInt()} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Macro ring item (small ring + label) ──

@Composable
private fun MacroRingItem(
    label: String,
    current: Double,
    goal: Double,
    unit: String,
    color: Color
) {
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1.5f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "${label}_progress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val strokeWidth = 6.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background track
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Text(
                text = "${current.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${current.toInt()}/${goal.toInt()}$unit",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

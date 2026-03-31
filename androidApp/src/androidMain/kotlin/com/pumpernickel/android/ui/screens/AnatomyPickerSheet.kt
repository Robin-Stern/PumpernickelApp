package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.domain.model.MuscleRegionPath
import com.pumpernickel.domain.model.MuscleRegionPaths

private val OutlineColor = Color(0xFF2A2A2A)
private val UnselectedRegionColor = Color(0xFF3A3A3A)
private val SelectedRegionColor = Color(0xCC66BB6A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnatomyPickerSheet(
    selectedGroup: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localSelection by remember { mutableStateOf(selectedGroup) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Muscle Group",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AnatomyBodyCanvas(
                    outlinePaths = MuscleRegionPaths.frontOutline,
                    regions = MuscleRegionPaths.frontRegions,
                    selectedGroup = localSelection,
                    onRegionTapped = { localSelection = it },
                    modifier = Modifier.weight(1f)
                )
                AnatomyBodyCanvas(
                    outlinePaths = MuscleRegionPaths.backOutline,
                    regions = MuscleRegionPaths.backRegions,
                    selectedGroup = localSelection,
                    onRegionTapped = { localSelection = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val displayName = localSelection?.let { MuscleGroup.fromDbName(it)?.displayName }
            if (displayName != null) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Tap a muscle group",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    localSelection?.let {
                        onConfirm(it)
                        onDismiss()
                    }
                },
                enabled = localSelection != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text("Select", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class ParsedRegion(
    val data: MuscleRegionPath,
    val path: Path
)

@Composable
private fun AnatomyBodyCanvas(
    outlinePaths: List<String>,
    regions: List<MuscleRegionPath>,
    selectedGroup: String?,
    onRegionTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedOutlines = remember(outlinePaths) {
        outlinePaths.mapNotNull { svgData ->
            runCatching { PathParser().parsePathString(svgData).toPath() }.getOrNull()
        }
    }

    val parsedRegions = remember(regions) {
        regions.mapNotNull { region ->
            runCatching {
                ParsedRegion(region, PathParser().parsePathString(region.pathData).toPath())
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
        parsedRegions.map { parsed ->
            ParsedRegion(
                parsed.data,
                Path().apply { addPath(parsed.path); transform(matrix) }
            )
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(MuscleRegionPaths.VIEW_BOX_WIDTH / MuscleRegionPaths.VIEW_BOX_HEIGHT)
            .pointerInput(scaledRegions, selectedGroup) {
                detectTapGestures { offset ->
                    for (region in scaledRegions.reversed()) {
                        val bounds = region.path.getBounds()
                        if (bounds.contains(offset)) {
                            onRegionTapped(region.data.groupName)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        canvasWidth = size.width

        scaledOutlines.forEach { path ->
            drawPath(path, OutlineColor)
        }

        scaledRegions.forEach { (data, path) ->
            val color = if (data.groupName == selectedGroup) SelectedRegionColor else UnselectedRegionColor
            drawPath(path, color)
        }
    }
}

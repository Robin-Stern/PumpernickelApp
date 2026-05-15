package com.pumpernickel.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

/**
 * Optimized wheel picker that minimizes recompositions and matches iOS aesthetics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrumPicker(
    items: List<Int>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    displayTransform: (Int) -> String = { it.toString() },
    visibleItemCount: Int = 5
) {
    val itemHeightDp = 40.dp
    val pickerHeightDp = itemHeightDp * visibleItemCount
    val spacerCount = visibleItemCount / 2

    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(listState)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    // Optimization: derive center index based on exact scroll position
    val centerIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val first = listState.firstVisibleItemIndex
            if (offset > itemHeightPx / 2f) first + 1 else first
        }
    }

    // Initialization and Sync
    var isInitialScroll by remember { mutableStateOf(true) }

    LaunchedEffect(selectedItem) {
        val targetIndex = items.indexOf(selectedItem)
        if (targetIndex >= 0 && !listState.isScrollInProgress) {
            if (isInitialScroll) {
                delay(100) // Small buffer for initial layout
                listState.scrollToItem(targetIndex)
                isInitialScroll = false
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (!isInitialScroll) {
                    onItemSelected(items[centerIndex.coerceIn(0, items.lastIndex)])
                }
            }
    }

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
            )
        }

        Box(modifier = Modifier.height(pickerHeightDp), contentAlignment = Alignment.Center) {
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(spacerCount) { Spacer(Modifier.height(itemHeightDp)) }
                itemsIndexed(items) { index, item ->
                    val distance = kotlin.math.abs(index - centerIndex)
                    val alpha = when (distance) {
                        0 -> 1f
                        1 -> 0.6f
                        else -> 0.3f
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(itemHeightDp)
                            .fillMaxWidth()
                            .alpha(alpha)
                    ) {
                        Text(
                            text = displayTransform(item),
                            style = if (distance == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (distance == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(spacerCount) { Spacer(Modifier.height(itemHeightDp)) }
            }

            // iOS-style selection indicator
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                HorizontalDivider(color = dividerColor)
                Spacer(Modifier.height(itemHeightDp))
                HorizontalDivider(color = dividerColor)
            }
        }
    }
}

@Composable
fun RepsPicker(selectedReps: Int, onRepsSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    DrumPicker(items = (0..50).toList(), selectedItem = selectedReps, onItemSelected = onRepsSelected, modifier = modifier, label = "Wdh.", displayTransform = { it.toString() })
}

@Composable
fun WeightPicker(selectedWeightKgX10: Int, onWeightSelected: (Int) -> Unit, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    DrumPicker(items = (0..10000 step 25).toList(), selectedItem = selectedWeightKgX10, onItemSelected = onWeightSelected, modifier = modifier, label = "Gewicht (${weightUnit.label})", displayTransform = { weightUnit.formatWeight(it) })
}

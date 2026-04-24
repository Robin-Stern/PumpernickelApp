package com.pumpernickel.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.flow.filter

/**
 * A drum/wheel picker composable that mimics iOS UIPickerView(.wheel) scroll behavior.
 *
 * Supports fling momentum with snap-to-item, visual feedback (highlighted center item,
 * faded items above/below), and a selection callback.
 *
 * @param items The list of integer values to display.
 * @param selectedItem The initially selected value (must be in [items]).
 * @param onItemSelected Called when the user settles on a new value after scrolling.
 * @param modifier Optional modifier for the outer Column.
 * @param label Optional label text displayed above the picker.
 * @param displayTransform Converts a raw integer item to a display string.
 * @param visibleItemCount Number of items visible at once (must be odd). Default 5.
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

    // Scroll to the initial selected item on first composition or when selectedItem changes.
    LaunchedEffect(selectedItem) {
        val targetIndex = items.indexOf(selectedItem)
        if (targetIndex >= 0) {
            listState.scrollToItem(targetIndex + spacerCount)
        }
    }

    // Detect when scrolling has stopped and report the centred item.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val centreIndex = listState.firstVisibleItemIndex + spacerCount - spacerCount
                // firstVisibleItemIndex is 0-based over the full list including spacers.
                // Subtract spacerCount to get the real items index.
                val realIndex = listState.firstVisibleItemIndex
                if (realIndex in items.indices) {
                    onItemSelected(items[realIndex])
                }
            }
    }

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
            )
        }

        Box {
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier
                    .height(pickerHeightDp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top spacer items so the first real item can scroll to the centre.
                items(spacerCount) {
                    Box(modifier = Modifier.height(itemHeightDp))
                }

                itemsIndexed(items) { index, item ->
                    // Determine visual distance from the centre position.
                    val centreVisibleIndex = listState.firstVisibleItemIndex + spacerCount
                    val distance = kotlin.math.abs((index + spacerCount) - centreVisibleIndex)
                    val alpha = maxOf(0.2f, 1f - distance * 0.3f)

                    val textStyle = when (distance) {
                        0 -> MaterialTheme.typography.headlineMedium
                        1 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyMedium
                    }
                    val fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(itemHeightDp)
                            .fillMaxWidth()
                            .alpha(alpha)
                    ) {
                        Text(
                            text = displayTransform(item),
                            style = textStyle,
                            fontWeight = fontWeight,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Bottom spacer items so the last real item can scroll to the centre.
                items(spacerCount) {
                    Box(modifier = Modifier.height(itemHeightDp))
                }
            }

            // Centre selection indicator lines.
            val dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            val centreTop = itemHeightDp * spacerCount
            val centreBottom = centreTop + itemHeightDp

            HorizontalDivider(
                modifier = Modifier.padding(top = centreTop),
                color = dividerColor
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = centreBottom),
                color = dividerColor
            )
        }
    }
}

/**
 * Convenience composable for selecting a rep count (0-50).
 */
@Composable
fun RepsPicker(
    selectedReps: Int,
    onRepsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    DrumPicker(
        items = (0..50).toList(),
        selectedItem = selectedReps,
        onItemSelected = onRepsSelected,
        modifier = modifier,
        label = "Reps",
        displayTransform = { it.toString() }
    )
}

/**
 * Convenience composable for selecting a weight value.
 *
 * Internally works with kgX10 integers (0-10000 in steps of 25, representing 0-1000 kg in
 * 2.5 kg increments). Display is handled by [WeightUnit.formatWeight].
 */
@Composable
fun WeightPicker(
    selectedWeightKgX10: Int,
    onWeightSelected: (Int) -> Unit,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    DrumPicker(
        items = (0..10000 step 25).toList(),
        selectedItem = selectedWeightKgX10,
        onItemSelected = onWeightSelected,
        modifier = modifier,
        label = "Weight (${weightUnit.label})",
        displayTransform = { weightUnit.formatWeight(it) }
    )
}

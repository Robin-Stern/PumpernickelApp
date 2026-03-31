package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.WorkoutHistoryDetailRoute
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.domain.model.WorkoutSummary
import com.pumpernickel.presentation.history.WorkoutHistoryViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryListScreen(navController: NavHostController) {
    val viewModel: WorkoutHistoryViewModel = koinViewModel()
    val summaries by viewModel.workoutSummaries.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (summaries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Workouts Yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Complete a workout to see your history here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(summaries, key = { it.id }) { summary ->
                    WorkoutHistorySummaryItem(
                        summary = summary,
                        weightUnit = weightUnit,
                        onClick = {
                            navController.navigate(WorkoutHistoryDetailRoute(workoutId = summary.id))
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistorySummaryItem(
    summary: WorkoutSummary,
    weightUnit: WeightUnit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = summary.name,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            val count = summary.exerciseCount
            Row {
                Text("$count exercise${if (count == 1) "" else "s"}")
                Text(
                    text = " · ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(weightUnit.formatVolume(summary.totalVolumeKgX10))
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDate(summary.startTimeMillis),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDuration(summary.durationMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}

private fun formatDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

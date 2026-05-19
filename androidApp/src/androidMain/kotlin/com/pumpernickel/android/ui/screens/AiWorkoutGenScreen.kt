package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.AiSettingsRoute
import com.pumpernickel.domain.ai.AiError
import com.pumpernickel.domain.ai.StreamingText
import com.pumpernickel.domain.ai.WorkoutAiSplit
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.presentation.ai.WorkoutAiUiState
import com.pumpernickel.presentation.ai.WorkoutAiViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorkoutGenScreen(
    navController: NavHostController,
    viewModel: WorkoutAiViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()

    // Mirrors iOS AIWorkoutGenView.swift's Saved-state dismiss.
    LaunchedEffect(uiState) {
        if (uiState is WorkoutAiUiState.Saved) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-Workout") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                WorkoutAiUiState.NoKey -> NoKeyBody(
                    onOpenSettings = { navController.navigate(AiSettingsRoute) }
                )

                is WorkoutAiUiState.Form -> WorkoutFormBody(
                    state = state,
                    onMusclesChanged = viewModel::onMusclesChanged,
                    onExerciseCountChanged = viewModel::onExerciseCountChanged,
                    onSetsPerExerciseChanged = viewModel::onSetsPerExerciseChanged,
                    onSplitStyleChanged = viewModel::onSplitStyleChanged,
                    onGenerate = viewModel::generate
                )

                is WorkoutAiUiState.Generating -> GeneratingBody(
                    streamingText = streamingText,
                    skeletonRowCount = state.skeletonRowCount,
                    headline = "${state.skeletonRowCount} Übungen",
                    onCancel = viewModel::cancel
                )

                is WorkoutAiUiState.Preview -> {
                    // Keep the originating form rendered behind the sheet so a
                    // discard returns the user straight into edit mode without a flash.
                    WorkoutFormBody(
                        state = state.originatingForm,
                        onMusclesChanged = {},
                        onExerciseCountChanged = {},
                        onSetsPerExerciseChanged = {},
                        onSplitStyleChanged = {},
                        onGenerate = {}
                    )
                    WorkoutPreviewSheet(
                        preview = state.preview,
                        onSaveAll = viewModel::save,
                        onDiscard = viewModel::discardPreview
                    )
                }

                is WorkoutAiUiState.Error -> ErrorBody(
                    error = state.error,
                    onOpenSettings = { navController.navigate(AiSettingsRoute) },
                    onRetry = viewModel::retryFromError
                )

                is WorkoutAiUiState.Saved -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutFormBody(
    state: WorkoutAiUiState.Form,
    onMusclesChanged: (List<MuscleGroup>) -> Unit,
    onExerciseCountChanged: (Int) -> Unit,
    onSetsPerExerciseChanged: (Int) -> Unit,
    onSplitStyleChanged: (WorkoutAiSplit) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionLabel("Zielmuskeln")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(MuscleGroup.entries.toList()) { group ->
                val selected = state.targetMuscles.contains(group)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val newList = if (selected) state.targetMuscles - group
                        else state.targetMuscles + group
                        onMusclesChanged(newList)
                    },
                    label = { Text(group.displayName) }
                )
            }
        }

        SectionLabel("Anzahl Übungen")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onExerciseCountChanged(state.exerciseCount - 1) },
                enabled = state.exerciseCount > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Weniger")
            }
            Text(
                text = "${state.exerciseCount} Übungen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { onExerciseCountChanged(state.exerciseCount + 1) },
                enabled = state.exerciseCount < 12
            ) {
                Icon(Icons.Default.Add, contentDescription = "Mehr")
            }
        }

        SectionLabel("Anzahl Sätze")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onSetsPerExerciseChanged(state.setsPerExercise - 1) },
                enabled = state.setsPerExercise > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Weniger Sätze")
            }
            Text(
                text = "${state.setsPerExercise} Sätze",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { onSetsPerExerciseChanged(state.setsPerExercise + 1) },
                enabled = state.setsPerExercise < 6
            ) {
                Icon(Icons.Default.Add, contentDescription = "Mehr Sätze")
            }
        }

        SectionLabel("Aufteilung")
        var splitExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = splitExpanded,
            onExpandedChange = { splitExpanded = it }
        ) {
            OutlinedTextField(
                value = splitLabel(state.splitStyle),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = splitExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = splitExpanded,
                onDismissRequest = { splitExpanded = false }
            ) {
                WorkoutAiSplit.entries.forEach { split ->
                    DropdownMenuItem(
                        text = { Text(splitLabel(split)) },
                        onClick = {
                            onSplitStyleChanged(split)
                            splitExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onGenerate,
            enabled = state.targetMuscles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Generieren")
        }
    }
}

private fun splitLabel(split: WorkoutAiSplit): String = when (split) {
    WorkoutAiSplit.NONE -> "Keine Aufteilung"
    WorkoutAiSplit.PUSH_PULL_LEGS -> "Push / Pull / Legs"
    WorkoutAiSplit.UPPER_LOWER -> "Upper / Lower"
    WorkoutAiSplit.FULL_BODY -> "Full Body"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutPreviewSheet(
    preview: com.pumpernickel.domain.ai.WorkoutAiPreview,
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { /* dismiss only via buttons — mirrors iOS interactiveDismissDisabled */ },
        sheetState = sheetState
    ) {
        AiPreviewSheetBody(
            content = AiPreviewContent.Workout(preview),
            onSaveAll = onSaveAll,
            onDiscard = onDiscard
        )
    }
}

@Composable
internal fun NoKeyBody(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Kein API-Schlüssel gespeichert",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Füge in den KI-Einstellungen einen Schlüssel hinzu, um KI-Generierung zu nutzen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Text("KI-Einstellungen öffnen")
        }
    }
}

@Composable
internal fun GeneratingBody(
    streamingText: StreamingText,
    skeletonRowCount: Int,
    headline: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = "KI denkt nach…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StreamingPanel(
            streamingText = streamingText,
            skeletonRowCount = skeletonRowCount,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abbrechen")
        }
    }
}

@Composable
private fun StreamingPanel(
    streamingText: StreamingText,
    skeletonRowCount: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(streamingText.content, streamingText.reasoning) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val nothingYet = streamingText.content.isBlank() && streamingText.reasoning.isBlank()
            if (nothingYet) {
                repeat(skeletonRowCount.coerceAtLeast(1)) {
                    SkeletonRow()
                }
            } else {
                if (streamingText.reasoning.isNotBlank()) {
                    Text(
                        text = "Gedanken",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = streamingText.reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (streamingText.content.isNotBlank()) {
                    Text(
                        text = "Antwort",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = streamingText.content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {}
}

@Composable
internal fun ErrorBody(
    error: AiError,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    val (title, body, isAuthOrQuota) = describeError(error)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isAuthOrQuota) {
            Button(onClick = onOpenSettings) {
                Text("KI-Einstellungen öffnen")
            }
        } else {
            Button(onClick = onRetry) {
                Text("Wiederholen")
            }
        }
    }
}

private data class ErrorCopy(val title: String, val body: String, val isAuthOrQuota: Boolean)

private fun describeError(error: AiError): ErrorCopy = when (error) {
    AiError.Timeout -> ErrorCopy(
        title = "Zeitüberschreitung",
        body = "Die KI hat zu lange gebraucht. Versuche es nochmal.",
        isAuthOrQuota = false
    )
    is AiError.Network -> ErrorCopy(
        title = "Netzwerkfehler",
        body = "Prüfe deine Internetverbindung und versuche es nochmal." +
            if (error.detail != null) "\n\nDetails: ${error.detail}" else "",
        isAuthOrQuota = false
    )
    is AiError.AuthOrQuota -> ErrorCopy(
        title = "Schlüssel oder Kontingent ungültig",
        body = "Dein API-Schlüssel wurde abgelehnt (HTTP ${error.httpStatus}). " +
            "Prüfe ihn in den KI-Einstellungen oder lade dein Kontingent auf.",
        isAuthOrQuota = true
    )
    is AiError.Provider -> ErrorCopy(
        title = "Anbieter nicht erreichbar",
        body = "Der KI-Anbieter meldet einen Serverfehler (HTTP ${error.httpStatus}). Bitte später erneut versuchen.",
        isAuthOrQuota = false
    )
    is AiError.SchemaInvalid -> ErrorCopy(
        title = "Antwort konnte nicht verarbeitet werden",
        body = "Die KI lieferte ein ungültiges Format. ${error.detail}",
        isAuthOrQuota = false
    )
    AiError.Cancelled -> ErrorCopy(
        title = "Abgebrochen",
        body = "Die Generierung wurde abgebrochen.",
        isAuthOrQuota = false
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

package com.pumpernickel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.presentation.progresspic.ProgressPicturePromptViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Post-workout prompt card (D-17-01). Mounted on `WorkoutSessionScreen`'s
 * Finished branch above the Done button. Non-blocking — the host's Done button
 * stays enabled regardless of this card's state.
 *
 * The card hosts THREE buttons (D-17-03): Foto aufnehmen (camera), Aus Galerie
 * (library), Überspringen (skip). After the first successful save the header
 * copy switches to "Noch ein Foto?" (D-17-02).
 *
 * The composable is a thin shell over `ProgressPicturePromptViewModel`. The VM
 * is shared across Android (this file) and iOS (handoff doc 17-08).
 */
@Composable
fun ProgressPicturePromptCard(
    workoutId: Long,
    modifier: Modifier = Modifier,
    viewModel: ProgressPicturePromptViewModel = koinViewModel { parametersOf(workoutId) }
) {
    val state by viewModel.uiState.collectAsState()

    if (state.dismissed) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header copy — D-17-02 switches when photoCount > 0
            Text(
                text = if (state.showAddAnother) "Noch ein Foto?" else "Fortschritts-Foto?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (state.showAddAnother)
                    "Du kannst weitere Fotos zu diesem Workout anhängen."
                else
                    "Halte deinen Fortschritt fest — Fotos bleiben verschlüsselt auf deinem Gerät.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Three actions row — D-17-03
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.onTakePhotoClick() },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp).width(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Foto aufnehmen", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { viewModel.onPickFromLibraryClick() },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp).width(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Aus Galerie", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Speichere…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (state.photoCount > 0) {
                    Text(
                        "${state.photoCount} Foto${if (state.photoCount == 1) "" else "s"} angehängt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.width(0.dp))
                }

                TextButton(onClick = { viewModel.onSkipClick() }) {
                    Text(if (state.photoCount > 0) "Fertig" else "Überspringen")
                }
            }

            state.error?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

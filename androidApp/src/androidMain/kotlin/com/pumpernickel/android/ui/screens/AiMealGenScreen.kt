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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.AiSettingsRoute
import com.pumpernickel.domain.ai.RemainingMacros
import com.pumpernickel.presentation.ai.RecipeAiUiState
import com.pumpernickel.presentation.ai.RecipeAiViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMealGenScreen(
    navController: NavHostController,
    viewModel: RecipeAiViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onAppearRefresh()
    }
    LaunchedEffect(uiState) {
        if (uiState is RecipeAiUiState.Saved) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI-Rezept") },
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
                RecipeAiUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                RecipeAiUiState.NoKey -> NoKeyBody(
                    onOpenSettings = { navController.navigate(AiSettingsRoute) }
                )

                is RecipeAiUiState.RemainingExhausted -> RemainingExhaustedBody(state.remaining)

                is RecipeAiUiState.Form -> MealFormBody(
                    remaining = state.remaining,
                    onGenerate = viewModel::generate
                )

                RecipeAiUiState.Generating -> GeneratingBody(
                    streamingText = streamingText,
                    skeletonRowCount = 5,
                    headline = "Rezept aus Restmakros",
                    onCancel = viewModel::cancel
                )

                is RecipeAiUiState.Preview -> {
                    MealFormBody(
                        remaining = state.originatingRemaining,
                        onGenerate = {}
                    )
                    RecipePreviewSheet(
                        preview = state.preview,
                        onSaveAll = viewModel::save,
                        onDiscard = viewModel::discardPreview
                    )
                }

                is RecipeAiUiState.Error -> ErrorBody(
                    error = state.error,
                    onOpenSettings = { navController.navigate(AiSettingsRoute) },
                    onRetry = viewModel::retryFromError
                )

                RecipeAiUiState.Saved -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MealFormBody(
    remaining: RemainingMacros,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Verbleibend heute",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroRow("Kalorien", "${remaining.kcal.toInt()} kcal")
                MacroRow("Protein", "${remaining.protein.toInt()} g")
                MacroRow("Fett", "${remaining.fat.toInt()} g")
                MacroRow("Kohlenhydrate", "${remaining.carbs.toInt()} g")
                MacroRow("Zucker", "${remaining.sugar.toInt()} g")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onGenerate,
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
            Text("Restliche Makros füllen")
        }
    }
}

@Composable
private fun MacroRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RemainingExhaustedBody(remaining: RemainingMacros) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Du hast deine Tagesziele bereits erreicht",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nur noch ${remaining.kcal.toInt()} kcal übrig — kein Rezept-Vorschlag mehr nötig.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePreviewSheet(
    preview: com.pumpernickel.domain.ai.RecipeAiPreview,
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { /* dismiss only via buttons */ },
        sheetState = sheetState
    ) {
        AiPreviewSheetBody(
            content = AiPreviewContent.Recipe(preview),
            onSaveAll = onSaveAll,
            onDiscard = onDiscard
        )
    }
}

package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.infrastructure.ai.ProviderId
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Phase 22 — Multi-Provider AI settings screen.
 *
 * Layout per CONTEXT.md D-22-05: a global active provider selected via radio
 * buttons, each with its own model dropdown and connect/disconnect action.
 * Anthropic gets a dedicated [AnthropicConnectSheet] (OAuth primary + API-key
 * fallback); OpenAI / Together use the simpler [ApiKeyDialog].
 *
 * Model ordering for Anthropic (D-22-06): Opus 4.7 (default, best quality) →
 * Sonnet 4.6 (balanced) → Haiku 4.5 (fastest).
 */
private data class ProviderDisplay(
    val id: ProviderId,
    val label: String,
    /** Pair of (model wire id, human-readable label). First entry is the default. */
    val modelOptions: List<Pair<String, String>>
)

private val PROVIDERS = listOf(
    ProviderDisplay(
        id = ProviderId.OpenAI,
        label = "OpenAI",
        modelOptions = listOf(
            "gpt-4o-mini" to "GPT-4o mini",
            "gpt-4o" to "GPT-4o",
            "gpt-4.1-mini" to "GPT-4.1 mini"
        )
    ),
    ProviderDisplay(
        id = ProviderId.Together,
        label = "Together.AI",
        modelOptions = listOf(
            "google/gemma-4-31B-it" to "Gemma 4 31B",
            "meta-llama/Llama-3.3-70B-Instruct-Turbo" to "Llama 3.3 70B",
            "Qwen/Qwen2.5-72B-Instruct-Turbo" to "Qwen 2.5 72B"
        )
    ),
    ProviderDisplay(
        id = ProviderId.Anthropic,
        label = "Anthropic (Claude)",
        modelOptions = listOf(
            "claude-opus-4-7" to "Opus 4.7 (beste Qualität)",
            "claude-sonnet-4-6" to "Sonnet 4.6 (balanced)",
            "claude-haiku-4-5-20251001" to "Haiku 4.5 (schnellst)"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = koinViewModel()
) {
    val activeProvider by viewModel.activeProvider.collectAsState()
    val modelByProvider by viewModel.modelByProvider.collectAsState()
    val connected by viewModel.connectedProviders.collectAsState()
    val oauthBusy by viewModel.oauthInProgress.collectAsState()
    val error by viewModel.lastError.collectAsState()

    var showAnthropicSheet by remember { mutableStateOf(false) }
    var apiKeyDialogProvider by remember { mutableStateOf<ProviderId?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KI-Provider") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Aktiver Provider",
                style = MaterialTheme.typography.titleMedium
            )

            PROVIDERS.forEach { display ->
                ProviderRow(
                    display = display,
                    isActive = display.id == activeProvider,
                    isConnected = display.id in connected,
                    currentModel = modelByProvider[display.id]
                        ?: display.modelOptions.first().first,
                    onSelect = { viewModel.setActiveProvider(display.id) },
                    onModelChange = { viewModel.setModel(display.id, it) },
                    onConnect = {
                        when (display.id) {
                            ProviderId.Anthropic -> showAnthropicSheet = true
                            else -> apiKeyDialogProvider = display.id
                        }
                    },
                    onDisconnect = { viewModel.disconnect(display.id) }
                )
            }

            error?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Schließen")
                        }
                    }
                }
            }
        }
    }

    if (showAnthropicSheet) {
        AnthropicConnectSheet(
            isBusy = oauthBusy,
            onOAuthClicked = { viewModel.startAnthropicOAuth() },
            onApiKeySubmit = { key ->
                viewModel.setAnthropicApiKey(key)
                showAnthropicSheet = false
            },
            onDismiss = { showAnthropicSheet = false }
        )
    }

    apiKeyDialogProvider?.let { provider ->
        ApiKeyDialog(
            provider = provider,
            onSubmit = { key ->
                viewModel.setApiKeyFor(provider, key)
                apiKeyDialogProvider = null
            },
            onDismiss = { apiKeyDialogProvider = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRow(
    display: ProviderDisplay,
    isActive: Boolean,
    isConnected: Boolean,
    currentModel: String,
    onSelect: () -> Unit,
    onModelChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isActive, onClick = onSelect)
                Text(
                    text = display.label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (isConnected) "✓ verbunden" else "– nicht verbunden",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }

            // Modell-Dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = display.modelOptions
                        .firstOrNull { it.first == currentModel }?.second
                        ?: currentModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Modell") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    display.modelOptions.forEach { (modelId, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onModelChange(modelId)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Verbindung trennen")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (display.id == ProviderId.Anthropic) {
                                "Mit Claude.ai verbinden"
                            } else {
                                "API-Key eintippen"
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple AlertDialog-based API-key entry — used for OpenAI and Together (and
 * any future API-Key-only provider). Anthropic has its own
 * [AnthropicConnectSheet] with OAuth as the primary action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyDialog(
    provider: ProviderId,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API-Key für ${provider.wireName}") },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API-Key") },
                singleLine = true,
                visualTransformation = if (visible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (visible) "Verbergen" else "Anzeigen"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(key) },
                enabled = key.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

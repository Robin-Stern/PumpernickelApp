package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.presentation.ai.AiSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

private val PROVIDER_PRESETS = listOf(
    "openai" to "OpenAI",
    "together" to "Together.AI",
    "openrouter" to "OpenRouter",
    "groq" to "Groq",
    "custom" to "Benutzerdefiniert"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = koinViewModel()
) {
    val providerPreset by viewModel.providerPreset.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val model by viewModel.model.collectAsState()
    val apiKeyConfigured by viewModel.apiKeyConfigured.collectAsState()

    var keyDraft by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var baseUrlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
    var modelDraft by remember(model) { mutableStateOf(model) }

    // T-18-05-03: Non-HTTPS base URL is rejected with inline error
    val baseUrlError = baseUrlDraft.isNotBlank() && !baseUrlDraft.startsWith("https://")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KI-Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider preset dropdown
            ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = it }
            ) {
                OutlinedTextField(
                    value = PROVIDER_PRESETS.firstOrNull { it.first == providerPreset }?.second
                        ?: providerPreset,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Anbieter") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false }
                ) {
                    PROVIDER_PRESETS.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setProviderPreset(key)
                                presetExpanded = false
                            }
                        )
                    }
                }
            }

            // API key field (masked by default, show/hide toggle)
            // T-18-05-01: PasswordVisualTransformation by default; explicit show/hide
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("API-Schlüssel") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Verbergen" else "Anzeigen"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            // Mirrors iOS AISettingsView.swift:115-130 — coloured status icon
            // alongside the label so the key state is visible at a glance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (apiKeyConfigured) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = "Gespeichert", style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = "Kein Schlüssel gespeichert", style = MaterialTheme.typography.bodySmall)
                }
            }

            // T-18-05-04: save button disabled when keyDraft is blank — empty key never reaches SecureKeyStore
            OutlinedButton(
                onClick = {
                    // T-18-05-05: clear keyDraft after save to prevent key lingering in memory
                    viewModel.setApiKey(keyDraft)
                    keyDraft = ""
                },
                enabled = keyDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Schlüssel speichern") }

            OutlinedButton(
                onClick = { viewModel.clearApiKey() },
                enabled = apiKeyConfigured,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Schlüssel löschen") }

            Spacer(Modifier.height(12.dp))

            // Base URL — disabled unless preset = custom (preset controls default value)
            OutlinedTextField(
                value = baseUrlDraft,
                onValueChange = { baseUrlDraft = it },
                label = { Text("Basis-URL") },
                isError = baseUrlError,
                supportingText = {
                    if (baseUrlError) Text("Nur HTTPS-URLs erlaubt.")
                },
                enabled = providerPreset == "custom",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Model field — always editable
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Modell") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Model suggestions per provider — mirrors iOS AISettingsView.swift:214-230.
            ModelSuggestions(
                onPick = { suggestion ->
                    modelDraft = suggestion
                    viewModel.setModel(suggestion)
                }
            )

            // T-18-05-03: save button disabled when base URL is invalid (non-HTTPS)
            OutlinedButton(
                onClick = {
                    if (!baseUrlError) {
                        viewModel.setBaseUrl(baseUrlDraft)
                        viewModel.setModel(modelDraft)
                    }
                },
                enabled = !baseUrlError,
                modifier = Modifier.fillMaxWidth()
            ) { Text("URL und Modell speichern") }
        }
    }
}

private data class ModelSuggestion(val name: String, val note: String)

private val MODEL_SUGGESTIONS_BY_PROVIDER: List<Pair<String, List<ModelSuggestion>>> = listOf(
    "OpenAI" to listOf(
        ModelSuggestion("gpt-4o-mini", "günstig, schnell"),
        ModelSuggestion("gpt-4o", "stärker, teurer"),
        ModelSuggestion("gpt-4.1-mini", "neuer, ausgewogen")
    ),
    "Together.AI" to listOf(
        ModelSuggestion("google/gemma-4-31B-it", "Default-Vorgabe"),
        ModelSuggestion("meta-llama/Llama-3.3-70B-Instruct-Turbo", "großes Open-Modell"),
        ModelSuggestion("Qwen/Qwen2.5-72B-Instruct-Turbo", "starke Reasoning-Leistung")
    ),
    "OpenRouter" to listOf(
        ModelSuggestion("openai/gpt-oss-20b:free", "Default-Vorgabe"),
        ModelSuggestion("anthropic/claude-3.5-sonnet", "Premium-Qualität"),
        ModelSuggestion("google/gemini-2.0-flash-exp:free", "schnell, gratis")
    ),
    "Groq" to listOf(
        ModelSuggestion("llama-3.3-70b-versatile", "Default, schnell"),
        ModelSuggestion("llama-3.1-8b-instant", "schnell, sehr günstig")
    )
)

@Composable
private fun ModelSuggestions(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Vorschläge",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MODEL_SUGGESTIONS_BY_PROVIDER.forEach { (providerLabel, suggestions) ->
            var expanded by remember(providerLabel) { mutableStateOf(false) }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = providerLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider()
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(suggestion.name) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                        Text(
                                            text = suggestion.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

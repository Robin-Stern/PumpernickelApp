package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Phase 22 — D-22-01 / D-22-02 — Anthropic connect bottom sheet.
 *
 * Layout (top → bottom):
 *   1. Title + recommendation text (OAuth is preferred so users can spend their
 *      existing Claude Pro/Max subscription instead of buying API credits).
 *   2. Big "Mit Claude.ai verbinden" button — primary action. While [isBusy] is
 *      true (OAuth round-trip in progress) the button is disabled and shows a
 *      spinner.
 *   3. Horizontal divider with "oder" affordance (handled implicitly via
 *      [HorizontalDivider] + helper text).
 *   4. API-Key OutlinedTextField (with visibility toggle) + "Speichern" /
 *      "Abbrechen" buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnthropicConnectSheet(
    isBusy: Boolean,
    onOAuthClicked: () -> Unit,
    onApiKeySubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Mit Anthropic verbinden",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Empfohlen: Mit deinem Claude.ai-Konto verbinden, um deine " +
                    "bestehende Pro/Max-Subscription zu nutzen — ohne separate API-Credits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOAuthClicked,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Mit Claude.ai verbinden")
            }

            HorizontalDivider()

            Text(
                text = "Oder API-Key von console.anthropic.com eintippen:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            var apiKey by remember { mutableStateOf("") }
            var visible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("sk-ant-...") },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onApiKeySubmit(apiKey) },
                    enabled = apiKey.isNotBlank()
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}

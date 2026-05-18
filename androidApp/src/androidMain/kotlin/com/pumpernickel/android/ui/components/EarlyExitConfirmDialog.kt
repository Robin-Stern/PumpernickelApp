package com.pumpernickel.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pumpernickel.android.R

data class EarlyExitDialogConfig(
    val totalBudget: Int,
    val remainingBeforeUse: Int,
    val penaltyXp: Int
) {
    val hasBudget: Boolean get() = remainingBeforeUse >= 1
}

/**
 * D-19-08 — AlertDialog with destructive role when budget is exhausted.
 */
@Composable
fun EarlyExitConfirmDialog(
    config: EarlyExitDialogConfig,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (config.hasBudget) R.string.early_exit_dialog_title_budget
                    else R.string.early_exit_dialog_title_penalty
                )
            )
        },
        text = {
            Text(
                if (config.hasBudget)
                    stringResource(R.string.early_exit_dialog_body_budget, config.remainingBeforeUse)
                else
                    stringResource(R.string.early_exit_dialog_body_penalty, config.penaltyXp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors = if (config.hasBudget) ButtonDefaults.textButtonColors()
                         else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    stringResource(
                        if (config.hasBudget) R.string.early_exit_dialog_primary_budget
                        else R.string.early_exit_dialog_primary_penalty
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.early_exit_dialog_cancel))
            }
        }
    )
}

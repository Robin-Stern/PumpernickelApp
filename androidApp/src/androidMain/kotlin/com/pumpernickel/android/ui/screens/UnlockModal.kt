package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.gamification.UnlockEvent
import com.pumpernickel.presentation.gamification.GamificationViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * D-19 + D-20: full-screen celebratory modal host. Observes the
 * GamificationViewModel.unlockEvents SharedFlow, buffers incoming events
 * into a queue (multiple unlocks from one save show one-at-a-time, not
 * stacked), and presents an AlertDialog for the head of the queue. On
 * dismiss, pops the head and shows the next event.
 *
 * MUST be hosted at the root of the composition (MainScreen.kt) so the
 * modal overlays the current tab regardless of which tab triggered the
 * unlock. Hosting inside a single tab would hide the modal when the user
 * switches tabs mid-unlock.
 */
@Composable
fun UnlockModalHost(
    viewModel: GamificationViewModel = koinViewModel()
) {
    val pending = remember { mutableStateListOf<UnlockEvent>() }
    val haptics = LocalHapticFeedback.current

    // Collect new events into the queue.
    LaunchedEffect(Unit) {
        viewModel.unlockEvents.collect { event ->
            pending.add(event)
        }
    }

    // Show head of queue, if any.
    val head = pending.firstOrNull()

    // Mirror iOS UnlockModalView.swift:65-71 — success haptic when a new
    // unlock dialog appears. Keyed on the head event so multiple unlocks
    // from one save each fire their own pulse as the queue advances.
    LaunchedEffect(head) {
        if (head != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    if (head != null) {
        UnlockDialog(
            event = head,
            onDismiss = { if (pending.isNotEmpty()) pending.removeAt(0) }
        )
    }
}

@Composable
private fun UnlockDialog(
    event: UnlockEvent,
    onDismiss: () -> Unit
) {
    val (icon, title, flavour) = when (event) {
        is UnlockEvent.RankPromotion -> Triple(
            Icons.Filled.MilitaryTech,
            "Promoted to ${event.toRank.displayName}",
            event.flavourCopy
        )
        is UnlockEvent.AchievementTierUnlocked -> Triple(
            Icons.Filled.EmojiEvents,
            "${event.tier.name.lowercase().replaceFirstChar { it.uppercase() }}: ${event.displayName}",
            event.flavourCopy
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = flavour,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event is UnlockEvent.RankPromotion) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${event.totalXp} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

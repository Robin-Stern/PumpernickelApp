package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.presentation.gamification.RankRow
import com.pumpernickel.presentation.gamification.RankRowStatus
import com.pumpernickel.presentation.gamification.RanksAndAchievementsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * D-151-11 / D-151-12 rank ladder screen. Lists all 10 CSGO tiers in a
 * single LazyColumn, ordered SILVER → GLOBAL_ELITE. Each row shows tier
 * badge + rank name + '#tier' + formatted threshold XP + a status element
 * derived from [RankRowStatus] (PASSED / CURRENT / LOCKED).
 *
 * In the Unranked state, a header Card renders the load-bearing D-11 copy
 * ("Unranked — complete a workout to unlock Silver" — D-151-23 pins this
 * string character-for-character across three call sites).
 *
 * Passive screen — consumes [RanksAndAchievementsViewModel.rankLadderState]
 * via koinViewModel + collectAsState. No state mutation; the ladder is
 * purely derived from the upstream repository flows by the VM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankLadderScreen(
    navController: NavHostController,
    viewModel: RanksAndAchievementsViewModel = koinViewModel()
) {
    val uiState by viewModel.rankLadderState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ranks", fontWeight = FontWeight.Bold) },
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
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isUnranked) {
                    item(key = "unranked-header") {
                        UnrankedHeaderCard()
                    }
                }
                items(
                    items = uiState.rows,
                    key = { row -> row.rank.name }
                ) { row ->
                    RankLadderRowCard(row = row, isUnranked = uiState.isUnranked)
                }
            }
        }
    }
}

@Composable
private fun UnrankedHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            // D-151-23 / D-11: load-bearing literal — MUST match
            // OverviewRankStrip.kt line 73 character-for-character.
            Text(
                text = "Unranked — complete a workout to unlock Silver",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankLadderRowCard(row: RankRow, isUnranked: Boolean) {
    val isLocked = row.status == RankRowStatus.LOCKED
    val isCurrent = row.status == RankRowStatus.CURRENT

    // Highlight the CURRENT row with a stronger container; PASSED/LOCKED use the
    // standard subtle surface-variant fill. Keep it subtle — the badge tint
    // carries most of the visual weight (D-151 Claude's Discretion).
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.45f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = rankTint(row.rank),
                modifier = Modifier.size(36.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${row.rank.tier}  ${row.rank.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                // D-151-18: locale-aware thousands separator on the UI layer.
                Text(
                    text = "%,d XP".format(row.threshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val (statusIcon, statusText, statusTint) = statusPresentation(
                    row = row,
                    isUnranked = isUnranked
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTint
                    )
                }
            }
        }
    }
}

/**
 * Pure presentation helper. Returns the (icon, subtitle, tint) triple for
 * the row's RankRowStatus. Special case per D-151-07: when Unranked and
 * the row is SILVER, render "First workout unlocks" instead of
 * "{xpToReach:,} XP to unlock" — the VM marks this with xpToReach == 0.
 */
@Composable
private fun statusPresentation(
    row: RankRow,
    isUnranked: Boolean
): Triple<ImageVector, String, Color> {
    return when (row.status) {
        RankRowStatus.PASSED -> Triple(
            Icons.Filled.CheckCircle,
            "Passed",
            MaterialTheme.colorScheme.primary
        )
        RankRowStatus.CURRENT -> Triple(
            Icons.Filled.Star,
            "Current rank",
            MaterialTheme.colorScheme.primary
        )
        RankRowStatus.LOCKED -> {
            val xpToReach = row.xpToReach ?: 0L
            val subtitle = if (isUnranked && row.rank == Rank.SILVER && xpToReach == 0L) {
                // D-151-07 + D-11: SILVER unlocks on first workout.
                "First workout unlocks"
            } else {
                "%,d XP to unlock".format(xpToReach)
            }
            Triple(
                Icons.Filled.Lock,
                subtitle,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tier-banded tint — mirrors OverviewRankStrip.kt lines 130-137 for
 * cross-surface parity. Keep both in sync.
 */
@Composable
private fun rankTint(rank: Rank) = when (rank.tier) {
    in 1..2 -> MaterialTheme.colorScheme.outline                  // silver tones
    in 3..5 -> MaterialTheme.colorScheme.tertiary                 // gold tones
    in 6..7 -> MaterialTheme.colorScheme.secondary                // guardian tones
    in 8..9 -> MaterialTheme.colorScheme.primary                  // eagle/supreme tones
    else    -> MaterialTheme.colorScheme.primary                  // global elite
}

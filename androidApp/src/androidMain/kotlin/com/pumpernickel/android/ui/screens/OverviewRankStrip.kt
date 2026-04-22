package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.gamification.Rank
import com.pumpernickel.domain.gamification.RankState

/**
 * D-18 compact rank strip. Renders at the top of the Overview tab, above
 * the muscle-activity card. Two states:
 *   - Unranked → lock icon + "Unranked — complete a workout to unlock Silver" (D-11 literal).
 *   - Ranked → medal icon + rank name + "X / Y XP" + LinearProgressIndicator.
 *
 * NOT a navigation link — the achievement gallery is under Settings only (D-21).
 */
@Composable
fun OverviewRankStrip(
    rankState: RankState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        when (rankState) {
            is RankState.Unranked -> UnrankedContent()
            is RankState.Ranked -> RankedContent(rankState)
        }
    }
}

@Composable
private fun UnrankedContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        // D-11 / CONTEXT specifics — literal string required.
        Text(
            text = "Unranked — complete a workout to unlock Silver",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RankedContent(state: RankState.Ranked) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TODO(polish): swap to per-rank icon/badge asset (Claude's discretion D-19 flavour).
        Icon(
            imageVector = Icons.Filled.MilitaryTech,
            contentDescription = null,
            tint = rankTint(state.currentRank),
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.currentRank.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            val nextLabel = state.nextRankThreshold?.let { "$it XP" } ?: "MAX"
            Text(
                text = "${state.totalXp} / $nextLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val next = state.nextRankThreshold
            val current = state.currentRankThreshold
            val progress = if (next != null && next > current) {
                ((state.totalXp - current).coerceAtLeast(0).toFloat() /
                    (next - current).toFloat()).coerceIn(0f, 1f)
            } else 1f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = rankTint(state.currentRank)
            )
        }
    }
}

/**
 * TODO(polish): per-rank gradient colours. Silver greys, Gold goldens, Eagle
 * brass, Supreme purples, Global Elite reds. Claude's discretion per D-19.
 */
@Composable
private fun rankTint(rank: Rank) = when (rank.tier) {
    in 1..2 -> MaterialTheme.colorScheme.outline                  // silver tones
    in 3..5 -> MaterialTheme.colorScheme.tertiary                 // gold tones
    in 6..7 -> MaterialTheme.colorScheme.secondary                // guardian tones
    in 8..9 -> MaterialTheme.colorScheme.primary                  // eagle/supreme tones
    else    -> MaterialTheme.colorScheme.primary                  // global elite
}

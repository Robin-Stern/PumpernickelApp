package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.domain.gamification.Category
import com.pumpernickel.domain.gamification.Tier
import com.pumpernickel.presentation.gamification.AchievementGalleryViewModel
import com.pumpernickel.presentation.gamification.AchievementTile
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementGalleryScreen(
    navController: NavHostController,
    viewModel: AchievementGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements", fontWeight = FontWeight.Bold) },
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                val categoriesOrder = listOf(
                    Category.VOLUME,
                    Category.CONSISTENCY,
                    Category.PR_HUNTER,
                    Category.VARIETY
                )
                categoriesOrder.forEach { category ->
                    val tiles = uiState.tilesByCategory[category] ?: return@forEach
                    if (tiles.isEmpty()) return@forEach

                    item(span = { GridItemSpan(2) }) {
                        CategoryHeader(category)
                    }
                    items(tiles, key = { it.id }) { tile ->
                        AchievementCard(tile)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: Category) {
    val label = when (category) {
        Category.VOLUME -> "Volume"
        Category.CONSISTENCY -> "Consistency"
        Category.PR_HUNTER -> "PR Hunter"
        Category.VARIETY -> "Exercise Variety"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AchievementCard(tile: AchievementTile) {
    val isLocked = tile.unlockedAtMillis == null
    val containerColor = if (isLocked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        tierColor(tile.tier).copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.45f else 1f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else tierColor(tile.tier),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = tile.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tierLabel(tile.tier),
                style = MaterialTheme.typography.labelSmall,
                color = tierColor(tile.tier)
            )
            // D-151-19 — reward XP label (D-17: BRONZE=+25, SILVER=+75, GOLD=+200).
            // Small secondary-text line per CONTEXT "not a shout".
            Text(
                text = "Reward: +${rewardXp(tile.tier)} XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tile.flavourCopy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val unlockedAt = tile.unlockedAtMillis
            val footer = if (unlockedAt == null) {
                "${tile.currentProgress} / ${tile.threshold}"
            } else {
                "Unlocked ${formatUnlockDate(unlockedAt)}"
            }
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun tierColor(tier: Tier) = when (tier) {
    Tier.BRONZE -> MaterialTheme.colorScheme.tertiary
    Tier.SILVER -> MaterialTheme.colorScheme.outline
    Tier.GOLD -> MaterialTheme.colorScheme.primary
}

private fun tierLabel(tier: Tier) = when (tier) {
    Tier.BRONZE -> "Bronze"
    Tier.SILVER -> "Silver"
    Tier.GOLD -> "Gold"
}

/** D-151-19 / D-17: reward XP values per tier. */
private fun rewardXp(tier: Tier) = when (tier) {
    Tier.BRONZE -> 25
    Tier.SILVER -> 75
    Tier.GOLD -> 200
}

private fun formatUnlockDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return local.toString()
}

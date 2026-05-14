package com.pumpernickel.presentation.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.domain.gamification.AchievementCatalog
import com.pumpernickel.domain.gamification.AchievementProgress
import com.pumpernickel.domain.gamification.Category
import com.pumpernickel.domain.gamification.Tier
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AchievementGalleryUiState(
    val tilesByCategory: Map<Category, List<AchievementTile>> = emptyMap(),
    val isLoading: Boolean = true
)

data class AchievementTile(
    val id: String,
    val displayName: String,
    val flavourCopy: String,
    val category: Category,
    val tier: Tier,
    val threshold: Long,
    val currentProgress: Long,
    val unlockedAtMillis: Long?
)

class AchievementGalleryViewModel(
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    @NativeCoroutinesState
    val uiState: StateFlow<AchievementGalleryUiState> = gamificationRepository
        .achievements
        .map { progressRows -> buildUiState(progressRows) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AchievementGalleryUiState(isLoading = true)
        )

    private fun buildUiState(progressRows: List<AchievementProgress>): AchievementGalleryUiState {
        val progressById = progressRows.associateBy { it.def.id }
        val tiles = AchievementCatalog.all.map { def ->
            val progress = progressById[def.id]
            AchievementTile(
                id = def.id,
                displayName = def.displayName,
                flavourCopy = def.flavourCopy,
                category = def.category,
                tier = def.tier,
                threshold = def.threshold,
                currentProgress = progress?.currentProgress ?: 0L,
                unlockedAtMillis = progress?.unlockedAtMillis
            )
        }
        val grouped: Map<Category, List<AchievementTile>> = tiles
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sortedBy { it.tier.ordinal } }
        return AchievementGalleryUiState(
            tilesByCategory = grouped,
            isLoading = false
        )
    }
}

package com.pumpernickel.data.db

import com.pumpernickel.domain.gamification.AchievementCatalog
import kotlinx.coroutines.flow.first

/**
 * Seeds one locked row per AchievementCatalog entry into achievement_state on
 * first launch. Mirror of NutritionDataSeeder. Idempotent via the
 * `insertAchievementStateIfMissing` IGNORE-on-conflict guard — safe to re-run.
 */
class AchievementStateSeeder(
    private val dao: GamificationDao
) {
    suspend fun seedIfEmpty() {
        // Cheap-path: if the table already has rows, everything is seeded.
        if (dao.achievementStateFlow().first().isNotEmpty()) return

        AchievementCatalog.all.forEach { def ->
            dao.insertAchievementStateIfMissing(
                AchievementStateEntity(
                    achievementId = def.id,
                    category = def.category.name,
                    tier = def.tier.name,
                    threshold = def.threshold,
                    currentProgress = 0L,
                    unlockedAtMillis = null
                )
            )
        }
    }
}

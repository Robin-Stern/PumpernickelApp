package com.pumpernickel.domain.gamification

/** UI-facing per-achievement progress. Joined from catalog + achievement_state table. */
data class AchievementProgress(
    val def: AchievementDef,
    val currentProgress: Long,
    val unlockedAtMillis: Long?   // null = locked
) {
    val isUnlocked: Boolean get() = unlockedAtMillis != null
    val progressFraction: Float
        get() = if (def.threshold <= 0L) 1f
                else (currentProgress.toDouble() / def.threshold.toDouble())
                    .coerceIn(0.0, 1.0).toFloat()
}

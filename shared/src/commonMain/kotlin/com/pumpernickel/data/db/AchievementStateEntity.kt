package com.pumpernickel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievement_state")
data class AchievementStateEntity(
    @PrimaryKey val achievementId: String,   // e.g. "volume-bronze", "consistency-longest-streak-gold"
    val category: String,                     // "VOLUME" | "CONSISTENCY" | "PR_HUNTER" | "VARIETY" (enum name as text)
    val tier: String,                         // "BRONZE" | "SILVER" | "GOLD"
    val threshold: Long,                      // copied from catalog for historical stability
    val currentProgress: Long = 0L,
    val unlockedAtMillis: Long? = null        // null = locked
)

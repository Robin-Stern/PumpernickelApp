package com.pumpernickel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rank_state")
data class RankStateEntity(
    @PrimaryKey val id: Long = 1,                     // Singleton: at most one row
    val totalXp: Long = 0L,
    val currentRank: String = "UNRANKED",             // Rank enum name or "UNRANKED" (D-11)
    val lastPromotedAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "1") val isUnranked: Boolean = true  // D-11 default true until first workout
)

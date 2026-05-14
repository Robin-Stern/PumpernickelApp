package com.pumpernickel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "xp_ledger",
    indices = [Index(value = ["source", "eventKey"], unique = true)]
)
data class XpLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,          // "workout" | "pr" | "nutrition_goal_day" | "streak_workout" | "streak_nutrition" | "achievement"
    val eventKey: String,        // e.g. "workout:123", "pr:bench_press:456", "goalday:2026-04-20"
    val xpAmount: Int,
    val awardedAtMillis: Long,
    @ColumnInfo(defaultValue = "0") val retroactive: Boolean = false
)

package com.pumpernickel.domain.gamification

/**
 * CSGO-style rank ladder — 10 condensed ranks. See D-08.
 * Order matters: ordinal is used as the tier index (1-based via ordinal + 1).
 * Never reorder — persisted as enum name strings in `rank_state.currentRank`.
 */
enum class Rank(val displayName: String) {
    SILVER("Silver"),
    SILVER_ELITE("Silver Elite"),
    GOLD_NOVA_I("Gold Nova I"),
    GOLD_NOVA_II("Gold Nova II"),
    GOLD_NOVA_III("Gold Nova III"),
    MASTER_GUARDIAN("Master Guardian"),
    DISTINGUISHED_MASTER_GUARDIAN("Distinguished Master Guardian"),
    LEGENDARY_EAGLE("Legendary Eagle"),
    SUPREME("Supreme Master First Class"),
    GLOBAL_ELITE("Global Elite");

    /** 1-based tier number (Silver = 1, Global Elite = 10). */
    val tier: Int get() = ordinal + 1
}

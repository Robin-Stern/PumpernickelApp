package com.pumpernickel.domain.workout

import com.pumpernickel.domain.model.MuscleGroup

/**
 * Recovery-based assessment for a single muscle group.
 *
 *  - `daysSinceLast` is `null` when the muscle has never received a qualifying stimulus
 *    (a set with RIR ≤ 3 — RIR 4+ is treated as warm-up volume).
 *  - `weeklyFrequency` is the count of qualifying sets in the last 28 days, divided by 4.
 *  - `severity` collapses both signals into a coarse band; see [UndertrainedSeverity].
 */
data class UndertrainedMuscle(
    val group: MuscleGroup,
    val daysSinceLast: Int?,
    val weeklyFrequency: Double,
    val severity: UndertrainedSeverity
)

enum class UndertrainedSeverity {
    /** 5–9 days since last qualifying stimulus AND frequency < 1/week. */
    NEEDS_ATTENTION,
    /** 10–20 days since last qualifying stimulus, OR frequency < 0.5/week. */
    OVERDUE,
    /** ≥ 21 days since last qualifying stimulus, OR never qualified. */
    NEGLECTED
}

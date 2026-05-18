package com.pumpernickel.domain.geofence

import com.pumpernickel.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * D-19-07 — monthly Early-Exits budget. Pure domain wrapper over
 * SettingsRepository.earlyExits — handles the "consume one" semantics so the
 * VM doesn't reach into DataStore directly.
 *
 * Budget resets at the 1st of each calendar month via SettingsRepository's
 * year-month sentinel; this class does not own the reset logic.
 */
class EarlyExitTracker(
    private val settingsRepository: SettingsRepository
) {

    companion object {
        /** D-19-07 — not a user setting; hard-coded for v1. */
        const val EARLY_EXIT_BUDGET_PER_MONTH: Int = 2
    }

    /** Current month's snapshot (used + remaining). Auto-resets on month change. */
    val budget: Flow<EarlyExitBudget> = settingsRepository.earlyExits

    /**
     * Atomically consumes one Early Exit. Returns:
     *  - true if budget had >= 1 remaining and was decremented
     *  - false if budget was already at 0 (caller must apply penalty path instead)
     */
    suspend fun consumeOne(): Boolean {
        val current = settingsRepository.earlyExits.first()
        if (current.remaining <= 0) return false
        settingsRepository.incrementEarlyExitUsed()
        return true
    }
}

/**
 * D-19-07 — snapshot for the current calendar month.
 *
 * @param used how many Early Exits the user has used this month (0..EARLY_EXIT_BUDGET_PER_MONTH)
 * @param remaining computed = EARLY_EXIT_BUDGET_PER_MONTH - used (>= 0)
 * @param yearMonth ISO "YYYY-MM" — diagnostic field for UI ("Reset am 1. Juni")
 */
data class EarlyExitBudget(
    val used: Int,
    val remaining: Int,
    val yearMonth: String
)

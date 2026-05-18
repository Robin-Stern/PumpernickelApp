package com.pumpernickel.domain.geofence

import com.pumpernickel.domain.repository.EarlyExitBudgetStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * D-19-07 / D-20-05 — monthly Early-Exits budget. Pure domain wrapper over the
 * narrow [EarlyExitBudgetStore] port — handles the "consume one" semantics so
 * the VM doesn't reach into DataStore directly.
 *
 * Smell 13 fix (Plan 20-05): depends on the narrow `EarlyExitBudgetStore`
 * domain port instead of the fat settings facade. Budget resets at the
 * 1st of each calendar month are handled by the implementation's year-month
 * sentinel; this class does not own the reset logic.
 */
class EarlyExitTracker(
    private val store: EarlyExitBudgetStore
) {

    companion object {
        /** D-19-07 — not a user setting; hard-coded for v1. */
        const val EARLY_EXIT_BUDGET_PER_MONTH: Int = 2
    }

    /** Current month's snapshot (used + remaining). Auto-resets on month change. */
    val budget: Flow<EarlyExitBudget> = store.earlyExits

    /**
     * Atomically consumes one Early Exit. Returns:
     *  - true if budget had >= 1 remaining and was decremented
     *  - false if budget was already at 0 (caller must apply penalty path instead)
     */
    suspend fun consumeOne(): Boolean {
        val current = store.earlyExits.first()
        if (current.remaining <= 0) return false
        store.incrementEarlyExitUsed()
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

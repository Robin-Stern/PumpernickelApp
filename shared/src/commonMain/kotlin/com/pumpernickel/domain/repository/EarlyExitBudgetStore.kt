package com.pumpernickel.domain.repository

import com.pumpernickel.domain.geofence.EarlyExitBudget
import kotlinx.coroutines.flow.Flow

/**
 * D-19-07 / D-20-05 — narrow domain port (Smell 13) for the monthly Early-Exits
 * budget. Extracted from the fat `SettingsRepository` surface so that
 * `EarlyExitTracker` (and any future caller that only needs the budget
 * sub-domain) can depend on the smallest possible contract.
 *
 * Status quo: `SettingsRepository.earlyExits: Flow<EarlyExitBudget>` and
 * `SettingsRepository.incrementEarlyExitUsed()` are the only two members
 * `EarlyExitTracker` actually touches (verified via
 * `grep -n "settingsRepository\." domain/geofence/EarlyExitTracker.kt`:
 *   - line 25: `settingsRepository.earlyExits`
 *   - line 33: `settingsRepository.earlyExits.first()`
 *   - line 35: `settingsRepository.incrementEarlyExitUsed()`).
 *
 * Phase 20 wiring (Plan 20-04): `EarlyExitTracker` will inject this narrow
 * port instead of the full `SettingsRepository`. The DataStore-backed impl
 * (`SettingsRepositoryImpl`) implements this interface in addition to the
 * main `SettingsRepository` and `PendingGeofenceExitStore` ports.
 *
 * Budget resets at the 1st of each calendar month are handled by the
 * implementation's year-month sentinel; this port does not own the reset
 * logic. Method naming mirrors the existing API exactly to keep the Plan
 * 20-04 swap mechanical.
 */
interface EarlyExitBudgetStore {

    /**
     * Current month's Early-Exit budget snapshot (used + remaining + yearMonth).
     * Auto-resets when the underlying DataStore-persisted year-month sentinel
     * disagrees with the current calendar month — see `SettingsRepositoryImpl`.
     */
    val earlyExits: Flow<EarlyExitBudget>

    /**
     * Atomically consume one Early Exit by incrementing the "used" counter for
     * the current calendar month. Idempotent w.r.t. month change: the first
     * write of a new month re-anchors the sentinel.
     *
     * Caller responsibility: gate this with a prior `earlyExits.first()` read
     * to ensure `remaining >= 1` (see `EarlyExitTracker.consumeOne()` for the
     * canonical consume pattern).
     */
    suspend fun incrementEarlyExitUsed()
}

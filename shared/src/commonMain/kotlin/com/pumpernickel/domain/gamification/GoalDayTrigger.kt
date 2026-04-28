package com.pumpernickel.domain.gamification

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * D-22: Evaluate "was yesterday a goal-day?" on Overview tab appearance.
 *
 * Policy:
 * - Every appearance after a local-date rollover re-evaluates YESTERDAY.
 * - First-ever appearance evaluates BOTH yesterday AND today (today may already
 *   meet all goals — catch it now instead of waiting one more midnight).
 * - Idempotent via engine's (source, eventKey) dedupe. Calling maybeTrigger()
 *   N times per day causes at most 1 new ledger row per day.
 */
class GoalDayTrigger(
    private val engine: GamificationEngine
) {
    // In-memory "last evaluated" tracker — for same-session re-appearances.
    // Persistent de-dupe is the ledger's job, not this tracker's.
    private var lastEvaluatedDate: LocalDate? = null

    suspend fun maybeTrigger() {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        if (lastEvaluatedDate == today) return
        val yesterday = today.minus(DatePeriod(days = 1))

        // Evaluate yesterday (most common case — day rolled over since last open).
        engine.evaluateGoalDay(yesterday)
        // Also evaluate today so a user who's already met their goals by late
        // afternoon can still see the award without waiting.
        engine.evaluateGoalDay(today)

        lastEvaluatedDate = today
    }
}

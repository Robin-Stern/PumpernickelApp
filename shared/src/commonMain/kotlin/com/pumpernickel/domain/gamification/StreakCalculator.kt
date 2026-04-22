package com.pumpernickel.domain.gamification

/**
 * Pure streak arithmetic over sorted `epochDay` values (days since 1970-01-01).
 * Used for both workout streaks and nutrition goal-day streaks.
 * The engine is responsible for converting LocalDate / millis to epochDay before calling.
 */
object StreakCalculator {

    /**
     * Given a list of epochDay values (may be unsorted, may contain duplicates),
     * returns the length of the consecutive run ending at the MAX epochDay and the
     * epochDay on which that run started.
     *
     * "Current streak" = the run that includes the most recent (largest) epochDay.
     * A gap in the sequence resets the count — only the tail-anchored run is returned.
     */
    fun longestStreak(epochDays: List<Long>): StreakResult {
        if (epochDays.isEmpty()) return StreakResult(0, null)
        val sorted = epochDays.toSortedSet().toList()  // dedupe + ascending sort
        var runLength = 1
        var runStart = sorted.last()
        // Walk backwards from the tail while entries are consecutive.
        for (i in sorted.size - 1 downTo 1) {
            if (sorted[i] - sorted[i - 1] == 1L) {
                runLength++
                runStart = sorted[i - 1]
            } else {
                break
            }
        }
        return StreakResult(currentLength = runLength, runStartEpochDay = runStart)
    }

    /**
     * Returns which thresholds (in ascending order) were newly crossed by moving
     * from `previousCurrentLength` to `newCurrentLength`. Flat-on-threshold
     * semantics per D-06: a threshold fires only when the count crosses it from
     * below to at-or-above.
     *
     * Returns emptyList() if newCurrentLength <= previousCurrentLength (no progress).
     */
    fun thresholdsCrossed(
        previousCurrentLength: Int,
        newCurrentLength: Int,
        thresholds: List<Int>
    ): List<Int> {
        if (newCurrentLength <= previousCurrentLength) return emptyList()
        return thresholds.filter { t -> previousCurrentLength < t && newCurrentLength >= t }
            .sorted()
    }
}

/** Result of a streak calculation. */
data class StreakResult(
    /** Length of the consecutive run ending at the most recent epochDay. 0 for an empty list. */
    val currentLength: Int,
    /** EpochDay on which the current run started. Null for an empty list. */
    val runStartEpochDay: Long?
)

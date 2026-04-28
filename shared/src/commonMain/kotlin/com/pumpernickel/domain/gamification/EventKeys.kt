package com.pumpernickel.domain.gamification

/**
 * Typed constructors for XpLedgerEntity.eventKey. The format is load-bearing —
 * the unique (source, eventKey) index in xp_ledger is what enforces idempotency
 * (see D-13). DO NOT change these formats without bumping Room schema.
 */
object EventKeys {
    // Source tags — must match XpLedgerEntity.source column values.
    const val SOURCE_WORKOUT: String = "workout"
    const val SOURCE_PR: String = "pr"
    const val SOURCE_NUTRITION_GOAL_DAY: String = "nutrition_goal_day"
    const val SOURCE_STREAK_WORKOUT: String = "streak_workout"
    const val SOURCE_STREAK_NUTRITION: String = "streak_nutrition"
    const val SOURCE_ACHIEVEMENT: String = "achievement"

    fun workout(workoutId: Long): String = "workout:$workoutId"

    fun pr(exerciseId: String, workoutId: Long): String = "pr:$exerciseId:$workoutId"

    fun goalDay(isoDate: String): String = "goalday:$isoDate"

    /**
     * Including runStartEpochDay prevents awarding the same threshold twice across
     * two distinct streak runs (break -> re-start -> re-hit 7d counts as a new run).
     */
    fun streakWorkout(threshold: Int, runStartEpochDay: Long): String =
        "streak:workout:$threshold:$runStartEpochDay"

    fun streakNutrition(threshold: Int, runStartEpochDay: Long): String =
        "streak:nutrition:$threshold:$runStartEpochDay"

    fun achievement(achievementId: String): String = "achievement:$achievementId"

    /**
     * Parse a `pr:<exerciseId>:<workoutId>` event key back into its components.
     * Returns null if the key does not match the format. Used by PrHunter
     * snapshot derivation (Blocker 4 fix — plan 04's buildSnapshot reads
     * pr-source ledger rows via GamificationDao.getPrLedgerEntries and parses
     * the key here to get exerciseId + workoutId).
     *
     * NOTE: exerciseId strings MUST NOT contain ':' — verify when seeding
     * ExerciseCatalog (existing catalog uses UUID-like IDs that don't).
     */
    data class ParsedPr(val exerciseId: String, val workoutId: Long)

    fun parsePr(eventKey: String): ParsedPr? {
        // Format: "pr:<exerciseId>:<workoutId>". Split on the LAST ':' so
        // hypothetical exerciseIds with a ':' are still parseable.
        if (!eventKey.startsWith("pr:")) return null
        val rest = eventKey.substring(3) // drop "pr:"
        val lastColon = rest.lastIndexOf(':')
        if (lastColon <= 0) return null
        val exerciseId = rest.substring(0, lastColon)
        val workoutId = rest.substring(lastColon + 1).toLongOrNull() ?: return null
        return ParsedPr(exerciseId = exerciseId, workoutId = workoutId)
    }
}

package com.pumpernickel.domain.gamification

import com.pumpernickel.data.db.CompletedWorkoutDao
import com.pumpernickel.data.db.ExerciseDao
import com.pumpernickel.data.db.NutritionDao
import com.pumpernickel.data.repository.GamificationRepository
import com.pumpernickel.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Orchestrator. On each trigger (D-20 workout save, D-22 goal-day resume),
 * computes XP from every applicable source (D-01), writes ledger rows with
 * dedupe-safe (source, eventKey) pairs, runs achievement rules, promotes rank,
 * and emits `UnlockEvent`s for D-19 modals.
 *
 * Shared between live use (plan 05 WorkoutSessionViewModel hook) and
 * retroactive replay (plan 06 RetroactiveWalker).
 */
class GamificationEngine(
    private val gamificationRepo: GamificationRepository,
    private val completedWorkoutDao: CompletedWorkoutDao,
    private val nutritionDao: NutritionDao,
    private val exerciseDao: ExerciseDao,
    private val settingsRepo: SettingsRepository
) {

    private val _unlockEvents = MutableSharedFlow<UnlockEvent>(
        replay = 0,
        extraBufferCapacity = 16   // D-19: queue, don't drop; buffer >= 8 per plan spec
    )

    /** D-19: UI hosts (MainScreen / MainTabView) collect this for the unlock modal queue. */
    val unlockEvents: SharedFlow<UnlockEvent> = _unlockEvents.asSharedFlow()

    // ---------- D-20: live trigger after saveReviewedWorkout ----------

    /**
     * Called from WorkoutSessionViewModel.saveReviewedWorkout() (plan 05).
     * Computes workout XP + PR XP + streak bonuses + achievement checks + rank.
     */
    suspend fun onWorkoutSaved(workoutId: Long) {
        val nowMillis = currentTimeMillis()
        processWorkout(workoutId = workoutId, awardedAtMillis = nowMillis, retroactive = false)
        runAchievementAndRankChecks(nowMillis = nowMillis)
    }

    // ---------- D-22: nutrition goal-day evaluation ----------

    /**
     * Evaluate whether `date` was a goal-day (D-04) and, if so, award XP +
     * streak bonus (D-06). Idempotent per D-05 — re-evaluating the same day
     * cannot double-award due to the unique (source, eventKey) index.
     */
    suspend fun evaluateGoalDay(date: LocalDate) {
        val nowMillis = currentTimeMillis()
        val goals = settingsRepo.nutritionGoals.first()
        val isoDate = date.toString()   // "YYYY-MM-DD"
        // Filter all consumption entries to those on this calendar day (using local TZ).
        val allEntries = nutritionDao.getAllEntries()
        val entriesForDate = allEntries.filter { entry ->
            entry.timestampMillis.toLocalDateString() == isoDate
        }
        if (!NutritionGoalDayPolicy.isGoalDay(entriesForDate, goals)) return

        val awarded = gamificationRepo.awardXp(
            source = EventKeys.SOURCE_NUTRITION_GOAL_DAY,
            eventKey = EventKeys.goalDay(isoDate),
            amount = XpFormula.NUTRITION_GOAL_DAY_XP,
            awardedAtMillis = nowMillis,
            retroactive = false
        )
        if (!awarded) return   // already awarded — skip streak check too (it fired then)

        // BLOCKER-3 fix — fire nutrition streak via the shared helper.
        evaluateNutritionStreakAt(nowMillis, retroactive = false)

        runAchievementAndRankChecks(nowMillis = nowMillis)
    }

    // ---------- Retroactive replay entry points (used by plan 06 RetroactiveWalker) ----------

    /**
     * Process a single historical workout in chronological order. Used by
     * RetroactiveWalker (plan 06). PR detection uses the caller-maintained
     * running-PB map so "PB at that point in time" is correct (D-12).
     */
    suspend fun processHistoricalWorkout(
        workoutId: Long,
        awardedAtMillis: Long,
        runningPbKgX10: MutableMap<String, Int>
    ) {
        processWorkout(
            workoutId = workoutId,
            awardedAtMillis = awardedAtMillis,
            retroactive = true,
            pbOverride = runningPbKgX10
        )
    }

    suspend fun processHistoricalGoalDay(date: LocalDate, awardedAtMillis: Long) {
        val awarded = gamificationRepo.awardXp(
            source = EventKeys.SOURCE_NUTRITION_GOAL_DAY,
            eventKey = EventKeys.goalDay(date.toString()),
            amount = XpFormula.NUTRITION_GOAL_DAY_XP,
            awardedAtMillis = awardedAtMillis,
            retroactive = true
        )
        if (!awarded) return
        // BLOCKER-3 fix — retroactive path awards nutrition streak bonus identically to live path.
        evaluateNutritionStreakAt(awardedAtMillis, retroactive = true)
    }

    // ---------- Internals ----------

    private suspend fun processWorkout(
        workoutId: Long,
        awardedAtMillis: Long,
        retroactive: Boolean,
        pbOverride: MutableMap<String, Int>? = null
    ) {
        // 1. Read the workout's exercises + sets from Room.
        val exercises = completedWorkoutDao.getExercisesForWorkout(workoutId)
        val sets = exercises.flatMap { ex -> completedWorkoutDao.getSetsForExercise(ex.id) }
        val xpInputs = sets.map { WorkoutSetInput(actualReps = it.actualReps, actualWeightKgX10 = it.actualWeightKgX10) }

        // 2. Workout XP (D-02).
        val workoutXp = XpFormula.workoutXp(xpInputs)
        if (workoutXp > 0) {
            gamificationRepo.awardXp(
                source = EventKeys.SOURCE_WORKOUT,
                eventKey = EventKeys.workout(workoutId),
                amount = workoutXp,
                awardedAtMillis = awardedAtMillis,
                retroactive = retroactive
            )
        }

        // 3. PR detection (D-03). For each exercise, find the max weight in this session.
        //    A new PR = this session's max > the prior known PB for that exercise.
        val perExerciseMax: Map<String, Int> = exercises.associate { ex ->
            val maxForEx = sets
                .filter { s -> s.workoutExerciseId == ex.id && s.actualReps > 0 }
                .maxOfOrNull { it.actualWeightKgX10 } ?: 0
            ex.exerciseId to maxForEx
        }

        val priorPbs: Map<String, Int> = if (pbOverride != null) {
            // Retroactive path: use the caller-managed running-PB map.
            pbOverride.toMap()
        } else {
            // Live path: query Room for the current PBs for affected exercises.
            val ids = perExerciseMax.keys.toList()
            if (ids.isEmpty()) emptyMap()
            else completedWorkoutDao.getPersonalBests(ids).associate { dto -> dto.exerciseId to (dto.maxWeightKgX10 ?: 0) }
        }

        for ((exerciseId, maxInSession) in perExerciseMax) {
            if (maxInSession <= 0) continue
            val prior = priorPbs[exerciseId] ?: 0
            if (maxInSession > prior) {
                gamificationRepo.awardXp(
                    source = EventKeys.SOURCE_PR,
                    eventKey = EventKeys.pr(exerciseId, workoutId),
                    amount = XpFormula.PR_XP,
                    awardedAtMillis = awardedAtMillis,
                    retroactive = retroactive
                )
                // Maintain running PB map for retroactive replay.
                pbOverride?.put(exerciseId, maxInSession)
            }
        }

        // 4. Workout streak check (D-06). Pulls ALL completed workout dates from Room.
        val allWorkoutEpochDays = completedWorkoutDao.getAllWorkouts().first()
            .map { it.startTimeMillis.toEpochDay() }
        val streak = StreakCalculator.longestStreak(allWorkoutEpochDays)
        val workoutThresholds = listOf(3, 7, 30)
        for (t in workoutThresholds) {
            if (streak.currentLength < t) continue
            val key = EventKeys.streakWorkout(t, streak.runStartEpochDay ?: 0L)
            gamificationRepo.awardXp(
                source = EventKeys.SOURCE_STREAK_WORKOUT,
                eventKey = key,
                amount = XpFormula.streakWorkoutXp(t),
                awardedAtMillis = awardedAtMillis,
                retroactive = retroactive
            )
        }
    }

    /**
     * D-06 nutrition streak evaluator — shared helper used by both
     * evaluateGoalDay (live path) and processHistoricalGoalDay (retroactive
     * path) so behaviour stays identical. Reads ISO dates from the
     * xp_ledger via GamificationRepository.getGoalDayIsoDates(), converts to
     * epochDays, computes the current run length, and fires the 7-day threshold
     * bonus via EventKeys.streakNutrition(...) with (source, eventKey) dedupe.
     */
    private suspend fun evaluateNutritionStreakAt(
        awardedAtMillis: Long,
        retroactive: Boolean
    ) {
        val isoDates = gamificationRepo.getGoalDayIsoDates()
        if (isoDates.isEmpty()) return
        val epochDays = isoDates.mapNotNull { iso ->
            runCatching { LocalDate.parse(iso).toEpochDays().toLong() }.getOrNull()
        }
        if (epochDays.isEmpty()) return
        val streak = StreakCalculator.longestStreak(epochDays)
        val nutritionThresholds = listOf(7)   // D-06 — nutrition streak: +100 XP @ 7 consecutive goal-days
        for (t in nutritionThresholds) {
            if (streak.currentLength < t) continue
            val key = EventKeys.streakNutrition(t, streak.runStartEpochDay ?: 0L)
            gamificationRepo.awardXp(
                source = EventKeys.SOURCE_STREAK_NUTRITION,
                eventKey = key,
                amount = XpFormula.streakNutritionXp(t),
                awardedAtMillis = awardedAtMillis,
                retroactive = retroactive
            )
        }
    }

    private suspend fun runAchievementAndRankChecks(nowMillis: Long) {
        val snapshot = buildSnapshot()
        val currentStates = gamificationRepo.achievements.first()
        val eval = AchievementRules.evaluate(snapshot, currentStates)

        // Persist progress updates.
        for ((id, progress) in eval.updatedProgress) {
            gamificationRepo.setAchievementProgress(id, progress)
        }

        // Unlock tiers + award tier XP (D-17) + emit UnlockEvents (D-19).
        for (id in eval.toUnlock) {
            val def = AchievementCatalog.findById(id) ?: continue
            gamificationRepo.unlockAchievement(id, nowMillis, def.threshold)
            gamificationRepo.awardXp(
                source = EventKeys.SOURCE_ACHIEVEMENT,
                eventKey = EventKeys.achievement(id),
                amount = XpFormula.achievementXp(def.tier),
                awardedAtMillis = nowMillis,
                retroactive = false
            )
            _unlockEvents.tryEmit(
                UnlockEvent.AchievementTierUnlocked(
                    achievementId = def.id,
                    displayName = def.displayName,
                    tier = def.tier,
                    flavourCopy = def.flavourCopy
                )
            )
        }

        // Rank promotion (D-09, D-10, D-11).
        checkRankPromotion(nowMillis)
    }

    private suspend fun checkRankPromotion(nowMillis: Long) {
        val currentState = gamificationRepo.getRankStateSnapshot()
        val totalXp = gamificationRepo.totalXp.first()
        val newRank = RankLadder.rankForXp(totalXp)

        val previousRank: Rank? = when (currentState) {
            is RankState.Unranked -> null
            is RankState.Ranked -> currentState.currentRank
        }

        // D-10: rank is monotonically non-decreasing.
        val targetRank = if (previousRank != null && newRank.ordinal < previousRank.ordinal) {
            previousRank
        } else {
            newRank
        }

        if (previousRank == targetRank && currentState !is RankState.Unranked) return

        gamificationRepo.setRankState(
            totalXp = totalXp,
            currentRank = targetRank,
            lastPromotedAtMillis = nowMillis,
            isUnranked = false
        )

        if (previousRank != targetRank) {
            _unlockEvents.tryEmit(
                UnlockEvent.RankPromotion(
                    fromRank = previousRank,
                    toRank = targetRank,
                    totalXp = totalXp,
                    flavourCopy = "Promoted to ${targetRank.displayName}."
                )
            )
        }
    }

    /** Build an aggregated snapshot from Room for achievement evaluation. */
    private suspend fun buildSnapshot(): ProgressSnapshot {
        val allWorkouts = completedWorkoutDao.getAllWorkouts().first()
        val allExerciseRows = allWorkouts.flatMap { w -> completedWorkoutDao.getExercisesForWorkout(w.id) }
        val allSetRows = allExerciseRows.flatMap { e -> completedWorkoutDao.getSetsForExercise(e.id) }

        // Lifetime volume (D-14 volume family).
        val lifetimeVolume = allSetRows.sumOf { (it.actualReps * it.actualWeightKgX10).toLong() } / 10L

        // Best single-session volume (D-14 volume-single-session).
        val volumePerWorkout: List<Long> = allWorkouts.map { w ->
            val wExs = allExerciseRows.filter { ex -> ex.workoutId == w.id }
            val wSets = wExs.flatMap { ex -> allSetRows.filter { it.workoutExerciseId == ex.id } }
            wSets.sumOf { (it.actualReps * it.actualWeightKgX10).toLong() } / 10L
        }

        // Workout streak (D-14 consistency-longest-streak).
        val epochDays = allWorkouts.map { it.startTimeMillis.toEpochDay() }
        val longestWorkoutStreak = StreakCalculator.longestStreak(epochDays).currentLength

        // Distinct trained exercises (D-14 variety-exercises).
        val distinctExerciseIds = allExerciseRows.map { it.exerciseId }.toSet()

        // PR-hunter stats — BLOCKER-4 fix: derive from xp_ledger rows where source='pr'.
        // EventKeys.parsePr reverses the "pr:<exerciseId>:<workoutId>" format.
        val prLedgerEntries = gamificationRepo.getPrLedgerEntries()
        val totalPrsSet = prLedgerEntries.size.toLong()
        val prMeta: List<Pair<String, Long>> = prLedgerEntries.mapNotNull { row ->
            EventKeys.parsePr(row.eventKey)?.let { parsed -> parsed.exerciseId to parsed.workoutId }
        }
        val distinctExercisesWithPr = prMeta.map { it.first }.toSet().size
        val bestPrsInSingleSession = prMeta.groupBy { it.second }
            .values.maxOfOrNull { it.size } ?: 0

        // Variety-coverage: join trained exerciseIds → ExerciseEntity.primaryMuscles.
        // ExerciseEntity.primaryMuscles is comma-separated groupName values (e.g., "chest,abs").
        val allExerciseEntities = exerciseDao.getAllExercises().first()
            .filter { it.id in distinctExerciseIds }
        val trainedGroupNames: Set<String> = allExerciseEntities
            .flatMap { entity -> entity.primaryMuscles.split(",").map { s -> s.trim().lowercase() } }
            .filter { it.isNotBlank() }
            .toSet()
        val frontGroupNames = com.pumpernickel.domain.model.MuscleRegionPaths.frontRegions
            .map { r -> r.groupName.lowercase() }.toSet()
        val backGroupNames = com.pumpernickel.domain.model.MuscleRegionPaths.backRegions
            .map { r -> r.groupName.lowercase() }.toSet()
        val distinctFrontHit = trainedGroupNames.intersect(frontGroupNames).size
        val distinctBackHit = trainedGroupNames.intersect(backGroupNames).size

        // Nutrition stats — WARNING-9 fix: use shared NutritionGoalDayPolicy.
        val goals = settingsRepo.nutritionGoals.first()
        val allConsumption = nutritionDao.getAllEntries()
        // Group consumption entries by ISO date (derived from timestampMillis in local TZ).
        val goalDayEpochDays = allConsumption
            .groupBy { it.timestampMillis.toLocalDateString() }
            .filter { (_, entries) -> NutritionGoalDayPolicy.isGoalDay(entries, goals) }
            .keys
            .mapNotNull { isoDate ->
                runCatching { LocalDate.parse(isoDate).toEpochDays().toLong() }.getOrNull()
            }
        val longestNutritionStreak = StreakCalculator.longestStreak(goalDayEpochDays).currentLength

        return ProgressSnapshot(
            lifetimeVolumeKgReps = lifetimeVolume,
            bestSingleSessionVolumeKgReps = volumePerWorkout.maxOrNull() ?: 0L,
            longestWorkoutStreakDays = longestWorkoutStreak,
            totalWorkouts = allWorkouts.size.toLong(),
            totalNutritionGoalDays = goalDayEpochDays.size.toLong(),
            longestNutritionStreakDays = longestNutritionStreak,
            totalPrsSet = totalPrsSet,
            distinctExercisesWithPr = distinctExercisesWithPr,
            bestPrsInSingleSession = bestPrsInSingleSession,
            distinctExercisesTrained = distinctExerciseIds.size,
            distinctFrontGroupsTrained = distinctFrontHit,
            distinctBackGroupsTrained = distinctBackHit
        )
    }

    // ---------- Helpers ----------

    /** Convert epoch-milliseconds to an epochDay value in the local time zone. */
    private fun Long.toEpochDay(): Long =
        Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toEpochDays()
            .toLong()

    /** Convert epoch-milliseconds to an ISO "YYYY-MM-DD" string in the local time zone. */
    private fun Long.toLocalDateString(): String =
        Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    private fun currentTimeMillis(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}

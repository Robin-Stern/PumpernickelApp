package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XpFormulaTest {
    @Test fun emptySetsYieldZeroXp() {
        assertEquals(0, XpFormula.workoutXp(emptyList()))
    }

    @Test fun singleSetTenRepsOneHundredKgYieldsTenXp() {
        // 10 reps x 100.0 kg = 1000 volume -> /100 = 10 XP
        val sets = listOf(WorkoutSetInput(actualReps = 10, actualWeightKgX10 = 1000))
        assertEquals(10, XpFormula.workoutXp(sets))
    }

    @Test fun fractionalVolumeIsFloored() {
        // 5 reps x 5.5 kg = 27.5 volume -> /100 = 0.275 -> floor = 0 XP
        val sets = listOf(WorkoutSetInput(actualReps = 5, actualWeightKgX10 = 55))
        assertEquals(0, XpFormula.workoutXp(sets))
    }

    @Test fun prXpIsFifty() { assertEquals(50, XpFormula.PR_XP) }

    @Test fun streakBonusesMatchDesignDoc() {
        assertEquals(25, XpFormula.streakWorkoutXp(3))
        assertEquals(100, XpFormula.streakWorkoutXp(7))
        assertEquals(500, XpFormula.streakWorkoutXp(30))
        assertEquals(100, XpFormula.streakNutritionXp(7))
    }

    @Test fun achievementTierXpMatchesD17() {
        assertEquals(25, XpFormula.achievementXp(Tier.BRONZE))
        assertEquals(75, XpFormula.achievementXp(Tier.SILVER))
        assertEquals(200, XpFormula.achievementXp(Tier.GOLD))
    }

    @Test fun eventKeysFormatRegistry() {
        assertEquals("workout:42", EventKeys.workout(42L))
        assertEquals("pr:bench:7", EventKeys.pr("bench", 7L))
        assertEquals("goalday:2026-04-20", EventKeys.goalDay("2026-04-20"))
        assertEquals("streak:workout:7:20000", EventKeys.streakWorkout(7, 20000L))
        assertEquals("streak:nutrition:7:20000", EventKeys.streakNutrition(7, 20000L))
        assertEquals("achievement:volume-gold", EventKeys.achievement("volume-gold"))
    }

    @Test fun parsePrRoundTrip() {
        val key = EventKeys.pr("bench-press-uuid", 42L)
        val parsed = EventKeys.parsePr(key)
        assertEquals("bench-press-uuid", parsed?.exerciseId)
        assertEquals(42L, parsed?.workoutId)
    }

    @Test fun parsePrRejectsGarbage() {
        assertNull(EventKeys.parsePr("workout:42"))
        assertNull(EventKeys.parsePr("pr:onlyone"))
        assertNull(EventKeys.parsePr("pr:ex:notanumber"))
    }
}

package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreakCalculatorTest {

    @Test fun emptyListReturnsZeroAndNull() {
        val result = StreakCalculator.longestStreak(emptyList())
        assertEquals(0, result.currentLength)
        assertNull(result.runStartEpochDay)
    }

    @Test fun singleDayRunsLengthOne() {
        val result = StreakCalculator.longestStreak(listOf(100L))
        assertEquals(1, result.currentLength)
        assertEquals(100L, result.runStartEpochDay)
    }

    @Test fun threeConsecutiveDaysRunsLengthThree() {
        val result = StreakCalculator.longestStreak(listOf(100L, 101L, 102L))
        assertEquals(3, result.currentLength)
        assertEquals(100L, result.runStartEpochDay)
    }

    @Test fun gapBreaksRunAtTail() {
        // 100, 101, 103 → latest is 103, only one day in current run
        val result = StreakCalculator.longestStreak(listOf(100L, 101L, 103L))
        assertEquals(1, result.currentLength)
        assertEquals(103L, result.runStartEpochDay)
    }

    @Test fun duplicatesAreDeduplicated() {
        val result = StreakCalculator.longestStreak(listOf(100L, 100L, 101L, 101L, 102L))
        assertEquals(3, result.currentLength)
        assertEquals(100L, result.runStartEpochDay)
    }

    @Test fun unsortedInputIsSortedInternally() {
        val result = StreakCalculator.longestStreak(listOf(102L, 100L, 101L))
        assertEquals(3, result.currentLength)
        assertEquals(100L, result.runStartEpochDay)
    }

    @Test fun thresholdsCrossedFromScratchToSeven() {
        val crossed = StreakCalculator.thresholdsCrossed(0, 7, listOf(3, 7, 30))
        assertEquals(listOf(3, 7), crossed)
    }

    @Test fun thresholdsCrossedFromSixToSeven() {
        val crossed = StreakCalculator.thresholdsCrossed(6, 7, listOf(3, 7, 30))
        assertEquals(listOf(7), crossed)
    }

    @Test fun thresholdsCrossedNoChange() {
        val crossed = StreakCalculator.thresholdsCrossed(7, 7, listOf(3, 7, 30))
        assertEquals(emptyList(), crossed)
    }

    @Test fun thresholdsCrossedDecrease() {
        val crossed = StreakCalculator.thresholdsCrossed(7, 3, listOf(3, 7, 30))
        assertEquals(emptyList(), crossed)  // never fires on a decrease — streak broke
    }
}

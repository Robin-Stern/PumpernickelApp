package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementRulesTest {

    private fun locked(id: String): AchievementProgress {
        val def = AchievementCatalog.findById(id)!!
        return AchievementProgress(def = def, currentProgress = 0L, unlockedAtMillis = null)
    }

    private fun unlocked(id: String): AchievementProgress {
        val def = AchievementCatalog.findById(id)!!
        return AchievementProgress(def = def, currentProgress = def.threshold, unlockedAtMillis = 1L)
    }

    @Test fun volumeBronzeUnlocksAtExactThreshold() {
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(lifetimeVolumeKgReps = 10_000L),
            currentStates = listOf(locked("volume-bronze"))
        )
        assertTrue(result.toUnlock.contains("volume-bronze"))
        assertEquals(10_000L, result.updatedProgress["volume-bronze"])
    }

    @Test fun volumeBronzeDoesNotUnlockBelowThreshold() {
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(lifetimeVolumeKgReps = 9_999L),
            currentStates = listOf(locked("volume-bronze"))
        )
        assertTrue(result.toUnlock.isEmpty())
        assertEquals(9_999L, result.updatedProgress["volume-bronze"])
    }

    @Test fun alreadyUnlockedAchievementDoesNotUnlockAgain() {
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(totalWorkouts = 250L),
            currentStates = listOf(unlocked("consistency-total-workouts-gold"))
        )
        assertTrue(result.toUnlock.isEmpty(), "Already-unlocked achievement must not re-unlock")
    }

    @Test fun multipleTiersUnlockAtOnce() {
        // Bronze + silver for total workouts — 50 workouts qualifies both.
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(totalWorkouts = 50L),
            currentStates = listOf(
                locked("consistency-total-workouts-bronze"),
                locked("consistency-total-workouts-silver"),
                locked("consistency-total-workouts-gold")
            )
        )
        assertTrue(result.toUnlock.contains("consistency-total-workouts-bronze"))
        assertTrue(result.toUnlock.contains("consistency-total-workouts-silver"))
        assertTrue(!result.toUnlock.contains("consistency-total-workouts-gold"))
    }

    @Test fun varietyAchievementChecksDistinctCount() {
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(distinctExercisesTrained = 5),
            currentStates = listOf(locked("variety-exercises-bronze"))
        )
        assertTrue(result.toUnlock.contains("variety-exercises-bronze"))
    }

    @Test fun unknownFamilyIsIgnored() {
        // Simulate a rogue achievement def not covered by the rule — should no-op.
        val rogue = AchievementProgress(
            def = AchievementDef(
                id = "rogue-bronze", family = "rogue",
                displayName = "Rogue", category = Category.VOLUME,
                tier = Tier.BRONZE, threshold = 1L, flavourCopy = ""
            ),
            currentProgress = 0L, unlockedAtMillis = null
        )
        val result = AchievementRules.evaluate(
            snapshot = ProgressSnapshot(),
            currentStates = listOf(rogue)
        )
        assertTrue(result.toUnlock.isEmpty())
        assertTrue(!result.updatedProgress.containsKey("rogue-bronze"))
    }
}

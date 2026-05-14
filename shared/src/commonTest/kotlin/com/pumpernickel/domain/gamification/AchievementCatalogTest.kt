package com.pumpernickel.domain.gamification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AchievementCatalogTest {

    @Test fun catalogSizeWithinD15Range() {
        assertTrue(
            AchievementCatalog.all.size in 30..45,
            "Expected 30-45 entries per D-15, got ${AchievementCatalog.all.size}"
        )
    }

    @Test fun allIdsAreUnique() {
        val ids = AchievementCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate achievement IDs: $ids")
    }

    @Test fun allIdsMatchTieredFormat() {
        val regex = Regex("^[a-z][a-z0-9-]*-(bronze|silver|gold)$")
        AchievementCatalog.all.forEach { def ->
            assertTrue(regex.matches(def.id), "Bad id: ${def.id}")
        }
    }

    @Test fun everyFamilyHasAllThreeTiers() {
        val byFamily = AchievementCatalog.all.groupBy { it.family }
        byFamily.forEach { (family, defs) ->
            assertEquals(3, defs.size, "Family $family has ${defs.size} tiers, expected 3")
            assertEquals(setOf(Tier.BRONZE, Tier.SILVER, Tier.GOLD), defs.map { it.tier }.toSet())
        }
    }

    @Test fun thresholdsMonotonicallyIncrease() {
        AchievementCatalog.all.groupBy { it.family }.forEach { (family, defs) ->
            val sorted = defs.sortedBy { it.tier.ordinal }
            for (i in 0 until sorted.size - 1) {
                assertTrue(
                    sorted[i].threshold < sorted[i + 1].threshold,
                    "Family $family: ${sorted[i].tier} >= ${sorted[i+1].tier}"
                )
            }
        }
    }

    @Test fun everyCategoryHasAtLeastOneAchievement() {
        val categoriesCovered = AchievementCatalog.all.map { it.category }.toSet()
        assertEquals(setOf(Category.VOLUME, Category.CONSISTENCY, Category.PR_HUNTER, Category.VARIETY), categoriesCovered)
    }

    @Test fun findByIdReturnsMatchingEntry() {
        val entry = AchievementCatalog.findById("volume-bronze")
        assertNotNull(entry)
        assertEquals(Category.VOLUME, entry.category)
        assertEquals(Tier.BRONZE, entry.tier)
    }
}

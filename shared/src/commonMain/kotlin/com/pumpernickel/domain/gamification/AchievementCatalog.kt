package com.pumpernickel.domain.gamification

/**
 * Static catalog of achievements. See D-14, D-15, D-16.
 * 12 achievement families x 3 tiers = 36 entries (within D-15 10-15 x 3 = 30-45 range).
 *
 * ID format: "<family>-<tier>" — e.g., "volume-bronze".
 * Stable strings — do NOT rename without migrating achievement_state.
 *
 * TODO(tuning): thresholds are D-07 / D-15 Claude-discretion anchors, aligned with the
 * RankLadder BASE_XP = 500 curve. Re-calibrate after play-testing.
 */
object AchievementCatalog {

    val all: List<AchievementDef> = buildList {
        // ----- VOLUME (lifetime sum(reps x kg)) -----
        addFamily(
            family = "volume",
            displayBase = "Iron Tonnage",
            category = Category.VOLUME,
            bronze = 10_000L, silver = 100_000L, gold = 1_000_000L,
            flavour = mapOf(
                Tier.BRONZE to "First ton of iron moved.",
                Tier.SILVER to "Hundred tons of iron moved.",
                Tier.GOLD to "A thousand tons — approaching industrial."
            )
        )

        // ----- VOLUME — single-session volume -----
        addFamily(
            family = "volume-single-session",
            displayBase = "Monster Session",
            category = Category.VOLUME,
            bronze = 5_000L, silver = 15_000L, gold = 40_000L,
            flavour = mapOf(
                Tier.BRONZE to "5 000 kg·reps in a single session.",
                Tier.SILVER to "15 000 kg·reps in a single session.",
                Tier.GOLD to "40 000 kg·reps in a single session. Hero."
            )
        )

        // ----- CONSISTENCY — longest workout streak (days) -----
        addFamily(
            family = "consistency-longest-streak",
            displayBase = "On a Roll",
            category = Category.CONSISTENCY,
            bronze = 3L, silver = 7L, gold = 30L,
            flavour = mapOf(
                Tier.BRONZE to "Three days straight.",
                Tier.SILVER to "A full week without missing.",
                Tier.GOLD to "A month of consecutive grind."
            )
        )

        // ----- CONSISTENCY — total workouts completed -----
        addFamily(
            family = "consistency-total-workouts",
            displayBase = "Repetition",
            category = Category.CONSISTENCY,
            bronze = 10L, silver = 50L, gold = 250L,
            flavour = mapOf(
                Tier.BRONZE to "Ten workouts in the book.",
                Tier.SILVER to "Fifty sessions logged.",
                Tier.GOLD to "A quarter-thousand workouts. Veteran."
            )
        )

        // ----- CONSISTENCY — nutrition goal-days hit -----
        addFamily(
            family = "consistency-nutrition-days",
            displayBase = "Kitchen Discipline",
            category = Category.CONSISTENCY,
            bronze = 3L, silver = 15L, gold = 60L,
            flavour = mapOf(
                Tier.BRONZE to "Three nutrition goal-days met.",
                Tier.SILVER to "Fifteen goal-days across the log.",
                Tier.GOLD to "Sixty goal-days — the kitchen is dialled in."
            )
        )

        // ----- CONSISTENCY — nutrition streak (longest) -----
        addFamily(
            family = "consistency-nutrition-streak",
            displayBase = "Steady Fuel",
            category = Category.CONSISTENCY,
            bronze = 3L, silver = 7L, gold = 30L,
            flavour = mapOf(
                Tier.BRONZE to "Three nutrition goal-days in a row.",
                Tier.SILVER to "Seven-day nutrition streak.",
                Tier.GOLD to "Thirty-day nutrition streak — rare air."
            )
        )

        // ----- PR HUNTER — total PRs set -----
        addFamily(
            family = "pr-hunter-total",
            displayBase = "PR Hunter",
            category = Category.PR_HUNTER,
            bronze = 1L, silver = 10L, gold = 50L,
            flavour = mapOf(
                Tier.BRONZE to "First personal record.",
                Tier.SILVER to "Ten PRs and counting.",
                Tier.GOLD to "Fifty PRs — genuinely elite."
            )
        )

        // ----- PR HUNTER — PRs across distinct exercises -----
        addFamily(
            family = "pr-hunter-breadth",
            displayBase = "All-Rounder PR",
            category = Category.PR_HUNTER,
            bronze = 3L, silver = 10L, gold = 25L,
            flavour = mapOf(
                Tier.BRONZE to "PRs in 3 different exercises.",
                Tier.SILVER to "PRs in 10 different exercises.",
                Tier.GOLD to "PRs in 25 different exercises."
            )
        )

        // ----- PR HUNTER — single-session multi-PR -----
        addFamily(
            family = "pr-hunter-multi-session",
            displayBase = "Hot Hands",
            category = Category.PR_HUNTER,
            bronze = 2L, silver = 3L, gold = 5L,
            flavour = mapOf(
                Tier.BRONZE to "Two PRs in one session.",
                Tier.SILVER to "Three PRs in one session.",
                Tier.GOLD to "Five PRs in one session. Legendary day."
            )
        )

        // ----- VARIETY — distinct exercises performed -----
        addFamily(
            family = "variety-exercises",
            displayBase = "Explorer",
            category = Category.VARIETY,
            bronze = 5L, silver = 15L, gold = 30L,
            flavour = mapOf(
                Tier.BRONZE to "Five different exercises trained.",
                Tier.SILVER to "Fifteen different exercises trained.",
                Tier.GOLD to "Thirty different exercises — true explorer."
            )
        )

        // ----- VARIETY — muscle-group coverage (front) -----
        // Threshold = number of distinct front groupNames hit.
        // MuscleRegionPaths.frontRegions is the canonical source — count at unlock time.
        addFamily(
            family = "variety-front-coverage",
            displayBase = "Front Sculpt",
            category = Category.VARIETY,
            bronze = 3L, silver = 6L, gold = 10L,
            flavour = mapOf(
                Tier.BRONZE to "Training the front, starting strong.",
                Tier.SILVER to "Six front muscle groups trained.",
                Tier.GOLD to "All front groups covered. Balanced."
            )
        )

        // ----- VARIETY — muscle-group coverage (back) -----
        addFamily(
            family = "variety-back-coverage",
            displayBase = "Back Engine",
            category = Category.VARIETY,
            bronze = 3L, silver = 6L, gold = 10L,
            flavour = mapOf(
                Tier.BRONZE to "The back gets attention too.",
                Tier.SILVER to "Six back muscle groups trained.",
                Tier.GOLD to "All back groups covered. Unseen work pays off."
            )
        )
    }

    fun findById(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    fun byCategory(): Map<Category, List<AchievementDef>> = all.groupBy { it.category }

    // ----- Internal DSL helper -----
    private fun MutableList<AchievementDef>.addFamily(
        family: String,
        displayBase: String,
        category: Category,
        bronze: Long,
        silver: Long,
        gold: Long,
        flavour: Map<Tier, String>
    ) {
        add(AchievementDef(
            id = "$family-bronze",
            family = family,
            displayName = "$displayBase I",
            category = category,
            tier = Tier.BRONZE,
            threshold = bronze,
            flavourCopy = flavour.getValue(Tier.BRONZE)
        ))
        add(AchievementDef(
            id = "$family-silver",
            family = family,
            displayName = "$displayBase II",
            category = category,
            tier = Tier.SILVER,
            threshold = silver,
            flavourCopy = flavour.getValue(Tier.SILVER)
        ))
        add(AchievementDef(
            id = "$family-gold",
            family = family,
            displayName = "$displayBase III",
            category = category,
            tier = Tier.GOLD,
            threshold = gold,
            flavourCopy = flavour.getValue(Tier.GOLD)
        ))
    }
}

/** Static achievement definition. Immutable. Stable by `id`. */
data class AchievementDef(
    val id: String,
    val family: String,
    val displayName: String,
    val category: Category,
    val tier: Tier,
    val threshold: Long,
    val flavourCopy: String
)

enum class Category { VOLUME, CONSISTENCY, PR_HUNTER, VARIETY }
enum class Tier { BRONZE, SILVER, GOLD }

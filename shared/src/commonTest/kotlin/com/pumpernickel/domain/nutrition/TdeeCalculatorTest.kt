package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TdeeCalculatorTest {

    private fun stats(
        weightKg: Double = 80.0,
        heightCm: Int = 180,
        age: Int = 30,
        sex: Sex = Sex.MALE,
        activity: ActivityLevel = ActivityLevel.MODERATELY_ACTIVE
    ) = UserPhysicalStats(weightKg, heightCm, age, sex, activity)

    @Test fun bmrMaleKnownValue() {
        // 10*80 + 6.25*180 − 5*30 + 5 = 800 + 1125 − 150 + 5 = 1780
        assertEquals(1780.0, TdeeCalculator.bmr(stats()), absoluteTolerance = 0.01)
    }

    @Test fun bmrFemaleKnownValue() {
        // 10*60 + 6.25*165 − 5*25 − 161 = 600 + 1031.25 − 125 − 161 = 1345.25
        assertEquals(
            1345.25,
            TdeeCalculator.bmr(stats(weightKg = 60.0, heightCm = 165, age = 25, sex = Sex.FEMALE)),
            absoluteTolerance = 0.01
        )
    }

    @Test fun tdeeAppliesMultiplier() {
        // BMR 1780 × 1.55 = 2759.0
        assertEquals(2759.0, TdeeCalculator.tdee(stats()), absoluteTolerance = 0.5)
    }

    @Test fun tdeeMultipliersForEachTier() {
        val baseBmr = TdeeCalculator.bmr(stats())  // 1780
        val pairs = listOf(
            ActivityLevel.SEDENTARY         to 1.2,
            ActivityLevel.LIGHTLY_ACTIVE    to 1.375,
            ActivityLevel.MODERATELY_ACTIVE to 1.55,
            ActivityLevel.VERY_ACTIVE       to 1.725,
            ActivityLevel.EXTRA_ACTIVE      to 1.9
        )
        for ((tier, mult) in pairs) {
            val expected = baseBmr * mult
            val actual = TdeeCalculator.tdee(stats(activity = tier))
            assertEquals(expected, actual, absoluteTolerance = 0.5, message = "$tier")
        }
    }

    @Test fun cutSuggestionIsTdeeMinus500() {
        val s = TdeeCalculator.suggestions(stats())
        val tdeeRounded = TdeeCalculator.tdee(stats())
        assertEquals((tdeeRounded - 500).toInt().toDouble(), s.cut.kcal.toDouble(), absoluteTolerance = 1.0)
    }

    @Test fun maintainSuggestionEqualsTdee() {
        val s = TdeeCalculator.suggestions(stats())
        val tdeeRounded = TdeeCalculator.tdee(stats())
        assertEquals(tdeeRounded.toInt().toDouble(), s.maintain.kcal.toDouble(), absoluteTolerance = 1.0)
    }

    @Test fun bulkSuggestionIsTdeePlus300() {
        val s = TdeeCalculator.suggestions(stats())
        val tdeeRounded = TdeeCalculator.tdee(stats())
        assertEquals((tdeeRounded + 300).toInt().toDouble(), s.bulk.kcal.toDouble(), absoluteTolerance = 1.0)
    }

    @Test fun proteinCut2Point2PerKg() {
        val s = TdeeCalculator.suggestions(stats())  // 80 kg
        // 80 * 2.2 = 176 -> roundToStep(176) = ((176+2)/5)*5 = 35*5 = 175
        assertEquals(175, s.cut.proteinG)
    }

    @Test fun proteinMaintain2PerKg() {
        val s = TdeeCalculator.suggestions(stats())
        // 80 * 2.0 = 160 -> roundToStep(160) = ((160+2)/5)*5 = 32*5 = 160
        assertEquals(160, s.maintain.proteinG)
    }

    @Test fun proteinBulk1Point8PerKg() {
        val s = TdeeCalculator.suggestions(stats())
        // 80 * 1.8 = 144 -> roundToStep(144) = ((144+2)/5)*5 = 29*5 = 145
        assertEquals(145, s.bulk.proteinG)
    }

    @Test fun sugarAlwaysFifty() {
        val s = TdeeCalculator.suggestions(stats())
        assertEquals(50, s.cut.sugarG)
        assertEquals(50, s.maintain.sugarG)
        assertEquals(50, s.bulk.sugarG)
    }

    @Test fun carbsNonNegativeOnExtremeLowKcal() {
        val s = TdeeCalculator.suggestions(
            stats(weightKg = 50.0, heightCm = 155, age = 25, sex = Sex.FEMALE, activity = ActivityLevel.SEDENTARY)
        )
        assertTrue(s.cut.carbsG >= 0, "carbs must not be negative; got ${s.cut.carbsG}")
    }
}

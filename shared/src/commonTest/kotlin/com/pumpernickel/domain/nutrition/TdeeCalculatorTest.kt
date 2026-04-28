package com.pumpernickel.domain.nutrition

import com.pumpernickel.domain.model.ActivityLevel
import com.pumpernickel.domain.model.Sex
import com.pumpernickel.domain.model.UserPhysicalStats
import kotlin.test.Test
import kotlin.test.assertEquals

// RED: Minimal failing test — TdeeCalculator does not exist yet.
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
}

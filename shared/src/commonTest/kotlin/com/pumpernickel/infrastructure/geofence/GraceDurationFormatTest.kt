package com.pumpernickel.infrastructure.geofence

import kotlin.test.Test
import kotlin.test.assertEquals

class GraceDurationFormatTest {

    @Test fun tenSecondsIsZehnSekunden() {
        assertEquals("10 Sekunden", formatGraceDuration(10))
    }

    @Test fun fiftyNineSecondsIsBelowMinuteBoundary() {
        assertEquals("59 Sekunden", formatGraceDuration(59))
    }

    @Test fun sixtySecondsIsOneMinute() {
        // Plural form intentional — D-21-06 / behavior spec says singular is low-prio.
        assertEquals("1 Minuten", formatGraceDuration(60))
    }

    @Test fun threeHundredSecondsIsFiveMinutes() {
        assertEquals("5 Minuten", formatGraceDuration(300))
    }

    @Test fun sixHundredSecondsIsTenMinutes() {
        assertEquals("10 Minuten", formatGraceDuration(600))
    }

    @Test fun oneHourIsEinStunde() {
        assertEquals("1 Stunde", formatGraceDuration(3600))
    }

    @Test fun twoHoursIsZweiStunden() {
        assertEquals("2 Stunden", formatGraceDuration(7200))
    }

    @Test fun zeroSecondsDefensiveCase() {
        assertEquals("0 Sekunden", formatGraceDuration(0))
    }

    @Test fun negativeSecondsDefensiveCase() {
        assertEquals("0 Sekunden", formatGraceDuration(-5))
    }
}

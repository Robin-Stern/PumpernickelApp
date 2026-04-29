package com.pumpernickel.domain.location

import kotlin.math.*

data class GeoPoint(val lat: Double, val lon: Double) {

    fun distanceMetersTo(other: GeoPoint): Double {
        val r = 6_371_000.0
        val lat1 = lat.toRad()
        val lat2 = other.lat.toRad()
        val dLat = (other.lat - lat).toRad()
        val dLon = (other.lon - lon).toRad()
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun Double.toRad() = this * PI / 180.0
}

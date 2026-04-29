package com.pumpernickel.domain.location

interface LocationProvider {
    suspend fun getCurrentLocation(): GeoPoint?
}

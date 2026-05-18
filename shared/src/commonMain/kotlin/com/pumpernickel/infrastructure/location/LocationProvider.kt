package com.pumpernickel.infrastructure.location

import com.pumpernickel.domain.location.GeoPoint

interface LocationProvider {
    suspend fun getCurrentLocation(): GeoPoint?
}

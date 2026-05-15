package com.pumpernickel.domain.permissions

/**
 * D-19-12 — platform-agnostic location permission states. Mapping:
 *
 *  iOS (CLAuthorizationStatus):
 *    notDetermined         -> NOT_DETERMINED
 *    denied                -> DENIED
 *    restricted            -> RESTRICTED
 *    authorizedWhenInUse   -> WHEN_IN_USE
 *    authorizedAlways      -> ALWAYS
 *
 *  Android (PackageManager.PERMISSION_*):
 *    ACCESS_FINE_LOCATION denied                                  -> DENIED
 *    ACCESS_FINE_LOCATION granted, BACKGROUND_LOCATION denied     -> WHEN_IN_USE
 *    ACCESS_FINE_LOCATION granted, BACKGROUND_LOCATION granted    -> ALWAYS
 *    First-run (never requested)                                  -> NOT_DETERMINED
 *
 * RESTRICTED is iOS-only (parental controls / MDM). On Android, treat
 * provider-disabled or carrier-restricted as DENIED — the UI banner copy
 * works the same.
 */
enum class LocationPermissionStatus {
    NOT_DETERMINED,
    DENIED,
    RESTRICTED,
    WHEN_IN_USE,
    ALWAYS
}

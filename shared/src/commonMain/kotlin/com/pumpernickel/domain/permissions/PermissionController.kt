package com.pumpernickel.domain.permissions

/**
 * D-19-12 — platform-agnostic permission gateway. Implementations:
 *  - iosMain: IosPermissionController (CLLocationManager + UNUserNotificationCenter)
 *  - androidMain: AndroidPermissionController (ActivityResultContracts)
 *
 * iOS contract: requestWhenInUse() MUST be called first; requestAlways()
 * only succeeds after the user has granted WhenInUse (D-19-09 rationale step).
 *
 * Android contract: requestWhenInUse() requests ACCESS_FINE_LOCATION;
 * requestAlways() requests ACCESS_BACKGROUND_LOCATION (which Android only
 * shows a system dialog for when FINE_LOCATION is already granted, API 29+).
 *
 * openAppSettings() launches the OS app-details screen (iOS:
 * UIApplication.openSettingsURLString; Android:
 * Settings.ACTION_APPLICATION_DETAILS_SETTINGS). The caller can not
 * await the result — observe currentLocationStatus() on the next foregrounding.
 */
interface PermissionController {
    suspend fun currentLocationStatus(): LocationPermissionStatus
    suspend fun requestWhenInUse(): LocationPermissionStatus
    suspend fun requestAlways(): LocationPermissionStatus
    suspend fun requestNotifications(): Boolean
    fun openAppSettings()
}

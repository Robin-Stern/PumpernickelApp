package com.pumpernickel.infrastructure.geofence

/**
 * Formats a grace-period (configured in Settings, persisted in SettingsRepository.gracePeriodSeconds)
 * into the German prose snippet used by the geofence-exit notification body on both Android and iOS.
 *
 * Per D-21-06 — replaces the hardcoded "5 Minuten" string in Android strings.xml and the iOS
 * notification builder. Pure function — no platform dependency, no resource lookup.
 *
 * Boundaries:
 *  - `seconds <= 0`     -> "0 Sekunden" (defensive — covers misconfigured pref / default-init race)
 *  - `seconds < 60`     -> "$seconds Sekunden"
 *  - `seconds < 3600`   -> "${seconds / 60} Minuten" (plural even at 60s — singular is low-prio per spec)
 *  - `seconds == 3600`  -> "1 Stunde"
 *  - `seconds > 3600`   -> "${seconds / 3600} Stunden"
 */
fun formatGraceDuration(seconds: Int): String = when {
    seconds <= 0 -> "0 Sekunden"
    seconds < 60 -> "$seconds Sekunden"
    seconds < 3600 -> "${seconds / 60} Minuten"
    seconds == 3600 -> "1 Stunde"
    else -> "${seconds / 3600} Stunden"
}

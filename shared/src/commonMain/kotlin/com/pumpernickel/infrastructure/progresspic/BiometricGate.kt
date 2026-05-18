package com.pumpernickel.infrastructure.progresspic

import com.pumpernickel.domain.progresspic.UnlockResult

/**
 * OS-level auth gate. Android `actual` uses BiometricPrompt with
 * BIOMETRIC_STRONG or DEVICE_CREDENTIAL (D-17-15). iOS `actual` uses LAContext
 * with .deviceOwnerAuthentication (D-17-15). Reason string is shown in the
 * system prompt (German per app convention, e.g. "Fortschrittsbild entsperren").
 *
 * On a device with no biometric AND no passcode, [requestUnlock] resolves to
 * Success without challenge (D-17-16) — we treat "no device security" as the
 * user's own choice.
 */
expect class BiometricGate {
    suspend fun requestUnlock(reason: String): UnlockResult
}

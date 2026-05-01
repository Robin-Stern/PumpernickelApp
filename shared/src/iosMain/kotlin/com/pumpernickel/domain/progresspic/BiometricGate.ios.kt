@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pumpernickel.domain.progresspic

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/**
 * iOS-side actual for [BiometricGate].
 *
 * Uses LAPolicyDeviceOwnerAuthentication (the policy with built-in passcode
 * fallback) per D-17-15 — the biometric-only sibling policy would block
 * passcode-only devices, which contradicts D-17-16's "treat no-credential and
 * passcode-only as equivalent paths".
 *
 * Reason string is the German-language `reason` argument passed by the caller (D-17-15:
 * "Fortschrittsbild entsperren"). The OS shows it in the system prompt.
 *
 * Per D-17-16: if canEvaluatePolicy reports LAErrorPasscodeNotSet (no biometric AND
 * no passcode), this method returns Success without challenge — we treat "no device
 * security" as the user's choice, not the app's problem.
 *
 * Per D-17-17: User-initiated cancel returns Cancelled; auth-failure returns Failed.
 * No error toast is surfaced; the caller leaves the tile blurred and lets the user retry.
 */
actual class BiometricGate {

    actual suspend fun requestUnlock(reason: String): UnlockResult {
        val context = LAContext()

        // D-17-16 short-circuit: device with no enrolled biometric AND no passcode
        // resolves to Success without prompting.
        memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            val canEvaluate = context.canEvaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                error = errorVar.ptr
            )
            if (!canEvaluate) {
                val code = errorVar.value?.code?.toLong()
                if (code == LAErrorPasscodeNotSet.toLong()) {
                    // No passcode set — D-17-16 says unblur freely.
                    return UnlockResult.Success
                }
                // Other "cannot evaluate" outcomes (no hardware, etc.) for the
                // deviceOwnerAuthentication policy fall through to the prompt
                // attempt below; deviceOwnerAuthentication still allows passcode
                // fallback when biometric is unavailable / not enrolled.
                if (code == LAErrorBiometryNotAvailable.toLong() ||
                    code == LAErrorBiometryNotEnrolled.toLong()
                ) {
                    // Intentional fall-through: do NOT short-circuit here —
                    // evaluatePolicy below will prompt for passcode.
                }
            }
        }

        return suspendCancellableCoroutine { cont ->
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = reason
            ) { success, error ->
                val result = when {
                    success -> UnlockResult.Success
                    error != null -> {
                        when (error.code.toLong()) {
                            LAErrorUserCancel.toLong(),
                            LAErrorAppCancel.toLong(),
                            LAErrorSystemCancel.toLong() -> UnlockResult.Cancelled
                            LAErrorAuthenticationFailed.toLong() -> UnlockResult.Failed
                            LAErrorPasscodeNotSet.toLong() -> UnlockResult.Success // D-17-16
                            else -> UnlockResult.Error(
                                error.localizedDescription ?: "Authentication error"
                            )
                        }
                    }
                    else -> UnlockResult.Failed
                }
                cont.resume(result)
            }
        }
    }
}

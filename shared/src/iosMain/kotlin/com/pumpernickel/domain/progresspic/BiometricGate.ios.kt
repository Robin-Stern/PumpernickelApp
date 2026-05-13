@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

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
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

/**
 * iOS-side actual for [BiometricGate].
 *
 * Two-step policy chain (D-17-15 + bug fix 260506-pf3):
 *  1. First try `LAPolicyDeviceOwnerAuthenticationWithBiometrics` — this is the
 *     ONLY iOS policy that reliably presents Face ID / Touch ID first instead
 *     of routing straight to passcode entry on Face-ID-enrolled devices.
 *  2. If that fails because biometrics is technically unavailable
 *     (`LAErrorBiometryNotAvailable` / `LAErrorBiometryNotEnrolled` /
 *     `LAErrorBiometryLockout`), fall back to `LAPolicyDeviceOwnerAuthentication`,
 *     which surfaces the passcode entry sheet. This preserves D-17-15's
 *     "auth required" guarantee on passcode-only devices and after a Face ID
 *     lockout.
 *
 * **User-cancel does NOT fall back to passcode.** If the user actively cancels
 * the Face ID sheet, that is a deliberate "not now" — we return
 * [UnlockResult.Cancelled] and let the caller leave the tile re-blurred. Falling
 * back would feel hostile and contradict iOS UX conventions.
 *
 * D-17-16 preserved: a device with no biometric AND no passcode resolves to
 * [UnlockResult.Success] without challenge — verified via the
 * `canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication)` pre-check returning
 * `LAErrorPasscodeNotSet`.
 *
 * Reason string is the German `reason` argument passed by the caller (currently
 * "Fortschrittsbild entsperren") and is shown in the system Face ID / passcode
 * prompt. The Face ID usage description string from the Info.plist
 * (`NSFaceIDUsageDescription = "Fortschrittsbild entsperren."`) is shown
 * separately by iOS the first time Face ID is requested.
 */
actual class BiometricGate {

    actual suspend fun requestUnlock(reason: String): UnlockResult {
        val context = LAContext()

        // D-17-16 short-circuit: device with no biometric AND no passcode
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
                    // No biometric AND no passcode — D-17-16 says unblur freely.
                    return UnlockResult.Success
                }
            }
        }

        // Step 1: try biometrics-only. This is what makes the prompt show Face ID
        // first instead of jumping straight to passcode entry.
        val biometricsOutcome = evaluate(
            context = LAContext(), // fresh context — a context that has been used
                                   // for one policy retains state about that attempt
            policy = LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            reason = reason
        )

        return when (biometricsOutcome) {
            is EvaluateOutcome.Success -> UnlockResult.Success
            is EvaluateOutcome.UserCancelled -> UnlockResult.Cancelled
            is EvaluateOutcome.AuthFailed -> UnlockResult.Failed
            is EvaluateOutcome.BiometryUnavailable -> {
                // Step 2: fall back to deviceOwnerAuthentication (passcode allowed).
                // Only entered when biometrics is technically unavailable — NOT on
                // user-cancel. Use a fresh LAContext so the passcode sheet is not
                // influenced by the prior biometric attempt's state.
                evaluate(
                    context = LAContext(),
                    policy = LAPolicyDeviceOwnerAuthentication,
                    reason = reason
                ).toUnlockResult()
            }
            is EvaluateOutcome.Other -> UnlockResult.Error(biometricsOutcome.message)
        }
    }

    private suspend fun evaluate(
        context: LAContext,
        policy: Long,
        reason: String
    ): EvaluateOutcome = suspendCancellableCoroutine { cont ->
        context.evaluatePolicy(
            policy = policy,
            localizedReason = reason
        ) { success, error ->
            val outcome: EvaluateOutcome = when {
                success -> EvaluateOutcome.Success
                error != null -> when (error.code.toLong()) {
                    LAErrorUserCancel.toLong(),
                    LAErrorAppCancel.toLong(),
                    LAErrorSystemCancel.toLong() -> EvaluateOutcome.UserCancelled
                    LAErrorAuthenticationFailed.toLong() -> EvaluateOutcome.AuthFailed
                    LAErrorPasscodeNotSet.toLong() -> EvaluateOutcome.Success // D-17-16
                    LAErrorBiometryNotAvailable.toLong(),
                    LAErrorBiometryNotEnrolled.toLong(),
                    LAErrorBiometryLockout.toLong() -> EvaluateOutcome.BiometryUnavailable
                    else -> EvaluateOutcome.Other(
                        error.localizedDescription ?: "Authentication error"
                    )
                }
                else -> EvaluateOutcome.AuthFailed
            }
            cont.resume(outcome)
        }
    }

    private fun EvaluateOutcome.toUnlockResult(): UnlockResult = when (this) {
        is EvaluateOutcome.Success -> UnlockResult.Success
        is EvaluateOutcome.UserCancelled -> UnlockResult.Cancelled
        is EvaluateOutcome.AuthFailed -> UnlockResult.Failed
        is EvaluateOutcome.BiometryUnavailable -> UnlockResult.Failed // shouldn't
        // happen on the fallback policy (it allows passcode), but if the device
        // somehow rejects passcode policy too, treat as Failed not Error.
        is EvaluateOutcome.Other -> UnlockResult.Error(message)
    }

    private sealed class EvaluateOutcome {
        object Success : EvaluateOutcome()
        object UserCancelled : EvaluateOutcome()
        object AuthFailed : EvaluateOutcome()
        object BiometryUnavailable : EvaluateOutcome()
        data class Other(val message: String) : EvaluateOutcome()
    }
}

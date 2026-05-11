package com.pumpernickel.domain.progresspic

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.pumpernickel.feature.biometric.BiometricGateActivityHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android-side actual for [BiometricGate]. Uses androidx.biometric.BiometricPrompt
 * with BIOMETRIC_STRONG OR DEVICE_CREDENTIAL (D-17-15) so the OS handles
 * fingerprint / face / PIN / pattern / password automatically.
 *
 * On a device with no enrolled biometric AND no device credential, requestUnlock
 * resolves to [UnlockResult.Success] without challenge (D-17-16).
 *
 * Failure (onAuthenticationFailed) is silent — the OS prompt stays open for
 * retry; only ERROR / SUCCESS callbacks complete the deferred (D-17-17).
 */
actual class BiometricGate(private val context: Context) {

    // REVIEW M-07: serialise concurrent requestUnlock calls. Two parallel
    // BiometricPrompt.authenticate() calls against the same FragmentActivity
    // collide in the prompt FragmentManager (IllegalStateException — fragment
    // already added). The mutex enforces "one prompt at a time" per gate
    // instance.
    private val authMutex = Mutex()

    actual suspend fun requestUnlock(reason: String): UnlockResult =
        authMutex.withLock {
            withContext(Dispatchers.Main) {
                val activity = BiometricGateActivityHolder.current
                    ?: return@withContext UnlockResult.Error(
                        "No FragmentActivity attached to BiometricGate"
                    )
                // M-07: the holder may still point at an Activity that has
                // been destroyed (e.g. capture finished after navigation).
                // Authenticating against a destroyed FragmentActivity throws.
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return@withContext UnlockResult.Error(
                        "Host activity is not in a started state"
                    )
                }

                val biometricManager = BiometricManager.from(context)
                val authenticators =
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                val canAuth = biometricManager.canAuthenticate(authenticators)
                // D-17-16: a device with no enrolled biometric AND no device
                // credential resolves to Success without prompting. We treat all
                // "cannot prompt" outcomes as Success rather than blocking the user.
                if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                    canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                    canAuth == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
                ) {
                    return@withContext UnlockResult.Success
                }

                val deferred = CompletableDeferred<UnlockResult>()
                val executor = ContextCompat.getMainExecutor(context)
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        deferred.complete(UnlockResult.Success)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        val r = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> UnlockResult.Cancelled
                            else -> UnlockResult.Error(errString.toString())
                        }
                        deferred.complete(r)
                    }

                    override fun onAuthenticationFailed() {
                        // D-17-17: the OS prompt stays open for retry; we do not
                        // close it on a single mismatch. Only ERROR / SUCCESS
                        // callbacks complete the deferred.
                    }
                }

                // M-07: BiometricPrompt.authenticate() can throw if the
                // FragmentActivity is in an inconsistent state (e.g. concurrent
                // attach/detach race during recreation). Catch and surface as
                // Error rather than crashing.
                try {
                    val prompt = BiometricPrompt(activity, executor, callback)
                    // Note: the negative-button-text builder option is intentionally
                    // NOT called. When DEVICE_CREDENTIAL is part of the allowed
                    // authenticators, that option throws IllegalArgumentException at
                    // build() time. Set ONLY title + allowedAuthenticators.
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(reason)
                        .setAllowedAuthenticators(authenticators)
                        .build()
                    prompt.authenticate(info)
                } catch (t: Throwable) {
                    return@withContext UnlockResult.Error(
                        t.message ?: "BiometricPrompt failed to start"
                    )
                }

                deferred.await()
            }
        }
}

package com.pumpernickel.domain.progresspic

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holder set by the host activity (MainActivity) at onCreate / onResume.
 * The Koin actual reads from this holder to call BiometricPrompt.
 *
 * Plan 17-08 binds [PhotoVault], [PhotoCaptureLauncher], [BiometricGate] in
 * PlatformModule.android.kt. Plan 17-05's MainActivity onCreate calls
 * [BiometricGateActivityHolder.attach(this)] after swapping the MainActivity
 * superclass to androidx.fragment.app.FragmentActivity (BiometricPrompt
 * requires FragmentActivity — ComponentActivity is NOT one).
 */
object BiometricGateActivityHolder {
    @Volatile
    var current: FragmentActivity? = null
        private set

    fun attach(activity: FragmentActivity) { current = activity }
    fun detach(activity: FragmentActivity) {
        if (current === activity) current = null
    }
}

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

    actual suspend fun requestUnlock(reason: String): UnlockResult =
        withContext(Dispatchers.Main) {
            val activity = BiometricGateActivityHolder.current
                ?: return@withContext UnlockResult.Error(
                    "No FragmentActivity attached to BiometricGate"
                )

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

            deferred.await()
        }
}

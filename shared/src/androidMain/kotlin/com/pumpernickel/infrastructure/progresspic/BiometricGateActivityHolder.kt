package com.pumpernickel.infrastructure.progresspic

import androidx.fragment.app.FragmentActivity

/**
 * Holder set by the host activity (MainActivity) at onCreate / onResume.
 * The Koin actual reads from this holder to call BiometricPrompt.
 *
 * Plan 17-08 binds [BiometricGate] in PlatformModule.android.kt.
 * Plan 17-05's MainActivity onCreate calls [BiometricGateActivityHolder.attach(this)]
 * after swapping the MainActivity superclass to androidx.fragment.app.FragmentActivity
 * (BiometricPrompt requires FragmentActivity — ComponentActivity is NOT one).
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

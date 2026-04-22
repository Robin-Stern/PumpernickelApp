package com.pumpernickel.di

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Swift-callable trigger. Safe to call every app launch — the underlying
 * GamificationStartup.run() is idempotent via two sentinel checks.
 *
 * NOTE: uses GlobalScope because iOS has no Application-scope coroutine.
 * The work is one-shot on first launch (cheap thereafter), so no leak risk.
 *
 * Swift access: GamificationStartupIos.shared.trigger()
 * (Kotlin object → Swift .shared accessor via the KMP Obj-C bridge.)
 */
@OptIn(DelicateCoroutinesApi::class)
object GamificationStartupIos {
    fun trigger() {
        val startup = KoinPlatform.getKoin().get<GamificationStartup>()
        GlobalScope.launch(Dispatchers.Default) {
            startup.run()
        }
    }
}

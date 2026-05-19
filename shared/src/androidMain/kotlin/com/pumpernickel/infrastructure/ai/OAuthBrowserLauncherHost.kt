package com.pumpernickel.infrastructure.ai

import android.content.Intent
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred

/**
 * D-22-01 — Activity-level singleton holder mirroring the
 * `PhotoCaptureLauncherActivityHolder` pattern (Phase 17). MainActivity (Plan
 * 22-07) routes its `onNewIntent` callback through [handleRedirect]: the
 * OAuth redirect arrives as an Intent with `data` of the form
 * `pumpernickel-oauth://callback?code=...&state=...`.
 *
 * Lifecycle:
 *   1. [OAuthBrowserLauncher] creates a CompletableDeferred and registers it
 *      via [awaitRedirect] BEFORE launching CustomTabsIntent.launchUrl.
 *   2. The browser redirects to `pumpernickel-oauth://callback?code=...`.
 *      Android delivers an Intent with this URL to MainActivity via
 *      `onNewIntent`.
 *   3. `MainActivity.onNewIntent` calls [handleRedirect], which completes the
 *      deferred with the extracted code (or null if no code present).
 *   4. The suspending launcher returns the code to the [AnthropicOAuthFlow]
 *      caller.
 *
 * A second concurrent OAuth attempt cancels the previous one (M-01 pattern
 * from PhotoCaptureLauncherHost).
 */
object OAuthBrowserLauncherHost {
    private val lock = Any()

    @Volatile
    private var pending: CompletableDeferred<OAuthRedirect?>? = null

    /**
     * Called from `OAuthBrowserLauncher.android.kt` before launching
     * `CustomTabsIntent`. Suspends until [handleRedirect] receives the
     * matching intent.
     *
     * WR-03 — atomic swap of the `pending` deferred + completion of any
     * stale predecessor under a single monitor to close the previous-
     * deferred race.
     */
    suspend fun awaitRedirect(): OAuthRedirect? {
        val deferred = CompletableDeferred<OAuthRedirect?>()
        val previous = synchronized(lock) {
            val p = pending
            pending = deferred
            p
        }
        // Cancel any in-flight wait so no awaiter is stranded.
        previous?.complete(null)
        return deferred.await()
    }

    /**
     * Called from `MainActivity.onNewIntent`. Extracts `code` (and `state`)
     * from the redirect URI and completes the pending deferred. No-op if no
     * deferred is pending (defensive — e.g. user opened the redirect URL
     * manually).
     */
    fun handleRedirect(intent: Intent) {
        val data = intent.data
        val code = data?.getQueryParameter("code")
        val state = data?.getQueryParameter("state")
        println("[OAuthBrowserLauncherHost.android] redirect code-present=${code != null} state-present=${state != null}")
        val deferred = synchronized(lock) {
            val p = pending
            pending = null
            p
        } ?: return
        deferred.complete(code?.let { OAuthRedirect(code = it, state = state) })
    }
}

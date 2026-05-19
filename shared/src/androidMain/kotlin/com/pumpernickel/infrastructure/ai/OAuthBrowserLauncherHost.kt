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
    @Volatile
    private var pending: CompletableDeferred<String?>? = null

    /**
     * Called from `OAuthBrowserLauncher.android.kt` before launching
     * `CustomTabsIntent`. Suspends until [handleRedirect] receives the
     * matching intent.
     */
    suspend fun awaitRedirect(): String? {
        // Cancel any in-flight wait so no awaiter is stranded.
        pending?.complete(null)
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        return deferred.await()
    }

    /**
     * Called from `MainActivity.onNewIntent`. Extracts `code` from the
     * redirect URI and completes the pending deferred. No-op if no deferred
     * is pending (defensive — e.g. user opened the redirect URL manually).
     */
    fun handleRedirect(intent: Intent) {
        val code = intent.data?.getQueryParameter("code")
        println("[OAuthBrowserLauncherHost.android] redirect code-present=${code != null}")
        val deferred = pending ?: return
        pending = null
        deferred.complete(code)
    }
}

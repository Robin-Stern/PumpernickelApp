@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pumpernickel.infrastructure.ai

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * D-22-01 — iOS actual using ASWebAuthenticationSession (system-provided
 * in-app browser sheet with cookie isolation; iOS intercepts the redirect
 * URL whose scheme matches `redirectScheme` and forwards the full URL to our
 * completion handler).
 *
 * Pattern symmetric to `PhotoCaptureLauncher.ios.kt` (M-05 retention pattern):
 *  - `CompletableDeferred<String?>` for the suspend bridge
 *  - `@Volatile` field retains the ASWebAuthenticationSession and the
 *    presentation-context-provider until the deferred completes (K/N
 *    continuation lowering may discard locals; field-level retention is
 *    the K/N-idiomatic fix)
 *  - Returns the `code` query parameter from the redirect URL, or null on
 *    cancel/error
 */
actual class OAuthBrowserLauncher {

    @Volatile
    private var currentSession: ASWebAuthenticationSession? = null

    @Volatile
    private var currentContextProvider: PresentationContextProvider? = null

    actual suspend fun startAuthFlow(authorizeUrl: String, redirectScheme: String): OAuthRedirect? {
        val url = NSURL.URLWithString(authorizeUrl) ?: return null
        val deferred = CompletableDeferred<OAuthRedirect?>()

        val contextProvider = PresentationContextProvider()
        currentContextProvider = contextProvider

        val session = ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = redirectScheme,
            completionHandler = { callbackURL: NSURL?, error: NSError? ->
                if (error != null || callbackURL == null) {
                    println("[OAuthBrowserLauncher.ios] cancel/error: $error")
                    deferred.complete(null)
                    return@ASWebAuthenticationSession
                }
                val redirect = extractRedirectParams(callbackURL)
                println("[OAuthBrowserLauncher.ios] redirect received code-present=${redirect?.code != null} state-present=${redirect?.state != null}")
                deferred.complete(redirect)
            }
        )
        session.presentationContextProvider = contextProvider
        // Don't share Safari cookies — every auth attempt is a fresh session.
        session.prefersEphemeralWebBrowserSession = true
        currentSession = session
        try {
            session.start()
            return deferred.await()
        } finally {
            // Only clear if it's still us — guards against a concurrent
            // startAuthFlow() that overwrote the field after we yielded.
            if (currentSession === session) currentSession = null
            if (currentContextProvider === contextProvider) currentContextProvider = null
        }
    }

    /**
     * Extracts `code` and `state` query parameters from a redirect URL.
     * Returns null when `code` is absent (state alone is meaningless for the
     * token exchange).
     */
    private fun extractRedirectParams(url: NSURL): OAuthRedirect? {
        val comps = NSURLComponents(uRL = url, resolvingAgainstBaseURL = false) ?: return null
        @Suppress("UNCHECKED_CAST")
        val items = comps.queryItems as? List<NSURLQueryItem> ?: return null
        val code = items.firstOrNull { it.name == "code" }?.value ?: return null
        val state = items.firstOrNull { it.name == "state" }?.value
        return OAuthRedirect(code = code, state = state)
    }
}

/** Returns the active key window so ASWebAuthenticationSession can attach its sheet. */
private class PresentationContextProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        // Try key window from the application's window list; fall back to first window.
        val app = UIApplication.sharedApplication
        @Suppress("UNCHECKED_CAST")
        val windows = app.windows as? List<UIWindow> ?: emptyList()
        val key = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
        // last-resort empty window — sheet still presents but unanchored
        return key ?: UIWindow()
    }
}

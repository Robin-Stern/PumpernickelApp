package com.pumpernickel.infrastructure.ai

/**
 * D-22-01 — platform-specific OAuth browser bridge for the PKCE auth flow
 * against `claude.ai/oauth/authorize`. Symmetric to
 * `com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncher`:
 * common surface is a suspend function returning nullable on user-cancel.
 *
 * Implementations:
 *  - iOS: `ASWebAuthenticationSession` (in-app browser sheet with system
 *    cookie isolation; iOS handles redirect-URI matching natively).
 *  - Android: `CustomTabsIntent.launchUrl(...)` + `<intent-filter>` on
 *    MainActivity that catches the redirect URI; MainActivity.onNewIntent
 *    forwards the intent to [OAuthBrowserLauncherHost] which completes the
 *    pending deferred.
 *
 * Returns the raw `code` query parameter extracted from the redirect URL,
 * or `null` on user-cancel / error. The PKCE `code_verifier` is tracked by
 * the caller ([AnthropicOAuthFlow]); this launcher only deals with the
 * browser round-trip.
 */
/**
 * D-22-01 — redirect query parameters extracted from the OAuth callback URL.
 *
 * [state] is nullable for backward compatibility with the (legacy) Android
 * `OAuthBrowserLauncherHost.handleRedirect` path that historically only
 * carried `code`; production paths set both, and [AnthropicOAuthFlow.authorize]
 * REJECTS a redirect with a missing or mismatched state to prevent CSRF
 * (RFC 6749 §10.12).
 */
data class OAuthRedirect(val code: String, val state: String?)

expect class OAuthBrowserLauncher {
    /**
     * @param authorizeUrl complete authorize URL including code_challenge,
     *                     state, redirect_uri, client_id, scope.
     * @param redirectScheme the scheme part of the redirect URI (e.g.
     *                       "pumpernickel-oauth"). Must match the URL types
     *                       registered in Info.plist (iOS) or the
     *                       <intent-filter> in AndroidManifest (Android).
     * @return [OAuthRedirect] containing `code` and (when present) `state`
     *         query parameters from the redirect URL, or `null` on
     *         cancel/error. The caller MUST validate `state` against the
     *         value it generated before exchanging the code.
     */
    suspend fun startAuthFlow(authorizeUrl: String, redirectScheme: String): OAuthRedirect?
}

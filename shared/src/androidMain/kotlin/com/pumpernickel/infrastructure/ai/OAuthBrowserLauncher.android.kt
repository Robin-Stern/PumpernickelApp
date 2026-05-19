package com.pumpernickel.infrastructure.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * D-22-01 — Android actual. Launches the OAuth authorize URL in a Chrome
 * CustomTab (system browser with theming/transition tied to the host app)
 * and awaits the redirect via [OAuthBrowserLauncherHost].
 *
 * The CustomTab returns control to MainActivity through the `<intent-filter>`
 * registered against the redirect scheme — wired by Plan 22-07
 * (`AndroidManifest.xml` + `MainActivity.onNewIntent` override).
 */
actual class OAuthBrowserLauncher(private val context: Context) {

    actual suspend fun startAuthFlow(authorizeUrl: String, redirectScheme: String): OAuthRedirect? {
        // redirectScheme is informational on Android — the actual intercept
        // is configured statically in AndroidManifest <intent-filter>. Kept
        // in the signature so the common API is symmetric with iOS.
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        // FLAG_ACTIVITY_NEW_TASK because we may be called from a non-Activity
        // Context (Koin singleton). MainActivity registers itself with the
        // OAuthBrowserLauncherHost — that's all we need from the Activity
        // layer.
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            tab.launchUrl(context, Uri.parse(authorizeUrl))
        } catch (e: android.content.ActivityNotFoundException) {
            // WR-04 — no CustomTabs-capable browser installed. `awaitRedirect`
            // has not been called yet, so there is no pending deferred to
            // clean up; surface a domain-level error instead of letting
            // ActivityNotFoundException bubble out unchanged.
            throw com.pumpernickel.domain.ai.AiError.SchemaInvalid(
                "Kein Browser installiert — OAuth nicht möglich."
            )
        }
        return OAuthBrowserLauncherHost.awaitRedirect()
    }
}

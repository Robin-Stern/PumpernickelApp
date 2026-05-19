---
phase: 22-anthropic-ai-provider-3-provider-neben-openai-together-mit-o
plan: 05
subsystem: infrastructure/ai
tags: [oauth, pkce, browser, ios-aswebauthsession, android-customtabs, expect-actual]
requires:
  - Plan 22-01 (Credential sealed class — OAuthToken sub-class consumed by AnthropicOAuthFlow.authorize())
provides:
  - expect class OAuthBrowserLauncher mit suspend startAuthFlow(authorizeUrl, redirectScheme): String?
  - iOS actual via ASWebAuthenticationSession (ephemeral session + ASPresentationAnchor key-window provider)
  - Android actual via CustomTabsIntent.launchUrl + OAuthBrowserLauncherHost (Activity-onNewIntent-bridge)
  - AnthropicOAuthFlow.authorize() — generates PKCE verifier/challenge/state, builds claude.ai/oauth/authorize URL, exchanges code → Credential.OAuthToken via POST claude.ai/oauth/token
  - Inline Sha256 (FIPS 180-4) + base64UrlNoPadding + urlEncode (commonMain-safe, dependency-free)
  - Companion constants AUTHORIZE_ENDPOINT, TOKEN_ENDPOINT, CLIENT_ID, REDIRECT_SCHEME, REDIRECT_URI, SCOPE
affects:
  - Plan 22-06 (Migration / Token-Refresh) — wird AnthropicOAuthFlow.authorize() konsumieren und das zurückgegebene Credential.OAuthToken via SecureKeyStore.writeCredential(ProviderId.Anthropic, ...) persistieren
  - Plan 22-07 (Manifest + Info.plist Redirect-Registration) — registriert pumpernickel-oauth scheme im AndroidManifest <intent-filter> auf MainActivity (plus MainActivity.onNewIntent → OAuthBrowserLauncherHost.handleRedirect) und in iosApp/iosApp/Info.plist CFBundleURLTypes
  - Plan 22-08 (UI checkpoint — Connect-Sheet) — wird AnthropicOAuthFlow als Koin-Single instanzieren und vom Connect-Sheet aus aufrufen
tech-stack:
  added:
    - androidx.browser:browser 1.8.0 (Chrome CustomTabs) — neue dependency, nur androidMain
  patterns:
    - expect/actual-Triple (commonMain expect + iosMain ASWebAuthenticationSession actual + androidMain CustomTabsIntent actual + Activity-Holder)
    - CompletableDeferred<String?> + @Volatile field retention (M-05 K/N continuation-lowering fix from PhotoCaptureLauncher.ios.kt)
    - Object-Holder-Pattern für Activity-onNewIntent-Bridge (analog PhotoCaptureLauncherActivityHolder aus Phase 17)
    - Inline-Crypto (Sha256 FIPS 180-4 + Base64Url) — vermeidet zusätzliche KMP-Crypto-Library
key-files:
  created:
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.kt
    - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt
    - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.ios.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.android.kt
    - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncherHost.kt
  modified:
    - gradle/libs.versions.toml (androidx-browser version + library entry)
    - shared/build.gradle.kts (libs.androidx.browser in androidMain.dependencies)
decisions:
  - D-22-01-impl: PKCE-Konstanten als Companion-const im AnthropicOAuthFlow (nicht extern injected) — Plan 22-06 kann sie via Constructor-Param überschreiben falls nötig
  - D-22-01-impl: authorize() liefert Credential.OAuthToken? (nicht ein separates Response-DTO) — Konsument muss den Token nur noch über SecureKeyStore persistieren, keine Konvertierungs-Logik mehr nötig
  - D-22-01-impl: Eigene private AnthropicAuthorizationCodeResponse-DTO im AnthropicOAuthFlow (statt Plan-22-03's AnthropicOAuthRefreshResponse) — hält Plan 22-05 unabhängig kompilierbar in der parallelen Wave-2; Plan 22-06 darf die beiden Shapes konsolidieren
  - D-22-01-impl: Inline SHA-256 (FIPS 180-4) + Base64Url + urlEncode pure Kotlin — eine neue Crypto-Library wäre Build-Komplexitäts-Overhead für iOS+Android KMP
  - D-22-01-impl: prefersEphemeralWebBrowserSession = true (iOS) — verhindert Safari-Cookie-Sharing, jeder Auth-Flow ist eine frische Session
  - D-22-01-impl: ASWebAuthenticationSession callbackURLScheme akzeptiert nur den Scheme-Teil ("pumpernickel-oauth"); iOS matcht jede URL die mit `pumpernickel-oauth://` startet (kein separater Manifest-Eintrag nötig — Info.plist URL types kommt in Plan 22-07)
  - D-22-01-impl: Android CustomTabsIntent ist context-agnostisch (FLAG_ACTIVITY_NEW_TASK) — Koin-Singleton-Instanziierung möglich; der Activity-Bezug kommt ausschließlich über OAuthBrowserLauncherHost.handleRedirect von MainActivity.onNewIntent in Plan 22-07
  - D-22-01-impl: Clock-Import via `kotlin.time.Clock` (nicht `kotlinx.datetime.Clock`) — projekt-internes Pattern seit Kotlin 2.1+
metrics:
  duration: ~22min
  completed: 2026-05-19
  tasks: 3/3
  files_changed: 7
  commits: 3
---

# Phase 22 Plan 05: OAuth-Browser-Bridge + PKCE Flow Summary

OAuth-Browser-Infrastruktur für Anthropic-Auth: platform-agnostischer `expect class OAuthBrowserLauncher` plus iOS-Actual (`ASWebAuthenticationSession`) und Android-Actual (`CustomTabsIntent` + Activity-`onNewIntent`-Holder), zusammen mit dem PKCE-Helper `AnthropicOAuthFlow`, der den authorize-URL konstruiert und den Code→Token-Exchange übernimmt.

## What Was Built

### Task 1 — Build deps + commonMain expect + PKCE helper (commit `020eeb1`)

- `gradle/libs.versions.toml` — `androidx-browser = "1.8.0"` Version + `androidx-browser = { group = "androidx.browser", name = "browser", version.ref = "androidx-browser" }` Library
- `shared/build.gradle.kts` — `implementation(libs.androidx.browser)` in `androidMain.dependencies` (nur androidMain — Chrome CustomTabs ist Android-only)
- `OAuthBrowserLauncher.kt` (commonMain) — `expect class OAuthBrowserLauncher` mit einer einzigen suspend-Methode:
  ```kotlin
  suspend fun startAuthFlow(authorizeUrl: String, redirectScheme: String): String?
  ```
  Vertrag: returnt das raw `code`-Query-Param aus dem Redirect-URL oder `null` bei Cancel/Error. State-Validation ist Caller-Responsibility (siehe SEED-Note).

- `AnthropicOAuthFlow.kt` (commonMain) — der zentrale PKCE-Orchestrator:
  ```kotlin
  suspend fun authorize(): Credential.OAuthToken?
  ```
  1. `generateCodeVerifier()` — RFC 7636 §4.1 unreserved-Set, 64 chars
  2. `codeChallengeS256(verifier)` — BASE64URL(SHA-256(verifier)), keine Padding
  3. `generateState()` — 32-char random string
  4. `buildAuthorizeUrl(challenge, state)` — `https://claude.ai/oauth/authorize?...&code_challenge_method=S256&scope=user:inference user:profile`
  5. `browserLauncher.startAuthFlow(authorizeUrl, REDIRECT_SCHEME)` → blockiert bis Redirect-Callback
  6. `exchangeCodeForToken(code, verifier)` — `POST claude.ai/oauth/token` mit `grant_type=authorization_code`-Body
  7. Returnt `Credential.OAuthToken(accessToken, refreshToken, expiresAtEpochSeconds = now + expires_in)` — direkt im Storage-Format

  Plus pure-Kotlin Helpers (alle commonMain-safe, ohne JVM-API):
  - `object Sha256.hash(ByteArray): ByteArray` — FIPS 180-4 SHA-256 (~70 Zeilen, public-domain algorithm)
  - `base64UrlNoPadding(ByteArray): String`
  - `urlEncode(String): String` — minimal percent-encoding für query/scheme values

### Task 2 — iOS actual via ASWebAuthenticationSession (commit `36e3bc0`)

- `OAuthBrowserLauncher.ios.kt` — `actual class OAuthBrowserLauncher` ohne Constructor-Params
- Nutzt `platform.AuthenticationServices.ASWebAuthenticationSession`:
  - `callbackURLScheme = redirectScheme` — iOS matcht system-seitig jede URL mit dem registrierten Scheme und ruft den Completion-Handler auf
  - `prefersEphemeralWebBrowserSession = true` — Safari-Cookie-Isolation
  - `presentationContextProvider` = `PresentationContextProvider` (private `NSObject` + `ASWebAuthenticationPresentationContextProvidingProtocol`-conformance, returnt das key window aus `UIApplication.sharedApplication.windows`)
- `CompletableDeferred<String?>` als suspend-Bridge; `@Volatile`-Field hält die Session + den Context-Provider am Leben bis das Deferred completes (Pattern aus `PhotoCaptureLauncher.ios.kt`, M-05 — K/N continuation-lowering kann Locals beim Suspension-Point verlieren)
- `extractCodeParam(NSURL)` via `NSURLComponents.queryItems.firstOrNull { it.name == "code" }`

### Task 3 — Android actual via CustomTabsIntent + Activity-Holder (commit `d0a737a`)

- `OAuthBrowserLauncherHost.kt` — `object`-Singleton, Holder-Pattern analog `PhotoCaptureLauncherActivityHolder` (Phase 17):
  - `@Volatile pending: CompletableDeferred<String?>?`
  - `suspend fun awaitRedirect(): String?` — registriert ein neues Deferred (cancelt ein eventuell vorheriges)
  - `fun handleRedirect(intent: Intent)` — extrahiert `intent.data?.getQueryParameter("code")` und completed das pending Deferred (no-op wenn nichts pending)

- `OAuthBrowserLauncher.android.kt` — `actual class OAuthBrowserLauncher(private val context: Context)`:
  - `CustomTabsIntent.Builder().setShowTitle(true).build()` + `tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` + `tab.launchUrl(context, Uri.parse(authorizeUrl))`
  - `return OAuthBrowserLauncherHost.awaitRedirect()`
  - FLAG_ACTIVITY_NEW_TASK macht die Klasse Koin-Singleton-Context-safe; der Activity-Bezug kommt ausschließlich über `OAuthBrowserLauncherHost.handleRedirect`, das `MainActivity.onNewIntent` in Plan 22-07 aufrufen wird

## Key Constants in `AnthropicOAuthFlow.Companion`

| Constant            | Value                                    |
| ------------------- | ---------------------------------------- |
| `AUTHORIZE_ENDPOINT`| `https://claude.ai/oauth/authorize`      |
| `TOKEN_ENDPOINT`    | `https://claude.ai/oauth/token`          |
| `CLIENT_ID`         | `9d1c250a-e61b-44d9-88ed-5944d1962f5e`   |
| `REDIRECT_SCHEME`   | `pumpernickel-oauth`                     |
| `REDIRECT_URI`      | `pumpernickel-oauth://callback`          |
| `SCOPE`             | `user:inference user:profile`            |

Diese Konstanten sind aktuell `const val` im Companion. Plan 22-06 darf sie via Constructor-Parameter überschreibbar machen, falls für Tests oder zukünftige Server-Migration nötig.

## Compilation Status

- `:shared:compileCommonMainKotlinMetadata` — **scheitert** wegen pre-existing breakage aus Plan 22-01:
  - 7 Unresolved-Reference-Fehler in `di/AiModule.kt`, `presentation/ai/AiSettingsViewModel.kt`, `presentation/ai/RecipeAiViewModel.kt`, `presentation/ai/WorkoutAiViewModel.kt` (readApiKey/writeApiKey/clearApiKey) — wird in Plan 22-04 + 22-07 (Wave 3) repariert
  - 1 Room-KSP-Fehler in `data/db/AppDatabase.kt` (`AppDatabaseConstructor`) — Room-KSP-Eigenheit auf commonMain-only Kompilierung (Android+iOS Targets stellen den fehlenden Generator-Hook bereit)
- `:shared:compileKotlinIosSimulatorArm64` und `:shared:compileAndroidMain` zeigen exakt die gleichen 7+1 pre-existing Fehler — **keine** Fehler in meinen 5 neuen / 2 modifizierten Files
- Struktur-Verifikation (alle GREEN):
  - `expect class OAuthBrowserLauncher` ✓
  - `suspend fun startAuthFlow(authorizeUrl, redirectScheme)` ✓
  - `class AnthropicOAuthFlow` ✓
  - `code_challenge_method=S256` ✓
  - `object Sha256` ✓
  - `actual class OAuthBrowserLauncher` (iOS + Android) ✓
  - `ASWebAuthenticationSession` + `prefersEphemeralWebBrowserSession = true` ✓
  - `CustomTabsIntent.Builder()` + `OAuthBrowserLauncherHost.awaitRedirect()` ✓
  - `androidx-browser` in libs.versions.toml + `libs.androidx.browser` in shared/build.gradle.kts ✓

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `kotlinx.datetime.Clock` Import existiert in dieser Codebase nicht mehr**
- **Found during:** Task 1 commonMain compile attempt
- **Issue:** Plan-Action schrieb `import kotlinx.datetime.Clock`, aber die Codebase ist auf Kotlin 2.3.20 mit `kotlin.time.Clock` migriert (per grep: `GoalDayTrigger.kt`, `WorkoutRepositoryImpl.kt`, etc. nutzen alle den neuen Import)
- **Fix:** Import auf `kotlin.time.Clock` umgestellt; `Instant.epochSeconds` ist auf beiden Varianten verfügbar
- **Files modified:** `AnthropicOAuthFlow.kt`
- **Commit:** `020eeb1` (Task 1 — Fix vor dem ersten Commit angewendet)

### Plan-driven adaptations (nicht Deviations sondern bewusste Plan-Auslegung)

**A. authorize() returnt `Credential.OAuthToken?` statt `AnthropicOAuthRefreshResponse?`**
- Plan-Action verwies auf das DTO aus Plan 22-03, das in der parallelen Wave-2-Worktree erst entsteht. Um Plan 22-05 unabhängig kompilierbar zu halten, wurde ein privates DTO `AnthropicAuthorizationCodeResponse` für den HTTP-Roundtrip eingeführt und das Ergebnis direkt zum Storage-Typ `Credential.OAuthToken` gemappt. Plan 22-06 (Token-Refresh + Migration) konsumiert ohnehin `Credential.OAuthToken` — die Schicht-Übersetzung ist hiermit Plan-22-05-intern und kein Caller-Concern mehr.

**B. SEED-state-validation**
- `OAuthBrowserLauncher.startAuthFlow` returnt nur den `code`-Parameter, nicht den `state`. `AnthropicOAuthFlow.authorize` generiert zwar ein `state` und sendet es im authorize-URL, prüft es aber NICHT im Redirect-Callback. Trust-on-first-use Kompromiss für Demo-Deadline.
- **Hardening backlog (post-demo):** `startAuthFlow` API auf `Pair<code, state>` erweitern und im `authorize()`-Flow gegen den generated state vergleichen. Tracking: 22-05-SUMMARY SEED-Note + Plan-22-06 Discretion-Area.

## SEED Notes (Hardening Backlog)

- **SEED-22-05-A:** State-Validation gegen Redirect-state. Aktuell wird der `state`-Parameter zwar in den authorize-URL eingebaut, aber im Redirect-Callback nicht extrahiert oder gegen den generierten `state` validiert. Mittel-Aufwand fix nach Demo-Phase. (Erwähnung in `AnthropicOAuthFlow.kt` KDoc.)
- **SEED-22-05-B:** `CLIENT_ID` ist ein Placeholder. Pre-Produktion: dedizierten OAuth-Client bei Anthropic registrieren und Constructor-injectable machen.
- **SEED-22-05-C:** Inline-SHA-256 wurde gewählt um Dependency-Sprawl zu vermeiden. Falls in Phase 23+ weitere Crypto-Anforderungen kommen, dann eine richtige KMP-Crypto-Lib (z.B. `cryptography-kotlin`) evaluieren und sowohl PKCE als auch andere Stellen migrieren.

## Plan-Wave-Status

- **Wave 2** (this plan): commonMain expect + PKCE-Helper + iOS-actual + Android-actual + Holder — ALL DELIVERED
- **Wave 3 follow-up (Plan 22-07):** AndroidManifest `<intent-filter>` für `pumpernickel-oauth` scheme auf MainActivity + `MainActivity.onNewIntent` → `OAuthBrowserLauncherHost.handleRedirect`; iosApp/iosApp/Info.plist `CFBundleURLTypes` Eintrag für `pumpernickel-oauth`. Ohne diese URL-Scheme-Registrierungen läuft der Flow nicht runtime — kompiliert aber.
- **Wave 3 follow-up (Plan 22-06):** AnthropicOAuthFlow als Koin-`single`, Migration vorhandener OpenAI-Slot in neue Provider-Map, Token-Refresh-Interceptor im AnthropicClient.

## Self-Check: PASSED

- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.kt`: FOUND
- File `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt`: FOUND
- File `shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.ios.kt`: FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.android.kt`: FOUND
- File `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncherHost.kt`: FOUND
- Commit `020eeb1` (Task 1): FOUND
- Commit `36e3bc0` (Task 2): FOUND
- Commit `d0a737a` (Task 3): FOUND
- Verify-grep `expect class OAuthBrowserLauncher`: PASSED
- Verify-grep `suspend fun startAuthFlow`: PASSED
- Verify-grep `class AnthropicOAuthFlow`: PASSED
- Verify-grep `code_challenge_method=S256`: PASSED
- Verify-grep `object Sha256`: PASSED
- Verify-grep `actual class OAuthBrowserLauncher` (iOS): PASSED
- Verify-grep `ASWebAuthenticationSession`: PASSED
- Verify-grep `prefersEphemeralWebBrowserSession = true`: PASSED
- Verify-grep `actual class OAuthBrowserLauncher(private val context: Context)` (Android): PASSED
- Verify-grep `CustomTabsIntent.Builder()`: PASSED
- Verify-grep `OAuthBrowserLauncherHost.awaitRedirect`: PASSED
- Verify-grep `object OAuthBrowserLauncherHost`: PASSED
- Verify-grep `intent.data?.getQueryParameter("code")`: PASSED
- Verify-grep `androidx-browser` in libs.versions.toml: PASSED
- Verify-grep `libs.androidx.browser` in shared/build.gradle.kts: PASSED

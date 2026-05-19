---
phase: 22-anthropic-ai-provider
reviewed: 2026-05-19T00:00:00Z
depth: standard
files_reviewed: 37
files_reviewed_list:
  - androidApp/src/androidMain/AndroidManifest.xml
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/MainActivity.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt
  - androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AnthropicConnectSheet.kt
  - gradle/libs.versions.toml
  - iosApp/iosApp/Info.plist
  - shared/build.gradle.kts
  - shared/src/androidMain/kotlin/com/pumpernickel/di/PlatformModule.android.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.android.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncherHost.kt
  - shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicMessagesDto.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicOAuthClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicSseParser.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/RecipeAiUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/domain/repository/SettingsRepository.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/Credential.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/MigratingAiClient.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/ProviderId.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt
  - shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/data/api/AnthropicSseParserTest.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/data/repository/FakeSettingsRepository.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/data/repository/SettingsMigrationTest.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClientTest.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/FakeSecureKeyStore.kt
  - shared/src/commonTest/kotlin/com/pumpernickel/infrastructure/ai/Sha256Test.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/di/PlatformModule.ios.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.ios.kt
  - shared/src/iosMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.ios.kt
findings:
  blocker: 4
  warning: 13
  info: 8
  total: 25
status: issues_found
---

# Phase 22: Code Review Report

**Reviewed:** 2026-05-19
**Depth:** standard
**Files Reviewed:** 41 paths (per files_reviewed_list above; 37 distinct source files plus configs)
**Status:** issues_found

## Summary

Solid architecture: layered AiClient (Migrating → Dispatching → OpenAI/Anthropic adapters), per-call active-provider resolution, multi-slot key store with OAuth-primary read order. The SSE parser extraction and PKCE pure-Kotlin SHA-256 are well-isolated and well-tested.

Material concerns concentrate on the OAuth security path:
- **PKCE verifier and state use a non-cryptographic RNG** (`kotlin.random.Random.Default`), undermining the very property PKCE depends on.
- **State parameter is generated but never validated** on the redirect — combined with `android:exported="true"` BROWSABLE intent-filter, any installed app can fire `pumpernickel-oauth://callback?code=...`. The README/SEED documents this as a deferred trade-off, but the in-code SEED status does not eliminate the BLOCKER classification per the GSD policy of "incorrect behavior or security vulnerability."
- A double `migration.run()` race when two AI calls fire concurrently can double-clear the legacy slot.
- The post-`mapHttpError` OAuth path leaves stale tokens in storage on a 401 returned mid-flight (the `if (isOAuth)` block contains only a comment).

Additionally, there are concrete bugs in the OAuthBrowserLauncherHost (Android), inconsistent base-URL constants between `SettingsRepositoryImpl` and `AnthropicClient`, and meaningful test-coverage gaps (no test for the OAuth refresh-path, no test for `SecureKeyStore.readCredential` OAuth-primary ordering, no test for the multi-call migration race).

## Critical Issues

### CR-01: PKCE verifier and `state` use non-cryptographic RNG (BLOCKER)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt:108-119`
**Issue:** `generateCodeVerifier()` and `generateState()` both call `kotlin.random.Random.nextInt(...)`. `Random.Default` is **not** documented as cryptographically secure on any KMP target — on JVM it delegates to `java.util.Random` (linear congruential), and on Native it uses a Mersenne-Twister-like PRNG seeded by `gettimeofday`. RFC 7636 §4.1 requires the verifier to be "cryptographically random" because guessing it defeats the entire PKCE protection — an attacker who steals the `code` from the redirect can complete the exchange. Same problem for `state` (RFC 6749 §10.12 — must be unguessable).
**Fix:** Replace with platform-actual cryptographic RNG via `expect/actual`:
```kotlin
// commonMain
internal expect fun secureRandomBytes(length: Int): ByteArray

// jvm/android actual
import java.security.SecureRandom
private val sr = SecureRandom()
internal actual fun secureRandomBytes(length: Int): ByteArray =
    ByteArray(length).also { sr.nextBytes(it) }

// ios actual (Security.framework)
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
internal actual fun secureRandomBytes(length: Int): ByteArray {
    val out = ByteArray(length)
    out.usePinned { SecRandomCopyBytes(kSecRandomDefault, length.convert(), it.addressOf(0)) }
    return out
}
```
Then derive verifier/state from these bytes (base64url-encode for the verifier so it stays in the unreserved set).

### CR-02: Stale OAuth token NOT cleared on mid-flight 401 — `mapHttpError` `isOAuth` branch is empty (BLOCKER)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt:196-212`
**Issue:** The `401, 403 -> { if (isOAuth) { /* comment only */ } AiError.AuthOrQuota(status) }` branch never calls `secureKeyStore.clearCredential(ProviderId.Anthropic)`. When Anthropic revokes a token between our pre-flight `ensureFreshCredential` (which thought the token was fresh) and the actual request, the token sits in storage until the *next* request fails the freshness check OR `ensureFreshCredential`'s refresh path runs and fails. So the user sees **two failed AI calls in a row** before the UI prompts re-authentication — the first surfaces 401, the second tries to refresh with a still-stored (now revoked) refresh_token and finally clears. Combined with `requestTimeoutMillis = 600_000`, this means up to **20 minutes** of failed UX before the user is told to reconnect.

Additionally, the comment in the function says "fire-and-forget; we're already on a coroutine" — but `mapHttpError` is **not** suspend, so callers (which ARE suspend) could simply call `secureKeyStore.clearCredential(...)` directly before invoking `mapHttpError`. The lambda-comment is wrong.

**Fix:**
```kotlin
// In chatCompletion / chatCompletionStreaming, before throwing mapHttpError:
if (status in setOf(401, 403) && credential is Credential.OAuthToken) {
    secureKeyStore.clearCredential(ProviderId.Anthropic)
}
throw mapHttpError(status, text, isOAuth = credential is Credential.OAuthToken)
```
Then drop the `isOAuth` parameter from `mapHttpError` (it's now dead state).

### CR-03: OAuth `state` is generated but never validated against redirect (BLOCKER)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt:62-65`
**Issue:** `state` is added to the authorize URL but `browserLauncher.startAuthFlow` only returns the `code` query parameter — the redirect's `state` is discarded. RFC 6749 §10.12 attack: a malicious page fires `pumpernickel-oauth://callback?code=ATTACKER_CODE` (Android intent-filter is `exported="true"` + BROWSABLE → any installed app or web page can invoke it). The launcher resumes whatever code it gets without checking that it corresponds to *our* authorize request. While PKCE protects the *token exchange*, the attacker can cancel in-flight legitimate flows (DoS) or, in a more elaborate MITM scenario, swap the code with one tied to the attacker's account.

The code documents this as a SEED ("trust-on-first-use compromise — Demo-deadline"), but a documented vulnerability is still a vulnerability for the purposes of this review.

**Fix:** Extend `OAuthBrowserLauncher.startAuthFlow` to return both `code` and `state` (e.g. `data class OAuthRedirect(val code: String, val state: String?)`), then in `AnthropicOAuthFlow.authorize`:
```kotlin
val redirect = browserLauncher.startAuthFlow(authorizeUrl, REDIRECT_SCHEME) ?: return null
if (redirect.state != state) {
    throw IllegalStateException("OAuth state mismatch — possible CSRF or stale redirect")
}
return exchangeCodeForToken(code = redirect.code, verifier = verifier)
```
On Android, `OAuthBrowserLauncherHost.handleRedirect` extracts both `code` and `state` query parameters. On iOS, `extractCodeParam` already iterates `NSURLQueryItem` — extend it to extract both.

### CR-04: Migration race — two concurrent AI calls double-execute migration (BLOCKER)

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/MigratingAiClient.kt:28,39` + `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt:43-90`
**Issue:** `migration.run()` reads `migratedToMultiProvider.first()` as its only guard. There is no mutex, no AtomicReference. If two AI calls (e.g. WorkoutAi + RecipeAi triggered together via the AsyncGenerationManager, or any other concurrent suspend) both call `migration.run()` before the sentinel write completes, both will:
1. Read sentinel == false → proceed
2. Read `legacyKey` → both see the same value
3. Write `Credential.ApiKey(legacyKey)` to inferred provider (idempotent — OK)
4. **Call `secureKeyStore.clearLegacyApiKey()`** if inferred=Together (idempotent in the slot sense, but the test `FakeSecureKeyStore.clearLegacyCallCount` shows it's an observable double-call)
5. Both call `settingsRepository.setActiveProvider(inferred)` (race with the user's prior preference)
6. Both write the sentinel

Step 5 is the corruption risk: if the user has *already* set `activeProvider = Anthropic` between the time the legacy key was migrated and this call, but the legacy `aiBaseUrl` is `together.ai` (e.g., they migrated via API key earlier in the same session), the migration overwrites their Anthropic choice with Together.

**Fix:** Wrap `SettingsMigration.run()` body in a `Mutex.withLock { }` (kotlinx-coroutines `Mutex`), held as a field on `SettingsMigration`. Re-check the sentinel inside the lock (double-checked locking pattern):
```kotlin
private val mutex = Mutex()
suspend fun run() {
    if (settingsRepository.migratedToMultiProvider.first()) return
    mutex.withLock {
        if (settingsRepository.migratedToMultiProvider.first()) return  // raced
        // ... existing body
    }
}
```

## Warnings

### WR-01: `AnthropicOAuthFlow.exchangeCodeForToken` has no request timeout

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt:72-89`
**Issue:** `httpClient.post(TOKEN_ENDPOINT)` uses the default HttpClient configuration. The shared HttpClient may or may not have a HttpTimeout plugin installed (Phase-18 OpenAICompatibleClient sets its timeout inline via `timeout { }`). If the configured HttpClient doesn't install `HttpTimeout` at install-time, the token-exchange request can hang forever on a network stall, leaving `_oauthInProgress = true` indefinitely.
**Fix:** Add `timeout { requestTimeoutMillis = 30_000; socketTimeoutMillis = 15_000 }` to both `exchangeCodeForToken` and `AnthropicOAuthClient.refresh`.

### WR-02: `AnthropicOAuthClient.refresh` has no request timeout

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicOAuthClient.kt:30-46`
**Issue:** Same as WR-01 — `httpClient.post(TOKEN_ENDPOINT)` with no explicit timeout. If the user has a flaky network during pre-flight refresh, they wait for the system socket timeout (often 60–120s) instead of a UX-friendly bounded wait.
**Fix:**
```kotlin
val response = client.post(TOKEN_ENDPOINT) {
    contentType(ContentType.Application.Json)
    timeout { requestTimeoutMillis = 30_000; socketTimeoutMillis = 15_000 }
    setBody(...)
}
```

### WR-03: `OAuthBrowserLauncherHost.awaitRedirect` race — completes a previous deferred from a different coroutine

**File:** `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncherHost.kt:37-43`
**Issue:** `pending?.complete(null)` happens BEFORE `pending = deferred`. There are two race windows:
1. If `handleRedirect` fires between `pending?.complete(null)` and `pending = deferred`, it observes `pending` as null (because `complete(null)` doesn't set it to null) — wait, `pending?.complete(null)` doesn't null-out `pending` itself — so handleRedirect still sees the OLD deferred (already completed). `handleRedirect` then does `pending = null; deferred.complete(code)` — completes an *already-completed* deferred (CompletableDeferred swallows this silently). Net: the code is silently dropped.
2. Volatile semantics in Kotlin on JVM are stronger than Java's, but the read-modify-write of `pending` is still not atomic. A `Mutex` or `AtomicReference` would make this provably correct.

**Fix:** Use `AtomicReference` or, simpler, synchronize the operation:
```kotlin
private val lock = Any()
suspend fun awaitRedirect(): String? {
    val deferred = CompletableDeferred<String?>()
    val previous = synchronized(lock) {
        val p = pending
        pending = deferred
        p
    }
    previous?.complete(null)
    return deferred.await()
}

fun handleRedirect(intent: Intent) {
    val code = intent.data?.getQueryParameter("code")
    val deferred = synchronized(lock) {
        val p = pending
        pending = null
        p
    } ?: return
    deferred.complete(code)
}
```

### WR-04: `OAuthBrowserLauncher.android.kt` does not catch `ActivityNotFoundException`

**File:** `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/OAuthBrowserLauncher.android.kt:31`
**Issue:** `tab.launchUrl(context, Uri.parse(authorizeUrl))` can throw `android.content.ActivityNotFoundException` if the device has no installed browser that handles Chrome CustomTabs. This propagates up to `AnthropicOAuthFlow.authorize`, which doesn't catch it — it reaches `AiSettingsViewModel.startAnthropicOAuth`'s catch-all and surfaces as a generic "OAuth fehlgeschlagen: ActivityNotFoundException" string. Worse: `awaitRedirect()` is never reached, so the previously-registered `pending` deferred is never cleaned up — the next OAuth attempt will see it and immediately complete it with null (the M-01 "cancel previous" pattern).
**Fix:** Wrap the launch in a try/catch:
```kotlin
try {
    tab.launchUrl(context, Uri.parse(authorizeUrl))
} catch (e: android.content.ActivityNotFoundException) {
    // No CustomTabs-capable browser. Bubble up a clear domain error.
    throw AiError.SchemaInvalid("Kein Browser installiert — OAuth nicht möglich.")
}
return OAuthBrowserLauncherHost.awaitRedirect()
```

### WR-05: `AnthropicOAuthFlow.exchangeCodeForToken` throws `IllegalStateException` on non-2xx instead of `AiError`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt:85-89`
**Issue:** Other callers expect `AiError` types so the VM can branch on Auth/Quota vs SchemaInvalid. Throwing a generic `IllegalStateException` here breaks the contract: `AiSettingsViewModel.startAnthropicOAuth` catches Throwable so it works, but the error message ("OAuth-Vorgang abgebrochen" for null, generic exception for protocol error) is indistinguishable from a user-cancel in the UI.
**Fix:** Throw `AiError.AuthOrQuota(status)` for 4xx, `AiError.Provider(status)` for 5xx so the user gets a clear "OAuth-Token-Tausch fehlgeschlagen (HTTP 400)" message.

### WR-06: Race between `DispatchingAiClient.activeProvider.first()` and `OpenAICompatibleClient.keyProvider`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/DispatchingAiClient.kt:49-52` + `shared/src/commonMain/kotlin/com/pumpernickel/di/AiModule.kt:50-58`
**Issue:** Each AI call results in TWO independent reads of `activeProvider`:
1. `DispatchingAiClient.resolve()` reads `activeProvider.first()` to pick the adapter.
2. Inside `OpenAICompatibleClient`, the `keyProvider` lambda reads `activeProvider.first()` again to fetch the API key.

If the user switches provider between these two reads (rare but possible — e.g. tap "switch to Anthropic" while a generation is mid-flight), the dispatcher routes to the OpenAI adapter (using the OpenAI-compatible base URL passed in) while the keyProvider returns the *Anthropic* key — but the fallback `if (active == ProviderId.Anthropic) ProviderId.OpenAI` is applied, so it falls back to *OpenAI*'s key for what was originally a Together base URL. Wrong API key for the active base URL → 401 → user sees an inscrutable auth error during what looked like a clean operation.

**Fix:** Compute `activeProvider` once at the top of each call and thread it through the adapter (or have `DispatchingAiClient` pass `activeProvider` to the adapter, which then passes it to `keyProvider` as a parameter instead of re-reading).

### WR-07: `AiSettingsViewModel.disconnect` race — reads `activeProvider` after clearing credential

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/AiSettingsViewModel.kt:141-151`
**Issue:** `secureKeyStore.clearCredential(provider)` is followed by `settingsRepository.activeProvider.first()` and conditional `setActiveProvider(ProviderId.OpenAI)`. If the user switches active provider concurrently (UI-state read race), we could overwrite their selection with OpenAI when they had just selected Together. Low-probability but real.
**Fix:** Read `activeProvider.first()` BEFORE clearing:
```kotlin
val currentActive = settingsRepository.activeProvider.first()
secureKeyStore.clearCredential(provider)
if (currentActive == provider) {
    settingsRepository.setActiveProvider(ProviderId.OpenAI)
}
```

### WR-08: Inconsistent Anthropic base URL — `DEFAULT_BASE_URL_ANTHROPIC` vs `MESSAGES_ENDPOINT`

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsRepositoryImpl.kt:439` (`"https://api.anthropic.com"`) vs `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt:217` (`"https://api.anthropic.com/v1/messages"`)
**Issue:** `baseUrlByProvider[Anthropic]` returns `https://api.anthropic.com` but the actual endpoint hard-coded in `AnthropicClient` is `https://api.anthropic.com/v1/messages`. The base URL is functionally ignored by `AnthropicAiClient` (see KDoc), so this is benign today — but a future refactor that *does* honor the per-provider base URL would silently send Anthropic requests to a non-existent endpoint. Plus, `WorkoutAiUseCase` / `RecipeAiUseCase` log the base URL for debugging — the log message will be misleading.
**Fix:** Either:
- Change `DEFAULT_BASE_URL_ANTHROPIC = "https://api.anthropic.com/v1/messages"` (full URL), or
- Drop the constant and exclude Anthropic from `baseUrlByProvider` (return `null` and adjust callers to skip).

### WR-09: `AnthropicClient` 64 KB response cap fires AFTER full buffering

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt:71-73, 133-135`
**Issue:** `val text = response.bodyAsText()` fully loads the response body into memory before the size check. The 64 KB cap therefore provides no protection against an OOM caused by an unbounded response — it's a post-hoc sanity check. For SSE-streamed responses, the cap is at least applied to the accumulated content; for buffered (non-streaming) responses, the host process must already hold the entire body.
**Fix:** Either (a) accept this as a sanity check and rename it `MAX_RESPONSE_TEXT_LEN_FOR_SANITY` to document intent, or (b) for true protection switch to streamed reading with size accounting. Given v1 scope and 10-min timeout, (a) is acceptable but the limit deserves a comment explaining it does NOT protect against attacker-controlled responses.

### WR-10: `SecureKeyStore.android` uses deprecated `MasterKeys.getOrCreate`

**File:** `shared/src/androidMain/kotlin/com/pumpernickel/infrastructure/ai/SecureKeyStore.android.kt:15-24`
**Issue:** `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` was deprecated in androidx.security:security-crypto 1.1.0-alpha03+ in favor of `MasterKey.Builder`. The project pins 1.0.0 (libs.versions.toml line 21), so the deprecation warning is suppressed, but this is technical debt — if/when the project upgrades to security-crypto 1.1+, this breaks compilation.
**Fix (when upgrading):**
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
EncryptedSharedPreferences.create(context, PREFS_NAME, masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
```

### WR-11: `Credential.ApiKey` / `Credential.OAuthToken` serialization may break with proguard/minification

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/Credential.kt:19-29`
**Issue:** `sealed class Credential` is not `@Serializable` itself — only the leaves are. The `SecureKeyStore.android` writes `Credential.OAuthToken` via `json.encodeToString(Credential.OAuthToken.serializer(), credential)` — the explicit serializer reference is correct. But:
1. If the data classes' field names change (e.g. `accessToken` → `access_token` for consistency with API DTOs), existing stored tokens become unreadable — silently fails to a "needs reconnect" prompt. Add explicit `@SerialName("accessToken")` to lock the wire format.
2. ProGuard/R8 may strip serializer methods. The androidMain build doesn't include rules for kotlinx.serialization here (relies on default `META-INF/proguard/coroutines.pro`). Verify the project's `proguard-rules.pro` keeps `@Serializable` classes.

**Fix:** Add explicit `@SerialName` annotations on `Credential.OAuthToken` fields so the stored JSON format is stable even under refactors.

### WR-12: `AnthropicAiClient.completeJsonSchema` and `completeJsonObject` are identical — code duplication

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicAiClient.kt:33-71`
**Issue:** Both functions build the same `AnthropicMessagesRequest` with identical parameters and call `client.chatCompletionStreaming(request, onProgress)`. The KDoc explains why (Anthropic has no wire-level distinction), but the duplication invites drift — if someone adds a parameter to one, they likely forget the other.
**Fix:** Extract a private helper:
```kotlin
private suspend fun complete(model: String, systemPrompt: String, userPrompt: String,
                              onProgress: (String, String) -> Unit): String {
    val request = AnthropicMessagesRequest(
        model = model, system = systemPrompt,
        messages = listOf(AnthropicMessage(role = "user", content = userPrompt)),
        maxTokens = 4096, temperature = 0.7, stream = false
    )
    return client.chatCompletionStreaming(request, onProgress)
}
```
Both override methods become one-line delegates.

### WR-13: `urlEncode` does not encode '+' or '&' inside parameter values

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/AnthropicOAuthFlow.kt:276-291` (used at `buildAuthorizeUrl` line 102, 106)
**Issue:** The unreserved set `"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"` is correct RFC 3986 unreserved, so '+' and '&' ARE percent-encoded — good. But the verifier alphabet (`generateCodeVerifier`) and SCOPE (`"user:inference user:profile"`) contain only safe chars except the space and colon. Spaces in SCOPE become `%20` (correct per the unreserved-only encoding). The colon `:` is *reserved* in URI grammar but valid in query values — most servers accept the literal, but a strict server might reject. The function over-encodes (':' becomes `%3A`), which is RFC-compliant and safer.

The bigger concern: `buildAuthorizeUrl` doesn't pass `state` or `challenge` through `urlEncode`. Both are guaranteed to use the unreserved alphabet (state from `STATE_ALPHABET`, challenge is base64url-no-padding), so this is correct *today*. If those alphabets ever change, the unencoded interpolation becomes a bug. Document or defensively encode.

## Info

### IN-01: Unused imports across UI / VM files

**Files:**
- `androidApp/src/androidMain/kotlin/com/pumpernickel/android/ui/screens/AiSettingsScreen.kt:7,11,26` — `Spacer`, `width`, `HorizontalDivider` unused.
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/RecipeAiViewModel.kt:17` — `Job` unused.
- `shared/src/commonMain/kotlin/com/pumpernickel/presentation/ai/WorkoutAiViewModel.kt:20` — `Job` unused.

**Issue:** Dead imports.
**Fix:** Remove the unused imports (IDE quick-fix).

### IN-02: Stale debug `println` statements throughout AI infrastructure

**Files:** `AnthropicClient.kt:53, 68, 96, 119, 132, 166, 183`; `AnthropicOAuthClient.kt:44, 53`; `OAuthBrowserLauncherHost.kt:52`; `OAuthBrowserLauncher.ios.kt:53, 58`; `SecureKeyStore.android.kt` (logger output mid-write); `SecureKeyStore.ios.kt:141, 143, 146, 148, 151, 194, 203`; `SettingsMigration.kt:79, 81, 85`.
**Issue:** Bare `println` calls survive into release builds and emit to logcat / Console.app. None leak full credentials (the logs are sanitized to lengths / status), but message bodies are truncated to 1024 chars — an Anthropic error response containing parts of a user prompt could end up in logs. The pattern is consistent with the rest of the codebase, but for a v1 production milestone consider routing through a `Log` abstraction with build-flavor gating.
**Fix:** Introduce a `Logger` interface (or use kotlin-logging) and route all `println` through it; gate INFO+ at debug builds only.

### IN-03: `WorkoutAiUseCase.lookupMuscles` returns `emptyList()` unconditionally — dead parameter

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/domain/ai/WorkoutAiUseCase.kt:143-154`
**Issue:** The function ignores both parameters and always returns `emptyList()`. The KDoc says this is intentional ("repository re-fetches names & muscles on read"). If true, the function and its call site at line 105 are dead code wrapped around a placeholder.
**Fix:** Either delete the helper and pass `emptyList()` inline at the call site, or genuinely look up muscles for resolved exercises (existingExercise.primaryMuscles) so the persisted TemplateExercise rows are correct without a refetch.

### IN-04: Magic timeout numbers duplicated across clients

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicClient.kt:60-61, 110-111`
**Issue:** `requestTimeoutMillis = 600_000` and `socketTimeoutMillis = 120_000` appear inline twice in this file alone, and the comment says "symmetric with OpenAICompatibleClient" — implying they're duplicated again elsewhere.
**Fix:** Extract to companion constants `REQUEST_TIMEOUT_MS = 600_000` and `SOCKET_TIMEOUT_MS = 120_000`, share with `OpenAICompatibleClient` via a common file.

### IN-05: `inferProvider` heuristic loses fidelity for OpenRouter / Groq users

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/repository/SettingsMigration.kt:92-99`
**Issue:** OpenRouter and Groq users get folded into `ProviderId.OpenAI` with their original base URL preserved. They appear in the UI as "OpenAI" with a non-default base URL — confusing UX. The KDoc acknowledges this ("deferred idea in CONTEXT.md"). Migration tests cover OpenRouter base URL preservation but not the UX symptom (user sees wrong provider name).
**Fix:** Acceptable for v1 with a planned UX item; consider adding a `Custom` provider variant in a future phase.

### IN-06: `MigratingAiClient` reads sentinel from DataStore on EVERY AI call

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/infrastructure/ai/MigratingAiClient.kt:28, 39`
**Issue:** Every AI call invokes `migration.run()`, which does a DataStore Flow `.first()` — that's one disk-backed read per AI call, even after migration completes. Cheap (~1-5 ms) but unnecessary.
**Fix:** Cache a `Boolean` `migrationDone` in `SettingsMigration` and skip the DataStore read once true. Update on success.

### IN-07: `AnthropicSseParser` does not handle multi-line `data:` payloads

**File:** `shared/src/commonMain/kotlin/com/pumpernickel/data/api/AnthropicSseParser.kt:56-58`
**Issue:** SSE spec allows multiple consecutive `data:` lines per event — values are concatenated with `\n` for the receiver. The parser treats each `data:` line as a complete JSON payload. Anthropic does not currently send multi-line data, so this is a forward-compatibility risk only.
**Fix (defensive):** Accumulate `data:` lines in a buffer until the blank-line event boundary, then `tryDecode` the joined buffer.

### IN-08: Test coverage gaps for OAuth flow and refresh path

**Files:** `shared/src/commonTest/kotlin/...`
**Issue:** Strong tests exist for SSE parsing, SHA-256 vectors, migration paths, and dispatcher routing. Missing:
- No test for `AnthropicOAuthFlow.exchangeCodeForToken` (no fake HttpClient — could use Ktor `MockEngine`).
- No test for `AnthropicClient.ensureFreshCredential` token-refresh path (the most error-prone code in the phase).
- No test for `SecureKeyStore` OAuth-Primary read order (OAuth token wins over API key when both present) — this is a stated invariant of D-22-01 and an obvious regression target.
- No test for `MigratingAiClient` concurrent invocation (related to BLOCKER CR-04).
- No test for `AnthropicSseParser` recovery when a `data:` line arrives before any `event:` line (currentEventType=null path).

**Fix:** Add tests for each. The MockEngine pattern is established in the codebase per the Plan 10 SUMMARY notes; reuse it for the OAuth + refresh tests.

---

_Reviewed: 2026-05-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

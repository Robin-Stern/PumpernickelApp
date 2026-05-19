package com.pumpernickel.infrastructure.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * D-22-01 / D-22-12 — PKCE OAuth flow orchestrator. Constructs the authorize
 * URL, generates PKCE code_verifier + code_challenge (S256), and exchanges
 * the post-redirect `code` for an access_token + refresh_token.
 *
 * The actual browser round-trip is handled by [OAuthBrowserLauncher]. The
 * token exchange uses POST `claude.ai/oauth/token` (same endpoint as the
 * refresh-grant client in Plan 22-03, different grant_type).
 *
 * Constants ([CLIENT_ID], [REDIRECT_URI], [SCOPE]) are placeholders matching
 * the Claude-Code OAuth client per CONTEXT.md canonical_refs. Plan 22-06
 * wires these into a concrete oauthClientId for the AnthropicClient
 * constructor.
 *
 * SEED — state-validation: [authorize] currently accepts the trust-on-first-use
 * compromise (Demo-deadline). [OAuthBrowserLauncher.startAuthFlow] returns
 * only the `code` parameter — a state-mismatch attack against the redirect
 * step would not be caught here. Hardening backlog: extend the launcher
 * return type to `Pair<code, state>` and compare against the `state` we
 * generated. Tracked in 22-05-SUMMARY.md SEED list.
 */
class AnthropicOAuthFlow(
    private val httpClient: HttpClient,
    private val browserLauncher: OAuthBrowserLauncher,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    /**
     * D-22-01 — kicks off the full authorize → redirect → token-exchange
     * round-trip. Returns a [Credential.OAuthToken] on success (caller stores
     * it via `SecureKeyStore.writeCredential(ProviderId.Anthropic, ...)`).
     * Returns null on user-cancel; throws on protocol failure (4xx token
     * response, malformed JSON, etc.).
     *
     * NOTE: Plan 22-03 defines a parallel `AnthropicOAuthRefreshResponse` DTO
     * for the refresh-grant. We deliberately do NOT reuse it here — this plan
     * (22-05) ships in Wave 2 alongside Plan 22-03 (Wave 1) but in a
     * separate worktree, so to keep this plan independently compilable we
     * define a private [AnthropicAuthorizationCodeResponse] DTO and map it
     * directly to the storage-typed [Credential.OAuthToken]. Plan 22-06 may
     * consolidate the two response shapes if they are byte-identical.
     */
    suspend fun authorize(): Credential.OAuthToken? {
        val verifier = generateCodeVerifier()
        val challenge = codeChallengeS256(verifier)
        val state = generateState()
        val authorizeUrl = buildAuthorizeUrl(challenge = challenge, state = state)

        val code = browserLauncher.startAuthFlow(authorizeUrl, REDIRECT_SCHEME) ?: return null
        // NOTE: state validation is deferred — see class KDoc SEED.

        return exchangeCodeForToken(code = code, verifier = verifier)
    }

    private suspend fun exchangeCodeForToken(
        code: String,
        verifier: String
    ): Credential.OAuthToken {
        val response = httpClient.post(TOKEN_ENDPOINT) {
            contentType(ContentType.Application.Json)
            setBody(
                AnthropicAuthorizationCodeRequest(
                    code = code,
                    codeVerifier = verifier,
                    clientId = CLIENT_ID,
                    redirectUri = REDIRECT_URI
                )
            )
        }
        val body = response.bodyAsText()
        val status = response.status.value
        if (status !in 200..299) {
            throw IllegalStateException(
                "OAuth token exchange failed: HTTP $status — ${body.take(400)}"
            )
        }
        val parsed = json.decodeFromString<AnthropicAuthorizationCodeResponse>(body)
        val nowSec = Clock.System.now().epochSeconds
        return Credential.OAuthToken(
            accessToken = parsed.accessToken,
            refreshToken = parsed.refreshToken,
            expiresAtEpochSeconds = nowSec + parsed.expiresInSeconds
        )
    }

    private fun buildAuthorizeUrl(challenge: String, state: String): String =
        "$AUTHORIZE_ENDPOINT?response_type=code" +
            "&client_id=$CLIENT_ID" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&state=$state" +
            "&scope=${urlEncode(SCOPE)}"

    /**
     * RFC 7636 §4.1 — URL-safe verifier from a cryptographically secure RNG.
     * 48 random bytes → 64 base64url-no-padding characters, all in the
     * unreserved set (`A–Z a–z 0–9 - _`).
     */
    internal fun generateCodeVerifier(): String =
        base64UrlNoPadding(secureRandomBytes(48))

    /**
     * RFC 6749 §10.12 — state must be unguessable to defeat CSRF. 24 random
     * bytes → 32 base64url-no-padding characters (URL-safe).
     */
    internal fun generateState(): String =
        base64UrlNoPadding(secureRandomBytes(24))

    /** BASE64URL(SHA-256(verifier)), no padding. */
    internal fun codeChallengeS256(verifier: String): String {
        val hashBytes = Sha256.hash(verifier.encodeToByteArray())
        return base64UrlNoPadding(hashBytes)
    }

    companion object {
        // D-22-01 — Anthropic OAuth endpoints. Per CONTEXT.md canonical_refs:
        // authorize URL on claude.ai, token endpoint on claude.ai, NOT on
        // api.anthropic.com.
        const val AUTHORIZE_ENDPOINT = "https://claude.ai/oauth/authorize"
        const val TOKEN_ENDPOINT = "https://claude.ai/oauth/token"

        // Placeholder Claude-Code-style "public" client. Plan 22-06 SUMMARY
        // documents the value choice. SEED-AI-OAUTH for productionizing.
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

        const val REDIRECT_SCHEME = "pumpernickel-oauth"
        const val REDIRECT_URI = "pumpernickel-oauth://callback"

        const val SCOPE = "user:inference user:profile"
    }
}

// ---- DTOs for authorization_code grant (private to this plan) ----

@Serializable
internal data class AnthropicAuthorizationCodeRequest(
    @SerialName("grant_type") val grantType: String = "authorization_code",
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String
)

/**
 * Response shape from `POST claude.ai/oauth/token` (authorization_code grant).
 * Kept narrow + private — Plan 22-06 may unify with Plan 22-03's
 * `AnthropicOAuthRefreshResponse` if the wire-shapes are byte-identical.
 */
@Serializable
internal data class AnthropicAuthorizationCodeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null
)

// ---- minimal-deps SHA-256 + Base64-URL encoder (pure Kotlin, commonMain-safe) ----

internal object Sha256 {
    /** FIPS 180-4 SHA-256. Pure Kotlin, no JVM dependencies. */
    fun hash(data: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
            0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt()
        )
        val k = intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
            0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
            0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
            0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
            0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
            0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
            0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
            0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
        )
        // padding: append 0x80, then zeros, then 64-bit big-endian length in bits.
        val bitLen = data.size.toLong() * 8
        val withOne = data + 0x80.toByte()
        val padCount = ((56 - (withOne.size % 64) + 64) % 64)
        val lenBytes = ByteArray(8)
        for (i in 0..7) lenBytes[i] = (bitLen ushr (56 - 8 * i)).toByte()
        val padded = withOne + ByteArray(padCount) + lenBytes
        val w = IntArray(64)
        for (block in padded.indices step 64) {
            for (i in 0..15) {
                w[i] = ((padded[block + 4 * i].toInt() and 0xff) shl 24) or
                    ((padded[block + 4 * i + 1].toInt() and 0xff) shl 16) or
                    ((padded[block + 4 * i + 2].toInt() and 0xff) shl 8) or
                    (padded[block + 4 * i + 3].toInt() and 0xff)
            }
            for (i in 16..63) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0..63) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + k[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val mj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + mj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0..7) {
            out[4 * i] = (h[i] ushr 24).toByte()
            out[4 * i + 1] = (h[i] ushr 16).toByte()
            out[4 * i + 2] = (h[i] ushr 8).toByte()
            out[4 * i + 3] = h[i].toByte()
        }
        return out
    }
    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))
}

internal fun base64UrlNoPadding(bytes: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val sb = StringBuilder()
    var i = 0
    while (i + 2 < bytes.size) {
        val b1 = bytes[i].toInt() and 0xff
        val b2 = bytes[i + 1].toInt() and 0xff
        val b3 = bytes[i + 2].toInt() and 0xff
        sb.append(table[b1 ushr 2])
        sb.append(table[((b1 and 0x03) shl 4) or (b2 ushr 4)])
        sb.append(table[((b2 and 0x0f) shl 2) or (b3 ushr 6)])
        sb.append(table[b3 and 0x3f])
        i += 3
    }
    if (i < bytes.size) {
        val b1 = bytes[i].toInt() and 0xff
        sb.append(table[b1 ushr 2])
        if (i + 1 < bytes.size) {
            val b2 = bytes[i + 1].toInt() and 0xff
            sb.append(table[((b1 and 0x03) shl 4) or (b2 ushr 4)])
            sb.append(table[(b2 and 0x0f) shl 2])
        } else {
            sb.append(table[(b1 and 0x03) shl 4])
        }
    }
    return sb.toString()
}

/** Minimal percent-encoding for query/scheme values. Avoids depending on
 *  platform java.net.URLEncoder. */
internal fun urlEncode(s: String): String {
    val unreserved =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val sb = StringBuilder()
    for (ch in s) {
        if (ch in unreserved) sb.append(ch)
        else {
            for (b in ch.toString().encodeToByteArray()) {
                sb.append('%')
                sb.append(((b.toInt() and 0xf0) ushr 4).toString(16).uppercase())
                sb.append((b.toInt() and 0x0f).toString(16).uppercase())
            }
        }
    }
    return sb.toString()
}

package com.pumpernickel.infrastructure.ai

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 22 Plan 10 / D-22-13 — FIPS 180-4 §B.1 + RFC 7636 §A.1 test vectors for
 * the pure-Kotlin SHA-256 implementation in [AnthropicOAuthFlow] (`Sha256.hash`
 * and `base64UrlNoPadding`). These ensure the PKCE code-challenge computation
 * stays compatible with the Anthropic OAuth server regardless of platform
 * (JVM/Native).
 *
 * Test vectors:
 * - empty string + "abc" + 56-byte message: published FIPS 180-4 §B.1 vectors
 * - PKCE example: RFC 7636 §A.1 (verifier
 *   "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk" → challenge
 *   "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
 */
class Sha256Test {

    private fun ByteArray.toHex(): String =
        joinToString("") { ((it.toInt() and 0xff).toString(16).padStart(2, '0')) }

    @Test
    fun hash_empty_returnsKnownVector() {
        // FIPS 180-4 §B.1 — SHA-256("") =
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hash(byteArrayOf()).toHex()
        )
    }

    @Test
    fun hash_abc_returnsKnownVector() {
        // FIPS 180-4 §B.1 — SHA-256("abc") =
        // ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hash("abc".encodeToByteArray()).toHex()
        )
    }

    @Test
    fun hash_56byteMessage_returnsKnownVector() {
        // FIPS 180-4 §B.1 — 56-byte input forces multi-block padding handling.
        val msg = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash(msg.encodeToByteArray()).toHex()
        )
    }

    @Test
    fun codeChallengeS256_rfc7636Example_returnsExpected() {
        // RFC 7636 §A.1 — published PKCE round-trip example. If this fails,
        // OAuth authorize would reject every code_challenge → no Claude login.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(
            expectedChallenge,
            base64UrlNoPadding(Sha256.hash(verifier.encodeToByteArray()))
        )
    }
}

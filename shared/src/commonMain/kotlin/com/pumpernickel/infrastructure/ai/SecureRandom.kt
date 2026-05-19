package com.pumpernickel.infrastructure.ai

/**
 * D-22-01 — Cryptographically secure random bytes for PKCE verifier and OAuth
 * state generation. RFC 7636 §4.1 requires the verifier to be cryptographically
 * random; RFC 6749 §10.12 requires state to be unguessable.
 *
 * Platform actuals:
 *  - Android/JVM: `java.security.SecureRandom`
 *  - iOS:        `Security.framework` `SecRandomCopyBytes(kSecRandomDefault, ...)`
 *
 * Do NOT use `kotlin.random.Random.Default` for this — on JVM it delegates to a
 * linear congruential generator and on Native it uses a Mersenne-Twister-like
 * PRNG seeded by `gettimeofday`; neither is cryptographically secure.
 */
internal expect fun secureRandomBytes(length: Int): ByteArray

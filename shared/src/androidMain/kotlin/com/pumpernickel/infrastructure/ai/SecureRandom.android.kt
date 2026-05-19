package com.pumpernickel.infrastructure.ai

import java.security.SecureRandom

private val secureRandom = SecureRandom()

internal actual fun secureRandomBytes(length: Int): ByteArray {
    val out = ByteArray(length)
    secureRandom.nextBytes(out)
    return out
}
